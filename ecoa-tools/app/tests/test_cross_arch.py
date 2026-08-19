"""
Unit tests for ARM64 cross-compilation and QEMU-based distributed debug.

Run with:
    cd ecoa-tools
    python -m pytest app/tests/test_cross_arch.py -v
"""
import json
import stat
import sys
import types
import unittest
from pathlib import Path

# ---------------------------------------------------------------------------
# Minimal stubs so the module can import without a real config file
# ---------------------------------------------------------------------------
yaml_stub = types.ModuleType("yaml")
yaml_stub.safe_load = lambda _: {
    "verbose": 3, "uploads_dir": "uploads", "outputs_dir": "outputs",
    "logs_dir": "logs", "tools": {},
}
sys.modules.setdefault("yaml", yaml_stub)

from app.services.distributed_debug import (
    DebugProcess,
    DebugTopology,
    collect_debug_topology,
    gdbserver_command,
    write_distributed_debug_assets,
    write_distributed_debug_launch_json,
    _compose_yaml,
    _distributed_launch_config,
)

TEST_TMP_ROOT = Path(__file__).resolve().parents[2] / ".tmp-tests" / "cross_arch"
TEST_TMP_ROOT.mkdir(parents=True, exist_ok=True)

DEBUG_START_PORT = 2000

# ---------------------------------------------------------------------------
# Shared fixture helpers
# ---------------------------------------------------------------------------

DEPLOYMENT_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<deployment xmlns="http://www.ecoa.technology/deployment-2.0"
            finalAssembly="demo" logicalSystem="cs1">
  <protectionDomain name="PD_Writer">
    <executeOn computingNode="node0" computingPlatform="Plat"/>
  </protectionDomain>
  <protectionDomain name="PD_Reader">
    <executeOn computingNode="node1" computingPlatform="Plat"/>
  </protectionDomain>
</deployment>
"""

NODES_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<nodesDeployment>
  <logicalComputingNode id="main"  ipAddress="192.168.10.1"/>
  <logicalComputingNode id="node0" ipAddress="192.168.10.1"/>
  <logicalComputingNode id="node1" ipAddress="192.168.10.2"/>
</nodesDeployment>
"""

PROJECT_XML = """\
<?xml version='1.0' encoding='utf-8'?>
<ECOAProject xmlns="http://www.ecoa.technology/project-2.0" name="demo">
  <outputDirectory>6-output</outputDirectory>
  <deploymentSchema>5-Integration/demo.deployment.xml</deploymentSchema>
</ECOAProject>
"""


def _make_project(root: Path, build_has_platform: bool = True) -> tuple:
    """Create a minimal ECOA Steps layout with topology files."""
    import shutil
    if root.exists():
        shutil.rmtree(root)
    steps = root / "Steps"
    integ = steps / "5-Integration"
    integ.mkdir(parents=True)
    (integ / "demo.deployment.xml").write_text(DEPLOYMENT_XML)
    (integ / "nodes_deployment.xml").write_text(NODES_XML)
    (steps / "demo.project.xml").write_text(PROJECT_XML)

    build_dir = steps / "6-output" / "build"
    if build_has_platform:
        (build_dir / "bin").mkdir(parents=True)
        binary = build_dir / "bin" / "platform"
        binary.write_text("fake-elf")
        binary.chmod(binary.stat().st_mode | stat.S_IEXEC)

    return steps, build_dir


# ---------------------------------------------------------------------------
# 1. DebugTopology carries target_arch
# ---------------------------------------------------------------------------

class TestDebugTopologyTargetArch(unittest.TestCase):

    def setUp(self):
        self.root = TEST_TMP_ROOT / "topology"
        self.root.mkdir(parents=True, exist_ok=True)

    def test_default_target_arch_is_native(self):
        steps, build_dir = _make_project(self.root / "native")
        topology = collect_debug_topology(str(steps), str(build_dir))
        self.assertIsNotNone(topology)
        self.assertEqual(topology.target_arch, "native")

    def test_arm64_target_arch_is_stored(self):
        steps, build_dir = _make_project(self.root / "arm64")
        topology = collect_debug_topology(str(steps), str(build_dir), target_arch="arm64")
        self.assertIsNotNone(topology)
        self.assertEqual(topology.target_arch, "arm64")

    def test_topology_is_distributed(self):
        steps, build_dir = _make_project(self.root / "dist")
        topology = collect_debug_topology(str(steps), str(build_dir), target_arch="arm64")
        self.assertTrue(topology.is_distributed)
        self.assertGreater(len(topology.processes), 1)


# ---------------------------------------------------------------------------
# 2. gdbserver_command: native vs arm64
# ---------------------------------------------------------------------------

class TestGdbserverCommand(unittest.TestCase):

    def _process(self, target_arch: str = "native") -> DebugProcess:
        return DebugProcess(
            name="platform",
            node_id="main",
            host="192.168.10.1",
            port=DEBUG_START_PORT,
            service_name="ecoa-main",
            target_arch=target_arch,
        )

    # build_dir is always the native build base; gdbserver_command adjusts internally
    def _build_dir(self) -> str:
        return "/workspace/demo/Steps/6-output/build"

    def test_native_uses_gdbserver(self):
        cmd = gdbserver_command(self._build_dir(), self._process("native"))
        self.assertIn("gdbserver", cmd)
        self.assertNotIn("qemu", cmd)

    def test_arm64_uses_qemu_gdb_stub(self):
        cmd = gdbserver_command(self._build_dir(), self._process("arm64"))
        self.assertIn("qemu-aarch64-static", cmd)
        self.assertIn("-g 2000", cmd)
        self.assertNotIn("gdbserver 0.0.0.0:", cmd)

    def test_arm64_binary_dir_uses_build_arm64(self):
        cmd = gdbserver_command(self._build_dir(), self._process("arm64"))
        self.assertIn("build-arm64", cmd)

    def test_arm64_command_checks_qemu_binary_exists(self):
        cmd = gdbserver_command(self._build_dir(), self._process("arm64"))
        self.assertIn("command -v qemu-aarch64-static", cmd)

    def test_arm64_command_checks_target_binary_exists(self):
        cmd = gdbserver_command(self._build_dir(), self._process("arm64"))
        self.assertIn("[ -f ./platform ]", cmd)

    def test_arm64_nohup_in_background(self):
        cmd = gdbserver_command(self._build_dir(), self._process("arm64"))
        self.assertIn("nohup", cmd)
        self.assertIn("&", cmd)

    def test_native_nohup_in_background(self):
        cmd = gdbserver_command(self._build_dir(), self._process("native"))
        self.assertIn("nohup bash", cmd)
        self.assertIn("gdbserver 0.0.0.0:", cmd)
        self.assertIn("&", cmd)


# ---------------------------------------------------------------------------
# 3. Compose YAML: no platform: field (stays amd64 for offline support)
# ---------------------------------------------------------------------------

class TestComposeYamlNoPlatform(unittest.TestCase):

    def _topology(self, target_arch: str) -> DebugTopology:
        processes = [
            DebugProcess("platform", "main", "192.168.10.1", 2000, "ecoa-main"),
            DebugProcess("PD_Writer", "node0", "192.168.10.1", 2001, "ecoa-main"),
            DebugProcess("PD_Reader", "node1", "192.168.10.2", 2002, "ecoa-node1"),
        ]
        return DebugTopology(
            integration_dir="/workspace/Steps/5-Integration",
            docker_subnet="192.168.10.0/24",
            processes=processes,
            is_distributed=True,
            target_arch=target_arch,
        )

    def _compose(self, target_arch: str) -> str:
        return _compose_yaml(
            build_dir="/workspace/Steps/6-output/build",
            topology=self._topology(target_arch),
        )

    def test_arm64_compose_has_no_platform_field(self):
        yaml = self._compose("arm64")
        self.assertNotIn("platform:", yaml,
            "arm64 containers must stay linux/amd64 so no binfmt_misc is needed offline")

    def test_native_compose_has_no_platform_field(self):
        yaml = self._compose("native")
        self.assertNotIn("platform:", yaml)

    def test_compose_has_both_services(self):
        yaml = self._compose("arm64")
        self.assertIn("ecoa-main:", yaml)
        self.assertIn("ecoa-node1:", yaml)

    def test_compose_has_cap_add_sys_ptrace(self):
        yaml = self._compose("arm64")
        self.assertIn("SYS_PTRACE", yaml)


# ---------------------------------------------------------------------------
# 4. launch.json: gdb-multiarch for arm64
# ---------------------------------------------------------------------------

class TestLaunchJsonArch(unittest.TestCase):

    def _process(self, name: str, node_id: str, host: str, port: int,
                 service: str, target_arch: str) -> DebugProcess:
        return DebugProcess(name=name, node_id=node_id, host=host, port=port,
                            service_name=service, target_arch=target_arch)

    def _topology(self, arm64: bool) -> DebugTopology:
        arch = "arm64" if arm64 else "native"
        processes = [
            self._process("platform",  "main",  "192.168.10.1", 2000, "ecoa-main",  arch),
            self._process("PD_Reader", "node1", "192.168.10.2", 2001, "ecoa-node1", arch),
        ]
        return DebugTopology(
            integration_dir="/workspace/Steps/5-Integration",
            docker_subnet="192.168.10.0/24",
            processes=processes,
            is_distributed=True,
        )

    def test_arm64_uses_gdb_multiarch(self):
        p = self._process("platform", "main", "192.168.10.1", 2000, "ecoa-main", "arm64")
        config = _distributed_launch_config("/workspace/Steps", "/workspace/Steps/6-output/build", p)
        self.assertEqual(config["miDebuggerPath"], "/usr/bin/gdb-multiarch")

    def test_arm64_sets_target_architecture(self):
        p = self._process("platform", "main", "192.168.10.1", 2000, "ecoa-main", "arm64")
        config = _distributed_launch_config("/workspace/Steps", "/workspace/Steps/6-output/build", p)
        self.assertEqual(config.get("targetArchitecture"), "arm64")

    def test_arm64_binary_path_uses_build_arm64(self):
        p = self._process("platform", "main", "192.168.10.1", 2000, "ecoa-main", "arm64")
        config = _distributed_launch_config("/workspace/Steps", "/workspace/Steps/6-output/build", p)
        self.assertIn("build-arm64", config["program"])

    def test_native_uses_gdb(self):
        p = self._process("platform", "main", "192.168.10.1", 2000, "ecoa-main", "native")
        config = _distributed_launch_config("/workspace/Steps", "/workspace/Steps/6-output/build", p)
        self.assertEqual(config["miDebuggerPath"], "/usr/bin/gdb")
        self.assertNotIn("targetArchitecture", config)

    def test_launch_json_file_written_with_gdb_multiarch(self):
        root = TEST_TMP_ROOT / "launch_arm64"
        root.mkdir(parents=True, exist_ok=True)
        topology = self._topology(arm64=True)
        write_distributed_debug_launch_json(str(root), "/workspace/Steps/6-output/build", topology)
        launch = json.loads((root / ".vscode" / "launch.json").read_text())
        debugger_paths = [c["miDebuggerPath"] for c in launch["configurations"]
                          if "miDebuggerPath" in c]
        self.assertTrue(all(p == "/usr/bin/gdb-multiarch" for p in debugger_paths),
                        f"Expected all gdb-multiarch, got: {debugger_paths}")

    def test_launch_json_native_uses_gdb(self):
        root = TEST_TMP_ROOT / "launch_native"
        root.mkdir(parents=True, exist_ok=True)
        topology = self._topology(arm64=False)
        write_distributed_debug_launch_json(str(root), "/workspace/Steps/6-output/build", topology)
        launch = json.loads((root / ".vscode" / "launch.json").read_text())
        debugger_paths = [c["miDebuggerPath"] for c in launch["configurations"]
                          if "miDebuggerPath" in c]
        self.assertTrue(all(p == "/usr/bin/gdb" for p in debugger_paths),
                        f"Expected all gdb, got: {debugger_paths}")


# ---------------------------------------------------------------------------
# 5. compile.sh: arm64 injects toolchain argument
# ---------------------------------------------------------------------------

class TestCompileScriptArch(unittest.TestCase):

    # Logical system XML with configurable per-node processor type
    @staticmethod
    def _logical_system_xml(machine0_type: str, machine1_type: str) -> str:
        return f"""\
<?xml version="1.0" encoding="UTF-8"?>
<ecoa:logicalSystem xmlns:ecoa="http://www.ecoa.technology/logicalsystem-2.0" id="cs1">
  <logicalComputingPlatform id="Dassault">
    <logicalComputingNode id="main">
      <logicalProcessors number="4" type="{machine0_type}"><stepDuration nanoSeconds="1"/></logicalProcessors>
    </logicalComputingNode>
    <logicalComputingNode id="machine0">
      <logicalProcessors number="4" type="{machine0_type}"><stepDuration nanoSeconds="1"/></logicalProcessors>
    </logicalComputingNode>
    <logicalComputingNode id="machine1">
      <logicalProcessors number="4" type="{machine1_type}"><stepDuration nanoSeconds="1"/></logicalProcessors>
    </logicalComputingNode>
  </logicalComputingPlatform>
</ecoa:logicalSystem>"""

    def _write_assets(self, target_arch: str, root: Path) -> dict:
        """Write ECOA Steps with a logical-system.xml whose processor type
        matches *target_arch*, then generate .vscode/ assets."""
        import shutil
        if root.exists():
            shutil.rmtree(root)
        steps = root / "Steps"
        (steps / "6-output").mkdir(parents=True)
        (steps / "6-output" / "CMakeLists.txt").write_text("project(demo)\n")
        (steps / "5-Integration").mkdir(parents=True)
        (steps / "5-Integration" / "demo.deployment.xml").write_text(DEPLOYMENT_XML)
        (steps / "5-Integration" / "nodes_deployment.xml").write_text(NODES_XML)
        # Embed the processor type so collect_debug_topology reads it from XML
        proc_type = target_arch if target_arch != "native" else "x86_64"
        (steps / "5-Integration" / "cs1.logical-system.xml").write_text(
            self._logical_system_xml(proc_type, proc_type)
        )
        (steps / "demo.project.xml").write_text(PROJECT_XML)
        build_dir = steps / "6-output" / "build"
        (build_dir / "bin").mkdir(parents=True)
        (build_dir / "bin" / "platform").write_text("fake")

        topology = collect_debug_topology(str(steps), str(build_dir))
        return write_distributed_debug_assets(
            target_dir=str(steps),
            build_dir=str(build_dir),
            topology=topology,
            cmake_dir=str(steps / "6-output"),
            project_file="demo.project.xml",
            tool_id="ldp",
        )

    def test_arm64_compile_script_has_toolchain_file(self):
        """arm64 blocks must reference the aarch64 toolchain file."""
        root = TEST_TMP_ROOT / "compile_arm64"
        root.mkdir(parents=True, exist_ok=True)
        assets = self._write_assets("arm64", root)
        script = Path(assets["compile_script"]).read_text()
        self.assertIn("toolchain-aarch64.cmake", script)

    def test_arm64_compile_script_has_build_arm64_dir(self):
        """arm64 cmake block must use build-arm64 directory."""
        root = TEST_TMP_ROOT / "compile_arm64b"
        root.mkdir(parents=True, exist_ok=True)
        assets = self._write_assets("arm64", root)
        script = Path(assets["compile_script"]).read_text()
        self.assertIn("build-arm64", script)

    def test_arm64_compile_script_arch_map_in_header(self):
        """The architecture map comment must appear at the top of the script."""
        root = TEST_TMP_ROOT / "compile_arm64c"
        root.mkdir(parents=True, exist_ok=True)
        assets = self._write_assets("arm64", root)
        script = Path(assets["compile_script"]).read_text()
        self.assertIn("arm64", script[:600])  # arch map is in the first ~20 lines

    def test_native_compile_script_has_no_toolchain(self):
        """Native build must not reference the arm64 toolchain."""
        root = TEST_TMP_ROOT / "compile_native"
        root.mkdir(parents=True, exist_ok=True)
        assets = self._write_assets("native", root)
        script = Path(assets["compile_script"]).read_text()
        self.assertNotIn("toolchain-aarch64.cmake", script)
        self.assertNotIn("build-arm64", script)

    def test_arm64_start_script_sets_ecoa_target_arch(self):
        root = TEST_TMP_ROOT / "start_arm64"
        root.mkdir(parents=True, exist_ok=True)
        assets = self._write_assets("arm64", root)
        start_script = Path(assets["start_script"]).read_text()
        self.assertIn("ECOA_TARGET_ARCH", start_script)


if __name__ == "__main__":
    unittest.main()
