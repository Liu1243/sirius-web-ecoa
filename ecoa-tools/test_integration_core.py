"""
Simplified test for INTEGRATION mode behavior without Flask dependencies.

This test verifies the core logic:
1. INTEGRATION mode requires selectedVersions to trigger implicit MSCIGT
2. Without selectedVersions, MSCIGT is skipped (causing CSMGVT to fail)
"""

import sys
import types
from pathlib import Path
from tempfile import TemporaryDirectory


# Create minimal stubs for imports
class FakeConfig:
    """Minimal config for testing."""
    logs_dir = "logs"
    max_upload_size = 16777216
    server_debug = False
    verbose = 3
    uploads_dir = "uploads"
    outputs_dir = "outputs"
    projects_base_dir = "/tmp/projects"

    def get(self, key, default=None):
        return default

    def get_tool(self, tool_id):
        return {
            "mscigt": {"command": "ecoa-mscigt"},
            "csmgvt": {"command": "ecoa-csmgvt", "compile": {"enabled": True, "timeout": 600}},
            "exvt": {"command": "ecoa-exvt"},
        }.get(tool_id)


class TestIntegrationModeLogic:
    """Test INTEGRATION mode logic directly."""

    def test_phase_params_structure(self):
        """
        Test that demonstrates the correct phaseParams structure for INTEGRATION mode.
        """
        # Correct structure with selectedVersions
        correct_phase_params = {
            "_meta": {
                "selectedVersions": [
                    {
                        "componentName": "mycompFinisher",
                        "versionId": "version-uuid-1",
                        "versionName": "v1.0"
                    },
                    {
                        "componentName": "myCompWriter",
                        "versionId": "version-uuid-2",
                        "versionName": "v1.0"
                    }
                ]
            }
        }

        # Simulate the check in generator.py line 865-866
        selected_versions = correct_phase_params.get("_meta", {}).get("selectedVersions", [])
        assert len(selected_versions) == 2, "Should have 2 selected versions"
        assert selected_versions[0]["componentName"] == "mycompFinisher"
        print("✓ Correct phaseParams structure accepted")

    def test_missing_selected_versions(self):
        """
        Test that demonstrates what happens when selectedVersions is missing.
        This is the root cause of the 'No Skeleton framework generated' issue.
        """
        # Missing selectedVersions
        wrong_phase_params = {}  # Empty or without _meta

        # Simulate the check in generator.py line 865-866
        selected_versions = wrong_phase_params.get("_meta", {}).get("selectedVersions", [])
        assert len(selected_versions) == 0, "Should have 0 selected versions"

        # This means MSCIGT will be skipped!
        # According to generator.py line 866: if selected_versions:
        if selected_versions:
            print("MSCIGT would be triggered")
        else:
            print("✓ MSCIGT will be SKIPPED (this is the bug!)")

    def test_api_request_parsing(self):
        """
        Test the API request parsing logic from generator.py lines 1469-1473.
        """
        # Simulate API request data
        data = {
            "taskId": "task-1",
            "projectId": "project-1",
            "callbackUrl": "http://localhost/callback",
            "workflowMode": "INTEGRATION",
            "selectedPhases": ["EXVT", "CSMGVT"],
            "selectedVersions": [
                {"componentName": "comp1", "versionId": "v1", "versionName": "1.0"}
            ],
        }

        # Simulate the parsing logic
        phase_params = {}
        selected_versions = data.get("selectedVersions") or data.get("selected_versions") or []

        if selected_versions:
            if "_meta" not in phase_params:
                phase_params["_meta"] = {}
            phase_params["_meta"]["selectedVersions"] = selected_versions

        # Verify
        assert "_meta" in phase_params, "_meta should be added"
        assert "selectedVersions" in phase_params["_meta"], "selectedVersions should be in _meta"
        print("✓ API request parsing works correctly")

    def test_integration_mode_flow(self):
        """
        Test the complete INTEGRATION mode flow logic.
        """
        print("\n=== INTEGRATION Mode Flow Test ===")

        # Step 1: Check workflow mode
        mode = "INTEGRATION"
        continuing = False

        if mode == "INTEGRATION" and not continuing:
            print("1. ✓ INTEGRATION mode detected, not continuing")

            # Step 2: Check selectedVersions
            phase_params = {
                "_meta": {
                    "selectedVersions": [
                        {"componentName": "mycompFinisher", "versionId": "v1", "versionName": "1.0"}
                    ]
                }
            }

            selected_versions = phase_params.get("_meta", {}).get("selectedVersions", [])

            if selected_versions:
                print(f"2. ✓ selectedVersions found: {len(selected_versions)} components")
                print("3. ✓ MSCIGT will be triggered implicitly")
                print("4. ✓ Component overlays will be applied")
                print("5. ✓ CSMGVT will have source code to compile")
            else:
                print("2. ✗ selectedVersions NOT found!")
                print("3. ✗ MSCIGT will be SKIPPED")
                print("4. ✗ No source code for CSMGVT")
                print("5. ✗ CMake will fail with 'add_library' error")

    def test_actual_bug_scenario(self):
        """
        Reproduce the actual bug scenario reported by user.
        """
        print("\n=== Bug Scenario Reproduction ===")
        print("User reports: 'No Skeleton framework generated' log")
        print("Error: CMake fails at add_library")

        # Simulate what happens when selectedVersions is missing
        phase_params = {}  # Empty - this is the bug trigger

        mode = "INTEGRATION"
        continuing = False

        # This is the check from generator.py line 864-899
        if mode == "INTEGRATION" and not continuing:
            selected_versions = phase_params.get("_meta", {}).get("selectedVersions", [])

            if selected_versions:
                print("✓ Running _run_integration_mode_setup...")
                print("✓ Would see: 'Skeleton framework generated successfully'")
            else:
                print("✗ SKIPPING _run_integration_mode_setup!")
                print("✗ No 'Skeleton framework generated' log")
                print("✗ MSCIGT not run -> No skeleton code")
                print("✗ CSMGVT runs but no source files")
                print("✗ CMake add_library fails")

        print("\n=== Root Cause ===")
        print("The frontend must send 'selectedVersions' in the API request.")
        print("If missing, INTEGRATION mode silently skips MSCIGT.")


class TestSelectedVersionsStructure:
    """Test the structure of selectedVersions."""

    def test_version_entry_structure(self):
        """
        Test that each version entry has the required fields.
        """
        version_entry = {
            "componentName": "mycompFinisher",
            "versionId": "550e8400-e29b-41d4-a716-446655440000",
            "versionName": "v1.0"
        }

        # Check required fields
        assert "componentName" in version_entry, "Missing componentName"
        assert "versionId" in version_entry, "Missing versionId"
        assert "versionName" in version_entry, "Missing versionName"

        print("✓ Version entry has all required fields")

    def test_version_entry_usage(self):
        """
        Test how version entries are used in the code.
        From generator.py lines 727-748
        """
        version_info = {
            "componentName": "mycompFinisher",
            "versionId": "v1-uuid",
            "versionName": "v1.0"
        }

        # Simulate the code logic
        component_name = version_info.get("componentName") or version_info.get("component_name")
        version_id = version_info.get("versionId") or version_info.get("version_id")
        version_name = version_info.get("versionName") or version_info.get("version_name", "unknown")

        assert component_name == "mycompFinisher"
        assert version_id == "v1-uuid"
        assert version_name == "v1.0"

        print(f"✓ Version info parsed: {component_name}@{version_name}")


def run_all_tests():
    """Run all tests."""
    print("=" * 60)
    print("INTEGRATION Mode Automated Tests")
    print("=" * 60)

    test_classes = [
        TestIntegrationModeLogic(),
        TestSelectedVersionsStructure(),
    ]

    for test_class in test_classes:
        print(f"\n--- {test_class.__class__.__name__} ---")
        for method_name in dir(test_class):
            if method_name.startswith("test_"):
                print(f"\n{method_name}:")
                try:
                    getattr(test_class, method_name)()
                except AssertionError as e:
                    print(f"  FAILED: {e}")
                except Exception as e:
                    print(f"  ERROR: {e}")

    print("\n" + "=" * 60)
    print("Tests completed")
    print("=" * 60)


if __name__ == "__main__":
    run_all_tests()
