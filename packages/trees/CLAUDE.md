# CLAUDE.md — packages/trees

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

The `trees` package implements the **tree representation type** — a hierarchical, expandable list used to navigate and manipulate model element hierarchies (e.g., the model explorer panel). It handles item expansion/collapse, inline renaming, drag-and-drop reordering, and context-menu actions.

---

## Backend Layers

All backend submodules live under `packages/trees/backend/`.

### `sirius-components-trees` — Domain Model

Pure Java domain for the tree representation:

- **`Tree.java`** — root representation object; contains a flat list of `TreeItem`
- **`TreeItem.java`** — a single row: id, label, icon URLs, `expanded` flag, `deletable`/`renamable`/`droppable` flags, and children ids
- **`FetchTreeItemContextMenuEntryKind`** — enum for context-menu entry kinds
- **`description/TreeDescription`** — descriptor used by the renderer to compute items from an editing context
- **`renderer/TreeRenderer`** — walks a `TreeDescription` and produces a `Tree` value object

### `sirius-components-trees-graphql` — GraphQL Data Fetchers

Spring beans that wire the GraphQL schema to the domain:

- **`SubscriptionTreeEventDataFetcher`** — WebSocket subscription entry point
- **`TreeRefreshedEventPayloadTreeDataFetcher`** — subscription payload resolver
- **`TreeItemIconURLDataFetcher`**, **`TreeItemInitialDirectEditLabelDataFetcher`** — field resolvers on `TreeItem`
- **`EditingContextTreePathDataFetcher`**, **`EditingContextExpandAllTreePathDataFetcher`** — query resolvers for computing expand paths
- **`TreeDescriptionContextMenuDataFetcher`**, **`TreeDescriptionFiltersDataFetcher`**, **`TreeDescriptionFetchTreeItemContextMenuEntryDataDataFetcher`** — description resolvers
- **`MutationDeleteTreeItemDataFetcher`**, **`MutationRenameTreeItemDataFetcher`**, **`MutationDropTreeItemDataFetcher`**, **`MutationInvokeSingleClickTreeItemContextMenuEntryDataFetcher`** — mutation resolvers

### `sirius-components-collaborative-trees` — Event Processor & Subscriptions

Manages the collaborative session for a tree representation:

- **`CreateTreeEventHandler`** — lifecycle handler for initial creation
- **`DeleteTreeItemEventHandler`** — handles delete-item mutations; delegates via `IDeleteTreeItemHandler` extension point
- **`DropTreeItemEventHandler`** — handles drag-and-drop; delegates via `IDropTreeItemHandler`
- **`ExpandAllTreePathEventHandler`** — computes the full expansion path; delegates via `IExpandAllTreePathProvider`
- **`FetchTreeItemContextMenuEntryDataEventHandler`** / **`FetchTreeItemContextMenuEntry`** — fetches dynamic context-menu entry data
- **`DefaultExpandAllTreePathHandler`** — default expansion strategy
- Extension-point interfaces: `IDeleteTreeItemHandler`, `IDropTreeItemHandler`, `IExpandAllTreePathProvider`, `ICollaborativeTreeMessageService`
- Owns the **`tree.graphqls`** schema (see below)

### `sirius-components-trees-tests` — Integration Tests

Architecture compliance and integration tests. Does not contain domain logic.

---

## Frontend

Single npm package under `packages/trees/frontend/sirius-components-trees/`.

| Field | Value |
|---|---|
| npm name | `@eclipse-sirius/sirius-components-trees` |
| Entry point | `src/index.ts` |

### `src/` layout

| Directory | Contents |
|---|---|
| `trees/` | Root `TreeRepresentation` component, WebSocket subscription wiring |
| `treeitems/` | `TreeItem` row component; handles expand/collapse, inline rename, drag handle |
| `toolbar/` | Filter input and toolbar controls rendered above the tree |
| `views/` | `TreeView` wrapper used by host applications (e.g., explorer panel) |

---

## GraphQL Schema

```
packages/trees/backend/sirius-components-collaborative-trees/src/main/resources/schema/tree.graphqls
```

Defines `Tree`, `TreeItem`, the `treeEvent` subscription, `TreePath` query, and mutations: `deleteTreeItem`, `renameTreeItem`, `dropTreeItem`, `invokeSingleClickTreeItemContextMenuEntry`.

---

## How to Compile

```bash
# Domain model only
cd packages/trees/backend/sirius-components-trees
mvn clean compile

# GraphQL data fetchers only
cd packages/trees/backend/sirius-components-trees-graphql
mvn clean compile

# Collaborative / event processor only
cd packages/trees/backend/sirius-components-collaborative-trees
mvn clean compile
```

To compile all trees backend submodules together from the repo root:

```bash
mvn clean compile -pl packages/trees/backend/sirius-components-trees,packages/trees/backend/sirius-components-trees-graphql,packages/trees/backend/sirius-components-collaborative-trees -am
```

Frontend changes do not require Maven; refresh the browser in dev mode.
