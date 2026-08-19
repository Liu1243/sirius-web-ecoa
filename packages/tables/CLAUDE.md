# CLAUDE.md — packages/tables

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

The `tables` package implements the **table representation type** — a grid view that maps model elements to rows and columns with inline-editable cells. It supports multiple cell types (textfield, textarea, dropdown, multi-select, icon+label), column sorting/filtering/resizing/reordering, row resizing, pagination, and global filter.

---

## Backend Layers

All backend submodules live under `packages/tables/backend/`.

### `sirius-components-tables` — Domain Model

Pure Java domain for the table representation:

- **`Table.java`** — root representation; contains `Column` list, `Line` list, and `PaginationData`
- **`Column.java`** — column header with filter (`ColumnFilter`), sort (`ColumnSort`), and visibility state
- **`Line.java`** — a data row; contains a list of `ICell`
- **`ICell`** — sealed cell interface; concrete types: `TextfieldCell`, `TextareaCell`, `SelectCell`, `MultiSelectCell`, `IconLabelCell`
- **`SelectCellOption`** — option entry for `SelectCell` / `MultiSelectCell`
- **`PaginationData`** — cursor-based pagination metadata
- **`descriptions/`** — `TableDescription`, `ColumnDescription`, `LineDescription`, `ICellDescription`, `TextfieldCellDescription`, `TextareaCellDescription`, `SelectCellDescription`, `MultiSelectCellDescription`
- **`components/`** — `ITableElementRequestor`, `ILinesRequestor`, `ICustomCellDescriptor`
- **`events/ITableEvent`** — event type hierarchy
- **`renderer/`** — `TableRenderer`, `TableElementFactory`, `TableRenderingCache`

### `sirius-components-tables-graphql` — GraphQL Data Fetchers

Spring beans that wire the GraphQL schema to the domain:

- **`ColumnHeaderIconURLsDataFetcher`**, **`LineHeaderIconURLsDataFetcher`**, **`IconLabelCellIconURLsDataFetcher`** — icon URL resolvers
- **`RepresentationMetadataConfigurationDataFetcher`** — configuration resolver
- **Mutation resolvers**: `MutationEditTextfieldCellDataFetcher`, `MutationEditTextareaCellDataFetcher`, `MutationEditSelectCellDataFetcher`, `MutationEditMultiSelectCellDataFetcher`
- **Column/row operation resolvers**: `MutationChangeColumnFilterDataFetcher`, `MutationChangeColumnSortDataFetcher`, `MutationChangeGlobalFilterValueDataFetcher`, `MutationChangeTableColumnVisibilityDataFetcher`, `MutationReorderTableColumnsDataFetcher`, `MutationResizeTableColumnDataFetcher`, `MutationResizeTableRowDataFetcher`, `MutationResetTableRowsHeightDataFetcher`
- **`MutationInvokeRowContextMenuEntryDataFetcher`** — row context-menu action resolver

### `sirius-components-collaborative-tables` — Event Processor & Subscriptions

Manages the collaborative session for a table representation:

- **`CreateTableEventHandler`** — lifecycle handler
- **`EditCellEventHandler`** — dispatches to the correct cell type handler
- **`ChangeColumnFilterEventHandler`**, **`ChangeColumnSortEventHandler`**, **`ChangeGlobalFilterValueEventHandler`** — column/filter state changes
- **`ChangeTableColumnVisibilityEventHandler`**, **`ReorderTableColumnsEventHandler`**, **`ResizeTableColumnEventHandler`**, **`ResizeTableRowEventHandler`**, **`ResetTableRowsHeightEventHandler`** — structural changes
- **`CellStdDeserializerProvider`**, **`ICellDeserializer`** — JSON deserialization extension point for custom cell types
- **`CursorBasedPaginationData`** — collaborative pagination state
- **`CollaborativeTablesMessageService`** — i18n error messages
- Owns the **`table.graphqls`** schema (see below)

### `sirius-components-tables-tests` — Integration Tests

Architecture compliance and integration tests. Does not contain domain logic.

---

## Frontend

Single npm package under `packages/tables/frontend/sirius-components-tables/`.

| Field | Value |
|---|---|
| npm name | `@eclipse-sirius/sirius-components-tables` |
| Entry point | `src/index.ts` |

### `src/` layout

| Directory | Contents |
|---|---|
| `table/` | Root `TableRepresentation` component; WebSocket subscription wiring, pagination state |
| `representation/` | Outer wrapper used by host applications |
| `columns/` | Column header rendering, resize handle, filter/sort controls |
| `rows/` | Row rendering, row resize handle, row context-menu |
| `cells/` | One component per cell type (Textfield, Textarea, Select, MultiSelect, IconLabel) |
| `actions/` | Toolbar-level actions (global filter input, column visibility panel) |

---

## GraphQL Schema

```
packages/tables/backend/sirius-components-collaborative-tables/src/main/resources/schema/table.graphqls
```

Defines `Table`, `Column`, `Line`, cell union type, `tableEvent` subscription, cell edit mutations, and all column/row structural mutations.

---

## How to Compile

```bash
# Domain model only
cd packages/tables/backend/sirius-components-tables
mvn clean compile

# GraphQL data fetchers only
cd packages/tables/backend/sirius-components-tables-graphql
mvn clean compile

# Collaborative / event processor only
cd packages/tables/backend/sirius-components-collaborative-tables
mvn clean compile
```

To compile all tables backend submodules together from the repo root:

```bash
mvn clean compile -pl packages/tables/backend/sirius-components-tables,packages/tables/backend/sirius-components-tables-graphql,packages/tables/backend/sirius-components-collaborative-tables -am
```

Frontend changes do not require Maven; refresh the browser in dev mode.
