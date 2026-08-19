# CLAUDE.md — packages/palette

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides the contextual tool palette UI component for Sirius Web diagrams (frontend only), allowing users to select tools and actions from a floating palette near selected elements.

## Sub-modules

### Frontend (`frontend/`)

- `sirius-components-palette` — npm package `@eclipse-sirius/sirius-components-palette`; React components implementing the diagram tool palette

There is no backend sub-module in this package; palette behavior is driven entirely from the frontend.

## How to Build

**Frontend:**
```bash
yarn workspace @eclipse-sirius/sirius-components-palette build
```
