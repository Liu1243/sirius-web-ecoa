"""Tool execution service."""

import os
import subprocess
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import time as _time
from datetime import datetime
import platform as _platform

from app.utils.config import get_config
from app.utils.logger import setup_logger
from app.services.distributed_debug import collect_debug_topology, write_distributed_debug_assets, _parse_platform_processor_types

# Initialize logger for this module
logger = setup_logger('app.services.executor')


def _elf_arch_matches(binary_path: str, target_arch: str) -> bool:
    """Return True if the ELF machine type of *binary_path* matches *target_arch*.

    Used by compile_project's skip-if-exists guard so a stale binary compiled for
    a different architecture (e.g. a native x86_64 binary left by ldp-compile.sh
    in a build/ dir now targeted for arm64 cross-compile) does not short-circuit
    a needed recompile.

    Reads the ELF header directly (e18 bytes) rather than shelling out to `file`
    so it works in minimal containers.  Any read/parse failure returns False
    (treat as mismatch → recompile), which is the safe direction.
    """
    try:
        with open(binary_path, "rb") as fh:
            ident = fh.read(20)
        if len(ident) < 20 or ident[:4] != b"\x7fELF":
            return False
        # ELF64: e_machine at offset 18 (2 bytes, little-endian on the file).
        # For ELF32 e_machine is at offset 18 too; we read 2 bytes either way.
        e_machine = int.from_bytes(ident[18:20], "little")
    except (OSError, ValueError):
        return False

    # Normalize target_arch the same way the rest of the codebase does.
    norm = (target_arch or "native").lower()
    if norm in ("native", "amd64", "x86_64", ""):
        expected = {0x3E, 0x03}        # EM_X86_64, EM_386
    elif norm in ("arm64", "aarch64"):
        expected = {0xB7}              # EM_AARCH64
    elif norm in ("arm", "arm32"):
        expected = {0x28}              # EM_ARM
    else:
        return False
    return e_machine in expected


def _detect_platforms(project_path: str) -> List[str]:
    """Parse 5-Integration/*.logical-system.xml to return list of logicalComputingPlatform ids.
    Returns empty list if no logical-system file found."""
    integration_dir = next(
        (d for d in Path(project_path).iterdir()
         if d.is_dir() and d.name.startswith("5-Integration")),
        None
    )
    if integration_dir is None:
        return []
    ls_files = list(integration_dir.glob("*.logical-system.xml"))
    if not ls_files:
        return []
    platforms = []
    try:
        root = ET.parse(ls_files[0]).getroot()
        for elem in root.iter():
            tag = elem.tag.split('}', 1)[-1] if '}' in elem.tag else elem.tag
            if tag == 'logicalComputingPlatform':
                pid = elem.get('id')
                if pid:
                    platforms.append(pid)
    except ET.ParseError:
        pass
    return platforms


def _has_platform_links(project_path: str) -> bool:
    """Return True if logical-system has logicalComputingPlatformLinks (multi-platform design)."""
    integration_dir = next(
        (d for d in Path(project_path).iterdir()
         if d.is_dir() and d.name.startswith("5-Integration")),
        None
    )
    if integration_dir is None:
        return False
    for ls_file in integration_dir.glob("*.logical-system.xml"):
        try:
            root = ET.parse(ls_file).getroot()
            for elem in root.iter():
                tag = elem.tag.split('}', 1)[-1] if '}' in elem.tag else elem.tag
                if tag == 'logicalComputingPlatformLinks':
                    return True
        except ET.ParseError:
            pass
    return False


class ProjectNotFoundError(Exception):
    """Raised when project directory is not found."""
    pass


class ProjectFileNotFoundError(Exception):
    """Raised when project file is not found in project directory."""
    pass


class ToolExecutor:
    """Service for executing ECOA tools."""

    def __init__(self):
        """Initialize tool executor."""
        self.config = get_config()

    def execute(
        self,
        tool_id: str,
        input_file: str,
        verbose: int = None
    ) -> Dict[str, any]:
        """
        Execute a tool with the given input file.

        Args:
            tool_id: Tool identifier (e.g., 'exvt')
            input_file: Path to input XML file
            verbose: Verbosity level (overrides default)

        Returns:
            Dictionary with execution result:
            {
                'success': bool,
                'tool': str,
                'input_file': str,
                'output_files': List[str],
                'stdout': str,
                'stderr': str,
                'return_code': int,
                'message': str
            }

        Raises:
            ValueError: If tool is not found or file doesn't exist
        """
        # Get tool configuration
        tool_config = self.config.get_tool(tool_id)
        if not tool_config:
            raise ValueError(f"Tool not found: {tool_id}")

        command = tool_config.get('command')
        if not command:
            raise ValueError(f"Command not defined for tool: {tool_id}")

        # Validate input file
        if not os.path.exists(input_file):
            raise ValueError(f"Input file not found: {input_file}")

        # Get verbose level
        if verbose is None:
            verbose = self.config.verbose

        # Get input file directory for context
        input_dir = os.path.dirname(os.path.abspath(input_file))
        input_filename = os.path.basename(input_file)

        # Build command - use filename only, run from file's directory
        cmd = [command, '-p', input_filename, '-v', str(verbose)]

        logger.info(f"Executing: {' '.join(cmd)} in directory: {input_dir}")

        # Execute tool
        try:
            result = subprocess.run(
                cmd,
                cwd=input_dir,  # Run in the input file's directory
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout
            )

            # Find output files (search for generated files in input directory)
            output_files = self._find_output_files(
                input_dir,
                tool_config.get('output_types', [])
            )

            success = result.returncode == 0

            # Copy output files to outputs directory
            copied_files = []
            if success:
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                output_subdir = os.path.join(
                    self.config.outputs_dir,
                    f"{tool_id}_{timestamp}"
                )
                Path(output_subdir).mkdir(parents=True, exist_ok=True)

                for output_file in output_files:
                    dest = os.path.join(output_subdir, os.path.basename(output_file))
                    shutil.copy2(output_file, dest)
                    copied_files.append(dest)

            return {
                'success': success,
                'tool': tool_id,
                'command': command,
                'input_file': input_file,
                'output_files': copied_files if success else [],
                'stdout': result.stdout,
                'stderr': result.stderr,
                'return_code': result.returncode,
                'message': self._get_message(result.returncode, tool_id)
            }

        except subprocess.TimeoutExpired:
            logger.error(f"[{tool_id.upper()}] Tool execution timeout: {command}")
            return {
                'success': False,
                'tool': tool_id,
                'command': command,
                'input_file': input_file,
                'output_files': [],
                'stdout': '',
                'stderr': 'Execution timeout (5 minutes)',
                'return_code': -1,
                'message': f'Tool execution timeout: {tool_id}'
            }
        except Exception as e:
            logger.exception(f"[{tool_id.upper()}] Error executing tool: {e}")
            return {
                'success': False,
                'tool': tool_id,
                'command': command,
                'input_file': input_file,
                'output_files': [],
                'stdout': '',
                'stderr': str(e),
                'return_code': -1,
                'message': f'Execution error: {str(e)}'
            }

    def _find_output_files(self, directory: str, extensions: List[str]) -> List[str]:
        """
        Find generated output files in directory.

        Args:
            directory: Directory to search
            extensions: List of file extensions to match (e.g., ['.h', '.c'])

        Returns:
            List of output file paths
        """
        output_files = []

        for ext in extensions:
            # Find all files with the given extension
            for file_path in Path(directory).glob(f"*{ext}"):
                # Skip the input file itself
                if file_path.is_file():
                    output_files.append(str(file_path))

        return sorted(output_files)

    def _create_vscode_launch_config(
        self,
        project_path: str,
        project_name: str,
        build_dir: str,
        cmake_dir: str,
        workspace_dir: str = None,
        project_file: str = None,
        tool_id: str = "ldp",
        target_arch: str = "native",
    ) -> None:
        """
        Create VSCode launch.json configuration for the project.

        Creates .vscode/launch.json in the workspace directory (or project directory if not provided)
        with debug configuration for the generated executables.
        Also creates a tool-specific compile script (e.g., ldp-compile.sh or csmgvt-compile.sh).

        Args:
            project_path: Project root directory (e.g., /workspace/.../Steps)
            project_name: Name of the project
            build_dir: Build directory path
            cmake_dir: CMake directory path
            workspace_dir: Target directory for .vscode (optional)
            tool_id: Tool identifier ('ldp' or 'csmgvt'), determines compile script name and content
        """

        # Provide a target directory for .vscode (use workspace_dir if provided, else project_path)
        target_dir = workspace_dir if workspace_dir else project_path
        topology = collect_debug_topology(project_path=project_path, build_dir=build_dir, project_file=project_file)
        assets = write_distributed_debug_assets(
            target_dir=target_dir,
            build_dir=build_dir,
            topology=topology,
            cmake_dir=cmake_dir,
            project_file=project_file,
            tool_id=tool_id,
        )
        logger.info(f"[{tool_id.upper()}] Created VSCode launch.json at: {assets['launch_json']}")
        if "compile_script" in assets:
            logger.info(f"[{tool_id.upper()}] Created compile script at: {assets['compile_script']}")

    @staticmethod
    def _host_machine() -> str:
        """Return normalised host machine architecture: 'x86_64' or 'aarch64'."""
        m = _platform.machine().lower()
        if m in ("aarch64", "arm64"):
            return "aarch64"
        return "x86_64"

    @staticmethod
    def _needs_cross_compile(host_arch: str, target_arch: str) -> bool:
        """Return True when target_arch cannot be compiled natively on host_arch."""
        _arm = {"arm64", "aarch64"}
        host_is_arm = host_arch in _arm
        target_is_arm = target_arch.lower() in _arm
        # native/x86_64/amd64 on x86 host = no cross; arm64 on arm64 host = no cross
        if target_arch.lower() in ("native", ""):
            return False
        return host_is_arm != target_is_arm

    @staticmethod
    def _toolchain_path(host_arch: str, target_arch: str, ecoa_tools_root: str) -> Optional[str]:
        """Return the cmake toolchain file path for cross-compilation, or None for native."""
        _arm = {"arm64", "aarch64"}
        host_is_arm = host_arch in _arm
        target_is_arm = target_arch.lower() in _arm
        if target_arch.lower() in ("native", ""):
            return None
        if host_is_arm == target_is_arm:
            return None  # native
        if not host_is_arm and target_is_arm:
            # x86_64 host → arm64 target
            return os.path.join(ecoa_tools_root, "cmake", "toolchain-aarch64.cmake")
        # arm64 host → x86_64 target
        return os.path.join(ecoa_tools_root, "cmake", "toolchain-x86_64.cmake")

    @staticmethod
    def _parse_dds_params(path: str) -> Dict[str, object]:
        """Parse a dds-binding.xml file and return a dict with DDS configuration.

        Expected format (schemas/ecoa-ddsbinding.xsd):
          <DDSBinding xmlns="http://www.ecoa.technology/ddsbinding">
            <domain id="0"/>
            <topic name="LdpLocalPeerData"/>   <!-- optional -->
          </DDSBinding>

        Returns a dict with keys:
          domain_id  (int, default 0)
          topic_name (str, default "LdpLocalPeerData")
        """
        defaults = {"domain_id": 0, "topic_name": "LdpLocalPeerData"}
        if not path or not os.path.isfile(path):
            logger.warning(f"[DDS] dds-params file not found: {path!r} — using defaults")
            return defaults
        try:
            ns = {"dds": "http://www.ecoa.technology/ddsbinding"}
            tree = ET.parse(path)
            root = tree.getroot()

            domain_el = root.find("dds:domain", ns)
            if domain_el is None:
                domain_el = root.find("domain")
            domain_id = int(domain_el.get("id", 0)) if domain_el is not None else 0

            topic_el = root.find("dds:topic", ns)
            if topic_el is None:
                topic_el = root.find("topic")
            topic_name = topic_el.get("name", "LdpLocalPeerData") if topic_el is not None else "LdpLocalPeerData"

            return {"domain_id": domain_id, "topic_name": topic_name}
        except Exception as exc:
            logger.warning(f"[DDS] Failed to parse dds-params file {path!r}: {exc} — using defaults")
            return defaults

    def _cmake_options_for_network_protocol(
        self,
        network_protocol: Optional[str],
        dds_domain_id: int = 0,
    ) -> List[str]:
        """Translate frontend protocol selection into LDP CMake protocol flags.

        For DDS, also injects -DCYCLONEDDS_DOMAIN_ID=<dds_domain_id> so that
        the generated LDP runtime joins the correct DDS domain.
        """
        if network_protocol is None:
            return []

        protocol = network_protocol.strip().lower()
        if protocol == "dds":
            return [
                "-DCMAKE_USE_DDS_PROTO=ON",
                "-DCMAKE_USE_UDP_PROTO=OFF",
                f"-DCYCLONEDDS_DOMAIN_ID={dds_domain_id}",
            ]
        if protocol == "udp":
            return ["-DCMAKE_USE_DDS_PROTO=OFF", "-DCMAKE_USE_UDP_PROTO=ON"]
        if protocol == "tcp":
            return ["-DCMAKE_USE_DDS_PROTO=OFF", "-DCMAKE_USE_UDP_PROTO=OFF"]

        raise ValueError("network_protocol must be one of: tcp, udp, dds")

    def _should_compile(self, compile_flag: Optional[bool], compile_config: Dict) -> bool:
        """
        Determine if compilation should be performed based on flag and config.

        Args:
            compile_flag: User-provided compile flag (True/False/None)
            compile_config: Compilation configuration from tool config

        Returns:
            True if compilation should be performed, False otherwise
        """
        if compile_flag is True:
            return True
        elif compile_flag is False:
            return False
        else:  # compile_flag is None, use configuration
            return compile_config.get('enabled', False)

    def _get_message_for_tool(
        self,
        return_code: int,
        tool_id: str,
        compile_result: Dict[str, any]
    ) -> str:
        """
        Get user-friendly message based on return code and compilation result.

        For LDP/csmgvt:
            - Tool success + compile success = "executed and compiled successfully"
            - Tool success + compile failure = "executed successfully but compilation failed"
            - Tool failure = "execution failed"

        Args:
            return_code: Tool execution return code
            tool_id: Tool identifier
            compile_result: Compilation result dictionary (if any)

        Returns:
            User-friendly message
        """
        if return_code == 0:
            # Tool executed successfully
            if compile_result:
                compile_success = compile_result.get('compile_success', False)
                if compile_success:
                    return f'Tool {tool_id} executed and compiled successfully'
                else:
                    return f'Tool {tool_id} executed successfully but compilation failed'
            else:
                return f'Tool {tool_id} executed successfully'
        elif return_code < 0:
            return f'Tool {tool_id} execution failed'
        else:
            return f'Tool {tool_id} execution failed with code {return_code}'

    def _get_message(self, return_code: int, tool_id: str) -> str:
        """Get user-friendly message based on return code."""
        if return_code == 0:
            return f'Tool {tool_id} executed successfully'
        elif return_code < 0:
            return f'Tool {tool_id} execution failed'
        else:
            return f'Tool {tool_id} execution failed with code {return_code}'

    def _find_cmakelists_dir(
        self,
        project_path: str,
        project_file: str = None,
        tool_id: str = None,
    ) -> str:
        """
        Find directory containing CMakeLists.txt in project.

        Args:
            project_path: Project root directory

        Returns:
            Path to directory containing CMakeLists.txt

        Raises:
            FileNotFoundError: If CMakeLists.txt not found
        """
        is_harness_project = bool(project_file and "harness" in project_file.lower())
        if is_harness_project and tool_id in ("ldp", "csmgvt"):
            platform_candidate = os.path.join(project_path, "6-output", "platform")
            if os.path.exists(os.path.join(platform_candidate, "CMakeLists.txt")):
                return platform_candidate
            platform_candidate = os.path.join(project_path, "6-Output", "platform")
            if os.path.exists(os.path.join(platform_candidate, "CMakeLists.txt")):
                return platform_candidate

        # Common output directory names
        common_output_dirs = ["6-output", "6-Output", "Output", "output", "build-output"]

        # First check common output directories
        for dir_name in common_output_dirs:
            candidate = os.path.join(project_path, dir_name)
            if os.path.exists(candidate) and os.path.isdir(candidate):
                # Check if CMakeLists.txt is in this directory
                cmake_path = os.path.join(candidate, "CMakeLists.txt")
                if os.path.exists(cmake_path):
                    return candidate
                # Check parent directory (as per cmake_generator.py pattern)
                parent_dir = os.path.dirname(candidate)
                cmake_path = os.path.join(parent_dir, "CMakeLists.txt")
                if os.path.exists(cmake_path):
                    return parent_dir

        # Recursive search in project directory
        for root, dirs, files in os.walk(project_path):
            if "CMakeLists.txt" in files:
                return root

        raise FileNotFoundError(f"CMakeLists.txt not found in project: {project_path}")

    def _prepare_harness_platform_wrapper(self, platform_dir: str) -> str:
        """Create a wrapper CMake project so harness platform targets can be built standalone."""
        platform_path = Path(platform_dir)
        output_root = platform_path.parent
        wrapper_dir = platform_path / ".distributed-debug-wrapper"
        wrapper_dir.mkdir(parents=True, exist_ok=True)
        types_shim_dir = wrapper_dir / "types-shim"
        if types_shim_dir.exists():
            shutil.rmtree(types_shim_dir)
        types_shim_dir.mkdir(parents=True, exist_ok=True)

        for header_source_dir in [
            output_root / "0-Types" / "inc",
            output_root / "0-Types" / "inc-gen",
            output_root.parent / "0-Types" / "inc",
            output_root.parent / "0-Types" / "inc-gen",
        ]:
            if not header_source_dir.exists():
                continue
            for header_path in header_source_dir.glob("*.h"):
                if header_path.name == "ECOA.h":
                    continue
                shutil.copy2(header_path, types_shim_dir / header_path.name)

        component_dirs = []
        for child in sorted(output_root.iterdir()):
            if not child.is_dir():
                continue
            if child.name in {"build", "platform", "0-Types", "src"}:
                continue
            if not (child / "CMakeLists.txt").exists():
                continue
            component_dirs.append(child.name)

        platform_cmake = (platform_path / "CMakeLists.txt").read_text(encoding="utf-8")
        executable_targets = []
        for line in platform_cmake.splitlines():
            stripped = line.strip()
            if not stripped.startswith("add_executable("):
                continue
            remainder = stripped[len("add_executable("):]
            target_name = remainder.split()[0].rstrip(")")
            if target_name:
                executable_targets.append(target_name)

        lines = [
            "cmake_minimum_required(VERSION 3.4)",
            "project(harness_platform_wrapper)",
            "",
            "find_path(APR_INCLUDE_DIR apr_poll.h",
            '  PATHS "${APR_DIR}/include/apr-1.0" "${APR_DIR}/include/apr-1" "${APR_DIR}/include" "/usr/include/apr-1.0" "/usr/include/apr-1" "/usr/include"',
            ")",
            "if(APR_INCLUDE_DIR)",
            '  include_directories("${APR_INCLUDE_DIR}")',
            '  set(APR_INCLUDE_DIR "${APR_INCLUDE_DIR}" CACHE PATH "" FORCE)',
            '  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -I${APR_INCLUDE_DIR}")',
            '  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -I${APR_INCLUDE_DIR}")',
            "endif()",
            "find_package(Threads REQUIRED)",
            "if(NOT TARGET log4cplus::log4cplus)",
            "  find_library(LOG4CPLUS_LIBRARY NAMES log4cplus REQUIRED)",
            "  add_library(log4cplus::log4cplus SHARED IMPORTED)",
            '  set_target_properties(log4cplus::log4cplus PROPERTIES IMPORTED_LOCATION "${LOG4CPLUS_LIBRARY}")',
            "endif()",
            "",
            'add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../lib" platform_lib)',
        ]

        for component_dir in component_dirs:
            lines.append(
                f'add_subdirectory("${{CMAKE_CURRENT_LIST_DIR}}/../../{component_dir}" {component_dir})'
            )
            lines.extend(
                [
                    f"if(TARGET lib_{component_dir})",
                    f'  target_include_directories(lib_{component_dir} PRIVATE "${{CMAKE_CURRENT_LIST_DIR}}/types-shim")',
                    "endif()",
                ]
            )

        lines.extend(
            [
                'add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/.." generated_platform)',
                "",
            ]
        )

        for target_name in executable_targets:
            lines.extend(
                [
                    f"if(TARGET {target_name})",
                    f'  target_include_directories({target_name} PRIVATE "${{CMAKE_CURRENT_LIST_DIR}}/../svc_deserial" "${{CMAKE_CURRENT_LIST_DIR}}/types-shim")',
                    f'  set_target_properties({target_name} PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${{CMAKE_BINARY_DIR}}/bin")',
                    "endif()",
                ]
            )

        lines.extend(
            [
                "if(TARGET ecoa)",
                '  set_target_properties(ecoa PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")',
                "endif()",
                "",
            ]
        )

        (wrapper_dir / "CMakeLists.txt").write_text("\n".join(lines), encoding="utf-8")
        return str(wrapper_dir)

    def _get_pkg_config_path(self, package: str, target_arch: str = "native") -> str:
        """
        Get package installation path using pkg-config with fallback methods.

        Tries multiple methods in order:
        1. Parse include path from --cflags (most reliable, works on Ubuntu)
        2. Fall back to --variable=prefix
        3. Return common system paths if pkg-config fails

        Args:
            package: Package name (e.g., 'log4cplus', 'apr-1', 'cunit')
            target_arch: Target architecture ('native', 'arm64', etc.)

        Returns:
            Installation path (returns '/usr' if package is installed but path not found)

        Raises:
            FileNotFoundError: If pkg-config is not found or package is not installed
        """
        host = self._host_machine()
        _cross_pkg_paths = {
            ("x86_64", "arm64"):    "/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig",
            ("x86_64", "aarch64"):  "/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig",
            ("aarch64", "x86_64"):  "/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig",
            ("aarch64", "amd64"):   "/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig",
        }
        _cross_key = (host, target_arch.lower())
        if _cross_key in _cross_pkg_paths:
            _pkg_path = _cross_pkg_paths[_cross_key]
            env = dict(os.environ)
            env["PKG_CONFIG_PATH"] = _pkg_path
            env["PKG_CONFIG_LIBDIR"] = _pkg_path
            env.pop("PKG_CONFIG_SYSROOT_DIR", None)
            try:
                result = subprocess.run(
                    ["pkg-config", "--variable=prefix", package],
                    capture_output=True, text=True, check=True, env=env
                )
                prefix = result.stdout.strip()
                return prefix if prefix else "/usr"
            except subprocess.CalledProcessError:
                return "/usr"

        # Check if pkg-config exists
        if not shutil.which("pkg-config"):
            raise FileNotFoundError("pkg-config not found. Please install pkg-config.")

        # Method 1: Parse from --cflags (most reliable on Ubuntu)
        try:
            result = subprocess.run(
                ["pkg-config", "--cflags", package],
                capture_output=True,
                text=True,
                check=True
            )
            cflags = result.stdout.strip()
            # Parse -I flags to get include paths
            for part in cflags.split():
                if part.startswith("-I"):
                    path = part[2:]  # Remove -I prefix
                    # Remove trailing /include or /include/apr-1 etc.
                    if "/include" in path:
                        path = path.split("/include")[0]
                        if path:
                            return path
        except subprocess.CalledProcessError:
            pass

        # Method 2: Try --variable=prefix (original method)
        try:
            result = subprocess.run(
                ["pkg-config", "--variable=prefix", package],
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            raise FileNotFoundError(f"pkg-config failed for {package}: {e.stderr}")
        except FileNotFoundError:
            raise FileNotFoundError("pkg-config not found. Please install pkg-config.")

    def compile_project(
        self,
        project_path: str,
        project_file: str = None,
        log_library: str = "log4cplus",
        network_protocol: Optional[str] = None,
        cmake_options: List[str] = None,
        timeout: int = 600,
        tool_id: str = "ldp",
        target_arch: str = "native",
        dds_params_file: Optional[str] = None,
        cmake_dir_override: Optional[str] = None,
    ) -> Dict[str, any]:
        """
        Execute CMake compilation in project directory.

        Args:
            project_path: Project root directory
            log_library: Logging library to use (log4cplus, zlog, lttng)
            network_protocol: Local transport protocol to use (tcp, udp, dds)
            cmake_options: Additional CMake options
            timeout: Compilation timeout in seconds
            tool_id: Tool identifier (ldp or csmgvt)

        Returns:
            Dictionary with compilation results
        """
        try:
            # Find CMakeLists.txt directory
            if cmake_dir_override and os.path.exists(cmake_dir_override):
                cmake_dir = cmake_dir_override
            else:
                cmake_dir = self._find_cmakelists_dir(
                    project_path,
                    project_file=project_file,
                    tool_id=tool_id,
                )
            logger.info(f"[{tool_id.upper()}][COMPILE] Compiling in directory: {cmake_dir}")

            # Build compile commands
            #
            # Build directory naming:
            #   - Multi-platform mode (cmake_dir_override points at 6-output/{Platform}):
            #     each platform declares exactly one arch, so the plain "build/" dir is
            #     sufficient and matches what ldp-compile.sh and the distributed-debug
            #     gdbserver_command look for.  Adding a "-arm64" suffix here would put the
            #     binary in build-arm64/ while the debug launcher looks in build/ — the
            #     binary would never be found.
            #   - Single-platform multi-arch mode (no override, multiple arches share one
            #     cmake dir): keep the "-{arch}" suffix so native and cross-compiled
            #     object files don't overwrite each other.
            _is_multi_platform = bool(cmake_dir_override)
            if _is_multi_platform:
                _arch_suffix = ""
            else:
                _arch_suffix = f"-{target_arch}" if target_arch not in ("native", "amd64", "x86_64", "") else ""
            build_dir = os.path.join(cmake_dir, f"build{_arch_suffix}")
            os.makedirs(build_dir, exist_ok=True)
            cmake_source_dir = cmake_dir
            if tool_id == "ldp" and project_file and "harness" in project_file.lower():
                cmake_source_dir = self._prepare_harness_platform_wrapper(cmake_dir)
                cmake_cache_path = os.path.join(build_dir, "CMakeCache.txt")
                if os.path.exists(cmake_cache_path):
                    shutil.rmtree(build_dir)
                    os.makedirs(build_dir, exist_ok=True)

            # Get dependency paths using pkg-config (NixOS style)
            try:
                log4cplus_dir = self._get_pkg_config_path("log4cplus", target_arch)
                apr_dir = self._get_pkg_config_path("apr-1", target_arch)
                cunit_dir = self._get_pkg_config_path("cunit", target_arch)
            except FileNotFoundError as e:
                logger.warning(f"Failed to get pkg-config paths: {e}")
                raise

            # Find cmake_config.cmake path (check project dir first, then ecoa-tools root)
            cmake_config_path = os.path.join(cmake_dir, "cmake_config.cmake")
            if not os.path.exists(cmake_config_path):
                # Try ecoa-tools root directory (assuming project is under projects_base_dir)
                ecoa_tools_root = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
                cmake_config_path = os.path.join(ecoa_tools_root, "cmake_config.cmake")
                if not os.path.exists(cmake_config_path):
                    cmake_config_path = None

            # Convert to absolute path to avoid CMake path resolution issues
            if cmake_config_path:
                cmake_config_path = os.path.abspath(cmake_config_path)

            # Get compile configuration from config
            tool_config = self.config.get_tool(tool_id)
            compile_config = tool_config.get("compile", {}) if tool_config else {}

            # Get default options from config
            default_cmake_options = compile_config.get("cmake_options", [])
            default_make_options = compile_config.get("make_options", ["-j"])

            if tool_id == "csmgvt":
                # Simple compilation for csmgvt: mkdir build; cd build; cmake ..; make

                # Ensure build directory exists
                os.makedirs(build_dir, exist_ok=True)

                # Simple CMake command: cmake .. (run from build directory)
                cmake_cmd = ["cmake", ".."]

                # Simple make command
                make_cmd = ["make"]
                make_cmd.extend(default_make_options)

                # No cmake_config.cmake for simple compilation
                cmake_config_path = None
            else:
                # Original compilation logic for ldp and other tools
                # Get dependency paths using pkg-config (NixOS style)
                try:
                    log4cplus_dir = self._get_pkg_config_path("log4cplus", target_arch)
                    apr_dir = self._get_pkg_config_path("apr-1", target_arch)
                    cunit_dir = self._get_pkg_config_path("cunit", target_arch)
                except FileNotFoundError as e:
                    logger.warning(f"Failed to get pkg-config paths: {e}")
                    raise

                # Find cmake_config.cmake path (check project dir first, then ecoa-tools root)
                cmake_config_path = os.path.join(cmake_dir, "cmake_config.cmake")
                if not os.path.exists(cmake_config_path):
                    # Try ecoa-tools root directory (assuming project is under projects_base_dir)
                    ecoa_tools_root = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
                    cmake_config_path = os.path.join(ecoa_tools_root, "cmake_config.cmake")
                    if not os.path.exists(cmake_config_path):
                        cmake_config_path = None

                # Select toolchain based on host architecture vs target architecture.
                # Native compilation uses no toolchain file; cross-compilation uses
                # the appropriate cross-compiler toolchain.
                _host_arch = self._host_machine()
                _ecoa_tools_root = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
                _toolchain_file = self._toolchain_path(_host_arch, target_arch, _ecoa_tools_root)
                if _toolchain_file and not os.path.exists(_toolchain_file):
                    logger.warning(
                        f"[{tool_id.upper()}][COMPILE] Toolchain file not found: {_toolchain_file}"
                    )
                    _toolchain_file = None

                # Build CMake command with paths.
                # LDP_LINK_TYPE=STATIC: ecoa lib and component libs are statically
                # linked into each executable, so bin/ is self-contained (only system
                # .so files need to be bundled alongside in lib/).
                dds_params = self._parse_dds_params(dds_params_file) if dds_params_file else {}
                protocol_cmake_options = self._cmake_options_for_network_protocol(
                    network_protocol,
                    dds_domain_id=dds_params.get("domain_id", 0),
                )
                cmake_cmd = [
                    "cmake",
                    f"-DAPR_DIR={apr_dir}",
                    f"-DLOG4CPLUS_DIR={log4cplus_dir}",
                    f"-DCUNIT_DIR={cunit_dir}",
                    f"-DLDP_LOG_USE={log_library}",
                    "-DLDP_LINK_TYPE=STATIC",
                    "-DCMAKE_BUILD_RPATH=$ORIGIN/../lib",
                    "-DCMAKE_INSTALL_RPATH=$ORIGIN/../lib",
                    "-DCMAKE_BUILD_RPATH_USE_ORIGIN=ON",
                    "-B", build_dir,
                    "-S", cmake_source_dir
                ]

                # Inject toolchain file for cross-compilation
                if _toolchain_file:
                    cmake_cmd.append(f"-DCMAKE_TOOLCHAIN_FILE={_toolchain_file}")

                # Add cmake_config.cmake if found
                if cmake_config_path:
                    cmake_cmd.extend(["-C", cmake_config_path])

                # Add any additional cmake options
                if cmake_options or default_cmake_options:
                    for opt in (cmake_options or default_cmake_options):
                        cmake_cmd.append(opt.replace("${log_library}", log_library))

                # Add frontend protocol selection last so it wins over config defaults.
                cmake_cmd.extend(protocol_cmake_options)

                # Build make command
                make_cmd = ["make"]
                make_cmd.extend(default_make_options)

            # Check if compilation already done
            platform_executable = os.path.join(build_dir, "bin", "platform")
            if os.path.exists(platform_executable) and os.access(platform_executable, os.X_OK):
                # Guard against a stale binary compiled for a different arch:
                # in multi-platform mode the build dir is shared across runs, so a
                # previously-compiled binary (e.g. native x86_64 from ldp-compile.sh)
                # would otherwise short-circuit an arm64 cross-compile.  Verify the
                # existing ELF machine matches the requested target arch; if not,
                # fall through and recompile.
                _elf_matches = _elf_arch_matches(platform_executable, target_arch)
                if _elf_matches:
                    logger.info(f"[{tool_id.upper()}][COMPILE] Compilation skipped: {platform_executable} already exists")
                    return {
                        "compile_success": True,
                        "compile_stdout": f"{platform_executable} already exists. Compilation bypassed.",
                        "compile_stderr": "",
                        "compile_return_code": 0,
                        "executable_files": ["platform"],
                        "cmake_dir": cmake_dir,
                        "build_dir": build_dir,
                        "cmake_command": "",
                        "make_command": ""
                    }
                else:
                    logger.info(
                        f"[{tool_id.upper()}][COMPILE] Existing {platform_executable} arch does not match "
                        f"target {target_arch}; recompiling"
                    )

            # Execute CMake
            logger.info(f"[{tool_id.upper()}][COMPILE] Running CMake: {' '.join(cmake_cmd)}")
            cmake_start = _time.monotonic()

            if tool_id == "csmgvt":
                # For csmgvt, run cmake from build directory with ".." argument
                cmake_result = subprocess.run(
                    cmake_cmd,
                    cwd=build_dir,  # Run from build directory
                    capture_output=True,
                    text=True,
                    timeout=timeout
                )
            else:
                # For ldp and other tools, run cmake from cmake_dir with -B/-S options
                cmake_result = subprocess.run(
                    cmake_cmd,
                    cwd=cmake_dir,  # Run from cmake_dir when using -B/-S
                    capture_output=True,
                    text=True,
                    timeout=timeout
                )

            cmake_elapsed = _time.monotonic() - cmake_start
            cmake_success = cmake_result.returncode == 0
            logger.info(f"[{tool_id.upper()}][COMPILE] CMake %s in %.1fs", "succeeded" if cmake_success else "failed", cmake_elapsed)

            # Build make command
            make_cmd = [
                "make",
                "--no-print-directory",
                "-C", build_dir,
                "all",
            ]

            # Execute make only if CMake succeeded
            make_result = None
            if cmake_success:
                logger.info(f"[{tool_id.upper()}][COMPILE] Running make {' '.join(make_cmd)}")
                make_start = _time.monotonic()
                make_result = subprocess.run(
                    make_cmd,
                    capture_output=True,
                    text=True,
                    timeout=timeout,
                )
                make_elapsed = _time.monotonic() - make_start
                logger.info(f"[{tool_id.upper()}][COMPILE] Make %s in %.1fs", "succeeded" if make_result.returncode == 0 else "failed", make_elapsed)
            else:
                # Create dummy make result for consistency
                make_result = subprocess.CompletedProcess(
                    args=make_cmd,
                    returncode=-1,
                    stdout="",
                    stderr="CMake failed, make not executed"
                )

            # Find executable files in build/bin or build directory
            executable_files = []
            bin_dir = os.path.join(build_dir, "bin")
            if os.path.exists(bin_dir):
                for file in os.listdir(bin_dir):
                    file_path = os.path.join(bin_dir, file)
                    if os.path.isfile(file_path) and os.access(file_path, os.X_OK):
                        executable_files.append(file)
            else:
                # Check build directory directly
                for file in os.listdir(build_dir):
                    file_path = os.path.join(build_dir, file)
                    if os.path.isfile(file_path) and os.access(file_path, os.X_OK):
                        executable_files.append(file)

            compile_success = cmake_success and (make_result.returncode == 0 if make_result else False)

            # Combine stdout and stderr from cmake/make
            combined_stdout = ""
            combined_stderr = ""
            if cmake_result:
                combined_stdout += f"=== CMake Output ===\n{cmake_result.stdout}\n"
                combined_stderr += f"=== CMake Errors ===\n{cmake_result.stderr}\n"
            if make_result:
                combined_stdout += f"=== Make Output ===\n{make_result.stdout}\n"
                combined_stderr += f"=== Make Errors ===\n{make_result.stderr}\n"

            # --- Bundle system shared libraries into build/lib/ ---
            # Internal ECOA/component libs are statically linked (LDP_LINK_TYPE=STATIC);
            # system .so files (apr, log4cplus, libc variants …) are collected via ldd
            # so that the bin/ + lib/ pair is self-contained for deployment.
            _host_for_bundle = self._host_machine()
            _is_native_build = not self._needs_cross_compile(_host_for_bundle, target_arch)
            if compile_success and os.path.exists(bin_dir) and _is_native_build:
                lib_dir = os.path.join(build_dir, "lib")
                os.makedirs(lib_dir, exist_ok=True)
                bundled_libs: list[str] = []
                ldd_stdout_lines: list[str] = []
                for exe_name in executable_files:
                    exe_path = os.path.join(bin_dir, exe_name)
                    if not os.path.isfile(exe_path):
                        continue
                    try:
                        ldd_result = subprocess.run(
                            ["ldd", exe_path],
                            capture_output=True,
                            text=True,
                            timeout=30,
                        )
                        for line in ldd_result.stdout.splitlines():
                            # Format: "   libfoo.so.1 => /path/to/libfoo.so.1 (0x...)"
                            # Skip linux-vdso and ld-linux (pseudo/loader libs)
                            if "=>" not in line:
                                continue
                            if "linux-vdso" in line or "ld-linux" in line:
                                continue
                            parts = line.split("=>")
                            if len(parts) < 2:
                                continue
                            lib_path = parts[1].strip().split()[0] if parts[1].strip() else ""
                            if not lib_path or not os.path.isfile(lib_path):
                                continue
                            dest = os.path.join(lib_dir, os.path.basename(lib_path))
                            if not os.path.exists(dest):
                                try:
                                    shutil.copy2(lib_path, dest)
                                    bundled_libs.append(os.path.basename(lib_path))
                                    ldd_stdout_lines.append(f"  Bundled: {os.path.basename(lib_path)}")
                                except OSError:
                                    pass
                    except Exception:
                        pass
                if bundled_libs:
                    bundle_msg = (
                        f"\n=== Bundled {len(bundled_libs)} system libs into {lib_dir} ===\n"
                        + "\n".join(ldd_stdout_lines)
                        + "\n"
                    )
                    combined_stdout += bundle_msg
                    logger.info(f"[{tool_id.upper()}][COMPILE] Bundled {len(bundled_libs)} system libs into {lib_dir}")

            return {
                "compile_success": compile_success,
                "compile_stdout": combined_stdout.strip(),
                "compile_stderr": combined_stderr.strip(),
                "compile_return_code": make_result.returncode if make_result else cmake_result.returncode,
                "executable_files": executable_files,
                "cmake_dir": cmake_dir,
                "build_dir": build_dir,
                "cmake_command": " ".join(cmake_cmd),
                "make_command": " ".join(make_cmd) if make_cmd else "",
                "network_protocol": network_protocol or ""
            }

        except subprocess.TimeoutExpired:
            logger.error(f"[{tool_id.upper()}][COMPILE] Compilation timeout in project: {project_path}")
            return {
                "compile_success": False,
                "compile_stdout": "",
                "compile_stderr": "Compilation timeout",
                "compile_return_code": -1,
                "executable_files": [],
                "cmake_dir": "",
                "build_dir": "",
                "cmake_command": "",
                "make_command": ""
            }
        except FileNotFoundError as e:
            logger.error(f"[COMPILE] CMakeLists.txt not found: {e}")
            return {
                "compile_success": False,
                "compile_stdout": "",
                "compile_stderr": str(e),
                "compile_return_code": -1,
                "executable_files": [],
                "cmake_dir": "",
                "build_dir": "",
                "cmake_command": "",
                "make_command": ""
            }
        except Exception as e:
            logger.exception(f"[COMPILE] Unexpected compilation error: {e}")
            return {
                "compile_success": False,
                "compile_stdout": "",
                "compile_stderr": f"Unexpected error: {str(e)}",
                "compile_return_code": -1,
                "executable_files": [],
                "cmake_dir": "",
                "build_dir": "",
                "cmake_command": "",
                "make_command": ""
            }

    def execute_in_project(
        self,
        tool_id: str,
        project_name: str,
        project_file: str,
        verbose: int = None,
        checker: str = None,
        config_file: str = None,
        compile: Optional[bool] = None,
        log_library: str = None,
        cmake_options: List[str] = None,
        network_protocol: str = None,
        force: Optional[bool] = None,
        additional_args: List[str] = None,
        workspace_dir: str = None,
        target_arch: str = "native",
        dds_params_file: Optional[str] = None,
    ) -> Dict[str, any]:
        """
        Execute a tool in a project directory.

        Args:
            tool_id: Tool identifier (e.g., 'exvt')
            project_name: Project directory name (under projects_base_dir)
            project_file: Project file name (e.g., 'marx_brothers.project.xml')
            verbose: Verbosity level (overrides default)
            checker: Checker tool for validation (e.g., 'ecoa-exvt')
            config_file: Config file name (for asctg tool)
            compile: Whether to compile the project after tool execution (for ldp tool).
                None: use configuration default (enabled: true),
                True: always compile,
                False: never compile
            log_library: Logging library to use for compilation (log4cplus, zlog, lttng)
            cmake_options: Additional CMake options for compilation
            network_protocol: Local transport protocol for make build (tcp, udp, dds)
            force: Whether to force overwrite generated files.
                None: use tool configuration default,
                True: always add force flag,
                False: never add force flag

        Returns:
            Dictionary with execution result:
            {
                'success': bool,
                'tool': str,
                'project_name': str,
                'project_path': str,
                'project_file': str,
                'generated_files': List[str],
                'stdout': str,
                'stderr': str,
                'return_code': int,
                'message': str,
                'compile_success': bool (optional),
                'compile_stdout': str (optional),
                'compile_stderr': str (optional),
                'compile_return_code': int (optional),
                'executable_files': List[str] (optional),
                'cmake_dir': str (optional),
                'build_dir': str (optional)
            }

        Raises:
            ValueError: If tool is not found
            ProjectNotFoundError: If project directory doesn't exist
            ProjectFileNotFoundError: If project file doesn't exist
        """
        # Get tool configuration
        tool_config = self.config.get_tool(tool_id)
        if not tool_config:
            raise ValueError(f"Tool not found: {tool_id}")

        command = tool_config.get('command')
        if not command:
            raise ValueError(f"Command not defined for tool: {tool_id}")

        # Build project path
        project_path = os.path.join(self.config.projects_base_dir, project_name)

        # Validate project directory exists
        if not os.path.exists(project_path):
            raise ProjectNotFoundError(
                f"Project directory not found: {project_path}"
            )

        if not os.path.isdir(project_path):
            raise ValueError(f"Not a directory: {project_path}")

        # Validate project file exists
        project_file_path = os.path.join(project_path, project_file)
        if not os.path.exists(project_file_path):
            raise ProjectFileNotFoundError(
                f"Project file not found: {project_file_path}"
            )

        # Get verbose level
        if verbose is None:
            verbose = self.config.verbose

        # Check if tool requires a checker parameter
        checker_param = None
        for param in tool_config.get('parameters', []):
            if param.get('flag') == '-k':
                checker_param = param
                break

        # Get checker value - use provided, or default from config
        if checker_param:
            if checker is None:
                checker = checker_param.get('default', 'ecoa-exvt')

        # Check if tool requires a config file parameter
        config_file_param = None
        for param in tool_config.get('parameters', []):
            if param.get('flag') == '-c':
                config_file_param = param
                break

        # Get config file value
        if config_file_param:
            if not config_file:
                raise ValueError(f"Tool {tool_id} requires config_file parameter")
            # Validate config file exists
            config_file_path = os.path.join(project_path, config_file)
            if not os.path.exists(config_file_path):
                raise ProjectFileNotFoundError(
                    f"Config file not found: {config_file_path}"
                )

        # Build command - use project file name only, run from project directory
        cmd = [command, '-p', project_file]

        # Add config file if required
        if config_file:
            cmd.extend(['-c', config_file])

        # Add checker if required
        if checker:
            cmd.extend(['-k', checker])

        # Add boolean flags using explicit request overrides when provided.
        for param in tool_config.get('parameters', []):
            if param.get('type') != 'boolean':
                continue

            param_name = param.get('name')
            default_enabled = param.get('default') is True
            enabled = default_enabled

            if param_name == 'force' and force is not None:
                enabled = force

            flag = param.get('flag')
            if enabled and flag and flag not in cmd:
                cmd.append(flag)

        # Add verbose flag based on tool's verbose_type
        verbose_type = tool_config.get('verbose_type', 'boolean')
        if verbose_type == 'boolean':
            # Boolean flag: just add -v without value
            cmd.append('-v')
        else:
            # Integer type: add -v with value
            cmd.extend(['-v', str(verbose)])

        if additional_args:
            cmd.extend(additional_args)

        # Multi-platform LDP: detect platforms and run LDP once per platform
        _multi_platform_results = {}
        _is_multi_platform_ldp = (
            tool_id == 'ldp'
            and _has_platform_links(project_path)
        )
        _platforms = _detect_platforms(project_path) if _is_multi_platform_ldp else []
        if _is_multi_platform_ldp and len(_platforms) > 1:
            logger.info(f"[LDP] Multi-platform project detected: {_platforms}")

        logger.info(f"[{tool_id.upper()}] Executing tool in project '{project_name}'")

        # Execute tool in project directory
        try:
            if _is_multi_platform_ldp and len(_platforms) > 1:
                # Run LDP once per platform
                _combined_stdout = ""
                _combined_stderr = ""
                _overall_rc = 0
                for _pf in _platforms:
                    _pf_out = os.path.join(project_path, "6-output", _pf)
                    _pf_cmd = cmd + ['--platform', _pf, '-o', _pf_out]
                    logger.info(f"[LDP] Running for platform {_pf}: {' '.join(_pf_cmd)}")
                    _pf_result = subprocess.run(_pf_cmd, cwd=project_path, capture_output=True, text=True, timeout=300)
                    _combined_stdout += f"\n=== Platform {_pf} ===\n{_pf_result.stdout}"
                    _combined_stderr += _pf_result.stderr
                    if _pf_result.returncode != 0:
                        _overall_rc = _pf_result.returncode
                        logger.error(f"[LDP] Platform {_pf} generation failed (rc={_pf_result.returncode})")
                        break
                import types
                result = types.SimpleNamespace(stdout=_combined_stdout, stderr=_combined_stderr, returncode=_overall_rc)
            else:
                result = subprocess.run(
                    cmd,
                    cwd=project_path,
                    capture_output=True,
                    text=True,
                    timeout=300  # 5 minute timeout
                )

            success = result.returncode == 0

            # Find generated files in project directory
            generated_files = []
            if success:
                output_types = tool_config.get('output_types', [])
                generated_files = self._find_output_files(project_path, output_types)

                # Generate VSCode launch.json and compile script for LDP and CSMGVT on success
                if tool_id in ['ldp', 'csmgvt']:
                    try:
                        cmake_dir = self._find_cmakelists_dir(
                            project_path,
                            project_file=project_file,
                            tool_id=tool_id,
                        )
                        build_dir = os.path.join(cmake_dir, "build")
                        self._create_vscode_launch_config(
                            project_path=project_path,
                            project_name=project_name,
                            build_dir=build_dir,
                            cmake_dir=cmake_dir,
                            workspace_dir=workspace_dir,
                            project_file=project_file,
                            tool_id=tool_id,
                            target_arch=target_arch,
                        )
                    except Exception as e:
                        logger.warning(f"[{tool_id.upper()}] Failed to create launch.json: {e}")

            # Compile project if tool is ldp or csmgvt and compilation is enabled
            compile_result = {}
            if success and tool_id in ['ldp', 'csmgvt']:
                # Get compile configuration
                tool_config_for_compile = self.config.get_tool(tool_id)
                compile_config = tool_config_for_compile.get('compile', {}) if tool_config_for_compile else {}

                # Determine if compilation should be performed
                should_compile = False
                if compile is True:
                    should_compile = True
                elif compile is False:
                    should_compile = False
                else:  # compile is None, use configuration
                    should_compile = compile_config.get('enabled', False)

                if should_compile:
                    # Determine log_library value
                    if log_library is None:
                        log_library = compile_config.get('default_log_library', 'log4cplus')

                    # Determine cmake_options
                    if cmake_options is None:
                        cmake_options = compile_config.get('cmake_options', [])

                    # Determine timeout
                    timeout = compile_config.get('timeout', 600)

                    if _is_multi_platform_ldp and len(_platforms) > 1:
                        # Resolve per-platform target architecture from LogicalProcessor.type
                        _pf_processor_types = _parse_platform_processor_types(project_path)
                        _all_compile_results = []
                        for _pf in _platforms:
                            _pf_out = os.path.join(project_path, "6-output", _pf)
                            _pf_arch = _pf_processor_types.get(_pf, target_arch)
                            _pf_compile = self.compile_project(
                                project_path=project_path,
                                project_file=project_file,
                                log_library=log_library,
                                network_protocol=network_protocol,
                                cmake_options=cmake_options,
                                timeout=timeout,
                                tool_id=tool_id,
                                target_arch=_pf_arch,
                                dds_params_file=dds_params_file,
                                cmake_dir_override=_pf_out,
                            )
                            _all_compile_results.append(_pf_compile)
                            if not _pf_compile.get('compile_success'):
                                break
                        # Merge results: success only if all succeeded
                        _merged_success = all(r.get('compile_success') for r in _all_compile_results)
                        _merged_stdout = "\n".join(r.get('compile_stdout', '') for r in _all_compile_results)
                        _merged_stderr = "\n".join(r.get('compile_stderr', '') for r in _all_compile_results)
                        _merged_exes = list({exe for r in _all_compile_results for exe in r.get('executable_files', [])})
                        compile_result = {
                            'compile_success': _merged_success,
                            'compile_stdout': _merged_stdout,
                            'compile_stderr': _merged_stderr,
                            'compile_return_code': 0 if _merged_success else -1,
                            'executable_files': _merged_exes,
                            'cmake_dir': ', '.join(r.get('cmake_dir', '') for r in _all_compile_results),
                            'build_dir': ', '.join(r.get('build_dir', '') for r in _all_compile_results),
                        }
                    else:
                        compile_result = self.compile_project(
                            project_path=project_path,
                            project_file=project_file,
                            log_library=log_library,
                            network_protocol=network_protocol,
                            cmake_options=cmake_options,
                            timeout=timeout,
                            tool_id=tool_id,
                            target_arch=target_arch,
                            dds_params_file=dds_params_file,
                        )

            # Prepare result dictionary
            result_dict = {
                'success': success,
                'tool': tool_id,
                'project_name': project_name,
                'project_path': project_path,
                'project_file': project_file,
                'generated_files': generated_files,
                'stdout': result.stdout,
                'stderr': result.stderr,
                'return_code': result.returncode,
                'message': self._get_message(result.returncode, tool_id)
            }

            # Add compilation results if available
            if compile_result:
                result_dict.update({
                    'compile_success': compile_result.get('compile_success', False),
                    'compile_stdout': compile_result.get('compile_stdout', ''),
                    'compile_stderr': compile_result.get('compile_stderr', ''),
                    'compile_return_code': compile_result.get('compile_return_code', -1),
                    'executable_files': compile_result.get('executable_files', []),
                    'cmake_dir': compile_result.get('cmake_dir', ''),
                    'build_dir': compile_result.get('build_dir', '')
                })
                if 'network_protocol' in compile_result:
                    result_dict['network_protocol'] = compile_result['network_protocol']

            return result_dict

        except subprocess.TimeoutExpired:
            logger.error(f"Tool execution timeout: {command}")
            return {
                'success': False,
                'tool': tool_id,
                'project_name': project_name,
                'project_path': project_path,
                'project_file': project_file,
                'generated_files': [],
                'stdout': '',
                'stderr': 'Execution timeout (5 minutes)',
                'return_code': -1,
                'message': f'Tool execution timeout: {tool_id}'
            }
        except Exception as e:
            logger.exception(f"Error executing tool: {e}")
            return {
                'success': False,
                'tool': tool_id,
                'project_name': project_name,
                'project_path': project_path,
                'project_file': project_file,
                'generated_files': [],
                'stdout': '',
                'stderr': str(e),
                'return_code': -1,
                'message': f'Execution error: {str(e)}'
            }

    def save_uploaded_file(self, file_content: bytes, filename: str) -> str:
        """
        Save an uploaded file to the uploads directory.

        Args:
            file_content: File content as bytes
            filename: Original filename

        Returns:
            Path to saved file
        """
        uploads_dir = self.config.uploads_dir
        Path(uploads_dir).mkdir(parents=True, exist_ok=True)

        # Generate unique filename with timestamp
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        base_name = Path(filename).stem
        extension = Path(filename).suffix
        unique_filename = f"{base_name}_{timestamp}{extension}"

        file_path = os.path.join(uploads_dir, unique_filename)

        with open(file_path, 'wb') as f:
            f.write(file_content)

        logger.info(f"Saved uploaded file: {filename} -> {file_path}")
        return file_path
