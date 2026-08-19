# CLAUDE.md — packages/selection

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides a modal selection dialog representation for Sirius Web, used to let users pick model elements from a filtered list, including domain model, collaborative handling, and GraphQL API.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-selection` — Domain model; root Java package `org.eclipse.sirius.components.selection` (sub-packages: `description`)
- `sirius-components-selection-graphql` — GraphQL data fetchers and type definitions for selection representations
- `sirius-components-collaborative-selection` — Collaborative event handlers and message dispatching for live selection sessions

### Frontend (`frontend/`)

- `sirius-components-selection` — npm package `@eclipse-sirius/sirius-components-selection`; React components for the selection dialog

## How to Build

**Backend:**
```bash
cd packages/selection/backend
mvn clean compile
```

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-selection build
```
