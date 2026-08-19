# CLAUDE.md — packages/sirius-web

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

---

## Module purpose

`packages/sirius-web` is the **application host** for the ECOA EDT tool. It wires together the upstream Sirius Web platform (diagram engine, EMF persistence, GraphQL layer) with ECOA-specific logic (model import/export, code generation orchestration, component code versioning). The Spring Boot main class `SiriusWeb.java` lives here; everything else is composed from downstream modules.

---

## Backend submodule responsibilities

| Module | Role |
|---|---|
| `sirius-web` | Spring Boot entry point only (`SiriusWeb.java`). No business logic. |
| `sirius-web-application` | REST and GraphQL controllers for projects, documents, editing context, diagrams, component code versions/tags (`componentcode`), capability, library, studio, undo/redo, omnibox, etc. All bounded-context application services live here. |
| `sirius-web-domain` | Domain layer: `auth` (AppUser, CurrentUserService) and `domain/boundedcontexts` (project search/creation services). Used by application and edt modules. |
| `sirius-web-edt` | ECOA EDT integration layer. Owns: generator REST endpoints (`generator/`), ECOA XML import/export (`importexport/`), diagram descriptions for composite / component-implementation / logical-system diagrams (`representations/`), detail form page descriptions (`views/details/`), explorer tree customisation (`views/explorer/`), project templates, omnibox commands, EMF/EMD configuration. |
| `sirius-web-infrastructure` | Spring Data JDBC repositories and Liquibase database migration changelogs (`db/changelog/`). All schema changes are made here. |
| `sirius-web-starter` | Dependency-aggregation module (no Java source currently). Pulling this as a Maven dependency gets a curated set of sirius-web features. |

Other backend modules present (`sirius-web-e2e-tests`, `sirius-web-papaya`, `sirius-web-tests`, `sirius-web-tests-data`, `sirius-web-view-fork`) are test support or downstream view prototypes; they are not part of the production runtime path.

---

## ECOA code generation task chain

The generation pipeline spans the Java backend and the Python `ecoa-tools` micro-service.

### 1. User entry point (frontend)

`GenerateEcoaDialog.tsx` (`views/edit-project/navbar/context-menu/`) lets the user choose:
- **Workflow mode**: `DIRECT_DEV`, `HARNESS_DEV`, or `INTEGRATION`
- **Phases** to run (e.g. EXVT, MSCIGT, ASCTG, CSMGVT, LDP)
- Component version selections (INTEGRATION mode)

### 2. Trigger (backend)

`POST /api/edt/ecoa/generate/{projectId}` → `EdtGeneratorController.triggerGeneration()`

The controller:
1. Creates a `GenerationTask` object (status `EXPORTING_XML`) with a random `taskId`.
   - Each new task gets its own `workspaceId` (defaults to `taskId`).
   - Persists the task to memory (`GenerationTaskStore`) **and** the database (`GenerationTaskJdbcRepository`).
2. Exports the EDT model as a ECOA XML ZIP via `EdtEcoaExportService.exportToZip()`, then unzips it into the Docker-shared volume at `/workspace/{projectId}/{workspaceId}/Steps/`.
3. Asynchronously calls `POST {ecoa.python.generator.url}/api/generate` (the `ecoa-tools` Python service) with a `PythonGenerateRequest` containing:
   - `stepsDir` = `/workspace/{projectId}/{workspaceId}/Steps`
   - `outputDir` = `/workspace/{projectId}/{workspaceId}/src`
   - `callbackUrl` = `{ecoa.backend.url}/api/internal/tasks/{taskId}/status`
   - `selectedPhases`, `workflowMode`, `continueOnError`, `phaseParams`
4. Returns HTTP 202 with the `taskId` immediately.

### 3. Python execution (ecoa-tools)

The Python service runs the requested phases in order:
- **EXVT** — validation of the ECOA Steps XML
- **MSCIGT** — module skeleton C code generation
- **ASCTG** — harness/adapter code generation (HARNESS_DEV only)
- **CSMGVT** — compilation and simulation verification
- **LDP** — link/deploy packaging

For each phase the service sends intermediate status updates (progress %, logs, sub-status) to the callback URL.

### 4. Callback (backend)

`POST /api/internal/tasks/{taskId}/status` → `EdtGeneratorController.receiveCallback()`

Updates the `GenerationTask` in memory and DB: `status`, `subStatus`, `progress`, `outputPath`, `logs`, `csmgvtResult`, `codeWorkspacePath`, etc.

### 5. Frontend polling

Frontend polls `GET /api/edt/ecoa/generate/status/{taskId}` until the task reaches a terminal or pause state.

### 6. AWAITING_CODE pause (DIRECT_DEV / HARNESS_DEV)

After MSCIGT the task enters `AWAITING_CODE` (also labelled `CODE_EDIT_REQUIRED`). The user edits generated business-logic skeletons in the Code Server IDE. When ready, the frontend posts to:

`POST /api/edt/ecoa/generate/continue/{taskId}` → `EdtGeneratorController.continueGeneration()`

This resets the same task record (`skipExport=true`) and re-calls the Python service for the remaining phases.

### 7. Re-run

`POST /api/edt/ecoa/generate/rerun/{taskId}` creates a new `GenerationTask` that **reuses the original `workspaceId`** directory, preserving previously persisted project files.

### 8. Code backflow

After generation completes, modified source files can be patched back to the source-of-truth repository via a three-step backflow:
- `POST /api/edt/ecoa/backflow/scan/{taskId}` — find changed files
- `POST /api/edt/ecoa/backflow/patch/{taskId}` — generate a diff patch
- `POST /api/edt/ecoa/backflow/apply/{taskId}` — apply the patch and optionally create `ComponentCodeVersion` records

### Workflow mode summary

| Mode | Phases | When to use |
|---|---|---|
| `DIRECT_DEV` | EXVT → MSCIGT → (await code) → CSMGVT → LDP | New component development |
| `HARNESS_DEV` | EXVT → ASCTG → MSCIGT → (await code) → CSMGVT → LDP | Component with test harness |
| `INTEGRATION` | EXVT → CSMGVT → LDP | System integration, source must already be ready |

### Key classes in `sirius-web-edt/generator/`

| Class | Role |
|---|---|
| `EdtGeneratorController` | All REST endpoints for generation lifecycle + backflow |
| `GenerationTask` | In-memory task state machine (status, subStatus, logs, progress, workspaceId) |
| `GenerationTaskStore` | Thread-safe in-memory map; fast polling cache |
| `GenerationTaskJdbcRepository` | Spring Data JDBC persistence of task records |
| `GenerationTaskDbInitializer` | Schema initialisation hook for the tasks table |
| `GenerationWorkflowMode` | Enum: `DIRECT_DEV`, `HARNESS_DEV`, `INTEGRATION` |
| `GenerationWorkflowRules` | Validates phase lists and resolves modes (maps legacy `HARNESS` → `HARNESS_DEV`) |
| `DistributedDebugController` | Debug/diagnostic REST endpoint for workspace inspection |

---

## Frontend view responsibilities

All views are under `packages/sirius-web/frontend/sirius-web-application/src/views/`.

| View directory | Purpose |
|---|---|
| `edit-project/` | Main modelling workbench. Contains `EditProjectView`, the navbar with `GenerateEcoaNavbarAction`, and all context-menu items: generate ECOA (`GenerateEcoaDialog`), generation history (`GenerationHistoryDialog`), code backflow (`CodeBackflowDialog`), component version selection (`ComponentVersionSelectionDialog`), import/export ECOA steps, project rename/delete/share/settings. |
| `project-browser/` | Home page listing all accessible projects. |
| `library-browser/` | Browse and open published libraries. |
| `new-project/` | Wizard to create a new project (blank or from template). |
| `upload-project/` | Upload an existing Sirius Web project archive. |
| `project-settings/` | Per-project settings panel. |
| `display-library/` | Read-only viewer for a library's representations. |
| `error/` | Generic error/not-found page. |

The frontend ECOA package `sirius-web-edt` (`packages/sirius-web/frontend/sirius-web-edt/`) contributes custom diagram tools and workbench views (node actions, custom diagram descriptions, EdtExtensionRegistry).

---

## Compilation / hot reload

### Classloader architecture

`spring-boot-devtools` uses a **dual classloader**:

| Classloader | Loads from | What's in it |
|---|---|---|
| **base classloader** | `~/.m2` JAR files | `packages/core/`, `packages/edt/`, third-party libs |
| **restart classloader** | `target/classes/` directories | `packages/sirius-web/backend/*` modules |

The restart classloader considers `sirius-web-domain`, `sirius-web-application`, `sirius-web-edt`, and `sirius-web-infrastructure` as **one reactor** — their `target/classes/` directories are on the same classpath. These modules can hot-reload via DevTools auto-restart after `mvn compile`.

Modules under `packages/core/` and `packages/edt/` are loaded as JARs from `~/.m2` by the base classloader. Changes there are invisible until you `install` and **manually restart** the app.

### Quick reference

| Where you changed | Command | Restart |
|---|---|---|
| `sirius-web-edt/` (existing imports only) | `mvn compile -pl sirius-web-edt -am` | DevTools auto |
| `sirius-web-edt/` (added new import from `sirius-web-application`) | `mvn compile -pl sirius-web-application,sirius-web-edt -am` | DevTools auto |
| `sirius-web-application/` | `mvn compile -pl sirius-web-application -am` | DevTools auto |
| `sirius-web-domain/` | `mvn compile -pl sirius-web-domain -am` | DevTools auto |
| `sirius-web-infrastructure/` | `mvn compile -pl sirius-web-infrastructure -am` | DevTools auto |
| `packages/core/` or `packages/edt/` | `mvn clean compile install -pl <module> -am` then recompile `sirius-web-*` | **Manual restart** |

### Why "new import from sirius-web-application" matters

When `sirius-web-edt` adds a **new** reference to a class in `sirius-web-application` (new `import`, new constructor parameter, etc.), DevTools recompiles `sirius-web-edt` but the restart classloader may fail to resolve the newly-referenced class across module boundaries. Compiling both modules together (`sirius-web-application,sirius-web-edt`) keeps the classpath coherent.

**Error signature** (if you hit this):
```
NoClassDefFoundError: SomeClass
... RestartClassLoader.loadClass ...
```

### ⚠️ `mvn clean` safety rule

After `mvn clean`, DevTools auto-restart is unreliable — the restart classloader may hold stale references to classes that were deleted and rebuilt. After ANY `mvn clean compile`, **always manually restart** the Spring Boot application. Do not rely on DevTools to pick up the change.

For incremental changes that don't require `clean`, DevTools auto-restart works fine.

### `packages/core/` or `packages/edt/` changes

These modules reach `sirius-web` as JAR dependencies via `~/.m2`. The base classloader only sees the JAR — never `target/classes/`. After editing:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/17.0.19-amzn && export PATH=$JAVA_HOME/bin:$PATH

# 1. Install updated JAR to ~/.m2
cd packages/core/backend   # or packages/edt/backend
mvn clean compile install -pl <module-name> -am -DskipTests

# 2. Recompile downstream sirius-web modules
cd packages/sirius-web/backend
mvn compile -pl sirius-web-application,sirius-web-edt -am -DskipTests

# 3. Manual restart required — DevTools cannot hot-reload base classloader JARs
```

---

## Database schema changes (Liquibase)

All schema changes must be added as Liquibase changeset XML files under:

```
packages/sirius-web/backend/sirius-web-infrastructure/src/main/resources/db/changelog/
```

Versioned subdirectories mirror the release cycle: `2024.3/`, `2024.5/`, `2024.11/`, `2025.1/`, `2025.2/`, `2025.4/`, `2025.10/`, `2026.1/`, `2026.2/`.

To add a new change:
1. Create (or append to) a `.xml` file inside the **current version's** subdirectory (e.g. `2026.2/03-my-change.xml`).
2. Include the file reference in the corresponding master changelog file (e.g. `2026.2/2026.2.0.xml`).
3. Compile `sirius-web-infrastructure` and restart to apply.

The `component_code_version` and `component_code_tag` tables were introduced in `2026.2/02-add-component-code-tables.xml`.

---

## Key application properties (generation)

The following Spring Boot properties govern the generation pipeline:

| Property | Purpose |
|---|---|
| `ecoa.python.generator.url` | Base URL of the Python `ecoa-tools` generator micro-service |
| `ecoa.workspace.dir` | Host-side path of the shared workspace volume (mapped into containers) |
| `ecoa.backend.url` | Public URL of this backend, used to construct the Python callback URL |
