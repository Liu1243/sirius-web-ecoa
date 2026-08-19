# CLAUDE.md — packages/releng

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Release engineering support package: provides the parent POM, shared code-style/editor config resources, test-coverage aggregation, and the shared TypeScript base configuration used across all frontend packages.

## Sub-modules

### Backend (`backend/`)

- `sirius-web-parent` — Root Spring Boot parent POM (`org.eclipse.sirius:sirius-web-parent`); defines dependency management and build plugins for all backend modules
- `sirius-components-resources` — Shared IDE resources: Checkstyle rules, editor configs, and code-style assets (no Java sources)
- `sirius-components-test-coverage` — JaCoCo test-coverage aggregation POM; collects coverage reports from all backend modules

### Frontend (`frontend/`)

- `sirius-components-tsconfig` — npm package `@eclipse-sirius/sirius-components-tsconfig`; shared `tsconfig.json` base configurations inherited by all frontend packages

## How to Build

**Backend (parent POM install — do this before building other modules after version changes):**
```bash
cd packages/releng/backend/sirius-web-parent
mvn clean install -N
```
