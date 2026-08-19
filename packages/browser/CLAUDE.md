# CLAUDE.md — packages/browser

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/browser` implements the **Model Browser** — a tree-based panel that allows users to browse the semantic model elements of a project and pick an element (e.g., for a reference widget value or a drag-and-drop target). It exposes a subscription-driven GraphQL API and a React frontend component.

## Directory Layout

```
packages/browser/
  backend/
    sirius-components-browser-graphql/          GraphQL subscription data fetcher
    sirius-components-collaborative-browser/    Collaborative session event processor
  frontend/
    sirius-components-browser/
      src/
        index.ts                                Public API re-exports
        modelbrowser/                           React components and hooks
```

## Key Backend Submodules

### `sirius-components-collaborative-browser`

Contains the collaborative infrastructure for model browser sessions:
- `ModelBrowserEventProcessorFactory` — creates event processors for browser sessions.
- `ComposedModelBrowserTreeDescriptionIdProvider` — aggregates `IModelBrowserTreeDescriptionIdProviderDelegate` implementations to resolve which tree description to use for a given context.
- `IModelBrowserRootCandidateSearchProvider` — SPI for supplying root candidates to the browser tree.

### `sirius-components-browser-graphql`

Exposes the model browser over GraphQL:
- `SubscriptionModelBrowserEventDataFetcher` — data fetcher for the `modelBrowserEvent` subscription.
- A `graphql/` resource directory contains the corresponding `.graphqls` schema fragment.

## Key Frontend Files

Under `frontend/sirius-components-browser/src/modelbrowser/`:
- `ModelBrowserTreeView.tsx` — the rendered tree panel component.
- `ModelBrowserTreeView.types.ts` — TypeScript prop/state types.
- `useModelBrowserSubscription.tsx` — custom hook that subscribes to `modelBrowserEvent` via GraphQL WebSocket.
- `useModelBrowserSubscription.types.ts` — types for the subscription hook.

## Dependencies

- **Backend depends on**: `packages/core/backend/` (tree representation, collaborative infrastructure, GraphQL wiring).
- **Frontend depends on**: `sirius-components-trees` (tree rendering primitives), `@apollo/client` (GraphQL subscription).
- **Consumed by**: `packages/sirius-web/` (registers the browser panel in the workbench), form widgets that open a model picker (e.g., the reference widget in `packages/view/backend/sirius-components-view-emf-widget-reference`).

## How to Compile

```bash
cd packages/browser/backend
mvn clean compile
```

Frontend changes do not require a Maven build; refresh the browser in development mode.
