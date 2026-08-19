# CLAUDE.md — packages/forms

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

The `forms` package implements the **form representation type** — the property-sheet / detail-panel UI that displays and edits model element attributes through typed widgets (textfields, checkboxes, dropdowns, rich text editors, etc.). It also hosts two custom widget extensions: `widget-reference` (EMF reference picker) and `widget-table` (table embedded inside a form group).

---

## Backend Layers

All backend submodules live under `packages/forms/backend/`.

### `sirius-components-forms` — Domain Model

Pure Java domain for the form representation:

- **`Form.java`** — root representation; contains a list of `Page`
- **`Page.java`**, **`Group.java`** — structural containers
- **`ToolbarAction.java`** — action buttons in group toolbars
- **Widget value objects** (one file each): `Textfield`, `Textarea`, `Checkbox`, `Select`, `MultiSelect`, `Radio`, `Button`, `SplitButton`, `Link`, `List`, `RichText`, `LabelWidget`, `Image`, `TreeWidget`, `FlexboxContainer`
- **Style objects**: `ButtonStyle`, `LinkStyle`, `LabelWidgetStyle`, `DateTimeStyle`, `ContainerBorderStyle`, etc.
- **`renderer/IWidgetDescriptor`** — extension point for custom widget types
- **`description/`** — `FormDescription` and one `*Description` class per widget type

### `sirius-components-forms-graphql` — GraphQL Data Fetchers

Spring beans that wire the GraphQL schema to the domain:

- `FormRefreshedEventPayloadFormDataFetcher` — subscription payload resolver
- `FormDescriptionCompletionProposalsDataFetcher`, `FormDescriptionHelpTextDataFetcher` — description resolvers
- `MutationEditTextfieldDataFetcher`, `MutationEditCheckboxDataFetcher`, `MutationEditSelectDataFetcher`, `MutationEditMultiSelectDataFetcher`, `MutationEditRadioDataFetcher`, `MutationEditRichTextDataFetcher`, `MutationEditSliderDataFetcher`, `MutationEditDateTimeDataFetcher` — one resolver per widget mutation
- `MutationClickListItemDataFetcher`, `MutationDeleteItemDataFetcher`, `MutationEditTreeCheckboxDataFetcher` — list / tree widget mutations

### `sirius-components-collaborative-forms` — Event Processor & Subscriptions

Manages the collaborative session for a form representation:

- **`FormEventProcessor`** / **`FormEventProcessorFactory`** — per-representation WebSocket session
- **`CreateFormEventHandler`** — lifecycle
- **Event handlers** — one class per widget mutation type (`EditTextfieldEventHandler`, `EditCheckboxEventHandler`, `EditSelectEventHandler`, `EditMultiSelectEventHandler`, `EditRichTextEventHandler`, `ClickListItemEventHandler`, `DeleteListItemEventHandler`, `CompletionProposalEventHandler`, etc.)
- Owns the **`form.graphqls`** schema (see below)

### `sirius-components-widget-reference` — Reference Widget Domain

Domain model for the EMF reference picker widget:

- `ReferenceWidget`, `ReferenceValue`, `ReferenceWidgetStyle`
- `description/ReferenceWidgetDescription`

### `sirius-components-widget-reference-graphql` — Reference Widget GraphQL

Data fetchers for the reference widget:

- `MutationAddReferenceValuesDataFetcher`, `MutationSetReferenceValueDataFetcher`, `MutationRemoveReferenceValueDataFetcher`, `MutationMoveReferenceValueDataFetcher`, `MutationClearReferenceDataFetcher`, `MutationCreateElementDataFetcher`
- `FormDescriptionReferenceValueOptionsDataFetcher`

### `sirius-components-collaborative-widget-reference` — Reference Widget Event Handlers

- `AddReferenceValuesEventHandler`, `ClearReferenceEventHandler`, `CreateElementEventHandler`, `RemoveReferenceValueEventHandler`, `SetReferenceValueEventHandler`, `MoveReferenceValueEventHandler`, `ClickReferenceValueEventHandler`

### `sirius-components-widget-table` — Table-in-Form Widget Domain

Domain model for embedding a `Table` representation inside a form group.

### `sirius-components-collaborative-widget-table` — Table-in-Form Event Handlers

Event handlers that delegate table cell edits and structural changes from within a form context.

### Test Modules

- `sirius-components-forms-tests` — integration and architecture tests for the core form modules
- `sirius-components-widget-reference-tests` — integration tests for the reference widget

---

## Frontend

Three npm packages under `packages/forms/frontend/`.

| npm name | Path | Purpose |
|---|---|---|
| `@eclipse-sirius/sirius-components-forms` | `sirius-components-forms/` | Core form rendering |
| `@eclipse-sirius/sirius-components-widget-reference` | `sirius-components-widget-reference/` | Reference picker widget |
| `@eclipse-sirius/sirius-components-widget-table` | `sirius-components-widget-table/` | Table-embedded-in-form widget |

### `sirius-components-forms/src/` layout

| Directory / File | Contents |
|---|---|
| `form/` | `FormRepresentation` root component |
| `pages/`, `pagelist/` | Page tab rendering |
| `groups/` | Group container with toolbar |
| `propertysections/` | One component per widget type (Textfield, Checkbox, Select, etc.) |
| `richtexteditor/` | Rich text widget integration |
| `toolbaraction/` | Toolbar action button component |
| `representations/` | Embedded representation (for widget-reference previews, etc.) |
| `views/` | View-level wrappers |
| `contexts/` | React context for form state |

### `sirius-components-widget-reference/src/` layout

| File / Directory | Contents |
|---|---|
| `ReferencePropertySection.tsx` | Main widget component rendered inside a form group |
| `ReferencePreview.tsx` | Read-only preview of a reference value |
| `ReferenceIcon.tsx` | Icon component |
| `components/` | Sub-components (value row, search dialog, etc.) |
| `modals/` | Create-element and browse modals |

### `sirius-components-widget-table/src/` layout

| File | Contents |
|---|---|
| `TableWidgetPropertySection.tsx` | Renders a `Table` representation embedded in a form group |
| `TableWidgetPreview.tsx` | Read-only table preview |
| `TableWidgetFragment.types.ts` | GQL fragment types |

---

## GraphQL Schema

```
packages/forms/backend/sirius-components-collaborative-forms/src/main/resources/schema/form.graphqls
packages/forms/backend/sirius-components-widget-reference/src/main/resources/schema/reference.graphqls
```

`form.graphqls` defines `Form`, `Page`, `Group`, all widget types, the `formEvent` subscription, and all widget edit mutations.  
`reference.graphqls` extends the form schema with `ReferenceWidget` and its mutations.

---

## How to Compile

```bash
# Core form domain
cd packages/forms/backend/sirius-components-forms
mvn clean compile

# Core form GraphQL fetchers
cd packages/forms/backend/sirius-components-forms-graphql
mvn clean compile

# Collaborative event processor
cd packages/forms/backend/sirius-components-collaborative-forms
mvn clean compile

# Reference widget (domain + graphql + collaborative)
cd packages/forms/backend/sirius-components-widget-reference
mvn clean compile

cd packages/forms/backend/sirius-components-widget-reference-graphql
mvn clean compile

cd packages/forms/backend/sirius-components-collaborative-widget-reference
mvn clean compile
```

Frontend changes do not require Maven; refresh the browser in dev mode.
