# CLAUDE.md — packages/formdescriptioneditors

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/formdescriptioneditors` implements the **Form Description Editor** — a WYSIWYG drag-and-drop editor that lets users visually design form representations defined by the View DSL. The editor renders a live preview of each widget and allows users to add, move, and delete widgets, groups, and pages within a `FormDescription`.

## Directory Layout

```
packages/formdescriptioneditors/
  backend/
    sirius-components-formdescriptioneditors/                   Core domain model and SPIs
    sirius-components-formdescriptioneditors-graphql/           GraphQL mutations/subscriptions
    sirius-components-collaborative-formdescriptioneditors/     Collaborative session processor
    sirius-components-collaborative-formdescriptioneditors-widget-reference/  Reference widget support
    sirius-components-collaborative-formdescriptioneditors-widget-table/      Table widget support
  frontend/
    sirius-components-formdescriptioneditors/
      src/                                                       React components per widget type
```

## Key Backend Submodules

### `sirius-components-formdescriptioneditors`
Core types and SPIs:
- `FormDescriptionEditor`, `FormDescriptionEditorFor`, `FormDescriptionEditorIf` — immutable representation objects.
- `IWidgetPreviewConverterProvider` — SPI for plugins that contribute preview rendering for custom widgets.
- `IWidgetDescriptionProvider` — SPI for plugins that supply widget descriptions.

### `sirius-components-formdescriptioneditors-graphql`
Data fetchers for GraphQL mutations (add/move/delete widget, add/move/delete group, add/move/delete page, add/delete toolbar action) and the representation subscription.

### `sirius-components-collaborative-formdescriptioneditors`
Collaborative session event processor that applies mutation handlers to the in-memory `FormDescriptionEditor` representation.

### `sirius-components-collaborative-formdescriptioneditors-widget-reference`
Contributes `ReferenceWidgetPreviewConverterProvider` and `ReferenceWidgetDescriptionProvider` so the reference widget can be previewed inside the editor.

### `sirius-components-collaborative-formdescriptioneditors-widget-table`
Analogous support for the table widget preview inside the editor.

## Key Frontend Files

Under `frontend/sirius-components-formdescriptioneditors/src/`:
- `FormDescriptionEditorRepresentation.tsx` — top-level component; manages the subscription and renders the editor canvas.
- `useFormDescriptionEditorEventSubscription.tsx` — GraphQL subscription hook.
- `PageList.tsx`, `Group.tsx` — structural layout components.
- `coreWidgets.tsx` — registry of built-in widget preview components.
- Individual widget previews: `ButtonWidget.tsx`, `CheckboxWidget.tsx`, `DateTimeWidget.tsx`, `ImageWidget.tsx`, `LabelWidget.tsx`, `LinkWidget.tsx`, `PieChartWidget.tsx`, `BarChartWidget.tsx`, `RichTextWidget.tsx`, `SliderWidget.tsx`, etc.

## Dependencies

- **Backend depends on**: `packages/view/backend/sirius-components-view-form` (the View DSL form model being edited), `packages/core/backend/` (collaborative infrastructure, GraphQL wiring).
- **Frontend depends on**: `sirius-components-forms` (shared form widget primitives), `@apollo/client`.
- **Consumed by**: `packages/sirius-web/` registers the Form Description Editor as a workbench representation type.

## How to Compile

```bash
cd packages/formdescriptioneditors/backend
mvn clean compile
```

Frontend changes do not require a Maven build; refresh the browser in development mode.
