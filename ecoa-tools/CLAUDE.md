# CLAUDE.md — ecoa-tools

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`ecoa-tools` is a Flask-based REST API (Python 3.12, port 5000) that wraps the ECOA (European Component Oriented Architecture) CLI toolchain and exposes it over HTTP. It acts as a sidecar service to the Spring Boot `sirius-web` backend: the Java backend exports ECOA model data into a shared filesystem volume, then calls this service to run the generation pipeline; this service reports progress back via HTTP callbacks.

The five ECOA CLI tools wrapped here are (in pipeline order):

| Tool ID  | CLI command    | Role                                                    |
|----------|----------------|---------------------------------------------------------|
| `exvt`   | `ecoa-exvt`    | XML validation                                          |
| `mscigt` | `ecoa-mscigt`  | Module skeleton & container interface generation        |
| `asctg`  | `ecoa-asctg`   | Application software component test harness generation  |
| `csmgvt` | `ecoa-csmgvt`  | Connected System Model framework generation + compile   |
| `ldp`    | `ecoa-ldp`     | Logic Deployment Platform generation + compile          |

All CLI tools are installed from `as6-tools/` as editable Python packages inside a venv at `/app/.venv`.

## Directory Structure

```
ecoa-tools/
├── main.py                    # Entry point: creates Flask app and starts server
├── config.yaml                # Tool definitions, server settings, paths
├── requirements.txt           # Python deps (Flask, PyYAML, requests, lxml, ...)
├── Dockerfile                 # Dev image (Ubuntu 22.04); installs ECOA CLI tools from as6-tools/
├── Dockerfile-deployment       # Production image; includes cross-compilation toolchains + Docker-in-Docker
├── Makefile                   # Reference only — shows cmake/make commands used internally
├── as6-tools/                 # ECOA CLI tools installed as editable pip packages
└── app/
    ├── app.py                 # Flask application factory; registers all blueprints
    ├── routes/
    │   ├── tools.py           # GET /api/tools, POST /api/tools/execute-project
    │   ├── generator.py       # POST /api/generate (async pipeline + backflow endpoints)
    │   ├── asctg.py           # /asctg/* ASCTG-specific component/config endpoints
    │   └── distributed_debug.py  # /api/distributed-debug/* debug session management
    ├── services/
    │   ├── executor.py        # ToolExecutor: subprocess wrapper for all ECOA CLI tools
    │   ├── generation_workflow.py  # WorkflowContext, phase resolution, workflow modes
    │   ├── asctg_service.py   # ASCTG harness execution from a Steps directory
    │   ├── code_backflow.py   # scan/generate/apply patch logic for HARNESS code return
    │   ├── distributed_debug.py      # Topology collection + debug asset generation
    │   └── distributed_debug_runtime.py  # Docker compose orchestration for debug sessions
    ├── utils/
    │   ├── config.py          # Config loader (config.yaml + env var overrides)
    │   ├── logger.py          # Structured logger + RequestContext helper
    │   └── xml_parser.py      # Helpers for reading ECOA XML files
    └── tests/
        ├── test_distributed_debug.py
        ├── test_generator_workflow_modes.py
        └── test_integration_mode.py
```

Root-level integration test scripts (`test_integration_*.py`, `test_source_readiness_fix.py`) are standalone test runners that hit the live service.

## HTTP Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | API root — lists all endpoint paths |
| GET | `/health` | Health check → `{"status": "healthy"}` |
| GET | `/api/tools` | List all configured tools |
| GET | `/api/tools/<tool_id>` | Tool details |
| POST | `/api/tools/execute-project` | Execute a single tool on a project in the workspace |
| POST | `/api/generate` | Start async full-pipeline run; returns 202 Accepted |
| GET | `/asctg/components` | List ASCTG-compatible components |
| GET/POST | `/asctg/config`, `/asctg/execute` | ASCTG config and execution |
| POST | `/api/distributed-debug/start` | Start GDB debug compose stack |
| POST | `/api/distributed-debug/stop` | Stop debug stack |
| GET | `/api/distributed-debug/status` | Query running debug sessions |
| GET | `/api/distributed-debug/check-docker` | Verify Docker socket availability |
| GET | `/api/distributed-debug/admin/containers` | Admin: list all debug containers (internal only) |
| GET | `/api/distributed-debug/my-containers` | List current user's containers (internal only) |
| POST | `/api/backflow/scan` | Scan HARNESS workspace for returnable files |
| POST | `/api/backflow/patch` | Generate a unified diff patch from HARNESS edits |
| POST | `/api/backflow/apply` | Apply backflow patch to source of truth |

Internal-only endpoints (guarded by `X-Internal-Service: ecoa-backend` header) are called exclusively by the Spring Boot backend, never directly by the browser.

## Integration with sirius-web (Spring Boot)

**Environment variables** that must be set at container start:

| Variable | Purpose | Default in dev |
|----------|---------|----------------|
| `SIRIUS_WEB_URL` | Base URL of the Spring Boot backend | `http://host.docker.internal:8080` |
| `ECOA_WORKSPACE` | Root of the shared volume | `/workspace` |
| `ECOA_PROJECTS_BASE_DIR` | Where project dirs live (same as workspace) | `/workspace` |
| `ECOA_DISTRIBUTED_DEBUG_CLIENT_CONTAINER` | Docker container name for Code Server | `code-server` |

**Outbound call from ecoa-tools → Spring Boot:**

```
POST {SIRIUS_WEB_URL}/api/edt/ecoa/export-to-disk/{projectId}?workspaceId={workspaceId}
```

This asks Spring Boot to serialize the ECOA model XML into `/workspace/{projectId}/{workspaceId}/Steps/`.

**Inbound calls from Spring Boot → ecoa-tools** use `POST /api/generate` with a `callbackUrl` pointing back to:

```
{SIRIUS_WEB_URL}/api/internal/tasks/{taskId}/status
```

Progress payloads POSTed to `callbackUrl` have the shape:
```json
{
  "status": "GENERATING | FAILED | COMPLETED | AWAITING_CODE",
  "subStatus": "RUNNING_EXVT | RUNNING_MSCIGT | ...",
  "progress": 0-100,
  "logs": ["[PHASE][LEVEL] message", ...]
}
```

The `_run_pipeline` function in `routes/generator.py` runs in a background daemon thread so `/api/generate` returns 202 immediately.

## /workspace Mount Convention

The host directory `./workspace` (project root relative) is bind-mounted to `/workspace` in both the `ecoa-tools` and `code-server` containers.

Project workspace layout inside the container:
```
/workspace/
└── {projectId}/
    └── {workspaceId}/        ← task_id passed in the generate request
        └── Steps/
            ├── *.project.xml              ← exported ECOA model descriptor
            ├── 1-Types/, 2-ComponentDefinitions/, ...
            ├── 4-ComponentImplementations/  ← editable business code (HARNESS mode)
            └── 6-output/                  ← LDP generated output + build/
```

`ToolExecutor.execute_in_project()` resolves paths as:
```
{ECOA_PROJECTS_BASE_DIR}/{project_name}/{project_file}
```

where `project_name` is typically `{projectId}/{workspaceId}/Steps` for pipeline runs.

## Running Locally (without Docker)

```bash
# Prerequisites: Python 3.12+, ECOA CLI tools from as6-tools/
cd ecoa-tools
pip install -r requirements.txt
cd as6-tools
for pkg in ecoa-toolset ecoa-exvt ecoa-csmgvt ecoa-mscigt ecoa-asctg ecoa-ldp; do
  pip install -e "./$pkg"
done
cd ..

# Edit config.yaml: set projects_base_dir to your local ECOA projects path
# Set required env vars:
export SIRIUS_WEB_URL=http://localhost:8080
export ECOA_WORKSPACE=/path/to/workspace
export ECOA_PROJECTS_BASE_DIR=/path/to/workspace

python main.py
# API available at http://localhost:5000
```

## Running with Docker (dev mode)

```bash
# From the repository root (sirius-web-ecoa/)

# First build (or after changing Dockerfile, requirements.txt, or as6-tools/):
docker compose -f docker-compose.dev.yml build ecoa-tools

# Start the service:
docker compose -f docker-compose.dev.yml up -d ecoa-tools

# The app/ directory is bind-mounted, so changes to app/*.py take effect immediately
# without rebuilding — just restart the container:
docker compose -f docker-compose.dev.yml restart ecoa-tools

# Tail logs:
docker compose -f docker-compose.dev.yml logs -f ecoa-tools
```

**When to rebuild** (full `--build`):
- Changes to `Dockerfile`, `requirements.txt`, or `main.py`
- Changes to anything under `as6-tools/` (CLI tool code)

**No rebuild needed** (just restart):
- Changes under `app/` — it is bind-mounted at `/app/app` in dev compose

## Running Tests

```bash
# Unit / integration tests in app/tests/:
cd ecoa-tools
python -m pytest app/tests/ -v

# Run a specific test file:
python -m pytest app/tests/test_generator_workflow_modes.py -v

# Root-level integration tests (require a running service at localhost:5000):
python test_integration_core.py
python test_integration_in_container.py
```

## Workflow Modes

The `/api/generate` pipeline supports named `workflowMode` values that control which phases run and what happens between them:

| Mode | Description |
|------|-------------|
| `STANDARD` | Full pipeline: EXVT → MSCIGT → ASCTG → CSMGVT → LDP |
| `HARNESS` | Generate skeleton + ASCTG harness; pause for code editing; resume with CSMGVT/LDP |
| `HARNESS_DEV` | Like HARNESS but targeted at iterative development |
| `INTEGRATION` | Fetch component versions from Java backend, overlay onto MSCIGT skeletons, then compile |

`continuing: true` in the request body resumes a paused `HARNESS`/`HARNESS_DEV` workflow without re-exporting the model (preserving user edits in `4-ComponentImplementations/`).

## Key Design Patterns

- **ToolExecutor** (`services/executor.py`) is the single subprocess gateway. All CLI invocations go through it, never through ad-hoc `subprocess.run` calls in routes.
- **Callback logging** uses `[PHASE][LEVEL] message` format. Classify with `INFO`, `WARN`, `ERROR`, `SUCCESS`. The frontend renders these lines verbatim.
- **`config.yaml`** is the authoritative source for tool definitions and server settings. Runtime paths come from env vars (`ECOA_PROJECTS_BASE_DIR`, `SIRIUS_WEB_URL`, `ECOA_WORKSPACE`).
- **Internal-only routes** (`/api/distributed-debug/admin/...`, `/api/distributed-debug/my-containers`) require the header `X-Internal-Service: ecoa-backend` and are only called by the Spring Boot backend, never by the browser.
