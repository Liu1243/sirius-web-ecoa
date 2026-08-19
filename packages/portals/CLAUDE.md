# CLAUDE.md — packages/portals

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides portal (dashboard) representations for Sirius Web that allow embedding other representations side-by-side, including domain model, collaborative session handling, GraphQL API, and integration tests.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-portals` — Domain model and renderer; root Java package `org.eclipse.sirius.components.portals` (sub-packages: `renderer`, `description`)
- `sirius-components-portals-graphql` — GraphQL data fetchers and type definitions for portal representations
- `sirius-components-collaborative-portals` — Collaborative event handlers and message dispatching for live portal sessions
- `sirius-components-portals-tests` — Integration tests for the portals representation

### Frontend (`frontend/`)

- `sirius-components-portals` — npm package `@eclipse-sirius/sirius-components-portals`; React components for rendering portals

## How to Build

**Backend:**
```bash
cd packages/portals/backend
mvn clean compile
```

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-portals build
```
