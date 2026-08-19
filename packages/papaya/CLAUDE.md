# CLAUDE.md — packages/papaya

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/papaya` provides the **Papaya metamodel** — an EMF-based DSL for modeling software architecture concepts: components, packages, classes, interfaces, enumerations, annotations, operational entities/actors/processes, channels, and cross-cutting concerns. It is used as a sample/reference domain within Sirius Web to demonstrate how a rich architectural model can be visualized with View-based representations.

## Directory Layout

Only a `backend/` subdirectory exists; there is no frontend package.

## Key Backend Submodules

### `sirius-components-papaya`

The generated EMF metamodel. Source of truth is:
- `src/main/resources/model/papaya.ecore` — the Ecore metamodel.

Representative generated Java interfaces (all in `org.eclipse.sirius.components.papaya`):

**Component / architecture layer**
- `Component`, `ComponentPort`, `ProvidedService`, `RequiredService` — component-level modeling.
- `ApplicationConcern`, `Channel`, `Publication`, `Subscription`, `Message` — event-driven architecture concepts.

**Java type system**
- `Package`, `Type`, `Classifier`, `Class`, `Interface`, `Enum`, `DataType`, `Annotation` — Java-style type hierarchy.
- `Constructor`, `Operation`, `Parameter`, `RecordComponent`, `AnnotationField` — type members.
- `InterfaceImplementation`, `Visibility` — relationships and visibility levels.

**Operational / process layer**
- `OperationalEntity`, `OperationalActor`, `OperationalProcess`, `OperationalActivity`, `OperationalInteraction` — business process modeling.

**General**
- `ModelElement`, `NamedElement`, `Tag`, `Folder`, `Link`, `ContainingLink`, `ReferencingLink`, `Contribution` — base types and cross-links.
- `PapayaFactory`, `PapayaPackage` — standard EMF factory and package singleton.

### `sirius-components-papaya-edit`

EMF edit support (item providers for every concept above, `PapayaEditPlugin`). Used by tree editors and property views. Item provider classes mirror the metamodel structure (e.g., `ComponentItemProvider`, `ClassItemProvider`, `OperationalEntityItemProvider`).

## Dependencies

- **Depends on**: `org.eclipse.emf.ecore`, `org.eclipse.emf.common`. No dependency on `packages/view/` or `packages/domain/`.
- **Consumed by**: `packages/sirius-web/backend/` — sample initializers and View-based diagram descriptions reference the Papaya EPackage to populate demo projects.

## How to Compile

```bash
cd packages/papaya/backend
mvn clean compile
```

To compile a single submodule:

```bash
mvn clean compile -pl packages/papaya/backend/sirius-components-papaya -am
```
