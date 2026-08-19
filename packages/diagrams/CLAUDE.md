# CLAUDE.md — packages/diagrams

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

The `diagrams` package implements the **diagram representation type** — the graphical node-and-edge canvas used to visualize and edit models as 2-D diagrams. It covers the complete stack from the server-side diagram domain model and collaborative event processing to the React renderer that runs in the browser.

---

## Backend Layers

All backend submodules live under `packages/diagrams/backend/`.

### `sirius-components-diagrams` — Domain Model

The pure Java domain with no Spring or GraphQL dependencies. Defines all diagram value objects:

- **`Diagram.java`** — root representation object
- **`Node.java`**, **`Edge.java`** — graph elements; both implement `IDiagramElement`
- **`InsideLabel.java`**, **`OutsideLabel.java`**, **`Label.java`** — label variants
- **`INodeStyle`** — sealed interface for node visual styles (`RectangularNodeStyle`, `ImageNodeStyle`, `IconLabelNodeStyle`, …)
- **`ILayoutStrategy`** — `FreeFormLayoutStrategy`, `ListLayoutStrategy`, etc.
- **`description/`** — `DiagramDescription`, `NodeDescription`, `EdgeDescription`, and `IDiagramElementDescription` used by the renderer
- **`events/`** — `IDiagramEvent` and subtypes that represent user or tool-triggered mutations
- **`renderer/`** — `DiagramRenderer` and helper interfaces (`INodeAppearanceHandler`, `IEdgeAppearanceHandler`)
- **`components/`** — `IDiagramElementRequestor`, `INodesRequestor`, `IEdgesRequestor`, `INodeDescriptionRequestor`

### `sirius-components-diagrams-graphql` — GraphQL Data Fetchers

Spring beans that wire the GraphQL schema to the domain. Notable files:

- `DiagramNodesDataFetcher`, `DiagramEdgesDataFetcher`, `DiagramLayoutDataDataFetcher` — field resolvers on `Diagram`
- `DiagramDescriptionPaletteDataFetcher`, `DiagramDescriptionConnectorToolsDataFetcher`, `DiagramDescriptionActionsDataFetcher` — palette and tool resolvers
- `DiagramRefreshedEventPayloadDiagramDataFetcher` — resolver for the subscription payload
- Various `MutationXxxDataFetcher` classes — mutation resolvers (drop, arrange, delete, appearance changes, etc.)

### `sirius-components-collaborative-diagrams` — Event Processor & Subscriptions

Manages the collaborative session for a single diagram representation:

- **`DiagramEventProcessor`** / **`DiagramEventProcessorFactory`** — per-representation WebSocket session that receives `IDiagramEvent` inputs and pushes refreshed `Diagram` payloads
- **`DiagramCreationService`**, **`DiagramDescriptionService`** — lifecycle and description lookup
- **Event handlers** — one class per user action (`ArrangeAllEventHandler`, `DeleteFromDiagramEventHandler`, `ReconnectEdgeEventHandler`, appearance handlers, etc.)
- **`DiagramContext`** — mutable state passed through all handlers in one event cycle
- Owns the **GraphQL schemas** (see below)

### `sirius-components-diagrams-tests` — Integration Tests

Architecture compliance and integration tests. Does not contain domain logic.

---

## Frontend

Single npm package under `packages/diagrams/frontend/sirius-components-diagrams/`.

| Field | Value |
|---|---|
| npm name | `@eclipse-sirius/sirius-components-diagrams` |
| Entry point | `src/index.ts` |

### `src/` layout

| Directory | Contents |
|---|---|
| `renderer/` | React Flow–based canvas; node and edge renderers |
| `representation/` | Top-level `DiagramRepresentation` component wiring context + renderer |
| `contexts/` | React context for diagram state and palette |
| `graphql/` | GraphQL fragment and subscription definitions (`.ts` query files) |
| `converter/` | Transforms raw GQL subscription data into renderer-ready objects |
| `dialog/` | Dialogs triggered from palette actions |
| `icons/` | Diagram-specific SVG icon components |

---

## GraphQL Schema

Both schema files are owned by `sirius-components-collaborative-diagrams`:

```
packages/diagrams/backend/sirius-components-collaborative-diagrams/src/main/resources/schema/diagram.graphqls
packages/diagrams/backend/sirius-components-collaborative-diagrams/src/main/resources/schema/appearance.graphqls
```

`diagram.graphqls` defines the `Diagram`, `Node`, `Edge`, subscription `diagramEvent`, and all diagram mutations.  
`appearance.graphqls` defines appearance-change input types and the `updateAppearance` mutation group.

---

## How to Compile

Compile only the submodule(s) you modified:

```bash
# Domain model only
cd packages/diagrams/backend/sirius-components-diagrams
mvn clean compile

# GraphQL data fetchers only
cd packages/diagrams/backend/sirius-components-diagrams-graphql
mvn clean compile

# Collaborative / event processor only
cd packages/diagrams/backend/sirius-components-collaborative-diagrams
mvn clean compile
```

To compile all diagrams backend submodules together from the repo root:

```bash
mvn clean compile -pl packages/diagrams/backend/sirius-components-diagrams,packages/diagrams/backend/sirius-components-diagrams-graphql,packages/diagrams/backend/sirius-components-collaborative-diagrams -am
```

Frontend changes do not require Maven; refresh the browser in dev mode.
