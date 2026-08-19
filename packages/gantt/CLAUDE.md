# CLAUDE.md — packages/gantt

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides Gantt chart / project timeline representation for Sirius Web, including domain model, collaborative session handling, GraphQL API, and integration tests.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-gantt` — Domain model and renderer; root Java package `org.eclipse.sirius.components.gantt` (sub-packages: `renderer`, `description`)
- `sirius-components-gantt-graphql` — GraphQL data fetchers and type definitions for Gantt representations
- `sirius-components-collaborative-gantt` — Collaborative event handlers and message dispatching for live Gantt sessions
- `sirius-components-gantt-tests` — Integration tests for the Gantt representation

### Frontend (`frontend/`)

- `sirius-components-gantt` — npm package `@eclipse-sirius/sirius-components-gantt`; React components for rendering Gantt charts

## How to Build

**Backend:**
```bash
cd packages/gantt/backend
mvn clean compile
```

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-gantt build
```
