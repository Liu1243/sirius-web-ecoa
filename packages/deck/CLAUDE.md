# CLAUDE.md — packages/deck

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides a Kanban-style deck (card board) representation for Sirius Web, including the domain model, collaborative session handling, and GraphQL API.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-deck` — Domain model and renderer; root Java package `org.eclipse.sirius.components.deck` (sub-packages: `renderer`, `description`)
- `sirius-components-deck-graphql` — GraphQL data fetchers and type definitions for deck representations
- `sirius-components-collaborative-deck` — Collaborative event handlers and message dispatching for live deck sessions

### Frontend (`frontend/`)

- `sirius-components-deck` — npm package `@eclipse-sirius/sirius-components-deck`; React components for rendering deck/card boards

## How to Build

**Backend:**
```bash
cd packages/deck/backend
mvn clean compile
```

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-deck build
```
