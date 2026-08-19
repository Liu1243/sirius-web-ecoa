# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a **monorepo** for an ECOA-extended fork of Eclipse Sirius Web. It combines:

- **Spring Boot backend** modules under `packages/*/backend/` — each is an independent Maven artifact.
- **React/TypeScript frontend** packages under `packages/*/frontend/` — managed via npm workspaces + TurboRepo.
- **Python ECOA tools** in `ecoa-tools/` — a Flask service that wraps ECOA toolchain binaries (exvt, mscigt, asctg, csmgvt, ldp).
- **PostgreSQL** managed via Liquibase changelogs in `packages/sirius-web/backend/sirius-web-infrastructure/src/main/resources/db/changelog/`.

### Key packages

| Package | Purpose |
|---|---|
| `packages/sirius-web/backend/sirius-web-edt` | ECOA-specific backend: code generation tasks, distributed debug controller, EDT representations |
| `packages/sirius-web/backend/sirius-web-application` | Core application services, REST/GraphQL controllers |
| `packages/sirius-web/backend/sirius-web-infrastructure` | DB changelogs (Liquibase), infrastructure config |
| `packages/sirius-web/backend/sirius-web` | Spring Boot entry point (`SiriusWeb.java`) |
| `packages/sirius-web/frontend/sirius-web-application` | Main React app: views (project browser, edit-project, library browser, new-project, upload-project) |
| `packages/core/backend/sirius-components-collaborative` | GraphQL schema root (`core.graphqls`) — `Viewer`, `EditingContext`, `Representation` |
| `ecoa-tools/` | Python Flask service on port 5000; runs inside Docker via `docker-compose.dev.yml` |

### GraphQL + Subscription model

The API is GraphQL-first. Core schema is in `packages/core/backend/sirius-components-collaborative/src/main/resources/schema/core.graphqls`. Frontend uses `useViewer` hooks (`ViewerContext.tsx`, `useViewer.fragment.ts`) for the authenticated viewer.

### ECOA Generation pipeline

`EdtGeneratorController.java` and `DistributedDebugController.java` in `sirius-web-edt/generator/` proxy requests to the Python `ecoa-tools` service (`ecoa.python.generator.url` env var). The `GenerationTask` / `GenerationTaskJdbcRepository` track generation state in PostgreSQL.

---

## Development Commands

### Java version requirement

**Required: Java 17.** The codebase uses Java 15+ text block syntax (`"""`). The default shell `java` may point to Java 8 (via sdkman), which will produce `需要';'` / `未结束的字符串文字` compile errors.

Always prefix Maven commands with the Java 17 environment:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/17.0.19-amzn
export PATH=$JAVA_HOME/bin:$PATH
```

Or set it permanently:
```bash
sdk use java 17.0.19-amzn
```

### Backend — compile a single module (required after every Java change)

```bash
# Compile only the changed module (fastest) — must use Java 17
export JAVA_HOME=~/.sdkman/candidates/java/17.0.19-amzn && export PATH=$JAVA_HOME/bin:$PATH
cd packages/sirius-web/backend/sirius-web-edt
mvn clean compile

# Or from repo root
export JAVA_HOME=~/.sdkman/candidates/java/17.0.19-amzn && export PATH=$JAVA_HOME/bin:$PATH
mvn clean compile -pl packages/sirius-web/backend/sirius-web-edt -am
```

Never run `mvn clean compile` from the repo root — it compiles all modules and takes very long.

### Frontend — build and watch

```bash
# Build all frontend packages once
npm run build

# Watch mode (all packages, for dev)
npm run start

# Build a single package
cd packages/sirius-web/frontend/sirius-web-application
npx turbo run build

# Run frontend tests
npm run test
```

Requires Node 22.16.0 and npm 10.9.2.

### Dev environment (Docker)

> **Platform**: `linux/arm64` (Apple Silicon). Configured in `.env` as `DOCKER_PLATFORM=linux/arm64`.

**⚠️ Do NOT use `docker compose up -d --build` directly.**
`code-server`'s `Dockerfile.code-server` uses `ecoa-tools` as its `FROM` base image. Docker Compose builds images concurrently, so `code-server` may start building before `ecoa-tools` is ready, causing a "failed to resolve source metadata" error.

Use the `Makefile` at the repo root which enforces the correct build order:

```bash
# Build images in correct order (ecoa-tools first, then code-server) and start all services
make up

# Build images only (without starting)
make build

# Force rebuild with --no-cache, then start
make rebuild

# Stop and remove containers (volumes preserved)
make down

# Check service status
make ps

# Tail logs
make logs

# Stop containers and delete all volumes (⚠️ destroys DB data)
make clean
```

**Manual equivalent** (if not using `make`):
```bash
# Step 1: build ecoa-tools first
docker compose -f docker-compose.dev.yml build ecoa-tools

# Step 2: then build code-server and start everything
docker compose -f docker-compose.dev.yml up -d --build
```

**Rebuild only ecoa-tools** after Python/Dockerfile changes:
```bash
docker compose -f docker-compose.dev.yml build ecoa-tools
docker compose -f docker-compose.dev.yml up -d ecoa-tools
```

The backend Spring Boot app runs on the host (IDEA/mvn spring-boot:run) at port 8080; `ecoa-tools` Flask service runs at port 5000; code-server at port 8443.

### Integration tests (Playwright)

```bash
cd integration-tests-playwright
npm install
npx playwright test
```

---

## Development Rules

### Backend changes
After **every Java file change**, compile the affected module. No restart required — a compile is sufficient.

```bash
# Must set Java 17 first (default shell may use Java 8 via sdkman)
export JAVA_HOME=~/.sdkman/candidates/java/17.0.19-amzn && export PATH=$JAVA_HOME/bin:$PATH

# Compile the most commonly edited module (sirius-web-edt)
cd packages/sirius-web/backend/sirius-web-edt && mvn clean compile
```

Common module paths to compile:
| Changed file location | Module to compile |
|---|---|
| `packages/sirius-web/backend/sirius-web-edt/` | `packages/sirius-web/backend/sirius-web-edt` |
| `packages/sirius-web/backend/sirius-web-application/` | `packages/sirius-web/backend/sirius-web-application` |
| `packages/edt/backend/sirius-components-edt/` | `packages/edt/backend/sirius-components-edt` |

### ⚠️ `packages/core/` 和 `packages/edt/` 修改（DevTools 陷阱）

`packages/core/backend/` 和 `packages/edt/backend/` 下的模块被 `sirius-web` 作为 **JAR 依赖**引用（通过 `~/.m2`），不是 project module 直接引用。

`spring-boot-devtools` 使用双类加载器：base classloader 加载 `~/.m2` 的 JAR，restart classloader 加载 `target/classes`。修改这些模块后只 `compile` 不 `install`，会导致 base classloader 拿到的 JAR 是旧版本，而 restart classloader 中有新 `.class`，两者不一致，抛 `ClassNotFoundException` 或 `NoClassDefFoundError`（堆栈中可见 `RestartClassLoader`）。

**修改 `packages/core/` 或 `packages/edt/` 下 Java 文件后的正确流程**：

```bash
export JAVA_HOME=~/.sdkman/candidates/java/17.0.19-amzn && export PATH=$JAVA_HOME/bin:$PATH

# 1. 修改的上游模块必须 compile + install（将新 JAR 写入 ~/.m2）
cd packages/core/backend   # 或 packages/edt/backend
mvn clean compile install -pl <module-name> -am -DskipTests

# 2. 重新编译下游引用模块
cd packages/sirius-web/backend
mvn clean compile -pl sirius-web-application -am -DskipTests

# 3. 重启 Spring Boot 应用
```

> 只改 `packages/sirius-web/backend/` 下的模块不需要 install，因为它们是最终应用的组成部分，不通过 `~/.m2` JAR 引用。

### Frontend changes
No compilation needed in dev mode — refresh the browser. For production, run `npm run build`.

### ecoa-tools changes — two paths, two rules

| Changed path | Requires rebuild? | Action |
|---|---|---|
| `ecoa-tools/app/` | **No** — volume-mounted at `/app/app` | Restart container only |
| `ecoa-tools/as6-tools/` | **Yes** — installed as Python packages during Docker build | **Run `make rebuild`** |
| `ecoa-tools/Dockerfile`, `requirements.txt` | **Yes** | **Run `make rebuild`** |

**`ecoa-tools/app/` changes** (Flask routes, services, utils — takes effect immediately):
```bash
docker compose -f docker-compose.dev.yml restart ecoa-tools
```

**`ecoa-tools/as6-tools/` changes** (ecoa-exvt, ecoa-ldp, ecoa-mscigt, ecoa-asctg, ecoa-csmgvt source):
```bash
# Automatically run after every as6-tools/ edit:
make rebuild
```

> **IMPORTANT for Claude Code**: After modifying ANY file under `ecoa-tools/as6-tools/`, automatically run `make rebuild` from the repo root **without waiting for the user to ask**. The as6-tools packages are installed into the Docker image via pip — changes are invisible to the running container until the image is rebuilt.

### Database schema changes
Add a new Liquibase XML file in `packages/sirius-web/backend/sirius-web-infrastructure/src/main/resources/db/changelog/<year.release>/`. The app applies pending changesets on startup.

---

*Maintained by Claude Code — follow the rules above for development.*

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **sirius-web-ecoa** (129743 symbols, 368431 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/sirius-web-ecoa/context` | Codebase overview, check index freshness |
| `gitnexus://repo/sirius-web-ecoa/clusters` | All functional areas |
| `gitnexus://repo/sirius-web-ecoa/processes` | All execution flows |
| `gitnexus://repo/sirius-web-ecoa/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
