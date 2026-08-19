# CLAUDE.md — packages/view

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/view` implements the **Sirius View DSL** — the EMF-based domain-specific language that lets users define graphical and form representations declaratively. A "view" is a model containing one or more `RepresentationDescription` instances (diagram, form, table, deck, gantt, tree). The View DSL is the primary authoring format consumed by the Sirius Web rendering engine at runtime.

## Directory Layout

Only a `backend/` subdirectory exists; there is no frontend package here.

## Key Backend Submodules (27 total)

| Submodule | Role |
|---|---|
| `sirius-components-view` | Core EMF metamodel: `View`, `RepresentationDescription`, `Operation`, `LabelStyle`, color palette. Generated from `view.ecore`. |
| `sirius-components-view-edit` | EMF edit support (item providers) for `sirius-components-view`. |
| `sirius-components-view-emf` | Runtime bridge: interprets View models and wires them to the collaborative representation engine. |
| `sirius-components-view-emf-widget-reference` | View-EMF bridge for the reference widget. |
| `sirius-components-view-emf-widget-table` | View-EMF bridge for the table widget. |
| `sirius-components-view-builder` | Fluent Java builder API for constructing View models programmatically. |
| `sirius-components-view-builder-generator` | Code-generation tooling that produces the builder classes. |
| `sirius-components-view-diagram` | Diagram-specific View DSL extensions (`NodeDescription`, `EdgeDescription`, etc.). |
| `sirius-components-view-diagram-edit` | EMF edit support for diagram DSL. |
| `sirius-components-view-diagram-customnodes` | Custom node shape extensions for diagrams. |
| `sirius-components-view-diagram-customnodes-edit` | EMF edit support for custom nodes. |
| `sirius-components-view-form` | Form-specific View DSL extensions (`FormDescription`, widget descriptions). |
| `sirius-components-view-form-edit` | EMF edit support for form DSL. |
| `sirius-components-view-table` | Table-specific View DSL extensions. |
| `sirius-components-view-table-edit` | EMF edit support for table DSL. |
| `sirius-components-view-table-customcells` | Custom cell type extensions for tables. |
| `sirius-components-view-table-customcells-edit` | EMF edit support for custom cells. |
| `sirius-components-view-deck` | Deck (Kanban-style) View DSL extensions. |
| `sirius-components-view-deck-edit` | EMF edit support for deck DSL. |
| `sirius-components-view-gantt` | Gantt chart View DSL extensions. |
| `sirius-components-view-gantt-edit` | EMF edit support for gantt DSL. |
| `sirius-components-view-tree` | Tree representation View DSL extensions. |
| `sirius-components-view-tree-edit` | EMF edit support for tree DSL. |
| `sirius-components-widget-reference-view` | Standalone reference widget View model. |
| `sirius-components-widget-reference-view-edit` | EMF edit support for reference widget. |
| `sirius-components-widget-table-view` | Standalone table widget View model. |
| `sirius-components-widget-table-view-edit` | EMF edit support for table widget. |

The EMF metamodel source files (`.ecore`, `.genmodel`) live under each submodule's `src/main/resources/model/`.

## Dependencies

- **Depends on**: `packages/core/backend/` (sirius-components-core, sirius-components-representations), EMF (`org.eclipse.emf.ecore`, `org.eclipse.emf.common`).
- **Consumed by**: `packages/sirius-web/backend/` (the main application that registers View-based representation descriptions), `packages/formdescriptioneditors/backend/` (reads View form models to build the editor), the collaborative rendering engine in `packages/core/backend/`.

## How to Compile

Compile the entire view backend aggregate from the `backend/` directory:

```bash
cd packages/view/backend
mvn clean compile
```

To compile only a specific submodule and its transitive dependencies within this aggregate:

```bash
mvn clean compile -pl packages/view/backend/sirius-components-view -am
```

Do not run `mvn clean compile` from the repository root — it will attempt to build the entire monorepo.
