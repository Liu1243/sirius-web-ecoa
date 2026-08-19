# CLAUDE.md — packages/validation

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides model validation representation for Sirius Web, surfacing validation rules and markers in the UI, including renderer, collaborative handling, and GraphQL API.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-validation` — Domain model and renderer; root Java package `org.eclipse.sirius.components.validation` (sub-packages: `render`, `components`, `elements`, `description`)
- `sirius-components-validation-graphql` — GraphQL data fetchers and type definitions for validation representations
- `sirius-components-collaborative-validation` — Collaborative event handlers and message dispatching for live validation sessions

### Frontend (`frontend/`)

- `sirius-components-validation` — npm package `@eclipse-sirius/sirius-components-validation`; React components for displaying validation results

## How to Build

**Backend:**
```bash
cd packages/validation/backend
mvn clean compile
```

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-validation build
```
