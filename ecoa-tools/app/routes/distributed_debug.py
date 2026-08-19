"""API routes for distributed debug orchestration."""

from flask import Blueprint, jsonify, request
from werkzeug.exceptions import BadRequest, InternalServerError, NotFound

from app.services.distributed_debug_runtime import DistributedDebugRuntime, DistributedDebugRuntimeError
from app.utils.logger import setup_logger

bp = Blueprint("distributed_debug", __name__, url_prefix="/api/distributed-debug")
logger = setup_logger("app.routes.distributed_debug")
runtime_service = DistributedDebugRuntime()


_INTERNAL_SERVICE_HEADER = "X-Internal-Service"
_INTERNAL_SERVICE_VALUE = "ecoa-backend"


def _is_internal_request() -> bool:
    """Check if request comes from the trusted internal backend service."""
    return request.headers.get(_INTERNAL_SERVICE_HEADER) == _INTERNAL_SERVICE_VALUE


@bp.route("/start", methods=["POST"])
def start_distributed_debug() -> tuple:
    """Start the distributed debug compose stack and attach the IDE container."""
    data = _require_json_body()
    target_dir = data.get("target_dir")
    client_container = data.get("client_container")
    project_id = data.get("project_id")
    project_name = data.get("project_name")
    user_id = data.get("user_id")
    username = data.get("username")
    target_arch = data.get("target_arch", "native")

    if not target_dir:
        raise BadRequest("'target_dir' is required")

    try:
        result = runtime_service.start(target_dir, client_container=client_container, project_id=project_id, project_name=project_name, user_id=user_id, username=username, target_arch=target_arch)
    except FileNotFoundError as exc:
        raise NotFound(str(exc)) from exc
    except ValueError as exc:
        raise BadRequest(str(exc)) from exc
    except DistributedDebugRuntimeError as exc:
        logger.error("Distributed debug start failed: %s", exc)
        raise InternalServerError(str(exc)) from exc

    return jsonify(result), 200


@bp.route("/stop", methods=["POST"])
def stop_distributed_debug() -> tuple:
    """Stop the distributed debug compose stack and detach the IDE container."""
    data = _require_json_body()
    target_dir = data.get("target_dir")
    client_container = data.get("client_container")
    session_id = data.get("session_id")

    if not target_dir and not session_id:
        raise BadRequest("'target_dir' or 'session_id' is required")

    try:
        result = runtime_service.stop(target_dir or "", client_container=client_container, session_id=session_id)
    except FileNotFoundError as exc:
        raise NotFound(str(exc)) from exc
    except ValueError as exc:
        raise BadRequest(str(exc)) from exc
    except DistributedDebugRuntimeError as exc:
        logger.error("Distributed debug stop failed: %s", exc)
        raise InternalServerError(str(exc)) from exc

    return jsonify(result), 200


@bp.route("/status", methods=["GET"])
def distributed_debug_status() -> tuple:
    """Return distributed debug status for the given Steps workspace."""
    target_dir = request.args.get("target_dir", type=str)
    client_container = request.args.get("client_container", type=str)

    if not target_dir:
        raise BadRequest("Query parameter 'target_dir' is required")

    try:
        result = runtime_service.status(target_dir, client_container=client_container)
    except FileNotFoundError as exc:
        raise NotFound(str(exc)) from exc
    except ValueError as exc:
        raise BadRequest(str(exc)) from exc
    except DistributedDebugRuntimeError as exc:
        logger.error("Distributed debug status failed: %s", exc)
        raise InternalServerError(str(exc)) from exc

    return jsonify(result), 200


@bp.route("/check-docker", methods=["GET"])
def check_docker() -> tuple:
    """Check Docker daemon connectivity from the ecoa-tools container."""
    try:
        docker_host = runtime_service._ensure_docker_available()
        return jsonify({"available": True, "docker_host": docker_host}), 200
    except DistributedDebugRuntimeError as exc:
        return jsonify({"available": False, "error": str(exc)}), 200


@bp.route("/admin/containers", methods=["GET"])
def list_all_containers() -> tuple:
    """List all distributed debug containers (admin only).

    This endpoint is only accessible from the trusted internal backend service.
    The backend (DistributedDebugController) already performs admin authorisation
    before forwarding the request here, so we only verify the internal service
    header rather than making a redundant callback to the auth endpoint.
    """
    if not _is_internal_request():
        return jsonify({"success": False, "error": "Forbidden: internal service only"}), 403

    try:
        containers = runtime_service.list_all_containers()
        running = len([c for c in containers if c.get("started", False)])
        return jsonify({
            "success": True,
            "containers": containers,
            "total": len(containers),
            "running": running
        }), 200
    except DistributedDebugRuntimeError as exc:
        logger.error("Failed to list containers: %s", exc)
        return jsonify({"success": False, "error": str(exc)}), 500


@bp.route("/my-containers", methods=["GET"])
def list_my_containers() -> tuple:
    """List distributed debug containers for the current user.

    The backend (DistributedDebugController) already validates the session and
    passes user identity via X-Ecoa-User-Id / X-Ecoa-Username headers, so we
    trust those values instead of making a redundant callback to the auth endpoint.
    """
    if not _is_internal_request():
        return jsonify({"success": False, "error": "Forbidden: internal service only"}), 403

    user_id = request.headers.get("X-Ecoa-User-Id")
    username = request.headers.get("X-Ecoa-Username", "")

    if not user_id:
        return jsonify({"success": False, "error": "Unauthorized"}), 401

    try:
        containers = runtime_service.list_user_containers(user_id, username)
        running = len([c for c in containers if c.get("started", False)])

        return jsonify({
            "success": True,
            "containers": containers,
            "total": len(containers),
            "running": running
        }), 200
    except Exception as exc:
        logger.error("Failed to list user containers: %s", exc)
        return jsonify({"success": False, "error": str(exc)}), 500


@bp.route("/admin/delete-session", methods=["POST"])
def delete_session() -> tuple:
    """Delete a stopped debug session (admin only).

    Cleans up the session metadata file and runtime compose file for a
    session that is no longer running.  Running sessions must be stopped
    before they can be deleted.
    """
    if not _is_internal_request():
        return jsonify({"success": False, "error": "Forbidden: internal service only"}), 403

    data = _require_json_body()
    session_id = data.get("session_id")

    if not session_id:
        return jsonify({"success": False, "error": "'session_id' is required"}), 400

    try:
        result = runtime_service.delete_session(session_id)
        status_code = 200 if result.get("success") else 400
        return jsonify(result), status_code
    except DistributedDebugRuntimeError as exc:
        logger.error("Failed to delete session %s: %s", session_id, exc)
        return jsonify({"success": False, "session_id": session_id, "error": str(exc)}), 500
    except Exception as exc:
        logger.error("Unexpected error deleting session %s: %s", session_id, exc)
        return jsonify({"success": False, "session_id": session_id, "error": str(exc)}), 500


@bp.route("/admin/batch-delete-sessions", methods=["POST"])
def batch_delete_sessions() -> tuple:
    """Batch delete multiple stopped debug sessions (admin only).

    Accepts a JSON body with a 'session_ids' array.  Each session must be
    stopped before it can be deleted; running sessions are skipped with an
    error entry in the results.
    """
    if not _is_internal_request():
        return jsonify({"success": False, "error": "Forbidden: internal service only"}), 403

    data = _require_json_body()
    session_ids = data.get("session_ids")

    if not session_ids or not isinstance(session_ids, list):
        return jsonify({"success": False, "error": "'session_ids' must be a non-empty list"}), 400

    try:
        result = runtime_service.batch_delete_sessions(session_ids)
        return jsonify(result), 200
    except DistributedDebugRuntimeError as exc:
        logger.error("Failed to batch delete sessions: %s", exc)
        return jsonify({"success": False, "error": str(exc)}), 500
    except Exception as exc:
        logger.error("Unexpected error in batch delete sessions: %s", exc)
        return jsonify({"success": False, "error": str(exc)}), 500


def _require_json_body() -> dict:
    if not request.is_json:
        raise BadRequest("Request must be JSON")
    return request.get_json(silent=True) or {}
