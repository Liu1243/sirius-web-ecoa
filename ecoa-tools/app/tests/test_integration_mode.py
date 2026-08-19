"""
Test INTEGRATION mode behavior, especially the implicit MSCIGT execution.

This test verifies:
1. INTEGRATION mode requires selectedVersions to trigger implicit MSCIGT
2. phaseParams._meta.selectedVersions is correctly parsed
3. Without selectedVersions, MSCIGT is skipped (causing CSMGVT to fail)
"""

import sys
import types
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch, MagicMock

# Stub yaml to avoid dependency
yaml_stub = types.ModuleType("yaml")
yaml_stub.safe_load = lambda _content: {
    "verbose": 3,
    "uploads_dir": "uploads",
    "outputs_dir": "outputs",
    "logs_dir": "logs",
    "tools": {
        "mscigt": {"command": "ecoa-mscigt"},
        "csmgvt": {"command": "ecoa-csmgvt", "compile": {"enabled": True}},
        "exvt": {"command": "ecoa-exvt"},
    },
    "api": {"max_upload_size": 16777216},
    "server": {"debug": False},
}
sys.modules.setdefault("yaml", yaml_stub)

from app.utils import config as config_module


class _FakeConfig:
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


config_module.get_config = lambda _config_path="config.yaml": _FakeConfig()
config_module._config = _FakeConfig()

from app.app import create_app
import app.routes.generator as generator_module


class IntegrationModeTests(unittest.TestCase):
    """Test INTEGRATION mode behavior."""

    @classmethod
    def setUpClass(cls):
        cls.app = create_app()
        cls.app.config["TESTING"] = True

    def test_integration_mode_without_selected_versions_skips_mscigt(self):
        """
        Test that INTEGRATION mode without selectedVersions skips implicit MSCIGT.
        This is the root cause of 'No Skeleton framework generated' issue.
        """
        callbacks = []
        recorded_tool_runs = []

        with TemporaryDirectory() as tmpdir:
            steps_root = Path(tmpdir)
            (steps_root / "base.project.xml").write_text("<project />", encoding="utf-8")

            def fake_callback(_url, payload, _task_id):
                callbacks.append(payload)

            def fake_execute_in_project(*, tool_id, project_file, **kwargs):
                recorded_tool_runs.append({
                    "tool_id": tool_id,
                    "project_file": project_file,
                    "compile": kwargs.get("compile"),
                })
                return {
                    "success": True,
                    "return_code": 0,
                    "stdout": "",
                    "stderr": "",
                    "generated_files": [],
                    "project_path": str(steps_root / project_file),
                    "compile_success": True,
                }

            with (
                patch.object(generator_module, "_send_callback", side_effect=fake_callback),
                patch.object(
                    generator_module,
                    "_resolve_project_file",
                    return_value=("project-1", "base.project.xml", steps_root),
                ),
                patch.object(
                    generator_module.ToolExecutor,
                    "execute_in_project",
                    side_effect=fake_execute_in_project,
                ),
            ):
                # INTEGRATION mode WITHOUT selectedVersions - MSCIGT should be skipped
                generator_module._run_pipeline(
                    task_id="task-no-versions",
                    project_id="project-1",
                    output_dir="/workspace",
                    callback_url="http://localhost/callback",
                    selected_phases=["EXVT", "CSMGVT"],
                    continue_on_error=False,
                    phase_params={},  # No _meta.selectedVersions
                    skip_export=True,
                    workflow_mode="INTEGRATION",
                    continuing=False,
                )

        # Verify: MSCIGT should NOT be called
        tool_ids = [r["tool_id"] for r in recorded_tool_runs]
        self.assertNotIn("mscigt", tool_ids, "MSCIGT should be skipped without selectedVersions")

        # Verify: Only EXVT and CSMGVT should be called
        self.assertEqual(tool_ids, ["exvt", "csmgvt"])

        # Verify callback logs do NOT contain "Skeleton framework generated"
        all_logs = " ".join(str(cb.get("logs", [])) for cb in callbacks)
        self.assertNotIn("Skeleton framework generated", all_logs)

    def test_integration_mode_with_selected_versions_triggers_mscigt(self):
        """
        Test that INTEGRATION mode with selectedVersions triggers implicit MSCIGT.
        This is the correct configuration.
        """
        callbacks = []
        recorded_tool_runs = []

        with TemporaryDirectory() as tmpdir:
            steps_root = Path(tmpdir)
            (steps_root / "base.project.xml").write_text("<project />", encoding="utf-8")

            def fake_callback(_url, payload, _task_id):
                callbacks.append(payload)

            def fake_execute_in_project(*, tool_id, project_file, **kwargs):
                recorded_tool_runs.append({
                    "tool_id": tool_id,
                    "project_file": project_file,
                    "compile": kwargs.get("compile"),
                })
                return {
                    "success": True,
                    "return_code": 0,
                    "stdout": "",
                    "stderr": "",
                    "generated_files": [],
                    "project_path": str(steps_root / project_file),
                    "compile_success": True,
                }

            def fake_fetch_version_content(version_id, project_id):
                return {"src/myfile.c": "// test content"}

            with (
                patch.object(generator_module, "_send_callback", side_effect=fake_callback),
                patch.object(
                    generator_module,
                    "_resolve_project_file",
                    return_value=("project-1", "base.project.xml", steps_root),
                ),
                patch.object(
                    generator_module.ToolExecutor,
                    "execute_in_project",
                    side_effect=fake_execute_in_project,
                ),
                patch.object(
                    generator_module,
                    "_fetch_component_version_content",
                    side_effect=fake_fetch_version_content,
                ),
            ):
                # INTEGRATION mode WITH selectedVersions - MSCIGT should be triggered
                generator_module._run_pipeline(
                    task_id="task-with-versions",
                    project_id="project-1",
                    output_dir="/workspace",
                    callback_url="http://localhost/callback",
                    selected_phases=["EXVT", "CSMGVT"],
                    continue_on_error=False,
                    phase_params={
                        "_meta": {
                            "selectedVersions": [
                                {
                                    "componentName": "mycompFinisher",
                                    "versionId": "version-1",
                                    "versionName": "v1.0"
                                }
                            ]
                        }
                    },
                    skip_export=True,
                    workflow_mode="INTEGRATION",
                    continuing=False,
                )

        # Verify: MSCIGT should be called implicitly
        tool_ids = [r["tool_id"] for r in recorded_tool_runs]
        self.assertIn("mscigt", tool_ids, "MSCIGT should be triggered with selectedVersions")

        # Verify: MSCIGT is called before CSMGVT
        mscigt_index = tool_ids.index("mscigt")
        csmgvt_index = tool_ids.index("csmgvt")
        self.assertLess(mscigt_index, csmgvt_index, "MSCIGT should run before CSMGVT")

        # Verify callback logs contain INTEGRATION mode messages
        all_logs = " ".join(str(cb.get("logs", [])) for cb in callbacks)
        self.assertIn("INTEGRATION", all_logs)

    def test_api_request_with_selected_versions(self):
        """
        Test API request parsing with selectedVersions parameter.
        Verifies that selectedVersions is correctly injected into phaseParams._meta.
        """
        client = self.app.test_client()

        recorded_pipeline_args = {}

        def capture_pipeline_args(*args, **kwargs):
            recorded_pipeline_args.update(kwargs)
            return None
        with patch.object(generator_module, "_run_pipeline", side_effect=capture_pipeline_args):
            response = client.post(
                "/api/generate",
                json={
                    "taskId": "task-api-test",
                    "projectId": "project-1",
                    "callbackUrl": "http://localhost/callback",
                    "workflowMode": "INTEGRATION",
                    "selectedPhases": ["EXVT", "CSMGVT"],
                    "selectedVersions": [
                        {
                            "componentName": "mycompFinisher",
                            "versionId": "version-1",
                            "versionName": "v1.0"
                        }
                    ],
                },
            )

        self.assertEqual(response.status_code, 202)

        # Verify phaseParams contains _meta.selectedVersions
        phase_params = recorded_pipeline_args.get("phase_params", {})
        self.assertIn("_meta", phase_params)
        self.assertIn("selectedVersions", phase_params["_meta"])
        selected_versions = phase_params["_meta"]["selectedVersions"]
        self.assertEqual(len(selected_versions), 1)
        self.assertEqual(selected_versions[0]["componentName"], "mycompFinisher")

    def test_api_request_without_selected_versions(self):
        """
        Test API request without selectedVersions.
        This should result in MSCIGT being skipped.
        """
        client = self.app.test_client()

        recorded_pipeline_args = {}

        def capture_pipeline_args(*args, **kwargs):
            recorded_pipeline_args.update(kwargs)
            return None

        with patch.object(generator_module, "_run_pipeline", side_effect=capture_pipeline_args):
            response = client.post(
                "/api/generate",
                json={
                    "taskId": "task-api-test-no-versions",
                    "projectId": "project-1",
                    "callbackUrl": "http://localhost/callback",
                    "workflowMode": "INTEGRATION",
                    "selectedPhases": ["EXVT", "CSMGVT"],
                    # No selectedVersions!
                },
            )

        self.assertEqual(response.status_code, 202)

        # Verify phaseParams does NOT contain _meta.selectedVersions
        phase_params = recorded_pipeline_args.get("phase_params", {})
        if "_meta" in phase_params:
            self.assertNotIn("selectedVersions", phase_params["_meta"])

    def test_integration_mode_phase_order(self):
        """
        Test that INTEGRATION mode correctly orders phases.
        MSCIGT should be filtered out if selectedVersions is provided.
        """
        from app.services.generation_workflow import resolve_phase_steps

        # Without continuing and without MSCIGT in selected phases
        phases = resolve_phase_steps(
            "INTEGRATION",
            selected_phases=["EXVT", "CSMGVT", "LDP"],
            continuing=False
        )
        self.assertEqual(phases, ["EXVT", "CSMGVT", "LDP"])
        self.assertNotIn("MSCIGT", phases)
        self.assertNotIn("ASCTG", phases)


class IntegrationModeMscigtFailureTests(unittest.TestCase):
    """Test INTEGRATION mode when MSCIGT fails."""

    @classmethod
    def setUpClass(cls):
        cls.app = create_app()
        cls.app.config["TESTING"] = True

    def test_integration_mode_fails_when_mscigt_fails(self):
        """
        Test that pipeline fails gracefully when implicit MSCIGT fails.
        """
        callbacks = []

        with TemporaryDirectory() as tmpdir:
            steps_root = Path(tmpdir)
            (steps_root / "base.project.xml").write_text("<project />", encoding="utf-8")

            def fake_callback(_url, payload, _task_id):
                callbacks.append(payload)

            def fake_execute_in_project(*, tool_id, **kwargs):
                if tool_id == "mscigt":
                    return {
                        "success": False,
                        "return_code": 1,
                        "stdout": "",
                        "stderr": "MSCIGT failed: missing project file",
                        "generated_files": [],
                    }
                return {
                    "success": True,
                    "return_code": 0,
                    "stdout": "",
                    "stderr": "",
                    "generated_files": [],
                }

            with (
                patch.object(generator_module, "_send_callback", side_effect=fake_callback),
                patch.object(
                    generator_module,
                    "_resolve_project_file",
                    return_value=("project-1", "base.project.xml", steps_root),
                ),
                patch.object(
                    generator_module.ToolExecutor,
                    "execute_in_project",
                    side_effect=fake_execute_in_project,
                ),
            ):
                generator_module._run_pipeline(
                    task_id="task-mscigt-fail",
                    project_id="project-1",
                    output_dir="/workspace",
                    callback_url="http://localhost/callback",
                    selected_phases=["EXVT", "CSMGVT"],
                    continue_on_error=False,
                    phase_params={
                        "_meta": {
                            "selectedVersions": [
                                {"componentName": "comp1", "versionId": "v1", "versionName": "v1.0"}
                            ]
                        }
                    },
                    skip_export=True,
                    workflow_mode="INTEGRATION",
                    continuing=False,
                )

        # Verify: Pipeline should fail
        final_callback = callbacks[-1]
        self.assertEqual(final_callback["status"], "FAILED")

        # Verify: Error message mentions MSCIGT failure
        all_logs = " ".join(str(cb.get("logs", [])) for cb in callbacks)
        self.assertIn("MSCIGT", all_logs)


if __name__ == "__main__":
    unittest.main(verbosity=2)
