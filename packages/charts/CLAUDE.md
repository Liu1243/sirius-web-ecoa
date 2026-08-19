# CLAUDE.md — packages/charts

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides bar chart, pie chart, and hierarchy chart representation support for Sirius Web, including the domain model, collaborative session handling, and GraphQL API.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-charts` — Domain model and renderer; root Java package `org.eclipse.sirius.components.charts` (sub-packages: `barchart`, `piechart`, `hierarchy`, `descriptions`)
- `sirius-components-charts-graphql` — GraphQL data fetchers and type definitions for chart representations
- `sirius-components-collaborative-charts` — Collaborative event handlers and message dispatching for live chart sessions

### Frontend (`frontend/`)

- `sirius-components-charts` — npm package `@eclipse-sirius/sirius-components-charts`; React components for rendering charts

## How to Build

**Backend** (compile only the charts module):
```bash
cd packages/charts/backend
mvn clean compile
```

**Frontend** (from repo root, using the workspace):
```bash
yarn workspace @eclipse-sirius/sirius-components-charts build
```
