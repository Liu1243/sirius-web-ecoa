# CLAUDE.md — packages/datatree

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides a generic data tree representation (distinct from the explorer tree) for Sirius Web, exposing hierarchical data as a scrollable tree widget.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-datatree` — Domain model and tree node types; root Java package `org.eclipse.sirius.components.datatree`

### Frontend (`frontend/`)

- `sirius-components-datatree` — npm package `@eclipse-sirius/sirius-components-datatree`; React components for rendering data trees

## How to Build

**Backend:**
```bash
cd packages/datatree/backend
mvn clean compile
```

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-datatree build
```
