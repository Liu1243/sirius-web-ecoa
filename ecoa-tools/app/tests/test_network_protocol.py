"""
Tests for DDS / UDP / TCP network-protocol selection:
  - ToolExecutor._cmake_options_for_network_protocol (unit)
  - ToolExecutor._parse_dds_params (unit)
  - _resolve_network_protocol in routes/tools.py (unit)
  - POST /api/tools/execute-project with network_protocol (integration-light)
"""

import sys
import types
import unittest
from tempfile import NamedTemporaryFile
from unittest.mock import MagicMock, patch

# ---------------------------------------------------------------------------
# Stub yaml so tests can run without the real PyYAML installed
# ---------------------------------------------------------------------------
yaml_stub = types.ModuleType("yaml")
yaml_stub.safe_load = lambda _: {
    "verbose": 3,
    "uploads_dir": "uploads",
    "outputs_dir": "outputs",
    "logs_dir": "logs",
    "tools": {},
    "api": {"max_upload_size": 16777216},
    "server": {"debug": False},
}
sys.modules.setdefault("yaml", yaml_stub)

from app.utils import config as config_module  # noqa: E402


class _FakeConfig:
    logs_dir = "logs"
    max_upload_size = 16777216
    server_debug = False

    def get(self, key, default=None):
        return default


config_module.get_config = lambda _config_path="config.yaml": _FakeConfig()

from app.services.executor import ToolExecutor  # noqa: E402
from app.routes.tools import _resolve_network_protocol  # noqa: E402
from app.app import create_app  # noqa: E402


# ---------------------------------------------------------------------------
# Unit tests: ToolExecutor._cmake_options_for_network_protocol
# ---------------------------------------------------------------------------

class CmakeOptionsForNetworkProtocolTests(unittest.TestCase):

    def setUp(self):
        with patch("app.services.executor.get_config", return_value=_FakeConfig()):
            self.executor = ToolExecutor()

    # --- DDS ---

    def test_dds_returns_dds_on_and_udp_off(self):
        opts = self.executor._cmake_options_for_network_protocol("dds")
        self.assertIn("-DCMAKE_USE_DDS_PROTO=ON", opts)
        self.assertIn("-DCMAKE_USE_UDP_PROTO=OFF", opts)

    def test_dds_uppercase_is_accepted(self):
        opts = self.executor._cmake_options_for_network_protocol("DDS")
        self.assertIn("-DCMAKE_USE_DDS_PROTO=ON", opts)

    def test_dds_mixed_case_is_accepted(self):
        opts = self.executor._cmake_options_for_network_protocol("Dds")
        self.assertIn("-DCMAKE_USE_DDS_PROTO=ON", opts)

    def test_dds_with_whitespace_is_accepted(self):
        opts = self.executor._cmake_options_for_network_protocol("  dds  ")
        self.assertIn("-DCMAKE_USE_DDS_PROTO=ON", opts)

    # --- UDP ---

    def test_udp_returns_udp_on_and_dds_off(self):
        opts = self.executor._cmake_options_for_network_protocol("udp")
        self.assertIn("-DCMAKE_USE_UDP_PROTO=ON", opts)
        self.assertIn("-DCMAKE_USE_DDS_PROTO=OFF", opts)

    # --- TCP ---

    def test_tcp_returns_both_flags_off(self):
        opts = self.executor._cmake_options_for_network_protocol("tcp")
        self.assertIn("-DCMAKE_USE_DDS_PROTO=OFF", opts)
        self.assertIn("-DCMAKE_USE_UDP_PROTO=OFF", opts)

    # --- None ---

    def test_none_returns_empty_list(self):
        opts = self.executor._cmake_options_for_network_protocol(None)
        self.assertEqual(opts, [])

    # --- Invalid ---

    def test_invalid_protocol_raises_value_error(self):
        with self.assertRaises(ValueError):
            self.executor._cmake_options_for_network_protocol("mqtt")

    def test_empty_string_raises_value_error(self):
        with self.assertRaises(ValueError):
            self.executor._cmake_options_for_network_protocol("")

    # --- DDS flags are mutually exclusive with UDP ---

    def test_dds_disables_udp(self):
        dds_opts = self.executor._cmake_options_for_network_protocol("dds")
        udp_opts = self.executor._cmake_options_for_network_protocol("udp")
        # DDS ON → UDP OFF
        self.assertNotIn("-DCMAKE_USE_UDP_PROTO=ON", dds_opts)
        # UDP ON → DDS OFF
        self.assertNotIn("-DCMAKE_USE_DDS_PROTO=ON", udp_opts)

    # --- DDS Domain ID injection ---

    def test_dds_default_domain_id_is_zero(self):
        opts = self.executor._cmake_options_for_network_protocol("dds")
        self.assertIn("-DCYCLONEDDS_DOMAIN_ID=0", opts)

    def test_dds_custom_domain_id_is_injected(self):
        opts = self.executor._cmake_options_for_network_protocol("dds", dds_domain_id=5)
        self.assertIn("-DCYCLONEDDS_DOMAIN_ID=5", opts)

    def test_udp_does_not_inject_domain_id(self):
        opts = self.executor._cmake_options_for_network_protocol("udp", dds_domain_id=5)
        domain_opts = [o for o in opts if "CYCLONEDDS_DOMAIN_ID" in o]
        self.assertEqual(domain_opts, [])

    def test_tcp_does_not_inject_domain_id(self):
        opts = self.executor._cmake_options_for_network_protocol("tcp", dds_domain_id=5)
        domain_opts = [o for o in opts if "CYCLONEDDS_DOMAIN_ID" in o]
        self.assertEqual(domain_opts, [])


# ---------------------------------------------------------------------------
# Unit tests: ToolExecutor._parse_dds_params
# ---------------------------------------------------------------------------

_DDS_NS = "http://www.ecoa.technology/ddsbinding"

_DDS_BINDING_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<DDSBinding xmlns="{ns}">
  <domain id="7"/>
  <topic name="MyEcoaTopic"/>
</DDSBinding>
""".format(ns=_DDS_NS)

_DDS_BINDING_NO_TOPIC_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<DDSBinding xmlns="{ns}">
  <domain id="3"/>
</DDSBinding>
""".format(ns=_DDS_NS)


class ParseDdsParamsTests(unittest.TestCase):

    def setUp(self):
        with patch("app.services.executor.get_config", return_value=_FakeConfig()):
            self.executor = ToolExecutor()

    def _write_tmp(self, content):
        f = NamedTemporaryFile(mode="w", suffix=".xml", delete=False)
        f.write(content)
        f.close()
        return f.name

    def test_parses_domain_id(self):
        path = self._write_tmp(_DDS_BINDING_XML)
        params = ToolExecutor._parse_dds_params(path)
        self.assertEqual(params["domain_id"], 7)

    def test_parses_topic_name(self):
        path = self._write_tmp(_DDS_BINDING_XML)
        params = ToolExecutor._parse_dds_params(path)
        self.assertEqual(params["topic_name"], "MyEcoaTopic")

    def test_missing_topic_defaults_to_ldp_local_peer_data(self):
        path = self._write_tmp(_DDS_BINDING_NO_TOPIC_XML)
        params = ToolExecutor._parse_dds_params(path)
        self.assertEqual(params["topic_name"], "LdpLocalPeerData")

    def test_missing_file_returns_defaults(self):
        params = ToolExecutor._parse_dds_params("/nonexistent/dds-binding.xml")
        self.assertEqual(params["domain_id"], 0)
        self.assertEqual(params["topic_name"], "LdpLocalPeerData")

    def test_none_path_returns_defaults(self):
        params = ToolExecutor._parse_dds_params(None)
        self.assertEqual(params["domain_id"], 0)

    def test_domain_id_zero_is_valid(self):
        xml = """\
<?xml version="1.0" encoding="UTF-8"?>
<DDSBinding xmlns="{ns}"><domain id="0"/></DDSBinding>""".format(ns=_DDS_NS)
        path = self._write_tmp(xml)
        params = ToolExecutor._parse_dds_params(path)
        self.assertEqual(params["domain_id"], 0)


# ---------------------------------------------------------------------------
# Unit tests: _resolve_network_protocol (routes/tools.py)
# ---------------------------------------------------------------------------

class ResolveNetworkProtocolTests(unittest.TestCase):

    def _call(self, data):
        return _resolve_network_protocol(data)

    # --- network_protocol key ---

    def test_dds_string_resolves_to_dds(self):
        self.assertEqual(self._call({"network_protocol": "dds"}), "dds")

    def test_udp_string_resolves_to_udp(self):
        self.assertEqual(self._call({"network_protocol": "udp"}), "udp")

    def test_tcp_string_resolves_to_tcp(self):
        self.assertEqual(self._call({"network_protocol": "tcp"}), "tcp")

    def test_dds_uppercase_resolves(self):
        self.assertEqual(self._call({"network_protocol": "DDS"}), "dds")

    def test_camel_case_key_networkProtocol_is_accepted(self):
        self.assertEqual(self._call({"networkProtocol": "dds"}), "dds")

    def test_invalid_protocol_string_raises(self):
        from werkzeug.exceptions import BadRequest
        with self.assertRaises(BadRequest):
            self._call({"network_protocol": "mqtt"})

    def test_non_string_network_protocol_raises(self):
        from werkzeug.exceptions import BadRequest
        with self.assertRaises(BadRequest):
            self._call({"network_protocol": 42})

    # --- boolean style: use_dds_proto ---

    def test_use_dds_proto_true_resolves_to_dds(self):
        self.assertEqual(self._call({"use_dds_proto": True}), "dds")

    def test_use_udp_proto_true_resolves_to_udp(self):
        self.assertEqual(self._call({"use_udp_proto": True}), "udp")

    def test_use_tcp_proto_true_resolves_to_tcp(self):
        self.assertEqual(self._call({"use_tcp_proto": True}), "tcp")

    def test_camel_case_useDdsProto_is_accepted(self):
        self.assertEqual(self._call({"useDdsProto": True}), "dds")

    def test_two_proto_flags_true_raises(self):
        from werkzeug.exceptions import BadRequest
        with self.assertRaises(BadRequest):
            self._call({"use_dds_proto": True, "use_udp_proto": True})

    def test_all_false_with_no_network_protocol_returns_tcp(self):
        self.assertEqual(
            self._call({"use_tcp_proto": False, "use_udp_proto": False, "use_dds_proto": False}),
            "tcp",
        )

    def test_empty_dict_returns_none(self):
        self.assertIsNone(self._call({}))

    def test_unrelated_keys_return_none(self):
        self.assertIsNone(self._call({"project_name": "demo"}))


# ---------------------------------------------------------------------------
# API-level smoke tests: POST /api/tools/execute-project with DDS protocol
# ---------------------------------------------------------------------------

class ExecuteProjectDdsApiTests(unittest.TestCase):
    """API-level smoke tests for network_protocol validation in execute-project."""

    _FAKE_LDP_TOOL_CONFIG = {
        "name": "ecoa-ldp",
        "command": "ecoa-ldp",
        "compile": {"enabled": True, "timeout": 600, "cmake_options": []},
    }

    @classmethod
    def setUpClass(cls):
        cls.app = create_app()
        cls.app.config["TESTING"] = True

    def _post(self, payload):
        """POST to execute-project with tool config and executor both mocked."""
        mock_config = MagicMock()
        mock_config.verbose = 3
        mock_config.get_tool.side_effect = (
            lambda tid: self._FAKE_LDP_TOOL_CONFIG if tid == "ldp" else None
        )

        mock_executor = MagicMock()
        mock_executor.execute_in_project.return_value = {
            "success": True,
            "return_code": 0,
            "message": "ok",
            "stdout": "",
            "stderr": "",
            "generated_files": [],
            "project_path": "/workspace/demo/demo.project.xml",
            "project_name": "demo",
            "project_file": "demo.project.xml",
            "tool": "ldp",
        }

        # executor is a module-level singleton — patch the instance, not the class
        with (
            patch("app.routes.tools.config", mock_config),
            patch("app.routes.tools.executor", mock_executor),
        ):
            with self.app.test_client() as client:
                return client.post("/api/tools/execute-project", json=payload)

    def test_dds_protocol_is_accepted_by_api(self):
        """network_protocol=dds must not be rejected by protocol validation."""
        resp = self._post({
            "project_name": "demo",
            "project_file": "demo.project.xml",
            "tool": "ldp",
            "network_protocol": "dds",
        })
        # 400 would mean the request was rejected (validation failure)
        self.assertNotEqual(resp.status_code, 400)

    def test_udp_protocol_is_accepted_by_api(self):
        resp = self._post({
            "project_name": "demo",
            "project_file": "demo.project.xml",
            "tool": "ldp",
            "network_protocol": "udp",
        })
        self.assertNotEqual(resp.status_code, 400)

    def test_tcp_protocol_is_accepted_by_api(self):
        resp = self._post({
            "project_name": "demo",
            "project_file": "demo.project.xml",
            "tool": "ldp",
            "network_protocol": "tcp",
        })
        self.assertNotEqual(resp.status_code, 400)

    def test_invalid_protocol_is_rejected_by_api(self):
        resp = self._post({
            "project_name": "demo",
            "project_file": "demo.project.xml",
            "tool": "ldp",
            "network_protocol": "mqtt",
        })
        self.assertEqual(resp.status_code, 400)

    def test_dds_and_udp_simultaneously_is_rejected(self):
        resp = self._post({
            "project_name": "demo",
            "project_file": "demo.project.xml",
            "tool": "ldp",
            "use_dds_proto": True,
            "use_udp_proto": True,
        })
        self.assertEqual(resp.status_code, 400)


if __name__ == "__main__":
    unittest.main()
