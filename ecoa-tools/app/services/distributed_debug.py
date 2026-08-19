import json
import os
import stat
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional
from xml.etree import ElementTree


DEBUG_START_PORT = 2000
LOCAL_LAUNCH_NAME = "Debug platform"
COMPOUND_NAME = "Attach distributed ECOA"
COMPOSE_PROJECT_NAME = "ecoa-distributed-debug"
COMPOSE_FILENAME = "distributed-debug.compose.yml"
START_SCRIPT_FILENAME = "start-distributed-debug.sh"
STOP_SCRIPT_FILENAME = "stop-distributed-debug.sh"
STATUS_SCRIPT_FILENAME = "status-distributed-debug.sh"
COMPILE_SCRIPT_FILENAME = "compile.sh"
README_FILENAME = "readme.md"
CONTAINER_PROJECT_ROOT = "/workspace/project"


@dataclass(frozen=True)
class DebugProcess:
    name: str
    node_id: str
    host: str
    port: int
    service_name: str
    target_arch: str = "native"


@dataclass(frozen=True)
class DebugTopology:
    integration_dir: str
    docker_subnet: str
    processes: List[DebugProcess]
    is_distributed: bool
    target_arch: str = "native"
    # Multi-platform fields: protocol is 'TCP', 'UDP', 'DDS', or None (legacy single-platform)
    protocol: Optional[str] = None
    dds_domain_id: int = 0


def container_binary_dir(build_dir: str) -> str:
    """Return the in-container path to the generated binary directory."""
    build_path = Path(build_dir)
    build_parts = build_path.parts
    output_index = next(
        (index for index, part in enumerate(build_parts) if part.lower().startswith("6-output")),
        None,
    )
    if output_index is None:
        raise ValueError(f"Build directory is not under a 6-output directory: {build_dir}")

    relative_build_dir = Path(*build_parts[output_index:])
    return f"{CONTAINER_PROJECT_ROOT}/{relative_build_dir.as_posix()}/bin"


def gdbserver_command(build_dir: str, process: DebugProcess) -> str:
    """Return the shell command used to start a GDB server for a process.

    Uses ``process.target_arch`` (set from LogicalProcessor.type in the model)
    to decide between native gdbserver and QEMU user-space emulation:

    - x86_64 / native: ``gdbserver 0.0.0.0:PORT ./binary``
    - arm64:           ``qemu-aarch64-static -g PORT ./binary``

    QEMU's built-in GDB stub (-g PORT) means the debug container stays
    linux/amd64 — no kernel binfmt_misc registration is needed, and the
    setup works fully offline because qemu-aarch64-static is baked into
    the image.

    gdbserver exits after each GDB client disconnects (standard protocol).
    A ``while true`` restart loop is used so subsequent attach sessions work
    without restarting the containers.
    """
    arch = process.target_arch
    # The compile script (ldp-compile.sh) builds every platform into its own
    # 6-output/{Platform}/build/ directory regardless of target arch — each
    # platform declares exactly one arch, so there is no build-{arch} split.
    # container_binary_dir() already maps build_dir → the in-container bin path.
    # We must NOT apply _arch_build_dir() here: that would look in build-arm64/,
    # where no binary exists, causing "Binary not found" → gdbserver never binds.
    binary_dir = container_binary_dir(build_dir)
    library_dir = f"{Path(binary_dir).parent.as_posix()}/lib"

    if arch == "arm64":
        return (
            f"mkdir -p {binary_dir}/../logs && "
            f"cd {binary_dir} && "
            "command -v qemu-aarch64-static >/dev/null || "
            "{ echo 'qemu-aarch64-static not found in container' >&2; exit 1; } && "
            f"[ -f ./{process.name} ] || "
            f"{{ echo 'Binary not found: {binary_dir}/{process.name}' >&2; exit 1; }} && "
            f"export LD_LIBRARY_PATH={library_dir}:${{LD_LIBRARY_PATH:-}} && "
            f"nohup bash -c 'while true; do qemu-aarch64-static -g {process.port} ./{process.name}; sleep 1; done' "
            f"> ../logs/{process.name}.gdbserver.log 2>&1 &"
        )

    # Use standard gdbserver mode: gdbserver forks the binary (paused at first
    # instruction) before accepting GDB connections.  This means the protocol
    # exchange (vMustReplyEmpty, qSupported, ?) works correctly because gdbserver
    # always has an inferior to describe.
    #
    # A 'while true' restart loop keeps gdbserver alive for subsequent attach
    # sessions without restarting containers.  The loop is safe because gdbserver
    # pauses the binary before accepting any GDB client — the binary never runs
    # (and therefore never crashes) until a GDB client explicitly resumes it.
    return (
        f"mkdir -p {binary_dir}/../logs && "
        f"cd {binary_dir} && "
        "command -v gdbserver >/dev/null || "
        "{ echo 'gdbserver not found in container — install gdb-server package' >&2; exit 1; } && "
        f"[ -f ./{process.name} ] || "
        f"{{ echo 'Binary not found: {binary_dir}/{process.name}' >&2; exit 1; }} && "
        f"export LD_LIBRARY_PATH={library_dir}:${{LD_LIBRARY_PATH:-}} && "
        f"nohup bash -c 'while true; do gdbserver 0.0.0.0:{process.port} ./{process.name}; sleep 1; done' "
        f"> ../logs/{process.name}.gdbserver.log 2>&1 &"
    )


def plain_launch_command(build_dir: str, process: DebugProcess) -> str:
    """Return a shell command that starts a binary WITHOUT gdbserver.

    Used for ``platform`` binaries in multi-platform mode: they must start first
    and bind ELI/lifecycle ports before the PD binaries try to connect.
    Any previous instance is killed first so re-running the start script stays clean.
    """
    arch = process.target_arch
    # See gdbserver_command(): the compile script builds every platform into
    # 6-output/{Platform}/build/ regardless of arch, so use build_dir directly.
    binary_dir = container_binary_dir(build_dir)
    library_dir = f"{Path(binary_dir).parent.as_posix()}/lib"
    # arm64 binaries cannot run natively on an x86 host.  When cross-compiling
    # to arm64, wrap the platform binary with qemu-aarch64-static so it executes
    # under user-space emulation.  Native (x86_64) binaries run directly.
    if arch == "arm64":
        runner = "qemu-aarch64-static"
        runner_check = (
            f"command -v {runner} >/dev/null || "
            f"{{ echo '{runner} not found in container' >&2; exit 1; }} && "
        )
        run_prefix = f"{runner} "
    else:
        runner_check = ""
        run_prefix = ""
    return (
        f"mkdir -p {binary_dir}/../logs && "
        f"cd {binary_dir} && "
        f"[ -f ./{process.name} ] || "
        f"{{ echo 'Binary not found: {binary_dir}/{process.name}' >&2; exit 1; }} && "
        f"{runner_check}"
        f"pkill -f './{process.name}' 2>/dev/null || true && "
        f"export LD_LIBRARY_PATH={library_dir}:${{LD_LIBRARY_PATH:-}} && "
        f"nohup {run_prefix}./{process.name} > ../logs/{process.name}.log 2>&1 &"
    )


def _local_name(tag: str) -> str:
    if "}" in tag:
        return tag.rsplit("}", 1)[1]
    return tag


def _parse_node_processor_types(project_path: str) -> Dict[str, str]:
    """Parse *.logical-system.xml to extract per-node processor architecture.

    Returns a mapping of computing-node id to processor type string, e.g.
    {"machine0": "x86_64", "machine1": "arm64"}.  Returns an empty dict when
    no logical-system file is found or when the type attribute is absent.
    """
    node_types: Dict[str, str] = {}
    for ls_file in sorted(Path(project_path).rglob("*.logical-system.xml")):
        try:
            root = ElementTree.parse(ls_file).getroot()
        except ElementTree.ParseError:
            continue
        for elem in root.iter():
            if _local_name(elem.tag) != "logicalComputingNode":
                continue
            node_id = elem.get("id")
            if not node_id:
                continue
            for child in elem:
                if _local_name(child.tag) == "logicalProcessors":
                    proc_type = child.get("type", "").strip()
                    if proc_type:
                        node_types[node_id] = proc_type
                    break
    return node_types


def _parse_platform_processor_types(project_path: str) -> Dict[str, str]:
    """Parse *.logical-system.xml to extract per-platform processor architecture.

    Traverses logicalComputingPlatform → logicalComputingNode → logicalProcessors
    and returns a mapping of platform id → processor type string, e.g.
    {"Platform_Writer": "x86_64", "Platform_Reader": "arm64"}.

    When a platform has no declared processor type the platform is omitted from
    the result — callers should fall back to ``native``.
    """
    platform_types: Dict[str, str] = {}
    for ls_file in sorted(Path(project_path).rglob("*.logical-system.xml")):
        try:
            root = ElementTree.parse(ls_file).getroot()
        except ElementTree.ParseError:
            continue
        for pf_elem in root.iter():
            if _local_name(pf_elem.tag) != "logicalComputingPlatform":
                continue
            pf_id = pf_elem.get("id")
            if not pf_id:
                continue
            # Find the first logicalComputingNode with a logicalProcessors type
            for node_elem in pf_elem:
                if _local_name(node_elem.tag) != "logicalComputingNode":
                    continue
                for proc_elem in node_elem:
                    if _local_name(proc_elem.tag) == "logicalProcessors":
                        proc_type = proc_elem.get("type", "").strip()
                        if proc_type:
                            platform_types[pf_id] = proc_type
                        break
                if pf_id in platform_types:
                    break
    return platform_types


def _arch_build_dir(base_build_dir: str, target_arch: str) -> str:
    """Return the per-architecture build directory path.

    Native x86_64/amd64 builds use the default ``build/`` directory.
    Cross-compiled architectures (arm64) use ``build-{arch}/``.
    """
    if target_arch in ("native", "amd64", "x86_64", ""):
        return base_build_dir
    parent = Path(base_build_dir).parent
    return str(parent / f"build-{target_arch}")


def _sanitize_service_name(node_id: str) -> str:
    return "".join(character if character.isalnum() else "-" for character in node_id.lower()).strip("-")


def _yaml_single_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _service_name_by_host(node_hosts: Dict[str, str]) -> Dict[str, str]:
    grouped_nodes: Dict[str, List[str]] = {}
    for node_id, host in node_hosts.items():
        grouped_nodes.setdefault(host, []).append(node_id)

    service_names: Dict[str, str] = {}
    for host, node_ids in grouped_nodes.items():
        preferred_node_id = next((node_id for node_id in node_ids if node_id != "main"), node_ids[0])
        service_names[host] = f"ecoa-{_sanitize_service_name(preferred_node_id)}"
    return service_names


def _find_integration_dir(project_path: str) -> Optional[Path]:
    root = Path(project_path)
    direct_candidates = [candidate for candidate in root.iterdir() if candidate.is_dir() and candidate.name.startswith("5-Integration")]
    if direct_candidates:
        return sorted(direct_candidates)[0]

    recursive_candidates = [candidate for candidate in root.rglob("*") if candidate.is_dir() and candidate.name.startswith("5-Integration")]
    if recursive_candidates:
        return sorted(recursive_candidates)[0]
    return None


def _resolve_project_file_path(project_path: str, project_file: Optional[str]) -> Optional[Path]:
    if not project_file:
        return None

    candidate = Path(project_file)
    if not candidate.is_absolute():
        candidate = Path(project_path) / project_file
    if candidate.exists():
        return candidate

    for existing_project_file in sorted(Path(project_path).rglob("*.project.xml")):
        if existing_project_file.name == project_file:
            return existing_project_file
    return None


def _project_deployment_file(project_file_path: Path) -> Optional[Path]:
    try:
        root = ElementTree.parse(project_file_path).getroot()
    except ElementTree.ParseError:
        return None

    for element in root:
        if _local_name(element.tag) != "deploymentSchema":
            continue
        deployment_schema = (element.text or "").strip()
        if not deployment_schema:
            continue

        deployment_file = project_file_path.parent / deployment_schema
        if deployment_file.exists():
            return deployment_file
    return None


def _deployment_candidates(project_path: str, integration_dir: Path, project_file: Optional[str]) -> List[Path]:
    requested_project_file = _resolve_project_file_path(project_path, project_file)
    if requested_project_file is not None:
        requested_deployment = _project_deployment_file(requested_project_file)
        if requested_deployment is not None:
            return [requested_deployment]

    deployment_candidates: List[Path] = []
    seen_candidates = set()
    for project_xml in sorted(Path(project_path).rglob("*.project.xml")):
        deployment_file = _project_deployment_file(project_xml)
        if deployment_file is None:
            continue
        deployment_key = str(deployment_file)
        if deployment_key in seen_candidates:
            continue
        seen_candidates.add(deployment_key)
        deployment_candidates.append(deployment_file)

    if deployment_candidates:
        return deployment_candidates

    return sorted(integration_dir.glob("*.deployment.xml"))


def _built_debug_binaries(build_dir: str) -> set[str]:
    bin_dir = Path(build_dir) / "bin"
    if not bin_dir.exists():
        return set()

    return {
        file_path.name
        for file_path in bin_dir.iterdir()
        if file_path.is_file() and file_path.name.startswith("PD_")
    }


def _parse_deployment_processes(deployment_file: Path) -> List[tuple[str, str]]:
    processes: List[tuple[str, str]] = []

    try:
        root = ElementTree.parse(deployment_file).getroot()
    except ElementTree.ParseError:
        return processes

    if _local_name(root.tag) != "deployment":
        return processes

    for element in root:
        if _local_name(element.tag) != "protectionDomain":
            continue

        name = element.get("name")
        if not name:
            continue

        execute_on = next((child for child in element if _local_name(child.tag) == "executeOn"), None)
        if execute_on is None:
            continue

        node_id = execute_on.get("computingNode")
        if not node_id:
            continue

        processes.append((f"PD_{name}", node_id))

    return processes


def _find_deployment_file(
    project_path: str,
    integration_dir: Path,
    build_dir: str,
    project_file: Optional[str],
) -> Optional[Path]:
    deployment_candidates = _deployment_candidates(project_path, integration_dir, project_file)
    if not deployment_candidates:
        return None

    if len(deployment_candidates) == 1:
        return deployment_candidates[0]

    built_binaries = _built_debug_binaries(build_dir)
    if not built_binaries:
        return deployment_candidates[0]

    def score(candidate: Path) -> tuple[int, int, int]:
        expected_binaries = {process_name for process_name, _node_id in _parse_deployment_processes(candidate)}
        matches = len(expected_binaries & built_binaries)
        missing = len(expected_binaries - built_binaries)
        extras = len(built_binaries - expected_binaries)
        return (matches, -missing, -extras)

    return max(deployment_candidates, key=score)


def _parse_nodes_deployment(integration_dir: Path) -> Dict[str, str]:
    nodes_path = next(iter(sorted(integration_dir.rglob("nodes_deployment.xml"))), None)
    if nodes_path is None:
        return {}

    root = ElementTree.parse(nodes_path).getroot()
    nodes: Dict[str, str] = {}
    for element in root:
        if _local_name(element.tag) != "logicalComputingNode":
            continue
        node_id = element.get("id")
        ip_address = element.get("ipAddress")
        if node_id and ip_address:
            nodes[node_id] = ip_address
    return nodes


def _derive_docker_subnet(addresses: List[str]) -> str:
    if not addresses:
        return ""

    octets = [address.split(".") for address in addresses]
    if all(parts[:3] == octets[0][:3] for parts in octets):
        return ".".join(octets[0][:3] + ["0"]) + "/24"
    if all(parts[:2] == octets[0][:2] for parts in octets):
        return ".".join(octets[0][:2] + ["0", "0"]) + "/16"
    if all(parts[:1] == octets[0][:1] for parts in octets):
        return ".".join(octets[0][:1] + ["0", "0", "0"]) + "/8"
    raise ValueError("All nodes must share a common network prefix for Docker bridge generation")


def _parse_tcp_binding_ips(filepath: str) -> Dict[str, str]:
    """Parse tcp-params.xml → {platform_name: address}."""
    ips: Dict[str, str] = {}
    if not filepath or not os.path.exists(filepath):
        return ips
    try:
        root = ElementTree.parse(filepath).getroot()
        TCP_NS = '{http://www.ecoa.technology/tcpbinding}'
        candidates = list(root.findall(TCP_NS + 'platform')) or list(root.findall('platform'))
        for pf in candidates:
            name = pf.get('name')
            address = pf.get('address', '')
            if name and address:
                ips[name] = address
    except ElementTree.ParseError:
        pass
    return ips


def _parse_udp_binding_ips(filepath: str) -> Dict[str, str]:
    """Parse udp-binding.xml -> {platform_name: unicast_container_ip}.

    UDP inter-platform traffic uses multicast groups (224-239.x.x.x), NOT the
    container unicast IP. The container IP is only used by Docker for bridge
    membership. We always assign 10.201.0.x unicast IPs so containers sit on a
    predictable, non-conflicting subnet regardless of the multicast group address.
    """
    ips: Dict[str, str] = {}
    if not filepath or not os.path.exists(filepath):
        return ips
    try:
        root = ElementTree.parse(filepath).getroot()
        UDP_NS = '{http://www.ecoa.technology/udpbinding-2.0}'
        candidates = list(root.findall(UDP_NS + 'platform')) or list(root.findall('platform'))
        for i, pf in enumerate(candidates):
            name = pf.get('name')
            if name:
                # Multicast addresses (224-239.x.x.x) cannot be Docker container IPs.
                ips[name] = f'10.201.0.{i + 2}'
    except ElementTree.ParseError:
        pass
    return ips


def _assign_dds_ips(platform_names: List[str]) -> Dict[str, str]:
    """DDS uses a shared domain — assign 10.200.0.x/24 IPs for Docker containers."""
    return {name: f'10.200.0.{i + 2}' for i, name in enumerate(platform_names)}


def _collect_multi_platform_info(integration_dir: Path) -> Optional[dict]:
    """
    Parse the logical-system file and transport binding file to collect multi-platform info.

    Returns dict with keys:
      protocol, platforms, platform_ips, platform_ports, dds_domain_id, integration_dir_str
    Returns None if <= 1 platform or no platform links found.
    """
    ls_files = sorted(integration_dir.glob("*.logical-system.xml"))
    if not ls_files:
        return None

    try:
        root = ElementTree.parse(ls_files[0]).getroot()
    except ElementTree.ParseError:
        return None

    # Collect platforms (preserve order)
    platforms = []
    for elem in root.iter():
        if _local_name(elem.tag) == 'logicalComputingPlatform':
            pid = elem.get('id')
            if pid and pid not in platforms:
                platforms.append(pid)

    if len(platforms) <= 1:
        return None

    # Find first platform link with transport binding
    protocol = None
    parameters = None
    use_dds_overlay = False   # new format: dds="true" on a TCP/UDP link
    dds_domain_id_attr = 0
    for links_set in root.iter():
        if _local_name(links_set.tag) != 'logicalComputingPlatformLinks':
            continue
        for link in links_set:
            if _local_name(link.tag) != 'link':
                continue
            for child in link:
                if _local_name(child.tag) == 'transportBinding':
                    protocol = child.get('protocol')
                    parameters = child.get('parameters')
                    # New format: dds="true" attribute means DDS middleware on TCP/UDP transport
                    if child.get('dds', 'false').lower() == 'true':
                        use_dds_overlay = True
                        try:
                            dds_domain_id_attr = int(child.get('ddsDomainId', '0'))
                        except (ValueError, TypeError):
                            dds_domain_id_attr = 0
                    break
            if protocol:
                break
        if protocol:
            break

    if not protocol:
        return None

    # Parse binding file for platform IPs
    binding_file = str(integration_dir / parameters) if parameters else None
    platform_ips: Dict[str, str] = {}
    platform_ports: Dict[str, Optional[int]] = {p: None for p in platforms}
    dds_domain_id = 0

    if protocol == 'TCP' and binding_file:
        platform_ips = _parse_tcp_binding_ips(binding_file)
        # Read ports too
        try:
            bf_root = ElementTree.parse(binding_file).getroot()
            TCP_NS = '{http://www.ecoa.technology/tcpbinding}'
            for pf in (list(bf_root.findall(TCP_NS + 'platform')) or list(bf_root.findall('platform'))):
                name = pf.get('name')
                port = pf.get('port')
                if name and port:
                    try:
                        platform_ports[name] = int(port)
                    except ValueError:
                        pass
        except ElementTree.ParseError:
            pass
    elif protocol == 'UDP' and binding_file:
        platform_ips = _parse_udp_binding_ips(binding_file)
        # Read receiving ports
        try:
            bf_root = ElementTree.parse(binding_file).getroot()
            UDP_NS = '{http://www.ecoa.technology/udpbinding-2.0}'
            for pf in (list(bf_root.findall(UDP_NS + 'platform')) or list(bf_root.findall('platform'))):
                name = pf.get('name')
                port_str = pf.get('receivingPort')
                if name and port_str:
                    try:
                        platform_ports[name] = int(port_str)
                    except ValueError:
                        pass
        except ElementTree.ParseError:
            pass
    elif protocol == 'DDS' and binding_file:
        platform_ips = _assign_dds_ips(platforms)
        # Read domain ID from the separate dds-binding.xml (old format)
        try:
            bf_root = ElementTree.parse(binding_file).getroot()
            DDS_NS = '{http://www.ecoa.technology/ddsbinding}'
            domain_el = bf_root.find(DDS_NS + 'domain')
            if domain_el is None:
                domain_el = bf_root.find('domain')
            if domain_el is not None:
                dds_domain_id = int(domain_el.get('id', '0'))
        except (ElementTree.ParseError, ValueError):
            pass

    # New format: dds="true" on a TCP/UDP link → keep TCP/UDP IPs, activate DDS compile flags
    if use_dds_overlay and protocol in ('TCP', 'UDP'):
        dds_domain_id = dds_domain_id_attr
        protocol = 'DDS'  # signals DDS compile flags and LDP_DDS_DOMAIN_ID env var to downstream code
        # platform_ips were already populated from the TCP/UDP binding file above

    # Fill missing IPs with fallback addresses
    for i, pf in enumerate(platforms):
        if pf not in platform_ips or not platform_ips[pf]:
            platform_ips[pf] = f'10.202.0.{i + 2}'

    return {
        'protocol': protocol,
        'platforms': platforms,
        'platform_ips': platform_ips,
        'platform_ports': platform_ports,
        'dds_domain_id': dds_domain_id,
        'integration_dir_str': str(integration_dir),
    }


def collect_multi_platform_topology(
    project_path: str,
    build_base_dir: Optional[str] = None,
    project_file: Optional[str] = None,
    target_arch: str = "native",
) -> Optional[DebugTopology]:
    """
    Build DebugTopology for multi-platform designs (TCP/UDP/DDS).

    Looks for per-platform build directories: 6-output/{Platform_X}/build/bin/platform
    Port allocation: platform_index * 100 + DEBUG_START_PORT + process_offset
    service_name = ecoa-{sanitize(platform_name)}
    """
    integration_dir = _find_integration_dir(project_path)
    if integration_dir is None:
        return None

    info = _collect_multi_platform_info(integration_dir)
    if info is None:
        return None

    platforms = info['platforms']
    platform_ips = info['platform_ips']
    protocol = info['protocol']
    dds_domain_id = info['dds_domain_id']

    # Discover per-platform build directories
    output_root = Path(project_path)
    for candidate_name in ("6-output", "6-Output"):
        if (output_root / candidate_name).exists():
            output_root = output_root / candidate_name
            break

    # Find deployment file for PD processes
    deployment_file = _find_deployment_file(project_path, integration_dir, "", project_file)
    all_pd_processes = _parse_deployment_processes(deployment_file) if deployment_file else []

    # Group PD processes by computingPlatform attribute
    pd_by_platform: Dict[str, List[str]] = {pf: [] for pf in platforms}
    if deployment_file:
        try:
            dep_root = ElementTree.parse(deployment_file).getroot()
            for elem in dep_root:
                if _local_name(elem.tag) != 'protectionDomain':
                    continue
                pd_name = elem.get('name')
                pf_name = elem.get('computingPlatform') or elem.get('executeOn')
                # Also check executeOn child
                if not pf_name:
                    for child in elem:
                        if _local_name(child.tag) == 'executeOn':
                            pf_name = child.get('computingPlatform')
                            break
                if pd_name and pf_name and pf_name in pd_by_platform:
                    pd_by_platform[pf_name].append(f'PD_{pd_name}')
        except ElementTree.ParseError:
            pass

    # Parse per-platform processor types from logical-system XML.
    # Maps platform id → processor type, e.g. {"Platform_Writer": "x86_64", "Platform_Reader": "arm64"}.
    # Platforms with no declared type fall back to the global target_arch.
    platform_processor_types = _parse_platform_processor_types(project_path)

    processes: List[DebugProcess] = []

    for pf_index, pf_name in enumerate(platforms):
        ip = platform_ips.get(pf_name, f'10.202.0.{pf_index + 2}')
        service_name = f"ecoa-{_sanitize_service_name(pf_name)}"
        base_port = DEBUG_START_PORT + pf_index * 100

        # Each platform gets its own target_arch from LogicalProcessor.type;
        # falls back to the caller-supplied target_arch when undeclared.
        pf_target_arch = platform_processor_types.get(pf_name, target_arch)

        # platform binary — runs free in its own container (no gdbserver)
        processes.append(DebugProcess(
            name="platform",
            node_id=pf_name,
            host=ip,
            port=base_port,
            service_name=service_name,
            target_arch=pf_target_arch,
        ))

        # PD binaries — each runs in a SEPARATE container that shares the
        # platform container's network namespace (network_mode: service:...).
        # This replicates the physical LDP multi-node design where platform
        # and PD share the same IP but run as independent processes.
        pd_service_name = f"{service_name}-pd"
        for pd_offset, pd_name in enumerate(pd_by_platform.get(pf_name, []), start=1):
            processes.append(DebugProcess(
                name=pd_name,
                node_id=pf_name,
                host=ip,
                port=base_port + pd_offset,
                service_name=pd_service_name,
                target_arch=pf_target_arch,
            ))

    if len(processes) < 2:
        return None

    docker_subnet = _derive_docker_subnet([p.host for p in processes])
    return DebugTopology(
        integration_dir=str(integration_dir),
        docker_subnet=docker_subnet,
        processes=processes,
        is_distributed=True,
        target_arch=target_arch,
        protocol=info['protocol'],
        dds_domain_id=info['dds_domain_id'],
    )


def collect_debug_topology(project_path: str, build_dir: str, project_file: Optional[str] = None, target_arch: str = "native") -> Optional[DebugTopology]:
    # Try multi-platform topology first (tcp-params.xml / udp-binding.xml / dds-binding.xml)
    multi_topology = collect_multi_platform_topology(
        project_path=project_path,
        build_base_dir=build_dir,
        project_file=project_file,
        target_arch=target_arch,
    )
    if multi_topology is not None:
        return multi_topology

    integration_dir = _find_integration_dir(project_path)
    if integration_dir is None:
        return None

    node_hosts = _parse_nodes_deployment(integration_dir)
    if not node_hosts:
        return None

    deployment_file = _find_deployment_file(project_path, integration_dir, build_dir, project_file)
    if deployment_file is None:
        return None

    pd_processes = _parse_deployment_processes(deployment_file)
    if not pd_processes:
        return None

    platform_host = node_hosts.get("main")
    if not platform_host:
        return None

    # Parse per-node processor types from the logical-system XML.
    # Falls back to the caller-supplied target_arch when the XML is absent or
    # a node has no type attribute (e.g. for projects created before this feature).
    node_processor_types = _parse_node_processor_types(project_path)

    def _node_arch(node_id: str) -> str:
        return node_processor_types.get(node_id, target_arch)

    service_names = _service_name_by_host(node_hosts)
    processes = [
        DebugProcess(
            name="platform",
            node_id="main",
            host=platform_host,
            port=DEBUG_START_PORT,
            service_name=service_names[platform_host],
            target_arch=_node_arch("main"),
        )
    ]

    for index, (process_name, node_id) in enumerate(pd_processes, start=1):
        host = node_hosts.get(node_id)
        if not host:
            continue
        processes.append(
            DebugProcess(
                name=process_name,
                node_id=node_id,
                host=host,
                port=DEBUG_START_PORT + index,
                service_name=service_names[host],
                target_arch=_node_arch(node_id),
            )
        )

    if len(processes) == 1:
        return None

    docker_subnet = _derive_docker_subnet([process.host for process in processes])
    return DebugTopology(
        integration_dir=str(integration_dir),
        docker_subnet=docker_subnet,
        processes=processes,
        is_distributed=True,
        target_arch=target_arch,
    )


def _relative_build_dir(target_dir: str, build_dir: str) -> str:
    try:
        return os.path.relpath(build_dir, target_dir).replace("\\", "/")
    except ValueError:
        return build_dir.replace("\\", "/")


def _local_launch_config(target_dir: str, build_dir: str) -> dict:
    rel_build_dir = _relative_build_dir(target_dir, build_dir)
    return {
        "name": LOCAL_LAUNCH_NAME,
        "type": "cppdbg",
        "request": "launch",
        "program": f"${{workspaceFolder}}/{rel_build_dir}/bin/platform",
        "cwd": f"${{workspaceFolder}}/{rel_build_dir}/bin",
        "args": [],
        "stopAtEntry": True,
        "MIMode": "gdb",
    }


def _distributed_launch_config(target_dir: str, build_dir: str, process: DebugProcess) -> dict:
    binary_name = process.name
    # For multi-platform, node_id is the platform name; for single-platform it is the node id
    # (e.g. "main").  Always include it in the launch config name so compound configs are distinct.
    if process.name == "platform":
        name = f"Attach platform ({process.node_id})"
    else:
        name = f"Attach {process.name.replace('PD_', 'PD ')} ({process.node_id})"

    arch = process.target_arch
    # The compile script builds each platform into 6-output/{Platform}/build/
    # regardless of arch (one platform = one arch), so GDB must point at build/
    # directly — not the non-existent build-{arch}/.
    rel_build_dir = _relative_build_dir(target_dir, build_dir)
    _debugger_path = "/usr/bin/gdb-multiarch" if arch == "arm64" else "/usr/bin/gdb"
    _config = {
        "name": name,
        "type": "cppdbg",
        "request": "launch",
        "program": f"${{workspaceFolder}}/{rel_build_dir}/bin/{binary_name}",
        "cwd": f"${{workspaceFolder}}/{rel_build_dir}/bin",
        "args": [],
        "stopAtEntry": True,
        "MIMode": "gdb",
        "miDebuggerPath": _debugger_path,
        "miDebuggerServerAddress": f"{process.host}:{process.port}",
        "externalConsole": False,
        "setupCommands": [
            {
                "description": "Extend remote connect timeout to 30 s",
                "text": "set tcp connect-timeout 30",
                "ignoreFailures": False,
            },
            {
                "description": "Pretty-print STL containers",
                "text": "-enable-pretty-printing",
                "ignoreFailures": True,
            },
        ],
    }
    if arch == "arm64":
        _config["targetArchitecture"] = "arm64"
    return _config


def _compose_yaml(
    build_dir: str,
    topology: DebugTopology,
    project_mount_source: str = "..",
    debug_image: Optional[str] = None,
    compose_project_name: str = COMPOSE_PROJECT_NAME,
    network_name: Optional[str] = None,
    protocol: Optional[str] = None,
    dds_domain_id: int = 0,
) -> str:
    rel_bin_dir = Path(build_dir).name
    del rel_bin_dir
    binary_dir = container_binary_dir(build_dir)
    image_reference = debug_image or "${ECOA_DISTRIBUTED_DEBUG_IMAGE:-sirius-web-code-server:latest}"
    compose_network_name = network_name or f"{compose_project_name}_ecoa_debug_net"
    lines = [
        f"name: {compose_project_name}",
        'services:',
    ]

    unique_services = {}
    for process in topology.processes:
        existing = unique_services.get(process.service_name)
        if existing is None or (existing[0] == "main" and process.node_id != "main"):
            unique_services[process.service_name] = (process.node_id, process.host)

    # Determine whether this is a multi-platform topology (processes have distinct node_ids
    # that are not the legacy "main" node).  When true, per-service working_dir is derived
    # from the per-platform build sub-directory: 6-output/{node_id}/build/bin.
    distinct_node_ids = {node_id for node_id, _host in unique_services.values()}
    is_multi_platform = len(distinct_node_ids) > 1 or (
        len(distinct_node_ids) == 1 and "main" not in distinct_node_ids
    )

    # In multi-platform mode, PD containers share the network namespace of their
    # platform container (network_mode: service:...).  This replicates the LDP
    # multi-node physical design where platform and PD run on the same machine
    # (same IP, same loopback) but as separate processes.
    #
    # PD service names are derived as "{platform_service}-pd".  Platform services
    # get a real IP on ecoa_debug_net; PD services inherit the platform's network.
    #
    # Build a map: pd_service_name -> platform_service_name (for depends_on / network_mode)
    pd_to_platform: Dict[str, str] = {}
    if is_multi_platform:
        for svc_name in list(unique_services.keys()):
            if svc_name.endswith("-pd"):
                platform_svc = svc_name[:-3]  # strip "-pd"
                pd_to_platform[svc_name] = platform_svc

    # Debug containers always run as linux/amd64 (native on x86 hosts).
    # For arm64 cross-compiled builds, QEMU user-space emulation is used
    # inside the amd64 container (qemu-aarch64-static -g PORT), so no
    # kernel-level binfmt_misc registration is required — works offline.
    for service_name, (node_id, host) in unique_services.items():
        # Compute per-service binary directory for multi-platform designs.
        # Each platform has its own build sub-tree:
        #   6-output/{node_id}/build/bin  (inside the container)
        if is_multi_platform:
            service_binary_dir = f"{CONTAINER_PROJECT_ROOT}/6-output/{node_id}/build/bin"
        else:
            service_binary_dir = binary_dir

        is_pd_container = service_name in pd_to_platform

        lines.extend(
            [f"  {service_name}:", f"    image: {image_reference}"]
        )
        lines.extend(
            [
                '    command: ["bash", "-lc", "sleep infinity"]',
                f'    working_dir: "{service_binary_dir}"',
                '    cap_add:',
                '      - SYS_PTRACE',
                '    security_opt:',
                '      - seccomp:unconfined',
                '    volumes:',
                '      - type: bind',
                f"        source: {_yaml_single_quote(project_mount_source)}",
                f'        target: "{CONTAINER_PROJECT_ROOT}"',
                '    environment:',
                f'      ECOA_NODE_ID: "{node_id}"',
            ]
        )
        # Add protocol-specific environment variables
        if protocol == 'UDP':
            lines.append('      ECOA_TRANSPORT_PROTOCOL: UDP')
        elif protocol == 'DDS':
            lines.append(f'      LDP_DDS_DOMAIN_ID: "{dds_domain_id}"')
            lines.append('      ECOA_TRANSPORT_PROTOCOL: DDS')
        elif protocol == 'TCP':
            lines.append('      ECOA_TRANSPORT_PROTOCOL: TCP')

        if is_pd_container:
            # PD container shares the platform container's network namespace so
            # the PD binary sees the same IP and loopback — identical to running
            # on the same physical machine as the platform binary.
            platform_svc = pd_to_platform[service_name]
            lines.extend([
                f'    network_mode: "service:{platform_svc}"',
                '    depends_on:',
                f'      - {platform_svc}',
            ])
        else:
            lines.extend(
                [
                    '    networks:',
                    '      ecoa_debug_net:',
                    f'        ipv4_address: {host}',
                ]
            )

    lines.extend(
        [
            'networks:',
            '  ecoa_debug_net:',
            f'    name: {compose_network_name}',
            '    driver: bridge',
            '    ipam:',
            '      config:',
            f'        - subnet: {topology.docker_subnet}',
        ]
    )
    return "\n".join(lines) + "\n"


def render_distributed_debug_compose(
    build_dir: str,
    topology: DebugTopology,
    project_mount_source: str = "..",
    debug_image: Optional[str] = None,
    compose_project_name: str = COMPOSE_PROJECT_NAME,
    network_name: Optional[str] = None,
) -> str:
    """Render the distributed debug compose definition."""
    return _compose_yaml(
        build_dir,
        topology,
        project_mount_source=project_mount_source,
        debug_image=debug_image,
        compose_project_name=compose_project_name,
        network_name=network_name,
        protocol=getattr(topology, 'protocol', None),
        dds_domain_id=getattr(topology, 'dds_domain_id', 0) or 0,
    )


def _api_script(endpoint: str, method: str = "POST") -> str:
    lines = [
        "#!/usr/bin/env bash",
        "set -euo pipefail",
        "",
        'export ECOA_DISTRIBUTED_DEBUG_API_URL="${ECOA_DISTRIBUTED_DEBUG_API_URL:-http://ecoa-tools:5000}"',
        "",
        "python - <<'PY'",
        "import json",
        "import os",
        "import sys",
        "import urllib.error",
        "import urllib.parse",
        "import urllib.request",
        "",
        'api_base_url = os.environ["ECOA_DISTRIBUTED_DEBUG_API_URL"].rstrip("/")',
        'payload = {',
        '    "target_dir": os.environ.get("ECOA_DISTRIBUTED_DEBUG_TARGET_DIR", os.getcwd()),',
        '    "client_container": os.environ.get("ECOA_DISTRIBUTED_DEBUG_CLIENT_CONTAINER", "code-server"),',
        '    "project_id": os.environ.get("ECOA_DISTRIBUTED_DEBUG_PROJECT_ID"),',
        '    "project_name": os.environ.get("ECOA_DISTRIBUTED_DEBUG_PROJECT_NAME"),',
        '    "user_id": os.environ.get("ECOA_DISTRIBUTED_DEBUG_USER_ID"),',
        '    "username": os.environ.get("ECOA_DISTRIBUTED_DEBUG_USERNAME"),',
        '    "target_arch": os.environ.get("ECOA_TARGET_ARCH", "native"),',
        '}'
        "",
        f'if "{method}" == "GET":',
        '    query = urllib.parse.urlencode(payload)',
        f'    request = urllib.request.Request(f"{{api_base_url}}{endpoint}?{{query}}", method="GET")',
        "else:",
        '    body = json.dumps(payload).encode("utf-8")',
        f'    request = urllib.request.Request(f"{{api_base_url}}{endpoint}", data=body, headers={{"Content-Type": "application/json"}}, method="{method}")',
        "",
        "try:",
        "    with urllib.request.urlopen(request) as response:",
        '        sys.stdout.write(response.read().decode("utf-8"))',
        '        sys.stdout.write("\\n")',
        "except urllib.error.HTTPError as exc:",
        '    error_body = exc.read().decode("utf-8", errors="replace")',
        '    sys.stderr.write(error_body or str(exc))',
        '    sys.stderr.write("\\n")',
        "    try:",
        '        err_data = json.loads(error_body)',
        '        err_msg = err_data.get("message", "")',
        '        if "docker" in err_msg.lower() or "docker.sock" in err_msg.lower():',
        '            sys.stderr.write("\\n[提示] Docker 连接失败，请检查：\\n")',
        '            sys.stderr.write("  1. 优先确认 ecoa-tools 已挂载 /var/run/docker.sock\\n")',
        '            sys.stderr.write("  2. 如设置了 DOCKER_HOST，请确认对应 Docker API 可达；不可达时请取消该变量\\n")',
        '            sys.stderr.write("  3. 宿主机 Docker daemon 是否正在运行\\n")',
        "    except (json.JSONDecodeError, AttributeError):",
        "        pass",
        "    raise",
        "PY",
        "",
    ]
    return "\n".join(lines).strip() + "\n"


def _start_script() -> str:
    return _api_script("/api/distributed-debug/start")


def _stop_script() -> str:
    return _api_script("/api/distributed-debug/stop")


def _status_script() -> str:
    return _api_script("/api/distributed-debug/status", method="GET")


def _write_executable(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


def write_distributed_debug_launch_json(target_dir: str, build_dir: str, topology: Optional[DebugTopology]) -> str:
    target_path = Path(target_dir)
    vscode_dir = target_path / ".vscode"
    vscode_dir.mkdir(parents=True, exist_ok=True)

    launch_json_path = vscode_dir / "launch.json"
    launch_configurations = [_local_launch_config(target_dir, build_dir)]
    compounds = []

    if topology and topology.is_distributed:
        distributed_configs = []
        for process in topology.processes:
            # In multi-platform mode the `platform` binary runs free (no gdbserver):
            # it must start first and bind ELI/lifecycle ports before PD processes
            # connect. Only PD_* processes need a GDB launch config.
            if getattr(topology, 'protocol', None) and process.name == "platform":
                continue
            # Multi-platform: each process has its own build dir under 6-output/{node_id}/build/
            if getattr(topology, 'protocol', None):
                proc_build_dir = _per_platform_build_dir(target_dir, process, build_dir)
            else:
                proc_build_dir = build_dir
            distributed_configs.append(_distributed_launch_config(target_dir, proc_build_dir, process))
        launch_configurations.extend(distributed_configs)
        compounds = [
            {
                "name": COMPOUND_NAME,
                "configurations": [config["name"] for config in distributed_configs],
            }
        ]

    launch_json_path.write_text(
        json.dumps(
            {
                "version": "0.2.0",
                "configurations": launch_configurations,
                "compounds": compounds,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    return str(launch_json_path)


def _pkg_config_path_bash(package: str) -> str:
    """Return a bash snippet that resolves a package install prefix via pkg-config."""
    return (
        f'_pkg_config_path "{package}"'
    )


def _unique_archs_from_topology(topology: Optional["DebugTopology"]) -> "list[str]":
    """Return unique architectures from a topology, native-first.

    Normalises x86_64 / amd64 / native / "" → "native".
    Returns ["native"] when topology is absent or has no processes.
    """
    _native_aliases = {"native", "amd64", "x86_64", ""}
    if not topology or not topology.processes:
        return ["native"]
    seen: list[str] = []
    for proc in topology.processes:
        arch = proc.target_arch if proc.target_arch else "native"
        key = "native" if arch in _native_aliases else arch
        if key not in seen:
            seen.append(key)
    seen.sort(key=lambda a: (0 if a == "native" else 1, a))
    return seen


def _arch_label(arch: str) -> str:
    return "x86_64 (native)" if arch == "native" else arch


def _per_platform_build_dir(project_dir: str, process: DebugProcess, base_build_dir: str) -> str:
    """Return the build directory for a debug process.

    For multi-platform projects the node_id IS the platform name (e.g. 'Platform_Writer').
    In that case the binary lives in  6-output/{node_id}/build/bin/.
    Falls back to base_build_dir for single-platform / legacy layouts.
    """
    node_id = process.node_id
    if not node_id or node_id == "main":
        return base_build_dir
    for out_dir in ("6-output", "6-Output"):
        candidate = Path(project_dir) / out_dir / node_id / "build"
        if candidate.exists():
            return str(candidate)
    return base_build_dir


def _platform_target_arch(topology: DebugTopology) -> Dict[str, str]:
    """Map platform name → target_arch from topology processes.

    Returns the target_arch of the 'platform' binary for each node_id.
    Falls back to 'native' when no architecture info is available.
    """
    _native_aliases = {"native", "amd64", "x86_64", ""}
    arch_map: Dict[str, str] = {}
    for proc in topology.processes:
        if proc.name == "platform" and proc.node_id:
            raw = (proc.target_arch or "native").lower()
            arch_map[proc.node_id] = "native" if raw in _native_aliases else raw
    return arch_map


def _multi_platform_compile_script_content(topology: DebugTopology) -> str:
    """Generate ldp-compile.sh for multi-platform projects.

    Each logicalComputingPlatform has its own 6-output/{name}/ directory.
    Generates one cmake + make block per platform.  When platforms declare
    different LogicalProcessor types (x86_64 vs arm64), the script emits
    per-platform cross-compilation toolchain and pkg-config setup so that
    each platform binary matches its declared target architecture.
    """
    seen: set = set()
    platforms: List[str] = []
    for proc in topology.processes:
        if proc.node_id and proc.node_id not in seen:
            seen.add(proc.node_id)
            platforms.append(proc.node_id)

    # Per-platform target architecture (from LogicalProcessor.type in the model)
    platform_arch = _platform_target_arch(topology)

    protocol = getattr(topology, 'protocol', 'TCP') or 'TCP'
    dds_domain_id = getattr(topology, 'dds_domain_id', 0) or 0
    total = len(platforms)

    if protocol == "DDS":
        proto_flags = (
            f"    -DCMAKE_USE_DDS_PROTO=ON -DCMAKE_USE_UDP_PROTO=OFF "
            f"-DCYCLONEDDS_DOMAIN_ID={dds_domain_id} \\"
        )
    elif protocol == "UDP":
        proto_flags = "    -DCMAKE_USE_DDS_PROTO=OFF -DCMAKE_USE_UDP_PROTO=ON \\"
    else:
        proto_flags = "    -DCMAKE_USE_DDS_PROTO=OFF -DCMAKE_USE_UDP_PROTO=OFF \\"

    # Build arch annotations for header comment
    arch_annotations = []
    for pf in platforms:
        arch = platform_arch.get(pf, "native")
        arch_annotations.append(f"#   {pf:<30} → {_arch_label(arch)}")
    if not arch_annotations:
        arch_annotations = ["#   (all platforms → native)"]

    lines = [
        "#!/usr/bin/env bash",
        "# ECOA Multi-Platform LDP Compile Script — Auto-generated",
        f"# Protocol: {protocol}  |  Platforms: {total}",
        "# Per-platform target architectures (from LogicalProcessor.type):",
        *arch_annotations,
        "# Usage: .vscode/ldp-compile.sh [log_library]",
        "#   log_library: log4cplus (default), zlog, or lttng",
        "set -euo pipefail",
        "",
        'LOG_LIBRARY="${1:-log4cplus}"',
        'PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"',
        "",
        "_pkg_config_path() {",
        '    local pkg="$1" extra_env="${2:-}" result',
        '    result=$(env $extra_env pkg-config --cflags "$pkg" 2>/dev/null || true)',
        '    if [ -n "$result" ]; then',
        '        for part in $result; do',
        '            if [[ "$part" == -I* ]]; then',
        '                local path="${part#-I}"',
        '                [[ "$path" == *"/include"* ]] && path="${path%%/include*}"',
        '                [ -n "$path" ] && { echo "$path"; return 0; }',
        '            fi',
        '        done',
        '    fi',
        '    result=$(env $extra_env pkg-config --variable=prefix "$pkg" 2>/dev/null || true)',
        '    [ -n "$result" ] && { echo "$result"; return 0; }',
        '    echo "ERROR: pkg-config failed for $pkg" >&2; return 1',
        "}",
        "",
        "# Detect host architecture at runtime for native-vs-cross decision",
        "_HOST_ARCH=$(uname -m)",
        "",
        "# pkg-config env overrides for cross-compilation",
        "_ARM64_PKG_ENV='PKG_CONFIG_PATH=/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig PKG_CONFIG_LIBDIR=/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig'",
        "_X86_PKG_ENV='PKG_CONFIG_PATH=/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig PKG_CONFIG_LIBDIR=/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig'",
        "",
        "_collect_libs() {",
        '    local lib_dir="$1" exe="$2"; [ -f "$exe" ] || return',
        "    ldd \"$exe\" 2>/dev/null | awk '/=>/ && !/linux-vdso/ && !/ld-linux/ { print $3 }' | while read -r lib; do",
        '        [ -f "$lib" ] && [ ! -f "${lib_dir}/$(basename "$lib")" ] &&',
        '            cp -L "$lib" "${lib_dir}/" 2>/dev/null && echo "  Bundled: $(basename "$lib")" || true',
        '    done',
        "}",
        "",
        "# --- Runtime helper: select toolchain + pkg-config env per target arch ---",
        "_setup_arch_env() {",
        '    local _target="$1"  # "native" | "arm64" | "aarch64"',
        '    local _is_arm_host=false',
        '    [ "$_HOST_ARCH" = "aarch64" ] || [ "$_HOST_ARCH" = "arm64" ] && _is_arm_host=true',
        '    local _is_x86_host=false',
        '    [ "$_HOST_ARCH" = "x86_64" ] || [ "$_HOST_ARCH" = "amd64" ] && _is_x86_host=true',
        "",
        '    case "$_target" in',
        "        native|x86_64|amd64)",
        '            if $_is_x86_host; then',
        '                _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        '            elif $_is_arm_host; then',
        '                # arm64 host → x86_64 target (cross)',
        '                if command -v x86_64-linux-gnu-gcc >/dev/null 2>&1; then',
        '                    _TC_ARG="-DCMAKE_TOOLCHAIN_FILE=/app/cmake/toolchain-x86_64.cmake"',
        '                    _PKG_ENV="${_X86_PKG_ENV}"  _IS_NATIVE=false',
        "                else",
        '                    echo "WARNING: x86_64 cross-compiler not found, compiling native" >&2',
        '                    _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        "                fi",
        "            else",
        '                _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        "            fi",
        "            ;;",
        "        arm64|aarch64)",
        '            if $_is_arm_host; then',
        '                _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        '            elif $_is_x86_host; then',
        '                # x86_64 host → arm64 target (cross)',
        '                if command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then',
        '                    _TC_ARG="-DCMAKE_TOOLCHAIN_FILE=/app/cmake/toolchain-aarch64.cmake"',
        '                    _PKG_ENV="${_ARM64_PKG_ENV}"  _IS_NATIVE=false',
        "                else",
        '                    echo "WARNING: aarch64 cross-compiler not found, compiling native" >&2',
        '                    _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        "                fi",
        "            else",
        '                _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        "            fi",
        "            ;;",
        "        *)",
        '            _TC_ARG=""  _PKG_ENV=""  _IS_NATIVE=true',
        "            ;;",
        "    esac",
        "}",
        "",
    ]

    for idx, pf in enumerate(platforms, start=1):
        safe = pf.replace('-', '_').replace(' ', '_')
        d = f"_D_{safe}"   # cmake dir var
        b = f"_B_{safe}"   # build dir var
        arch = platform_arch.get(pf, "native")
        label = _arch_label(arch)
        lines.extend([
            f"# {'=' * 58}",
            f"# [{idx}/{total}] Platform: {pf}  (target: {label})",
            f"# {'=' * 58}",
            f'{d}="${{PROJECT_DIR}}/6-output/{pf}"',
            f"# Compute target arch and setup toolchain/pkg-config for this platform",
            f'_setup_arch_env "{arch}"',
            f'{b}="${{{d}}}/build"',
            f'if [ ! -f "${{{d}}}/CMakeLists.txt" ]; then',
            f'    echo "WARNING: 6-output/{pf}/CMakeLists.txt not found — skipping" >&2',
            "else",
            f'    _CFG_ARG=""',
            f'    [ -f "${{{d}}}/cmake_config.cmake" ] && _CFG_ARG="-C ${{{d}}}/cmake_config.cmake"',
            f'    [ -z "$_CFG_ARG" ] && [ -f "${{PROJECT_DIR}}/cmake_config.cmake" ] && _CFG_ARG="-C ${{PROJECT_DIR}}/cmake_config.cmake"',
            f'    [ -z "$_CFG_ARG" ] && [ -f "/app/cmake_config.cmake" ] && _CFG_ARG="-C /app/cmake_config.cmake"',
            # Resolve dependencies with the correct pkg-config env for this arch.
            # Cross-compiled platforms (e.g. arm64 on x86 host) may not have
            # -dev:arm64 packages installed (headers conflict with native -dev).
            # Fall back to /usr — the cmake toolchain file handles arch selection.
            f'    _APR_DIR=$(_pkg_config_path "apr-1" "${{_PKG_ENV}}" || echo "/usr")',
            f'    _LOG4CPLUS_DIR=$(_pkg_config_path "log4cplus" "${{_PKG_ENV}}" || echo "/usr")',
            f'    _CUNIT_DIR=$(_pkg_config_path "cunit" "${{_PKG_ENV}}" || echo "/usr")',
            f'    mkdir -p "${{{b}}}"',
            # Remove stale CMakeCache.txt so cmake picks up new -D flags (e.g. toolchain changes)
            f'    rm -f "${{{b}}}/CMakeCache.txt"',
            f'    echo "=== [{idx}/{total}] {pf} ({label}) — cmake (log: ${{LOG_LIBRARY}}) ==="',
            f'    cmake \\',
            f'        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \\',
            f'        -DAPR_DIR="${{_APR_DIR}}" \\',
            f'        -DLOG4CPLUS_DIR="${{_LOG4CPLUS_DIR}}" \\',
            f'        -DCUNIT_DIR="${{_CUNIT_DIR}}" \\',
            f'        -DLDP_LOG_USE="${{LOG_LIBRARY}}" \\',
            f'        -DLDP_LINK_TYPE=STATIC \\',
            f'        -DCMAKE_BUILD_RPATH="\\$ORIGIN/../lib" \\',
            f'        -DCMAKE_INSTALL_RPATH="\\$ORIGIN/../lib" \\',
            f'        -DCMAKE_BUILD_RPATH_USE_ORIGIN=ON \\',
            f'        {proto_flags}',
            f'        ${{_TC_ARG}} \\',
            f'        -B "${{{b}}}" \\',
            f'        -S "${{{d}}}" \\',
            f'        $_CFG_ARG',
            f'    echo "=== [{idx}/{total}] {pf} — touch src/ (Docker timestamp sync) ==="',
            f'    find "${{PROJECT_DIR}}/4-ComponentImplementations" -path "*/src/*.c" -exec touch {{}} \\; 2>/dev/null || true',
            f'    echo "=== [{idx}/{total}] {pf} — make ==="',
            f'    make --no-print-directory -C "${{{b}}}" all -j$(nproc)',
            # ldd bundling: only for native builds (ldd can't inspect cross-compiled ELFs)
            f'    if [ "${{_IS_NATIVE:-true}}" = "true" ]; then',
            f'        _LIB="${{{b}}}/lib"; _BIN="${{{b}}}/bin"; mkdir -p "$_LIB"',
            f'        for _e in "$_BIN"/*; do [ -f "$_e" ] && [ -x "$_e" ] && _collect_libs "$_LIB" "$_e"; done',
            f'    fi',
            "fi",
            "",
        ])

    lines.extend([
        'echo ""',
        f'echo "=== Multi-platform build complete ({protocol}) ==="',
    ])
    for pf in platforms:
        safe = pf.replace('-', '_').replace(' ', '_')
        b = f"_B_{safe}"
        lines.append(f'[ -d "${{{b}}}/bin" ] && echo "  {pf}: ${{{b}}}/bin/" || true')

    # Binary architecture verification
    lines.extend([
        '',
        'echo ""',
        'echo "=== Binary platform verification ==="',
    ])
    for pf in platforms:
        safe = pf.replace('-', '_').replace(' ', '_')
        b = f"_B_{safe}"
        lines.extend([
            f'if [ -d "${{{b}}}/bin" ]; then',
            f'    echo "  -- {pf} --"',
            f'    for _exe in "${{{b}}}/bin"/*; do',
            '        [ -f "$_exe" ] && [ -x "$_exe" ] || continue',
            '        _machine=$(readelf -h "$_exe" 2>/dev/null | awk \'/Machine:/{print $NF}\')',
            '        _class=$(readelf -h "$_exe" 2>/dev/null | awk \'/Class:/{print $NF}\')',
            '        printf "  %-35s %s %s\\n" "$(basename "$_exe")" "$_class" "$_machine"',
            '    done',
            'fi',
        ])

    return "\n".join(lines) + "\n"


def _compile_script_content(
    build_dir: str,
    cmake_dir: str,
    project_file: Optional[str],
    tool_id: str = "ldp",
    topology: Optional["DebugTopology"] = None,
) -> str:
    """Generate the content of compile script.

    When *topology* is provided the script is generated once per unique
    architecture found in the topology's processes (derived from
    LogicalProcessor.type in the ECOA model).  Each architecture gets its
    own cmake + make block that writes to a separate build directory:

        native / x86_64 → build/
        arm64            → build-arm64/

    This eliminates the need to pass a global ``target_arch`` flag — the
    architecture is embedded in the script at generation time.

    For CSMGVT, generates a simplified compilation script (no topology needed).
    """
    is_harness = bool(project_file and "harness" in project_file.lower())

    # Multi-platform: each platform has its own 6-output/{name}/ directory
    if topology and getattr(topology, 'protocol', None) and not is_harness:
        return _multi_platform_compile_script_content(topology)

    # CSMGVT uses simplified compilation
    if tool_id == "csmgvt":
        return _csmgvt_compile_script_content(build_dir, cmake_dir, project_file)

    # --- Build per-architecture info from topology ---
    unique_archs = _unique_archs_from_topology(topology)

    # Build the human-readable architecture map for the script header.
    # Maps arch label → list of process names assigned to that arch.
    _native_aliases = {"native", "amd64", "x86_64", ""}
    arch_to_procs: dict = {}
    if topology and topology.processes:
        for proc in topology.processes:
            raw = proc.target_arch if proc.target_arch else "native"
            key = "native" if raw in _native_aliases else raw
            arch_to_procs.setdefault(key, []).append(proc.name)
    else:
        arch_to_procs["native"] = ["platform"]

    # Header comments showing the architecture map baked into this script.
    arch_map_lines = ["# Architecture map (from LogicalProcessor.type in model):"]
    for arch in unique_archs:
        procs = arch_to_procs.get(arch, [])
        build_suffix = "" if arch == "native" else f"-{arch}"
        arch_map_lines.append(
            f"#   {_arch_label(arch):<22} build{build_suffix}/   ({', '.join(procs)})"
        )

    lines = [
        "#!/usr/bin/env bash",
        "# ECOA LDP Compile Script - Auto-generated",
        "# Usage: .vscode/ldp-compile.sh [log_library]",
        "#   log_library: log4cplus (default), zlog, or lttng",
        *arch_map_lines,
        "set -euo pipefail",
        "",
        'LOG_LIBRARY="${1:-log4cplus}"',
        'PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"',
        "",
        "# --- Helper: resolve package prefix via pkg-config ---",
        "_pkg_config_path() {",
        '    local pkg="$1"',
        '    local extra_env="${2:-}"',
        "    local result",
        "",
        '    # Method 1: parse from --cflags (most reliable on Ubuntu)',
        '    result=$(env $extra_env pkg-config --cflags "$pkg" 2>/dev/null || true)',
        '    if [ -n "$result" ]; then',
        '        for part in $result; do',
        '            if [[ "$part" == -I* ]]; then',
        '                local path="${part#-I}"',
        '                if [[ "$path" == *"/include"* ]]; then',
        '                    path="${path%%/include*}"',
        '                    if [ -n "$path" ]; then',
        '                        echo "$path"',
        '                        return 0',
        '                    fi',
        '                fi',
        '            fi',
        '        done',
        '    fi',
        "",
        '    # Method 2: try --variable=prefix',
        '    result=$(env $extra_env pkg-config --variable=prefix "$pkg" 2>/dev/null || true)',
        '    if [ -n "$result" ]; then',
        '        echo "$result"',
        '        return 0',
        '    fi',
        "",
        '    echo "ERROR: pkg-config failed for $pkg" >&2',
        '    return 1',
        "}",
        "",
        "# Detect host architecture at runtime for native-vs-cross decision",
        "_HOST_ARCH=$(uname -m)",
        "",
        "# pkg-config env overrides for cross-compilation",
        "_ARM64_PKG_ENV='PKG_CONFIG_PATH=/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig PKG_CONFIG_LIBDIR=/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig'",
        "_X86_PKG_ENV='PKG_CONFIG_PATH=/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig PKG_CONFIG_LIBDIR=/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig'",
        "",
        "# --- Detect harness vs integration mode ---",
        'CMAKE_DIR=""',
        'CMAKE_SOURCE_DIR=""',
        'BUILD_DIR=""',
        "",
    ]

    if is_harness:
        lines.extend(
            [
                "# Harness mode: CMakeLists.txt is under 6-output/platform/",
                'for _out_dir in "6-output" "6-Output"; do',
                '    if [ -f "${PROJECT_DIR}/${_out_dir}/platform/CMakeLists.txt" ]; then',
                '        CMAKE_DIR="${PROJECT_DIR}/${_out_dir}/platform"',
                '        break',
                '    fi',
                'done',
                'if [ -z "$CMAKE_DIR" ]; then',
                '    echo "ERROR: CMakeLists.txt not found in platform directory" >&2',
                '    exit 1',
                'fi',
                "",
                '# Harness uses the wrapper as cmake source',
                'CMAKE_SOURCE_DIR="${CMAKE_DIR}/.distributed-debug-wrapper"',
                'BUILD_DIR="${CMAKE_DIR}/build"',
                "",
                '# Harness: clear build dir if CMakeCache exists (wrapper may have changed)',
                'if [ -f "${BUILD_DIR}/CMakeCache.txt" ]; then',
                '    echo "Clearing build directory (CMakeCache.txt exists)..."',
                '    rm -rf "${BUILD_DIR}"',
                'fi',
            ]
        )
    else:
        lines.extend(
            [
                "# Integration mode: CMakeLists.txt is under 6-output/ (or similar)",
                'for _out_dir in "6-output" "6-Output" "Output" "output" "build-output"; do',
                '    if [ -f "${PROJECT_DIR}/${_out_dir}/CMakeLists.txt" ]; then',
                '        CMAKE_DIR="${PROJECT_DIR}/${_out_dir}"',
                '        break',
                '    fi',
                'done',
                'if [ -z "$CMAKE_DIR" ]; then',
                '    echo "ERROR: CMakeLists.txt not found" >&2',
                '    exit 1',
                'fi',
                "",
                'CMAKE_SOURCE_DIR="${CMAKE_DIR}"',
                'BUILD_DIR="${CMAKE_DIR}/build"',
            ]
        )

    # cmake_config.cmake discovery (shared across all arch blocks)
    lines.extend(
        [
            "",
            "# --- Find cmake_config.cmake ---",
            'CMAKE_CONFIG_ARG=""',
            'if [ -f "${CMAKE_DIR}/cmake_config.cmake" ]; then',
            '    CMAKE_CONFIG_ARG="-C ${CMAKE_DIR}/cmake_config.cmake"',
            'elif [ -f "${PROJECT_DIR}/cmake_config.cmake" ]; then',
            '    CMAKE_CONFIG_ARG="-C ${PROJECT_DIR}/cmake_config.cmake"',
            'elif [ -f "/app/cmake_config.cmake" ]; then',
            '    CMAKE_CONFIG_ARG="-C /app/cmake_config.cmake"',
            'fi',
            "",
            "# --- lib bundling helper (native builds only) ---",
            '_collect_libs() {',
            '    local lib_dir="$1" exe="$2"',
            '    if [ ! -f "$exe" ]; then return; fi',
            '    ldd "$exe" 2>/dev/null | awk \'/=>/ && !/linux-vdso/ && !/ld-linux/ { print $3 }\' | while read -r lib; do',
            '        if [ -f "$lib" ] && [ ! -f "${lib_dir}/$(basename "$lib")" ]; then',
            '            cp -L "$lib" "${lib_dir}/" 2>/dev/null || true',
            '            echo "  Bundled: $(basename "$lib")"',
            '        fi',
            '    done',
            '}',
        ]
    )

    total = len(unique_archs)
    for step_idx, arch in enumerate(unique_archs, start=1):
        procs = arch_to_procs.get(arch, [])
        label = _arch_label(arch)
        build_suffix = "" if arch == "native" else f"-{arch}"
        build_var = "BUILD_DIR" if arch == "native" else f"BUILD_DIR_{arch.upper().replace('-', '_')}"
        # Toolchain and pkg-config are determined at script RUNTIME based on
        # the host architecture, so the same generated script works on both
        # x86_64 servers and ARM64 Macs.
        is_cross = True  # actual value determined at runtime; affects ldd bundling below
        _arm_aliases = {"arm64", "aarch64"}
        _target_is_arm = arch.lower() in _arm_aliases
        # pkg_env: shell variable name to expand in the cmake command
        if _target_is_arm:
            pkg_env = '"${_ARM64_PKG_ENV}"'
            pkg_var_suffix = "_ARM64"
        else:
            pkg_env = '"${_X86_PKG_ENV}"'
            pkg_var_suffix = "_X86"
        toolchain_file = None  # generated as runtime bash conditional below

        lines.extend(
            [
                "",
                f"# {'═' * 56}",
                f"# [{step_idx}/{total}] {label}  →  build{build_suffix}/bin/",
                f"#         processes: {', '.join(procs) if procs else '(all)'}",
                f"# {'═' * 56}",
                f'{build_var}="${{CMAKE_DIR}}/build{build_suffix}"',
                f'mkdir -p "${{{build_var}}}"',
                "",
            ]
        )

        # Emit bash that selects toolchain at RUNTIME based on host architecture.
        _tc_var = f"_TOOLCHAIN_{step_idx}"
        if _target_is_arm:
            _native_cond   = '[ "$_HOST_ARCH" = "aarch64" ] || [ "$_HOST_ARCH" = "arm64" ]'
            _cross_tc      = "/app/cmake/toolchain-aarch64.cmake"
            _cross_pkg     = '"${_ARM64_PKG_ENV}"'
            _cross_cc      = "aarch64-linux-gnu-gcc"
        else:
            _native_cond   = '[ "$_HOST_ARCH" = "x86_64" ] || [ "$_HOST_ARCH" = "amd64" ]'
            _cross_tc      = "/app/cmake/toolchain-x86_64.cmake"
            _cross_pkg     = '"${_X86_PKG_ENV}"'
            _cross_cc      = "x86_64-linux-gnu-gcc"

        lines.extend(
            [
                f"# Runtime host-arch detection: native if host matches target, cross otherwise",
                f'if {_native_cond}; then',
                f'    {_tc_var}=""',
                f'    _PKG_ENV_{step_idx}=""',
                f'else',
                f'    # Cross-compiler required for this target architecture',
                f'    if ! command -v {_cross_cc} >/dev/null 2>&1; then',
                f'        echo "WARNING: cross-compiler {_cross_cc} not found on this host." >&2',
                f'        echo "  {_arch_label(arch)} target will be compiled with native compiler." >&2',
                f'        echo "  Binaries will be native ${{_HOST_ARCH}} — not {_arch_label(arch)}." >&2',
                f'        {_tc_var}=""',
                f'        _PKG_ENV_{step_idx}=""',
                f'    else',
                f'        {_tc_var}="-DCMAKE_TOOLCHAIN_FILE={_cross_tc}"',
                f'        _PKG_ENV_{step_idx}={_cross_pkg}',
                f'    fi',
                f'fi',
                "",
            ]
        )
        # Override pkg_env to use the runtime-selected variable
        pkg_env = f'"${{_PKG_ENV_{step_idx}}}"'

        # Dependency resolution — arm64 uses the arm64 multiarch pkg-config env
        pkg_suffix = f"_{arch.upper().replace('-', '_')}" if is_cross else ""
        lines.extend(
            [
                f'APR_DIR{pkg_suffix}=$(_pkg_config_path "apr-1" {pkg_env})',
                f'LOG4CPLUS_DIR{pkg_suffix}=$(_pkg_config_path "log4cplus" {pkg_env})',
                f'CUNIT_DIR{pkg_suffix}=$(_pkg_config_path "cunit" {pkg_env})',
                "",
                f'echo "=== [{step_idx}/{total}] {label} — cmake (mode: {"harness" if is_harness else "integration"}, log: ${{LOG_LIBRARY}}) ==="',
            ]
        )

        # cmake command
        cmake_lines = [
            "cmake \\",
            "    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \\",
            f"    -DAPR_DIR=\"${{APR_DIR{pkg_suffix}}}\" \\",
            f"    -DLOG4CPLUS_DIR=\"${{LOG4CPLUS_DIR{pkg_suffix}}}\" \\",
            f"    -DCUNIT_DIR=\"${{CUNIT_DIR{pkg_suffix}}}\" \\",
            '    -DLDP_LOG_USE="${LOG_LIBRARY}" \\',
            "    -DLDP_LINK_TYPE=STATIC \\",
            '    -DCMAKE_BUILD_RPATH="\\$ORIGIN/../lib" \\',
            '    -DCMAKE_INSTALL_RPATH="\\$ORIGIN/../lib" \\',
            "    -DCMAKE_BUILD_RPATH_USE_ORIGIN=ON \\",
            f"    -B \"${{{build_var}}}\" \\",
            '    -S "${CMAKE_SOURCE_DIR}" \\',
        ]
        cmake_lines.append(f"    ${{{_tc_var}}}")
        cmake_lines.append("    ${CMAKE_CONFIG_ARG}")
        lines.extend(cmake_lines)

        lines.extend(
            [
                "",
                f'echo "=== [{step_idx}/{total}] {label} — make ==="',
                f'make --no-print-directory -C "${{{build_var}}}" all',
            ]
        )

        # lib bundling: only for native builds (ldd can't inspect cross-compiled ELFs).
        # Use a runtime check since cross/native is determined by host arch at runtime.
        if _target_is_arm:
            _ldd_native_cond = '[ "$_HOST_ARCH" = "aarch64" ] || [ "$_HOST_ARCH" = "arm64" ]'
        else:
            _ldd_native_cond = '[ "$_HOST_ARCH" = "x86_64" ] || [ "$_HOST_ARCH" = "amd64" ]'
        if True:  # always emit the conditional block; bash decides at runtime
            lines.extend(
                [
                    "",
                    f'if {_ldd_native_cond}; then',
                    f'echo "=== [{step_idx}/{total}] {label} — bundling system libs ==="',
                    f'_LIB_DIR="${{{build_var}}}/lib"',
                    f'_BIN_DIR="${{{build_var}}}/bin"',
                    'mkdir -p "${_LIB_DIR}"',
                    'if [ -d "${_BIN_DIR}" ]; then',
                    '    for _exe in "${_BIN_DIR}"/*; do',
                    '        [ -f "$_exe" ] && [ -x "$_exe" ] && _collect_libs "${_LIB_DIR}" "$_exe"',
                    '    done',
                    'fi',
                    'fi  # end native-only ldd bundling',
                ]
            )

    # Final summary + binary platform verification
    build_vars = []
    for arch in unique_archs:
        build_var = "BUILD_DIR" if arch == "native" else f"BUILD_DIR_{arch.upper().replace('-', '_')}"
        build_vars.append((arch, build_var))

    lines.extend(
        [
            "",
            'echo ""',
            'echo "=== Build complete ==="',
        ]
    )
    for arch, build_var in build_vars:
        lines.append(f'echo "  {_arch_label(arch):<22} ${{{build_var}}}/bin/"')

    lines.extend(
        [
            "",
            'echo ""',
            'echo "=== Binary platform verification ==="',
            # Iterate over every bin/ directory that was built
        ]
    )
    for arch, build_var in build_vars:
        lines.extend(
            [
                f'if [ -d "${{{build_var}}}/bin" ]; then',
                f'    echo "  -- {_arch_label(arch)} --"',
                f'    for _exe in "${{{build_var}}}/bin"/*; do',
                '        [ -f "$_exe" ] && [ -x "$_exe" ] || continue',
                '        _machine=$(readelf -h "$_exe" 2>/dev/null | awk \'/Machine:/{print $NF}\')',
                '        _class=$(readelf -h "$_exe" 2>/dev/null | awk \'/Class:/{print $NF}\')',
                '        printf "  %-35s %s %s\\n" "$(basename "$_exe")" "$_class" "$_machine"',
                '    done',
                'fi',
            ]
        )

    return "\n".join(lines) + "\n"


def _csmgvt_compile_script_content(build_dir: str, cmake_dir: str, project_file: Optional[str] = None) -> str:
    """Generate the content of compile.sh for CSMGVT.

    CSMGVT uses a simplified compilation process:
    - Simple cmake .. from build directory
    - Simple make -j
    - No pkg-config dependency resolution needed
    Supports both Integration mode and Harness mode.
    """
    is_harness = bool(project_file and "harness" in project_file.lower())

    lines = [
        "#!/usr/bin/env bash",
        "# ECOA CSMGVT Compile Script - Auto-generated",
        "# Usage: .vscode/csmgvt-compile.sh",
        "set -euo pipefail",
        "",
        'PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"',
        "",
        "# --- Find CMakeLists.txt directory ---",
        'CMAKE_DIR=""',
        'BUILD_DIR=""',
        "",
    ]

    if is_harness:
        lines.extend([
            "# Harness mode: CMakeLists.txt is under 6-output/platform/",
            'for _out_dir in "6-output" "6-Output"; do',
            '    if [ -f "${PROJECT_DIR}/${_out_dir}/platform/CMakeLists.txt" ]; then',
            '        CMAKE_DIR="${PROJECT_DIR}/${_out_dir}/platform"',
            '        break',
            '    fi',
            'done',
            'if [ -z "$CMAKE_DIR" ]; then',
            '    echo "ERROR: CMakeLists.txt not found in platform directory" >&2',
            '    exit 1',
            'fi',
            "",
            'BUILD_DIR="${CMAKE_DIR}/build"',
            "",
            "# Harness: clear build dir if CMakeCache exists",
            'if [ -f "${BUILD_DIR}/CMakeCache.txt" ]; then',
            '    echo "Clearing build directory (CMakeCache.txt exists)..."',
            '    rm -rf "${BUILD_DIR}"',
            'fi',
        ])
    else:
        lines.extend([
            "# Integration mode: CMakeLists.txt is under 6-output/ (or similar)",
            'for _out_dir in "6-output" "6-Output" "Output" "output" "build-output"; do',
            '    if [ -f "${PROJECT_DIR}/${_out_dir}/CMakeLists.txt" ]; then',
            '        CMAKE_DIR="${PROJECT_DIR}/${_out_dir}"',
            '        break',
            '    fi',
            'done',
            'if [ -z "$CMAKE_DIR" ]; then',
            '    echo "ERROR: CMakeLists.txt not found in 6-output directory" >&2',
            '    exit 1',
            'fi',
            "",
            'BUILD_DIR="${CMAKE_DIR}/build"',
        ])

    lines.extend([
        "",
        'mkdir -p "${BUILD_DIR}"',
        "",
        "# --- Run CMake ---",
        'echo "=== Running CMake for CSMGVT ==="',
        'cd "${BUILD_DIR}"',
        'cmake ..',
        "",
        "# --- Run Make ---",
        'echo "=== Running Make ==="',
        'make -j',
        "",
        'echo "=== CSMGVT Build complete ==="',
    ])

    return "\n".join(lines) + "\n"


def write_compile_script(
    target_dir: str,
    build_dir: str,
    cmake_dir: str,
    project_file: Optional[str] = None,
    tool_id: str = "ldp",
    topology: Optional["DebugTopology"] = None,
) -> str:
    """Write compile script for recompiling the ECOA project.

    When *topology* is provided, the LDP compile script is generated
    dynamically per architecture derived from LogicalProcessor.type in the
    model.  Each unique architecture gets its own cmake + make block,
    eliminating the need for a runtime --arch flag.

    Args:
        target_dir: Directory where .vscode/ will be created
        build_dir: Build directory path
        cmake_dir: CMake source directory path
        project_file: Project file name (used to detect harness mode)
        tool_id: Tool identifier ('ldp' or 'csmgvt')
        topology: Debug topology carrying per-process target_arch (LDP only)

    Returns:
        Path to the generated compile script
    """
    target_path = Path(target_dir)
    vscode_dir = target_path / ".vscode"
    vscode_dir.mkdir(parents=True, exist_ok=True)

    script_filename = f"{tool_id}-compile.sh"
    compile_script_path = vscode_dir / script_filename
    content = _compile_script_content(build_dir, cmake_dir, project_file, tool_id, topology)
    _write_executable(compile_script_path, content)

    return str(compile_script_path)


def _vscode_readme_content(has_compile_script: bool, has_distributed_debug: bool, is_harness: bool = False, tool_id: str = "ldp") -> str:
    """Generate the content of the readme.md explaining .vscode scripts."""
    tool_name = "LDP" if tool_id == "ldp" else "CSMGVT"
    compile_script_name = f"{tool_id}-compile.sh"
    lines = [
        "# .vscode 脚本使用说明",
        "",
        f"本目录下的 `.vscode` 文件夹包含由 ECOA {tool_name} 工具自动生成的辅助脚本和配置文件，",
        "用于项目编译和分布式调试。",
        "",
        "---",
        "",
    ]

    if has_compile_script:
        mode_desc = "**Harness 模式**" if is_harness else "**Integration 模式**"
        if tool_id == "csmgvt":
            # CSMGVT simplified compile script documentation
            lines.extend(
                [
                    "## 编译脚本",
                    "",
                    f"### `.vscode/{compile_script_name}` — 重新编译 CSMGVT 项目",
                    "",
                    f"当前项目为" + mode_desc + "，脚本已针对该模式进行配置。",
                    "",
                    "**用法：**",
                    "",
                    "```bash",
                    f"# 直接执行编译",
                    f".vscode/{compile_script_name}",
                    "```",
                    "",
                    "**脚本功能：**",
                    "",
                    "- 自动检测 `6-output/` 目录中的 CMakeLists.txt",
                    "- 执行 `cmake ..` 配置",
                    "- 执行 `make -j` 编译",
                    "",
                ]
            )
        else:
            # LDP full compile script documentation
            lines.extend(
                [
                    "## 编译脚本",
                    "",
                    f"### `.vscode/{compile_script_name}` — 重新编译 LDP 项目",
                    "",
                    f"当前项目为" + mode_desc + "，脚本已针对该模式进行配置。",
                    "",
                    "**用法：**",
                    "",
                    "```bash",
                    "# 使用默认日志库 (log4cplus) 编译",
                    f".vscode/{compile_script_name}",
                    "",
                    "# 指定日志库编译 (支持: log4cplus, zlog, lttng)",
                    f".vscode/{compile_script_name} zlog",
                    "```",
                    "",
                    "**脚本功能：**",
                    "",
                    "- 自动检测项目模式（Harness / Integration）",
                    "- 通过 `pkg-config` 动态查找依赖路径（log4cplus、apr-1、cunit）",
                    "- 查找 `cmake_config.cmake` 配置文件",
                    "- 编译时设置 `RPATH=$ORIGIN/../lib`，使可执行文件在运行时优先从同级 `lib/` 目录加载共享库",
                    "- 执行 `cmake` 配置和 `make` 编译",
                    "- **自动打包依赖库**：编译完成后将所有依赖的共享库（apr-1、log4cplus、ecoa 等）复制到 `build/lib/` 目录",
                    "",
                    "**打包部署说明：**",
                    "",
                    "编译完成后，`build/` 目录即为完整的可部署包：",
                    "",
                    "```",
                    "build/",
                    "├── bin/         ← 可执行文件 (platform, PD_*)",
                    "└── lib/         ← 所有依赖共享库（自动收集）",
                    "```",
                    "",
                    "将整个 `build/` 目录复制到目标机器，即可直接运行，无需预装依赖库：",
                    "",
                    "```bash",
                    "cd build/bin && ./platform",
                    "```",
                    "",
                ]
            )

        if is_harness:
            lines.extend(
                [
                    "**Harness 模式特殊处理：**",
                    "",
                    "- CMakeLists.txt 位于 `6-output/platform/` 目录下",
                    "- 使用 `.distributed-debug-wrapper` 作为 CMake 源目录",
                    "- 当 `CMakeCache.txt` 存在时，自动清除 build 目录后重新配置",
                    "",
                ]
            )
        else:
            lines.extend(
                [
                    "**Integration 模式说明：**",
                    "",
                    "- CMakeLists.txt 位于 `6-output/` 目录下",
                    "- CMake 源目录与 CMakeLists.txt 所在目录相同",
                    "",
                ]
            )

        lines.extend(
            [
                "---",
                "",
            ]
        )

    if has_distributed_debug:
        lines.extend(
            [
                "## 分布式调试脚本",
                "",
                "以下脚本用于多节点分布式 ECOA 应用的调试，通过 Docker Compose 启动调试容器，",
                "并使用 gdbserver 远程调试各节点上的进程。",
                "",
                "### `.vscode/start-distributed-debug.sh` — 启动分布式调试环境",
                "",
                "```bash",
                ".vscode/start-distributed-debug.sh",
                "```",
                "",
                "启动 Docker Compose 服务，为每个计算节点创建调试容器，",
                "并在容器内启动 gdbserver 等待调试连接。",
                "",
                "### `.vscode/stop-distributed-debug.sh` — 停止分布式调试环境",
                "",
                "```bash",
                ".vscode/stop-distributed-debug.sh",
                "```",
                "",
                "停止并移除所有调试容器和 Docker 网络。",
                "",
                "### `.vscode/status-distributed-debug.sh` — 查看调试状态",
                "",
                "```bash",
                ".vscode/status-distributed-debug.sh",
                "```",
                "",
                "查看当前分布式调试环境的运行状态，包括各容器和服务信息。",
                "",
                "### `.vscode/distributed-debug.compose.yml` — Docker Compose 配置",
                "",
                "Docker Compose 配置文件，定义了各节点的调试容器，包括镜像、网络和挂载。",
                "此文件由工具自动生成，**请勿手动修改**。",
                "",
                "---",
                "",
                "## VS Code 调试配置",
                "",
                "### `.vscode/launch.json` — 调试启动配置",
                "",
                "包含以下调试配置：",
                "",
                "- **Debug platform** — 本地调试 platform 进程",
                "- **Attach platform (main)** — 远程附加到主节点的 platform 进程",
                "- **Attach PD_xxx (node)** — 远程附加到各保护域进程",
                "- **Attach distributed ECOA** — 复合配置，同时附加所有分布式进程",
                "",
                "**使用步骤：**",
                "",
                "1. 运行 `start-distributed-debug.sh` 启动调试环境",
                '2. 在 VS Code 中选择对应的调试配置（如 "Attach distributed ECOA"）',
                "3. 按 F5 开始调试",
                "",
                "---",
                "",
            ]
        )
    elif has_compile_script:
        lines.extend(
            [
                "## VS Code 调试配置",
                "",
                "### `.vscode/launch.json` — 调试启动配置",
                "",
                "包含本地调试配置：",
                "",
                "- **Debug platform** — 本地调试 platform 进程",
                "",
                "---",
                "",
            ]
        )

    tool_name_upper = "LDP" if tool_id == "ldp" else "CSMGVT"
    lines.extend(
        [
            "## 注意事项",
            "",
            f"- 以上所有文件由 ECOA {tool_name_upper} 工具自动生成，每次执行 {tool_name_upper} 时会覆盖更新",
            "- 如需自定义修改，请在生成后手动调整（但下次执行后会覆盖）",
        ]
    )

    if tool_id == "ldp":
        lines.append("- 编译脚本依赖 `pkg-config` 工具，请确保系统已安装")

    lines.extend(
        [
            "- 分布式调试脚本依赖 Docker 和 Docker Compose",
            "",
        ]
    )

    return "\n".join(lines)


def write_vscode_readme(target_dir: str, has_compile_script: bool, has_distributed_debug: bool, is_harness: bool = False, tool_id: str = "ldp") -> str:
    """Write readme.md in the target directory explaining .vscode scripts.

    Args:
        target_dir: Directory where readme.md will be created (project root, e.g. Steps/)
        has_compile_script: Whether compile script was generated
        has_distributed_debug: Whether distributed debug scripts were generated
        is_harness: Whether the project is in harness mode
        tool_id: Tool identifier ('ldp' or 'csmgvt')
        is_harness: Whether the project is in harness mode

    Returns:
        Path to the generated readme.md
    """
    target_path = Path(target_dir)
    readme_path = target_path / README_FILENAME
    content = _vscode_readme_content(has_compile_script, has_distributed_debug, is_harness, tool_id)
    readme_path.write_text(content, encoding="utf-8")
    return str(readme_path)


def write_distributed_debug_assets(target_dir: str, build_dir: str, topology: Optional[DebugTopology], cmake_dir: Optional[str] = None, project_file: Optional[str] = None, tool_id: str = "ldp") -> Dict[str, str]:
    """Write distributed debug assets including compile script and launch configuration.

    Args:
        target_dir: Directory where .vscode/ will be created
        build_dir: Build directory path
        topology: Debug topology information
        cmake_dir: CMake source directory path (if None, no compile script is generated)
        project_file: Project file name (used to detect harness mode)
        tool_id: Tool identifier ('ldp' or 'csmgvt'), determines compile script name and content

    Returns:
        Dictionary with paths to generated assets
    """
    target_path = Path(target_dir)
    vscode_dir = target_path / ".vscode"
    vscode_dir.mkdir(parents=True, exist_ok=True)

    result = {"launch_json": write_distributed_debug_launch_json(target_dir, build_dir, topology)}

    # Always generate compile script when cmake_dir is provided
    has_compile_script = cmake_dir is not None
    if has_compile_script:
        compile_script_path = write_compile_script(
            target_dir, build_dir, cmake_dir, project_file, tool_id,
            topology=topology,
        )
        result["compile_script"] = compile_script_path

    has_distributed_debug = topology is not None and topology.is_distributed
    if not has_distributed_debug:
        # Generate readme even for non-distributed projects
        is_harness = bool(project_file and "harness" in project_file.lower())
        readme_path = write_vscode_readme(target_dir, has_compile_script, has_distributed_debug, is_harness, tool_id)
        result["readme"] = readme_path
        return result

    compose_path = vscode_dir / COMPOSE_FILENAME
    start_script_path = vscode_dir / START_SCRIPT_FILENAME
    stop_script_path = vscode_dir / STOP_SCRIPT_FILENAME
    status_script_path = vscode_dir / STATUS_SCRIPT_FILENAME

    compose_path.write_text(render_distributed_debug_compose(build_dir, topology), encoding="utf-8")
    _write_executable(start_script_path, _start_script())
    _write_executable(stop_script_path, _stop_script())
    _write_executable(status_script_path, _status_script())

    # Generate readme for distributed debug projects
    is_harness = bool(project_file and "harness" in project_file.lower())
    readme_path = write_vscode_readme(target_dir, has_compile_script, has_distributed_debug, is_harness, tool_id)
    result["readme"] = readme_path

    result.update(
        {
            "docker_compose": str(compose_path),
            "start_script": str(start_script_path),
            "stop_script": str(stop_script_path),
            "status_script": str(status_script_path),
        }
    )
    return result
