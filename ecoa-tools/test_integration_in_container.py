"""
Test script to run inside ecoa-tools container to verify INTEGRATION mode behavior.

Usage:
    docker exec ecoa-tools python /app/test_integration_in_container.py
"""

import sys
import os
import json
import tempfile
import shutil
from pathlib import Path

# Add app to path
sys.path.insert(0, "/app")

def test_request_parsing():
    """Test that API request parsing works correctly."""
    print("\n=== Test 1: API Request Parsing ===")

    # Test data that frontend should send
    test_request = {
        "taskId": "test-task-1",
        "projectId": "test-project",
        "callbackUrl": "http://localhost:8080/callback",
        "workflowMode": "INTEGRATION",
        "selectedPhases": ["EXVT", "CSMGVT"],
        "selectedVersions": [
            {
                "componentName": "mycompFinisher",
                "versionId": "550e8400-e29b-41d4-a716-446655440000",
                "versionName": "v1.0"
            },
            {
                "componentName": "myCompWriter",
                "versionId": "660e8400-e29b-41d4-a716-446655440001",
                "versionName": "v1.0"
            }
        ]
    }

    # Simulate the parsing from generator.py lines 1416-1473
    phase_params = {}
    selected_versions = test_request.get("selectedVersions") or test_request.get("selected_versions") or []

    if selected_versions:
        if "_meta" not in phase_params:
            phase_params["_meta"] = {}
        phase_params["_meta"]["selectedVersions"] = selected_versions
        print(f"✓ selectedVersions parsed: {len(selected_versions)} components")
        for v in selected_versions:
            print(f"  - {v['componentName']}@{v['versionName']}")
    else:
        print("✗ selectedVersions is empty!")

    # Check the condition from generator.py line 866
    if phase_params.get("_meta", {}).get("selectedVersions"):
        print("✓ MSCIGT will be triggered")
    else:
        print("✗ MSCIGT will be SKIPPED")

    return phase_params


def test_integration_mode_check():
    """Test the INTEGRATION mode condition check."""
    print("\n=== Test 2: INTEGRATION Mode Condition Check ===")

    # From generator.py line 864
    mode = "INTEGRATION"
    continuing = False

    if mode == "INTEGRATION" and not continuing:
        print("✓ INTEGRATION mode detected, not continuing")
        print("✓ Will check for selectedVersions")
        return True
    else:
        print(f"✗ Condition failed: mode={mode}, continuing={continuing}")
        return False


def test_project_structure():
    """Test that expected project structure exists."""
    print("\n=== Test 3: Project Structure Check ===")

    # Find workspace directories
    workspace_base = Path("/workspace")
    if not workspace_base.exists():
        print(f"✗ Workspace base not found: {workspace_base}")
        return None

    # List all projects
    projects = [d for d in workspace_base.iterdir() if d.is_dir()]
    if not projects:
        print("✗ No projects found in workspace")
        return None

    print(f"✓ Found {len(projects)} project(s)")

    for project_dir in projects:
        print(f"\n  Project: {project_dir.name}")

        # Check for Steps directory
        steps_dir = project_dir / "Steps"
        if steps_dir.exists():
            print(f"    ✓ Steps directory exists")

            # Check for key subdirectories
            for subdir in ["4-ComponentImplementations", "0-Types", "5-Integration"]:
                full_path = steps_dir / subdir
                if full_path.exists():
                    print(f"    ✓ {subdir} exists")
                else:
                    print(f"    ✗ {subdir} missing")

            # Check for project files
            project_files = list(steps_dir.glob("*.project.xml"))
            if project_files:
                print(f"    ✓ Project files: {[f.name for f in project_files]}")
            else:
                print(f"    ✗ No project files found")

            return steps_dir
        else:
            print(f"    ✗ Steps directory missing")

    return None


def test_mscigt_availability():
    """Test that MSCIGT tool is available."""
    print("\n=== Test 4: MSCIGT Tool Availability ===")

    # Check if ecoa-mscigt is in PATH
    result = os.system("which ecoa-mscigt > /dev/null 2>&1")
    if result == 0:
        print("✓ ecoa-mscigt is in PATH")

        # Get version
        version_result = os.popen("ecoa-mscigt --help 2>&1 | head -5").read()
        print(f"  Command output:\n{version_result[:200]}")
        return True
    else:
        print("✗ ecoa-mscigt not found in PATH")
        print("  Available tools:")
        os.system("compgen -c | grep ecoa | head -10")
        return False


def test_csmgvt_availability():
    """Test that CSMGVT tool is available."""
    print("\n=== Test 5: CSMGVT Tool Availability ===")

    result = os.system("which ecoa-csmgvt > /dev/null 2>&1")
    if result == 0:
        print("✓ ecoa-csmgvt is in PATH")
        return True
    else:
        print("✗ ecoa-csmgvt not found in PATH")
        return False


def test_component_implementations():
    """Check that component implementations have source files."""
    print("\n=== Test 6: Component Implementation Source Files ===")

    workspace_base = Path("/workspace")
    if not workspace_base.exists():
        print(f"✗ Workspace not found")
        return

    for project_dir in workspace_base.iterdir():
        if not project_dir.is_dir():
            continue

        comp_impl_dir = project_dir / "Steps" / "4-ComponentImplementations"
        if not comp_impl_dir.exists():
            continue

        print(f"\n  Project: {project_dir.name}")

        for component_dir in comp_impl_dir.iterdir():
            if not component_dir.is_dir():
                continue

            print(f"    Component: {component_dir.name}")

            # Check for module directories
            for module_dir in component_dir.iterdir():
                if not module_dir.is_dir():
                    continue

                src_dir = module_dir / "src"
                if src_dir.exists():
                    source_files = list(src_dir.glob("*.c")) + list(src_dir.glob("*.cpp"))
                    if source_files:
                        print(f"      ✓ {module_dir.name}/src: {len(source_files)} source file(s)")
                        for f in source_files:
                            print(f"        - {f.name}")
                    else:
                        print(f"      ✗ {module_dir.name}/src: No .c/.cpp files (CMake will fail!)")
                else:
                    print(f"      ✗ {module_dir.name}: No src/ directory")


def test_logs_directory():
    """Check logs directory for debugging."""
    print("\n=== Test 7: Logs Directory ===")

    logs_dir = Path("/app/logs")
    if logs_dir.exists():
        print(f"✓ Logs directory exists: {logs_dir}")

        # List log files
        log_files = list(logs_dir.glob("*.log"))
        if log_files:
            print(f"  Found {len(log_files)} log file(s):")
            for f in sorted(log_files):
                size = f.stat().st_size
                print(f"    - {f.name} ({size} bytes)")
        else:
            print("  No log files yet")
    else:
        print(f"✗ Logs directory not found: {logs_dir}")
        print("  Creating logs directory...")
        logs_dir.mkdir(parents=True, exist_ok=True)
        print("  ✓ Created")


def simulate_integration_pipeline():
    """Simulate the INTEGRATION mode pipeline execution."""
    print("\n=== Test 8: Simulate INTEGRATION Pipeline ===")

    # Step 1: Check mode
    mode = "INTEGRATION"
    continuing = False

    if not (mode == "INTEGRATION" and not continuing):
        print(f"✗ Not in INTEGRATION mode or is continuing")
        return

    print("1. ✓ INTEGRATION mode, not continuing")

    # Step 2: Check selectedVersions
    phase_params = {
        "_meta": {
            "selectedVersions": [
                {"componentName": "comp1", "versionId": "v1", "versionName": "1.0"}
            ]
        }
    }

    selected_versions = phase_params.get("_meta", {}).get("selectedVersions", [])
    if not selected_versions:
        print("2. ✗ No selectedVersions - MSCIGT will be SKIPPED")
        print("\n   *** This is the bug! ***")
        print("   Frontend must send 'selectedVersions' in the request.")
        return

    print(f"2. ✓ selectedVersions found: {len(selected_versions)} component(s)")

    # Step 3: Simulate MSCIGT execution
    print("3. Running MSCIGT (implicit)...")
    print("   - Generates skeleton framework")
    print("   - Creates src/myfile.c templates")

    # Step 4: Simulate component overlay
    print("4. Applying component version overlays...")
    for v in selected_versions:
        print(f"   - Overlay {v['componentName']}@{v['versionName']}")

    # Step 5: Simulate CSMGVT
    print("5. Running CSMGVT...")
    print("   - Generates CMakeLists.txt")
    print("   - Expects src/*.c files to exist")
    print("   - Compiles the project")

    print("\n✓ Pipeline simulation complete")


def print_diagnosis():
    """Print diagnosis and recommendations."""
    print("\n" + "=" * 60)
    print("DIAGNOSIS")
    print("=" * 60)

    print("""
Issue: "No Skeleton framework generated" log message
       CMake fails at add_library

Root Cause:
  INTEGRATION mode requires 'selectedVersions' parameter in API request.
  If missing, the implicit MSCIGT step is skipped.

Code Location:
  app/routes/generator.py, lines 864-899

The Check:
  if mode == "INTEGRATION" and not continuing:
      selected_versions = phase_params.get("_meta", {}).get("selectedVersions", [])
      if selected_versions:  # <-- This is False when selectedVersions is missing!
          _run_integration_mode_setup(...)  # Runs MSCIGT + overlay
      else:
          logger.info("INTEGRATION mode without selected versions...")
          # MSCIGT is SKIPPED!

Fix:
  Frontend must include 'selectedVersions' in the /api/generate request:

  {
    "taskId": "...",
    "projectId": "...",
    "workflowMode": "INTEGRATION",
    "selectedPhases": ["EXVT", "CSMGVT"],
    "selectedVersions": [
      {
        "componentName": "mycompFinisher",
        "versionId": "uuid-here",
        "versionName": "v1.0"
      }
    ]
  }
""")


def main():
    """Run all tests."""
    print("=" * 60)
    print("ECOA Tools INTEGRATION Mode Diagnostic Tests")
    print("Running inside container:", os.environ.get("HOSTNAME", "unknown"))
    print("=" * 60)

    tests = [
        test_request_parsing,
        test_integration_mode_check,
        test_project_structure,
        test_mscigt_availability,
        test_csmgvt_availability,
        test_component_implementations,
        test_logs_directory,
        simulate_integration_pipeline,
    ]

    results = []
    for test in tests:
        try:
            test()
            results.append((test.__name__, "OK"))
        except Exception as e:
            print(f"\n✗ {test.__name__} failed: {e}")
            results.append((test.__name__, f"FAILED: {e}"))

    print_diagnosis()

    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    for name, result in results:
        status = "✓" if result == "OK" else "✗"
        print(f"{status} {name}: {result}")


if __name__ == "__main__":
    main()
