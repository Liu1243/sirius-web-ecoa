"""Runtime orchestration for distributed debug containers."""

import ipaddress
import json
import logging
import os
import subprocess
import time
import uuid
from dataclasses import dataclass, replace
from pathlib import Path
from pathlib import PurePosixPath, PureWindowsPath
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

from app.services.distributed_debug import (
    COMPOSE_FILENAME,
    COMPOSE_PROJECT_NAME,
    DebugProcess,
    DebugTopology,
    collect_debug_topology,
    container_binary_dir,
    gdbserver_command,
    plain_launch_command,
    render_distributed_debug_compose,
    write_distributed_debug_launch_json,
)


DEFAULT_CLIENT_CONTAINER = os.environ.get("ECOA_DISTRIBUTED_DEBUG_CLIENT_CONTAINER", "code-server")
COMPOSE_NETWORK_NAME = f"{COMPOSE_PROJECT_NAME}_ecoa_debug_net"
# Seconds to wait for gdbserver to bind its port before declaring the start failed.
GDBSERVER_READINESS_TIMEOUT_SECS = 10
RUNTIME_COMPOSE_FILENAME = "distributed-debug.runtime.compose.yml"
SESSION_FILENAME = "distributed-debug.session.json"
# Per-session metadata files: distributed-debug.{session_id}.session.json
# These avoid the legacy single-file problem where restarting a project overwrites
# the shared session.json, making list_all_containers return empty metadata for
# all older sessions that still have Docker networks alive.
SESSION_FILENAME_TEMPLATE = "distributed-debug.{session_id}.session.json"
SESSION_SUBNET_PREFIX = "172.29"

_DOCKER_HOST_CANDIDATES = [
    "unix:///var/run/docker.sock",
    "tcp://host.docker.internal:2375",
    "tcp://host.docker.internal:2376",
]


class DistributedDebugRuntimeError(RuntimeError):
    """Raised when distributed debug orchestration fails."""


@dataclass(frozen=True)
class DistributedDebugContext:
    """Resolved filesystem and Docker context for distributed debug."""

    target_dir: Path
    build_dir: Path
    compose_file: Path
    topology: DebugTopology
    per_platform_build_dirs: Optional[Dict[str, str]] = None


@dataclass(frozen=True)
class DistributedDebugSession:
    session_id: str
    compose_project_name: str
    network_name: str
    docker_subnet: str
    compose_file: Path
    client_container: Optional[str]
    topology: DebugTopology


class DistributedDebugRuntime:
    """Start, stop, and inspect distributed debug compose stacks."""

    def __init__(self, default_client_container: Optional[str] = None):
        self.default_client_container = default_client_container or DEFAULT_CLIENT_CONTAINER
        self._docker_host_checked: Optional[str] = None

    def _ensure_docker_available(self) -> str:
        """Detect a working Docker connection and set DOCKER_HOST accordingly.

        Tries candidates in order, caches the result.  Raises
        DistributedDebugRuntimeError when no connection can be established.
        """
        if self._docker_host_checked is not None:
            return self._docker_host_checked

        explicit_host = os.environ.get("DOCKER_HOST")
        candidates = []
        if explicit_host:
            candidates.append(explicit_host)
        candidates.extend(candidate for candidate in _DOCKER_HOST_CANDIDATES if candidate != explicit_host)

        last_error = ""
        for candidate in candidates:
            os.environ["DOCKER_HOST"] = candidate
            try:
                subprocess.run(
                    ["docker", "info", "--format", "{{.ServerVersion}}"],
                    capture_output=True,
                    text=True,
                    check=True,
                    timeout=10,
                )
                self._docker_host_checked = candidate
                return candidate
            except FileNotFoundError:
                last_error = "Docker CLI is not installed in the ecoa-tools container."
                break
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
                stderr = getattr(exc, "stderr", "") or str(exc)
                last_error = f"DOCKER_HOST={candidate}: {stderr}"
                continue

        # Restore the original setting so we don't leave a newly tried broken value behind.
        if explicit_host:
            os.environ["DOCKER_HOST"] = explicit_host
        else:
            os.environ.pop("DOCKER_HOST", None)
        raise DistributedDebugRuntimeError(
            f"Could not connect to Docker daemon. Tried: {', '.join(candidates)}. "
            f"Last error: {last_error}. "
            "Solutions: "
            "1) Mount /var/run/docker.sock into the ecoa-tools container; "
            "2) If using a TCP Docker API, set DOCKER_HOST to a reachable endpoint; "
            "3) Ensure Docker daemon is running on the host."
        )

    def start(self, target_dir: str, client_container: Optional[str] = None, project_id: Optional[str] = None, project_name: Optional[str] = None, user_id: Optional[str] = None, username: Optional[str] = None, target_arch: str = "native") -> Dict[str, Any]:
        self._ensure_docker_available()
        context = self._resolve_context(target_dir, target_arch=target_arch)
        client_name = client_container or self.default_client_container
        session = self._create_session(context, client_name, project_id=project_id, project_name=project_name, user_id=user_id, username=username)
        compose_result = self._run_command(self._compose_command(session, "up", "-d", "--remove-orphans"))
        client_connected = False

        if client_name:
            if not self._container_connected_to_network(client_name, session.network_name):
                self._run_command(["docker", "network", "connect", session.network_name, client_name])
            client_connected = True

        is_multi_platform = getattr(session.topology, 'protocol', None) is not None

        def _effective_build_dir(process: DebugProcess) -> str:
            if context.per_platform_build_dirs and process.node_id in context.per_platform_build_dirs:
                return context.per_platform_build_dirs[process.node_id]
            return str(context.build_dir)

        if is_multi_platform:
            # In multi-platform mode the `platform` binary must start first so it can
            # bind the ELI port (e.g. 30000) and the PD lifecycle port (30010) BEFORE
            # the PD processes try to connect. Running it free (no gdbserver) also avoids
            # the port-30000 bind conflict that would occur if both platform and PD ran
            # under gdbserver simultaneously.
            for process in session.topology.processes:
                if process.name != "platform":
                    continue
                self._run_command(
                    self._compose_command(
                        session, "exec", "-T", process.service_name,
                        "bash", "-lc",
                        plain_launch_command(_effective_build_dir(process), process),
                    )
                )
            # Give platform binaries time to initialize before PD processes connect.
            time.sleep(2)

        for process in session.topology.processes:
            if is_multi_platform and process.name == "platform":
                continue  # already started above
            # For multi-platform builds: use the per-platform build dir when one is
            # available for this process's node (platform name).  Fall back to the
            # single shared build_dir for flat / single-platform layouts.
            self._run_command(
                self._compose_command(
                    session,
                    "exec",
                    "-T",
                    process.service_name,
                    "bash",
                    "-lc",
                    gdbserver_command(_effective_build_dir(process), process),
                )
            )
            # Verify that gdbserver actually bound its port before declaring success.
            # The launch command uses 'nohup ... &' which returns exit code 0 even when
            # gdbserver fails (e.g. binary not found), so we must poll the port explicitly.
            self._wait_for_gdbserver_ready(session, process, _effective_build_dir(process))

        running_services = self._compose_services(session, "ps", "--services", "--status", "running")
        return {
            "success": True,
            "target_dir": str(context.target_dir),
            "build_dir": str(context.build_dir),
            "compose_file": str(session.compose_file),
            "network_name": session.network_name,
            "session_id": session.session_id,
            "compose_project_name": session.compose_project_name,
            "docker_subnet": session.docker_subnet,
            "client_container": client_name,
            "client_connected": client_connected,
            "running_services": running_services,
            "stdout": compose_result.stdout.strip(),
            "stderr": compose_result.stderr.strip(),
        }

    def _stop_by_session_id(self, session_id: str, client_container: Optional[str] = None) -> Dict[str, Any]:
        """Stop a debug session identified only by session_id.

        Used when target_dir is unknown (e.g. the container record had an empty
        target_dir because its session metadata file was missing at list-time).
        Looks up compose_project_name from the per-session metadata file and
        runs ``docker compose down`` directly, bypassing _load_or_create_session
        so that we never accidentally stop the *wrong* (newer) session.
        """
        info = self._find_session_info(session_id)
        if not info:
            raise FileNotFoundError(f"Session {session_id} not found in workspace")

        compose_project_name = info.get("compose_project_name", "")
        network_name = info.get("network_name", "")
        target_dir = info.get("target_dir", "")
        compose_file_path = info.get("compose_file", "")
        client_name = client_container or info.get("client_container") or self.default_client_container

        # Disconnect client container from the debug network
        if client_name and network_name and self._network_exists(network_name):
            if self._container_connected_to_network(client_name, network_name):
                try:
                    self._run_command(["docker", "network", "disconnect", network_name, client_name])
                except Exception as exc:
                    logger.warning("_stop_by_session_id: failed to disconnect %s from %s: %s", client_name, network_name, exc)

        # Build compose down command. Include -f only when the compose file exists.
        cmd = ["docker", "compose"]
        if compose_project_name:
            cmd += ["--project-name", compose_project_name]
        if compose_file_path and Path(compose_file_path).exists():
            cmd += ["-f", compose_file_path]
        cmd += ["down", "--remove-orphans"]

        compose_result = self._run_command(cmd)
        return {
            "success": True,
            "target_dir": target_dir,
            "session_id": session_id,
            "compose_project_name": compose_project_name,
            "network_name": network_name,
            "client_container": client_name,
            "stopped": True,
            "stdout": compose_result.stdout.strip(),
            "stderr": compose_result.stderr.strip(),
        }

    def stop(self, target_dir: str, client_container: Optional[str] = None, session_id: Optional[str] = None) -> Dict[str, Any]:
        self._ensure_docker_available()

        # Fast path: when target_dir is empty but session_id is known, stop the
        # compose stack directly by compose_project_name without going through
        # _load_or_create_session (which reads the legacy shared session file and
        # could load a *different* session's data if multiple starts have run).
        if not target_dir and session_id:
            logger.info("stop: target_dir empty, using session_id=%s direct stop path", session_id)
            return self._stop_by_session_id(session_id, client_container)

        if not target_dir:
            raise ValueError("target_dir is required (or provide session_id to resolve it automatically)")

        context = self._resolve_context(target_dir)
        session = self._load_or_create_session(context, client_container or self.default_client_container)
        client_name = client_container or session.client_container or self.default_client_container

        if client_name and self._network_exists(session.network_name) and self._container_connected_to_network(client_name, session.network_name):
            self._run_command(["docker", "network", "disconnect", session.network_name, client_name])

        compose_result = self._run_command(self._compose_command(session, "down", "--remove-orphans"))
        return {
            "success": True,
            "target_dir": str(context.target_dir),
            "build_dir": str(context.build_dir),
            "compose_file": str(session.compose_file),
            "network_name": session.network_name,
            "session_id": session.session_id,
            "compose_project_name": session.compose_project_name,
            "docker_subnet": session.docker_subnet,
            "client_container": client_name,
            "stopped": True,
            "stdout": compose_result.stdout.strip(),
            "stderr": compose_result.stderr.strip(),
        }

    def status(self, target_dir: str, client_container: Optional[str] = None) -> Dict[str, Any]:
        self._ensure_docker_available()
        context = self._resolve_context(target_dir)
        session = self._load_or_create_session(context, client_container or self.default_client_container)
        client_name = client_container or session.client_container or self.default_client_container
        configured_services = self._compose_services(session, "config", "--services")
        running_services = self._compose_services(session, "ps", "--services", "--status", "running")
        client_connected = False

        if client_name and self._network_exists(session.network_name):
            client_connected = self._container_connected_to_network(client_name, session.network_name)

        return {
            "success": True,
            "target_dir": str(context.target_dir),
            "build_dir": str(context.build_dir),
            "compose_file": str(session.compose_file),
            "network_name": session.network_name,
            "session_id": session.session_id,
            "compose_project_name": session.compose_project_name,
            "docker_subnet": session.docker_subnet,
            "client_container": client_name,
            "client_connected": client_connected,
            "configured_services": configured_services,
            "running_services": running_services,
            "started": bool(running_services),
        }

    def _resolve_context(self, target_dir: str, target_arch: str = "native") -> DistributedDebugContext:
        if not target_dir:
            raise ValueError("target_dir is required")

        target_path = Path(target_dir).resolve()
        if not target_path.exists():
            raise FileNotFoundError(f"Target directory not found: {target_dir}")

        compose_file = target_path / ".vscode" / COMPOSE_FILENAME
        if not compose_file.exists():
            raise FileNotFoundError(f"Distributed debug compose file not found: {compose_file}")

        # Discover per-platform build dirs (6-output/{Platform}/build/bin/platform).
        per_platform_build_dirs = self._find_per_platform_build_dirs(target_path)

        # Resolve the single "primary" build dir used for compose file rendering and
        # backward-compat paths.  When the classic flat layout exists, use it;
        # otherwise fall back to the first per-platform dir so we never leave
        # build_dir undefined.
        try:
            build_dir = self._find_build_dir(target_path)
        except FileNotFoundError:
            if per_platform_build_dirs:
                # Use the alphabetically first platform's build dir as the primary.
                build_dir = Path(next(iter(sorted(per_platform_build_dirs.values()))))
                logger.info(
                    "_resolve_context: no flat build dir found; using per-platform primary %s",
                    build_dir,
                )
            else:
                raise

        topology = collect_debug_topology(str(target_path), str(build_dir), target_arch=target_arch)
        if topology is None or not topology.is_distributed:
            raise FileNotFoundError(f"Distributed debug topology metadata not found under: {target_path}")

        return DistributedDebugContext(
            target_dir=target_path,
            build_dir=build_dir,
            compose_file=compose_file,
            topology=topology,
            per_platform_build_dirs=per_platform_build_dirs if per_platform_build_dirs else None,
        )

    def _find_build_dir(self, target_dir: Path) -> Path:
        # Check architecture-specific build dirs first, then fall back to the
        # default "build" dir used for native compilation.
        direct_candidates = [
            target_dir / "6-Output" / "build-arm64",
            target_dir / "6-output" / "build-arm64",
            target_dir / "6-Output" / "build-amd64",
            target_dir / "6-output" / "build-amd64",
            target_dir / "6-Output" / "build",
            target_dir / "6-output" / "build",
            target_dir / "build-arm64",
            target_dir / "build-amd64",
            target_dir / "build",
        ]
        for candidate in direct_candidates:
            if (candidate / "bin" / "platform").exists():
                return candidate

        for candidate in sorted(target_dir.rglob("build*")):
            if candidate.is_dir() and (candidate / "bin" / "platform").exists():
                return candidate

        raise FileNotFoundError(f"Build directory with platform binary not found under: {target_dir}")

    def _find_per_platform_build_dirs(self, target_dir: Path) -> Dict[str, str]:
        """Scan 6-output/{Platform_Name}/build/bin/platform to find per-platform build dirs.

        Returns {platform_name: build_dir} for each platform that has a compiled binary.
        """
        result: Dict[str, str] = {}
        for candidate_output in ("6-output", "6-Output"):
            output_root = target_dir / candidate_output
            if not output_root.exists():
                continue
            for pf_dir in output_root.iterdir():
                if not pf_dir.is_dir():
                    continue
                build_candidate = pf_dir / "build"
                binary_candidate = build_candidate / "bin" / "platform"
                if binary_candidate.exists():
                    result[pf_dir.name] = str(build_candidate)
            if result:
                break
        return result

    def _compose_file_for_runtime(
        self,
        context: DistributedDebugContext,
        client_container: Optional[str],
        session: DistributedDebugSession,
    ) -> Path:
        host_project_dir = ".."
        if str(context.target_dir).startswith("/workspace/"):
            resolved_host_project_dir = self._resolve_host_project_dir(context.target_dir, client_container)
            if resolved_host_project_dir:
                host_project_dir = resolved_host_project_dir
        debug_image = self._resolve_debug_image(client_container)
        return self._write_runtime_compose_file(
            target_dir=context.target_dir,
            build_dir=context.build_dir,
            topology=session.topology,
            host_project_dir=host_project_dir,
            debug_image=debug_image,
            compose_project_name=session.compose_project_name,
            network_name=session.network_name,
            runtime_compose_file=session.compose_file,
        )

    def _write_runtime_compose_file(
        self,
        target_dir: Path,
        build_dir: Path,
        topology: DebugTopology,
        host_project_dir: str,
        debug_image: Optional[str] = None,
        compose_project_name: str = COMPOSE_PROJECT_NAME,
        network_name: str = COMPOSE_NETWORK_NAME,
        output_dir: Optional[Path] = None,
        runtime_compose_file: Optional[Path] = None,
    ) -> Path:
        compose_output_dir = output_dir or (target_dir / ".vscode")
        compose_output_dir.mkdir(parents=True, exist_ok=True)
        runtime_compose_file = runtime_compose_file or (compose_output_dir / RUNTIME_COMPOSE_FILENAME)
        runtime_compose_file.write_text(
            render_distributed_debug_compose(
                str(build_dir),
                topology,
                project_mount_source=host_project_dir,
                debug_image=debug_image,
                compose_project_name=compose_project_name,
                network_name=network_name,
            ),
            encoding="utf-8",
        )
        return runtime_compose_file

    def _session_file(self, target_dir: Path) -> Path:
        """Return the legacy shared session file path (used by stop/status for the current session)."""
        return target_dir / ".vscode" / SESSION_FILENAME

    def _session_file_for(self, target_dir: Path, session_id: str) -> Path:
        """Return the per-session metadata file path for a specific session ID."""
        filename = SESSION_FILENAME_TEMPLATE.format(session_id=session_id)
        return target_dir / ".vscode" / filename

    def _create_session(self, context: DistributedDebugContext, client_container: Optional[str], project_id: Optional[str] = None, project_name: Optional[str] = None, user_id: Optional[str] = None, username: Optional[str] = None) -> DistributedDebugSession:
        session_id = uuid.uuid4().hex[:8]
        compose_project_name = f"{COMPOSE_PROJECT_NAME}-{session_id}"
        network_name = f"{compose_project_name}_ecoa_debug_net"
        docker_subnet, runtime_topology = self._resolve_subnet_and_topology(context.topology)
        compose_file = context.target_dir / ".vscode" / f"distributed-debug.{session_id}.runtime.compose.yml"
        session = DistributedDebugSession(
            session_id=session_id,
            compose_project_name=compose_project_name,
            network_name=network_name,
            docker_subnet=docker_subnet,
            compose_file=compose_file,
            client_container=client_container,
            topology=runtime_topology,
        )
        runtime_compose = self._compose_file_for_runtime(context, client_container, session)
        session = replace(session, compose_file=runtime_compose)
        self._write_session_metadata(context.target_dir, session, project_id=project_id, project_name=project_name, user_id=user_id, username=username)
        write_distributed_debug_launch_json(str(context.target_dir), str(context.build_dir), session.topology)
        return session

    def _load_or_create_session(self, context: DistributedDebugContext, client_container: Optional[str]) -> DistributedDebugSession:
        session = self._read_session_metadata(context.target_dir, context.topology)
        if session is not None:
            return session
        return self._create_session(context, client_container)

    def _write_session_metadata(self, target_dir: Path, session: DistributedDebugSession, project_id: Optional[str] = None, project_name: Optional[str] = None, user_id: Optional[str] = None, username: Optional[str] = None) -> None:
        from datetime import datetime, timezone
        metadata = {
            "session_id": session.session_id,
            "compose_project_name": session.compose_project_name,
            "network_name": session.network_name,
            "docker_subnet": session.docker_subnet,
            "compose_file": str(session.compose_file),
            "client_container": session.client_container,
            "project_id": project_id,
            "project_name": project_name,
            "user_id": user_id,
            "username": username,
            "target_arch": session.topology.target_arch,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "topology": {
                "integration_dir": session.topology.integration_dir,
                "docker_subnet": session.topology.docker_subnet,
                "is_distributed": session.topology.is_distributed,
                "processes": [
                    {
                        "name": process.name,
                        "node_id": process.node_id,
                        "host": process.host,
                        "port": process.port,
                        "service_name": process.service_name,
                        "target_arch": process.target_arch,
                    }
                    for process in session.topology.processes
                ],
            },
        }
        metadata_json = json.dumps(metadata, indent=2)

        # Write per-session file: distributed-debug.{session_id}.session.json
        # Each session keeps its own file so that list_all_containers can always
        # retrieve metadata even after subsequent starts overwrite the legacy file.
        per_session_file = self._session_file_for(target_dir, session.session_id)
        per_session_file.parent.mkdir(parents=True, exist_ok=True)
        per_session_file.write_text(metadata_json, encoding="utf-8")

        # Also write the legacy shared file so that stop() / status() (which call
        # _read_session_metadata → _session_file) can still find the current session.
        legacy_file = self._session_file(target_dir)
        legacy_file.write_text(metadata_json, encoding="utf-8")

    def _read_session_metadata(self, target_dir: Path, fallback_topology: DebugTopology) -> Optional[DistributedDebugSession]:
        session_file = self._session_file(target_dir)
        if not session_file.exists():
            return None

        session_data = json.loads(session_file.read_text(encoding="utf-8"))
        topology_data = session_data.get("topology") or {}
        processes = [
            DebugProcess(
                name=process["name"],
                node_id=process["node_id"],
                host=process["host"],
                port=process["port"],
                service_name=process["service_name"],
                target_arch=process.get("target_arch", "native"),
            )
            for process in topology_data.get("processes", [])
        ]
        runtime_topology = DebugTopology(
            integration_dir=topology_data.get("integration_dir", fallback_topology.integration_dir),
            docker_subnet=topology_data.get("docker_subnet", fallback_topology.docker_subnet),
            processes=processes or fallback_topology.processes,
            is_distributed=topology_data.get("is_distributed", fallback_topology.is_distributed),
            target_arch=session_data.get("target_arch", fallback_topology.target_arch),
        )
        return DistributedDebugSession(
            session_id=session_data["session_id"],
            compose_project_name=session_data["compose_project_name"],
            network_name=session_data["network_name"],
            docker_subnet=session_data["docker_subnet"],
            compose_file=Path(session_data["compose_file"]),
            client_container=session_data.get("client_container"),
            topology=runtime_topology,
        )

    def _resolve_subnet_and_topology(self, topology: DebugTopology) -> tuple:
        """Prefer the user-specified subnet and IPs from the binding file.

        TCP mode — IP addresses are baked into the platform binary at compile time
        (via route.h generated from tcp-params.xml). Remapping IPs would cause TCP
        connection failures because the binary still tries to connect to the
        original IPs. For TCP, we MUST use the exact IPs or raise a clear error.

        For UDP/DDS — IPs are for container network membership only (the actual
        inter-platform traffic uses multicast groups / DDS domain IDs). Remapping
        is safe and transparent.
        """
        desired_subnet = topology.docker_subnet
        is_tcp = (getattr(topology, 'protocol', None) == 'TCP')

        if desired_subnet:
            occupied = self._existing_debug_subnets()
            if not self._subnet_overlaps_any(desired_subnet, occupied):
                logger.info("Using subnet %s with original IPs", desired_subnet)
                return desired_subnet, topology

            if is_tcp:
                # TCP IPs are compiled into the binary — we cannot remap silently.
                # Try to create the network anyway; Docker may allow it if the conflict
                # is only with an inactive/down network. If not, the user must update
                # tcp-params.xml to use a non-conflicting subnet.
                logger.warning(
                    "[TCP] Subnet %s is occupied. TCP platform IPs are compile-time baked "
                    "and cannot be remapped. Attempting to use original IPs anyway. "
                    "If Docker rejects the network, update tcp-params.xml to use a "
                    "non-conflicting subnet (e.g. 172.20.x.x) and rebuild.",
                    desired_subnet,
                )
                return desired_subnet, topology

            logger.warning(
                "Subnet %s is already occupied; attempting same-family fallback",
                desired_subnet,
            )
            same_family_subnet = self._allocate_same_family_subnet(desired_subnet, occupied)
            if same_family_subnet:
                logger.info("Same-family fallback subnet %s allocated", same_family_subnet)
                return same_family_subnet, self._runtime_topology_for_subnet(topology, same_family_subnet)
            logger.warning(
                "No free same-family subnet found for %s; falling back to 172.29.x.x",
                desired_subnet,
            )

        fallback_subnet = self._allocate_docker_subnet()
        logger.info("Auto-allocated subnet %s for distributed debug session", fallback_subnet)
        return fallback_subnet, self._runtime_topology_for_subnet(topology, fallback_subnet)

    def _allocate_docker_subnet(self) -> str:
        occupied = self._existing_debug_subnets()
        # Try multiple private /16 ranges so a single broad existing network
        # (e.g. a corporate 172.29.0.0/16 overlay) cannot exhaust allocation.
        # 172.29 first (matches prior behaviour), then 172.30/172.31, then 10.x.
        fallback_prefixes = ["172.29", "172.30", "172.31", "10.200", "10.201"]
        for prefix in fallback_prefixes:
            for subnet_index in range(1, 255):
                candidate = f"{prefix}.{subnet_index}.0/24"
                if not self._subnet_overlaps_any(candidate, occupied):
                    return candidate
        raise DistributedDebugRuntimeError(
            "No free distributed debug subnet available in fallback ranges "
            "(172.29/30/31.0.0/16, 10.200/201.0.0/16). Prune unused Docker "
            "networks: docker network prune")

    def _allocate_same_family_subnet(self, desired_subnet: str, occupied: set) -> Optional[str]:
        """Try to find a free /24 subnet in the same /16 range as the desired subnet.

        This preserves the last octet (host portion) of user-specified IPs
        when the original /24 subnet is occupied but another /24 in the
        same /16 range is free.

        For example, if the user specified 192.168.10.x and 192.168.10.0/24
        is occupied, this will try 192.168.{1..255}.0/24 until a free one
        is found.

        Returns None if no free subnet can be found in the same /16 range.
        """
        parts = desired_subnet.split("/")
        if len(parts) != 2:
            return None
        prefix_len = int(parts[1])
        octets = parts[0].split(".")
        if len(octets) != 4:
            return None

        # Only handle /24 subnets within a /16 range for now
        if prefix_len != 24:
            return None

        base_prefix = ".".join(octets[:2])  # e.g. "192.168"
        original_third = int(octets[2])

        # Try the same third octet first (already checked by caller), then
        # search outward from the original position.
        candidates = [original_third]
        for offset in range(1, 128):
            higher = original_third + offset
            lower = original_third - offset
            if higher <= 255:
                candidates.append(higher)
            if lower >= 1:
                candidates.append(lower)

        for third_octet in candidates:
            if third_octet == original_third:
                continue  # already checked by caller
            candidate = f"{base_prefix}.{third_octet}.0/24"
            if not self._subnet_overlaps_any(candidate, occupied):
                return candidate

        return None

    def _existing_debug_subnets(self) -> List[str]:
        """Return all subnets currently in use by Docker networks.

        Previously this only returned 172.29.x.x subnets which meant that
        conflicts with user-specified subnets in other ranges (e.g.
        192.168.x.x) were never detected, causing Docker Compose to either
        fail or silently assign a different IP to the container.
        """
        try:
            result = subprocess.run(
                ["docker", "network", "ls", "--format", "{{.Name}}"],
                capture_output=True,
                text=True,
                check=False,
            )
        except FileNotFoundError as exc:
            raise DistributedDebugRuntimeError(
                "Docker CLI is unavailable in ecoa-tools. Install Docker and mount /var/run/docker.sock."
            ) from exc
        if result.returncode != 0:
            raise DistributedDebugRuntimeError(self._format_command_error(result, ["docker", "network", "ls", "--format", "{{.Name}}"]))

        subnets: List[str] = []
        for network_name in [line.strip() for line in result.stdout.splitlines() if line.strip()]:
            inspect_result = subprocess.run(
                ["docker", "network", "inspect", network_name],
                capture_output=True,
                text=True,
                check=False,
            )
            if inspect_result.returncode != 0:
                continue
            network_info = json.loads(inspect_result.stdout or "[]")
            if not network_info:
                continue
            ipam_config = network_info[0].get("IPAM", {}).get("Config") or []
            for config in ipam_config:
                subnet = config.get("Subnet")
                if subnet:
                    subnets.append(subnet)
        return subnets

    @staticmethod
    def _parse_network(subnet: str) -> Optional[ipaddress._BaseNetwork]:
        """Parse a CIDR string into an ipaddress network, tolerating bad input."""
        try:
            return ipaddress.ip_network(subnet, strict=False)
        except (ValueError, TypeError):
            return None

    def _subnet_overlaps_any(self, subnet: str, occupied: List[str]) -> bool:
        """True if `subnet` overlaps ANY network in `occupied` (real CIDR check).

        This replaces exact-string equality.  A candidate /24 like
        172.29.1.0/24 must be rejected if an existing network holds a broader
        172.29.0.0/16 — string equality misses that and Docker then fails with
        "Pool overlaps with other one on this address space".
        """
        candidate = self._parse_network(subnet)
        if candidate is None:
            return True  # unparseable request — treat as unsafe, force re-alloc
        for existing in occupied:
            existing_net = self._parse_network(existing)
            if existing_net is not None and candidate.overlaps(existing_net):
                return True
        return False

    def _runtime_topology_for_subnet(self, topology: DebugTopology, docker_subnet: str) -> DebugTopology:
        subnet_prefix = ".".join(docker_subnet.split("/")[0].split(".")[:3])
        host_mapping: Dict[str, str] = {}
        used_host_octets: set = set()
        remapped_processes = []

        # First pass: try to preserve each host's original last octet
        for process in topology.processes:
            if process.host in host_mapping:
                continue
            original_octets = process.host.split(".")
            if len(original_octets) == 4:
                preferred_octet = int(original_octets[3])
                if preferred_octet not in used_host_octets and 1 <= preferred_octet <= 254:
                    host_mapping[process.host] = f"{subnet_prefix}.{preferred_octet}"
                    used_host_octets.add(preferred_octet)
                    continue
            # Fallback: find the next available octet starting from 10
            for octet in range(10, 255):
                if octet not in used_host_octets:
                    host_mapping[process.host] = f"{subnet_prefix}.{octet}"
                    used_host_octets.add(octet)
                    break

        for process in topology.processes:
            remapped_processes.append(replace(process, host=host_mapping[process.host]))
        return replace(topology, docker_subnet=docker_subnet, processes=remapped_processes)

    def _resolve_host_project_dir(self, target_dir: Path, client_container: Optional[str]) -> Optional[str]:
        if not client_container:
            return None

        try:
            container_info = self._inspect_container(client_container)
        except DistributedDebugRuntimeError:
            return None

        return self._host_path_for_target_dir(target_dir, container_info.get("Mounts", []))

    def _resolve_debug_image(self, client_container: Optional[str]) -> Optional[str]:
        if not client_container:
            return None

        try:
            container_info = self._inspect_container(client_container)
        except DistributedDebugRuntimeError:
            return None

        return container_info.get("Config", {}).get("Image")

    def _host_path_for_target_dir(self, target_dir: Path, mounts: List[Dict[str, Any]]) -> Optional[str]:
        target_path = PurePosixPath(target_dir.as_posix())

        for mount in mounts:
            destination = mount.get("Destination")
            source = mount.get("Source")
            if not destination or not source:
                continue

            destination_path = PurePosixPath(destination)
            try:
                relative_target = target_path.relative_to(destination_path)
            except ValueError:
                continue

            if len(source) >= 2 and source[1] == ":":
                return str(PureWindowsPath(source) / PureWindowsPath(*relative_target.parts))

            return str(Path(source) / Path(*relative_target.parts))

        return None

    def _compose_command(self, session: DistributedDebugSession, *args: str) -> List[str]:
        return [
            "docker",
            "compose",
            "--project-name",
            session.compose_project_name,
            "-f",
            str(session.compose_file),
            *args,
        ]

    def _compose_services(self, session: DistributedDebugSession, *args: str) -> List[str]:
        result = self._run_command(self._compose_command(session, *args))
        return [line.strip() for line in result.stdout.splitlines() if line.strip()]

    def _network_exists(self, network_name: str) -> bool:
        try:
            result = subprocess.run(
                ["docker", "network", "inspect", network_name],
                capture_output=True,
                text=True,
                check=False,
            )
        except FileNotFoundError as exc:
            raise DistributedDebugRuntimeError(
                "Docker CLI is unavailable in ecoa-tools. Install Docker and mount /var/run/docker.sock."
            ) from exc
        if result.returncode == 0:
            return True
        if "No such network" in (result.stderr or "") or "not found" in (result.stderr or ""):
            return False
        raise DistributedDebugRuntimeError(
            f"Unable to inspect Docker network {network_name}: {self._format_command_error(result)}"
        )

    def _inspect_container(self, container_name: str) -> Dict[str, Any]:
        result = self._run_command(["docker", "inspect", container_name])
        container_info = json.loads(result.stdout or "[]")
        if not container_info:
            raise DistributedDebugRuntimeError(f"Docker container not found: {container_name}")
        return container_info[0]

    def _container_connected_to_network(self, container_name: str, network_name: str) -> bool:
        container_info = self._inspect_container(container_name)
        networks = container_info.get("NetworkSettings", {}).get("Networks", {})
        return network_name in networks

    def _run_command(self, command: List[str]) -> subprocess.CompletedProcess[str]:
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                check=False,
            )
        except FileNotFoundError as exc:
            raise DistributedDebugRuntimeError(
                "Docker CLI is unavailable in ecoa-tools. Install Docker and mount /var/run/docker.sock."
            ) from exc

        if result.returncode != 0:
            raise DistributedDebugRuntimeError(self._format_command_error(result, command))

        return result

    def _wait_for_gdbserver_ready(self, session: "DistributedDebugSession", process: DebugProcess, build_dir: str) -> None:
        """Block until gdbserver is listening on *process.port* inside *process.service_name*.

        Uses a two-probe approach so it works even when ``ss`` is unavailable:
        1. ``ss -tlnp`` (requires iproute2 — may be absent in minimal images)
        2. ``/proc/net/tcp{,6}`` — always present on Linux, no external tools needed

        Polls every 0.5 s for up to GDBSERVER_READINESS_TIMEOUT_SECS seconds.
        On timeout a full diagnosis is echoed to stderr: the gdbserver log
        (absolute path), whether the binary exists, and whether the launcher
        (gdbserver / qemu-aarch64-static) is installed — so a startup failure
        is never a black box.

        ``build_dir`` MUST be the same value passed to ``gdbserver_command`` so
        the diagnosed paths match the launch paths exactly.

        Raises:
            DistributedDebugRuntimeError: when gdbserver does not bind the port
                within the timeout.
        """
        iterations = GDBSERVER_READINESS_TIMEOUT_SECS * 2  # 0.5 s intervals
        # /proc/net/tcp stores the port as a 4-digit uppercase hex big-endian value,
        # e.g. port 2000 → "07D0".  This is computed in Python to avoid any shell
        # arithmetic in the container.
        hex_port = format(process.port, "04X")
        # Absolute in-container paths so the diagnosis works regardless of the
        # exec session's working directory.  These mirror gdbserver_command()
        # exactly: container_binary_dir(build_dir) — the compile script builds
        # every platform into 6-output/{Platform}/build/ regardless of arch.
        binary_dir = container_binary_dir(build_dir)
        log_path = f"{Path(binary_dir).parent.as_posix()}/logs/{process.name}.gdbserver.log"
        binary_path = f"{binary_dir}/{process.name}"
        launcher = "qemu-aarch64-static" if process.target_arch == "arm64" else "gdbserver"
        check_cmd = (
            f"for i in $(seq 1 {iterations}); do "
            # Primary: ss (iproute2) — most containers have it
            f"  ss -tlnp 2>/dev/null | grep -q ':{process.port}' && exit 0; "
            # Fallback: /proc/net/tcp — always available on Linux, no extra tools required.
            # The local-address field ends with ":PPPP" where PPPP is the port in hex.
            f"  grep -q ':{hex_port}' /proc/net/tcp /proc/net/tcp6 2>/dev/null && exit 0; "
            f"  sleep 0.5; "
            f"done; "
            f"echo 'gdbserver did not bind port {process.port} within "
            f"{GDBSERVER_READINESS_TIMEOUT_SECS}s' >&2; "
            f"echo '--- target_arch: {process.target_arch}' >&2; "
            f"echo '--- binary: {binary_path}' >&2; "
            f"ls -l '{binary_path}' >&2 2>&1 || echo '  (binary MISSING)' >&2; "
            f"echo '--- launcher: {launcher}' >&2; "
            f"command -v {launcher} >&2 2>&1 || echo '  ({launcher} MISSING)' >&2; "
            f"echo '--- log: {log_path}' >&2; "
            f"cat '{log_path}' >&2 2>/dev/null || echo '  (log file empty or missing)' >&2; "
            f"echo '--- recent container processes:' >&2; "
            f"ps -ef 2>/dev/null | grep -E '{process.name}|{launcher}|gdbserver' | grep -v grep >&2 2>&1 | head -5; "
            f"exit 1"
        )
        self._run_command(
            self._compose_command(session, "exec", "-T", process.service_name, "bash", "-lc", check_cmd)
        )

    @staticmethod
    def _format_command_error(result: subprocess.CompletedProcess[str], command: Optional[List[str]] = None) -> str:
        command_text = " ".join(command or result.args or [])
        stderr = (result.stderr or "").strip()
        stdout = (result.stdout or "").strip()
        details = stderr or stdout or f"exit code {result.returncode}"
        return f"Command failed: {command_text}. {details}"

    def list_all_containers(self) -> List[Dict[str, Any]]:
        """List all distributed debug containers across the system.

        Scans Docker networks to find all running distributed debug sessions
        and returns their status information.
        """
        self._ensure_docker_available()
        containers: List[Dict[str, Any]] = []

        try:
            # Get all Docker networks with ecoa debug prefix
            result = subprocess.run(
                ["docker", "network", "ls", "--format", "{{.Name}}"],
                capture_output=True,
                text=True,
                check=False,
            )
            if result.returncode != 0:
                return containers

            network_names = [line.strip() for line in result.stdout.splitlines() if line.strip()]
            debug_networks = [n for n in network_names if "ecoa-distributed-debug" in n and "ecoa_debug_net" in n]


            for network_name in debug_networks:
                try:
                    # Extract session info from network name
                    # Format: ecoa-distributed-debug-{session_id}_ecoa_debug_net
                    parts = network_name.split("_")
                    if len(parts) < 2:
                        logger.warning("list_all_containers: skipping network with invalid name format: %s", network_name)
                        continue

                    project_prefix = parts[0]  # ecoa-distributed-debug-{session_id}
                    session_id = project_prefix.replace("ecoa-distributed-debug-", "")

                    # Inspect network to get containers
                    inspect_result = subprocess.run(
                        ["docker", "network", "inspect", network_name],
                        capture_output=True,
                        text=True,
                        check=False,
                    )
                    if inspect_result.returncode != 0:
                        continue

                    network_info = json.loads(inspect_result.stdout or "[]")
                    if not network_info:
                        continue

                    # Get subnet information
                    ipam_config = network_info[0].get("IPAM", {}).get("Config") or []
                    docker_subnet = ipam_config[0].get("Subnet", "") if ipam_config else ""

                    # Get containers connected to this network
                    containers_info = network_info[0].get("Containers", {})
                    running_services = []
                    for container_id, container_data in containers_info.items():
                        container_name = container_data.get("Name", "")
                        if container_name.startswith("ecoa-"):
                            running_services.append(container_name)

                    # Check if compose file exists to get more info
                    # Search for session file in common locations
                    session_info = self._find_session_info(session_id)

                    container_data = {
                        "session_id": session_id,
                        "project_id": session_info.get("project_id") or "unknown",
                        "project_name": session_info.get("project_name") or None,
                        "user_id": session_info.get("user_id") or "unknown",
                        "username": session_info.get("username") or None,
                        "target_dir": session_info.get("target_dir", ""),
                        "compose_project_name": project_prefix,
                        "network_name": network_name,
                        "docker_subnet": docker_subnet,
                        "client_container": session_info.get("client_container", "code-server"),
                        "client_connected": session_info.get("client_connected", False),
                        "running_services": running_services,
                        "configured_services": session_info.get("configured_services", running_services),
                        "started": len(running_services) > 0,
                        "created_at": session_info.get("created_at", ""),
                    }
                    containers.append(container_data)

                except Exception as exc:
                    logger.warning("Failed to inspect network %s: %s", network_name, exc)
                    continue

        except Exception as exc:
            logger.error("Failed to list containers: %s", exc)

        return containers

    def _find_session_info(self, session_id: str) -> Dict[str, Any]:
        """Try to find session info from session files.

        Strategy (fastest first):
        1. Search for the per-session file by name: distributed-debug.{session_id}.session.json
           This is O(1) per directory and avoids reading every shared session.json.
        2. Fall back to the legacy shared file distributed-debug.session.json (only
           present when its recorded session_id matches — i.e. the session is the
           most recently started one in that workspace directory).
        """
        info: Dict[str, Any] = {}
        workspace_base = Path("/workspace")

        if not workspace_base.exists():
            return info

        def _load_from_file(session_file: Path) -> bool:
            """Read a session file and populate ``info`` if session_id matches. Returns True on match."""
            try:
                session_data = json.loads(session_file.read_text(encoding="utf-8"))
                if session_data.get("session_id") != session_id:
                    return False
                info["target_dir"] = str(session_file.parent.parent)
                info["compose_project_name"] = session_data.get("compose_project_name", "")
                info["network_name"] = session_data.get("network_name", "")
                info["docker_subnet"] = session_data.get("docker_subnet", "")
                info["client_container"] = session_data.get("client_container", "code-server")
                info["created_at"] = session_data.get("created_at", "")
                info["project_id"] = session_data.get("project_id") or self._extract_project_id_from_path(session_file)
                info["project_name"] = session_data.get("project_name") or ""
                info["user_id"] = session_data.get("user_id") or ""
                info["username"] = session_data.get("username") or ""
                return True
            except Exception as exc:
                logger.warning("_find_session_info: error reading %s: %s", session_file, exc)
                return False

        try:
            # --- Strategy 1: per-session files (O(directories) glob, no content scan) ---
            per_session_pattern = SESSION_FILENAME_TEMPLATE.format(session_id=session_id)
            per_session_files = list(workspace_base.rglob(per_session_pattern))
            for session_file in per_session_files:
                if _load_from_file(session_file):
                    return info

            # --- Strategy 2: legacy shared file (backward compat for pre-existing sessions) ---
            legacy_files = list(workspace_base.rglob(SESSION_FILENAME))
            for session_file in legacy_files:
                if _load_from_file(session_file):
                    return info

        except Exception as exc:
            logger.warning("Failed to find session info for %s: %s", session_id, exc)

        return info

    def _extract_project_id_from_path(self, session_file: Path) -> str:
        """Extract project ID from session file path."""
        parts = session_file.parts
        # Path format: /workspace/{project_id}/{component_id}/Steps/.vscode/...
        for i, part in enumerate(parts):
            if part == "workspace" and i + 1 < len(parts):
                return parts[i + 1]
        return "unknown"

    def list_user_containers(self, user_id: str, username: str) -> List[Dict[str, Any]]:
        """List distributed debug containers for a specific user.

        Returns containers that are either:
        - Attributed to this user (user_id matches), OR
        - Unattributed (user_id is empty / "unknown") — the container may have
          been started before user identity tracking was in place or without the
          ECOA_DISTRIBUTED_DEBUG_USER_ID env var set in Code Server.

        When user_id is not provided, all containers are returned (admin fallback).
        """
        all_containers = self.list_all_containers()
        if not user_id:
            return all_containers

        def _belongs_to_user(c: Dict[str, Any]) -> bool:
            cid = c.get("user_id") or ""
            return cid == user_id or cid in ("", "unknown")

        return [c for c in all_containers if _belongs_to_user(c)]

    def _inspect_network_containers(self, network_name: str) -> List[str]:
        """Return a list of container names currently attached to the given network."""
        try:
            inspect_result = subprocess.run(
                ["docker", "network", "inspect", network_name],
                capture_output=True,
                text=True,
                check=False,
            )
        except FileNotFoundError:
            return []
        if inspect_result.returncode != 0:
            return []
        network_info = json.loads(inspect_result.stdout or "[]")
        if not network_info:
            return []
        containers_info = network_info[0].get("Containers", {})
        return [v.get("Name", "") for v in containers_info.values() if v.get("Name")]

    def _network_has_active_containers(self, network_name: str) -> bool:
        """Return True only if the network exists AND has at least one ecoa- container attached.

        This mirrors the same logic used by list_all_containers to determine 'started'.
        A network that exists but has zero attached containers (or only the client
        container like code-server) means the compose stack was stopped; treat as 'stopped'.
        """
        names = self._inspect_network_containers(network_name)
        return any(name.startswith("ecoa-") for name in names)

    def _disconnect_all_from_network(self, network_name: str) -> None:
        """Disconnect every container from the network so it can be removed.

        This handles the case where the client container (e.g. code-server) was
        connected via 'docker network connect' during start and was never disconnected
        when the debug compose stack was stopped.
        """
        names = self._inspect_network_containers(network_name)
        for container_name in names:
            try:
                result = subprocess.run(
                    ["docker", "network", "disconnect", network_name, container_name],
                    capture_output=True,
                    text=True,
                    check=False,
                )
                if result.returncode == 0:
                    logger.info("Disconnected %s from network %s", container_name, network_name)
                else:
                    logger.warning(
                        "Failed to disconnect %s from %s: %s",
                        container_name, network_name, result.stderr.strip()
                    )
            except Exception as exc:
                logger.warning("Exception disconnecting %s from %s: %s", container_name, network_name, exc)

    def _remove_orphan_network(self, network_name: str) -> bool:
        """Disconnect all remaining containers then remove the network (best-effort)."""
        # First disconnect any lingering containers (e.g. code-server client)
        self._disconnect_all_from_network(network_name)
        try:
            result = subprocess.run(
                ["docker", "network", "rm", network_name],
                capture_output=True,
                text=True,
                check=False,
            )
            if result.returncode == 0:
                logger.info("Removed orphan network %s", network_name)
                return True
            logger.warning("Failed to remove network %s: %s", network_name, result.stderr.strip())
        except Exception as exc:
            logger.warning("Exception removing network %s: %s", network_name, exc)
        return False

    def delete_session(self, session_id: str) -> Dict[str, Any]:
        """Delete a stopped debug session by cleaning up its session file and compose file.

        Only sessions that are NOT running (i.e., no ecoa- containers attached to the
        debug network) can be deleted.  If the compose stack is still up the caller must
        stop it first.

        Handles two extra cases gracefully:
        - Orphan network (network exists, containers gone, session file missing):
          removes the network and returns success.
        - Stale session file (file exists, network already gone): removes the file.

        Returns a result dict with 'success', 'session_id', and optionally 'error'.
        """
        self._ensure_docker_available()

        # ── Step 1: locate the session metadata file ──────────────────────────
        # Prefer the per-session file (fast, direct name lookup) before falling
        # back to the legacy shared file (requires content-scan to match session_id).
        workspace_base = Path("/workspace")
        session_file: Optional[Path] = None
        if workspace_base.exists():
            # 1a. Per-session files: distributed-debug.{session_id}.session.json
            per_session_pattern = SESSION_FILENAME_TEMPLATE.format(session_id=session_id)
            for candidate in workspace_base.rglob(per_session_pattern):
                session_file = candidate
                break  # filename already encodes the session_id; first match is authoritative

            # 1b. Legacy shared file: distributed-debug.session.json (backward compat)
            if session_file is None:
                for candidate in workspace_base.rglob(SESSION_FILENAME):
                    try:
                        data = json.loads(candidate.read_text(encoding="utf-8"))
                        if data.get("session_id") == session_id:
                            session_file = candidate
                            break
                    except Exception:
                        continue

        # ── Step 2: if no session file, try to clean up an orphan network ─────
        if session_file is None:
            # Reconstruct the expected network name from the session_id convention:
            # ecoa-distributed-debug-{session_id}_ecoa_debug_net
            orphan_network = f"ecoa-distributed-debug-{session_id}_ecoa_debug_net"
            if self._network_exists(orphan_network):
                if self._network_has_active_containers(orphan_network):
                    return {
                        "success": False,
                        "session_id": session_id,
                        "error": f"Session {session_id} is still running. Stop it before deleting.",
                    }
                # Network is empty — remove it and report success
                self._remove_orphan_network(orphan_network)
            return {
                "success": True,
                "session_id": session_id,
                "deleted_files": [],
            }

        # ── Step 3: read session metadata ─────────────────────────────────────
        session_data = json.loads(session_file.read_text(encoding="utf-8"))
        compose_project_name = session_data.get("compose_project_name", "")
        network_name = session_data.get("network_name", "")
        compose_file_path = session_data.get("compose_file", "")

        # ── Step 4: guard — refuse to delete if containers are still running ──
        # Use the same "has active ecoa- containers" check as list_all_containers,
        # NOT just "network exists", so stopped-but-not-removed networks are fine.
        if network_name and self._network_has_active_containers(network_name):
            return {
                "success": False,
                "session_id": session_id,
                "error": f"Session {session_id} is still running. Stop it before deleting.",
            }

        # Secondary guard via docker compose ps
        if compose_project_name:
            try:
                result = subprocess.run(
                    ["docker", "compose", "--project-name", compose_project_name, "ps", "--services", "--status", "running"],
                    capture_output=True,
                    text=True,
                    check=False,
                )
                if result.returncode == 0 and result.stdout.strip():
                    return {
                        "success": False,
                        "session_id": session_id,
                        "error": f"Session {session_id} has running services. Stop it before deleting.",
                    }
            except Exception:
                pass

        # ── Step 5: clean up ──────────────────────────────────────────────────
        deleted_files: List[str] = []

        # Remove the runtime compose file
        if compose_file_path:
            compose_path = Path(compose_file_path)
            if compose_path.exists():
                try:
                    compose_path.unlink()
                    deleted_files.append(str(compose_path))
                except Exception as exc:
                    logger.warning("Failed to delete compose file %s: %s", compose_path, exc)

        # Remove the session metadata file(s).
        # Always delete the file that was found. If it was the legacy shared file,
        # also try to remove the per-session file in the same directory. (We don't
        # remove the legacy file when we found a per-session file because the legacy
        # file may belong to a newer session in the same workspace.)
        per_session_sibling = session_file.parent / SESSION_FILENAME_TEMPLATE.format(session_id=session_id)
        files_to_remove = [session_file]
        if session_file.name == SESSION_FILENAME and per_session_sibling.exists():
            files_to_remove.append(per_session_sibling)
        for candidate_file in files_to_remove:
            try:
                candidate_file.unlink()
                deleted_files.append(str(candidate_file))
            except Exception as exc:
                logger.warning("Failed to delete session file %s: %s", candidate_file, exc)

        # Remove orphan network if it still exists (stopped but not removed)
        if network_name and self._network_exists(network_name):
            self._remove_orphan_network(network_name)

        logger.info("Deleted session %s, removed files: %s", session_id, deleted_files)
        return {
            "success": True,
            "session_id": session_id,
            "deleted_files": deleted_files,
        }

    def batch_delete_sessions(self, session_ids: List[str]) -> Dict[str, Any]:
        """Delete multiple stopped debug sessions in bulk.

        Returns aggregated results with per-session success/failure details.
        """
        results: List[Dict[str, Any]] = []
        success_count = 0
        fail_count = 0

        for session_id in session_ids:
            try:
                result = self.delete_session(session_id)
                results.append(result)
                if result.get("success"):
                    success_count += 1
                else:
                    fail_count += 1
            except Exception as exc:
                fail_count += 1
                results.append({
                    "success": False,
                    "session_id": session_id,
                    "error": str(exc),
                })

        return {
            "success": fail_count == 0,
            "total": len(session_ids),
            "success_count": success_count,
            "fail_count": fail_count,
            "results": results,
        }
