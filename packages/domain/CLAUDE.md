# CLAUDE.md — packages/domain

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/domain` provides the **Domain metamodel** — a lightweight EMF-based DSL for defining user data models (entities, attributes, relations). A `Domain` instance describes the shape of the semantic objects a user creates in a Sirius Web project. It is deliberately simple and technology-neutral: no JPA or persistence concerns live here.

## Directory Layout

Only a `backend/` subdirectory exists; there is no frontend package.

## Key Backend Submodules

### `sirius-components-domain`

The generated EMF metamodel. Source of truth is:
- `src/main/resources/model/domain.ecore` — the Ecore metamodel.

Key generated Java interfaces (all in `org.eclipse.sirius.components.domain`):
- `Domain` — root container, holds `Entity` instances.
- `Entity` — a named type; can extend another `Entity`.
- `Feature` (abstract), `Attribute`, `Relation` — typed structural features of an entity.
- `DataType` — enumeration of built-in scalar types.
- `DomainFactory`, `DomainPackage` — standard EMF factory and package singleton.

Implementation classes live in the `impl/` sub-package; adapter utilities in `util/`.

### `sirius-components-domain-edit`

EMF edit support (item providers, `AdapterFactory`) for use in tree-based editors and property views.

### `sirius-components-domain-emf`

Runtime integration with the Sirius Web EMF infrastructure:
- `DomainConverter` — converts a `Domain` model into a dynamic Ecore `EPackage` at runtime so the application can instantiate typed semantic objects.
- `DomainValidator` — validates domain model constraints (e.g., no circular inheritance).
- `DomainEMFConfiguration` — Spring configuration bean that registers the domain converter and validator.

## Dependencies

- **Depends on**: `packages/core/backend/` (core Spring/EMF wiring), `org.eclipse.emf.ecore`, `org.eclipse.emf.common`.
- **Consumed by**: `packages/sirius-web/backend/` (loads domain models at startup to create dynamic EPackages), the View DSL interpreter (`packages/view/backend/sirius-components-view-emf`) which queries domain entities to populate candidate lists.

## How to Compile

```bash
cd packages/domain/backend
mvn clean compile
```

To compile a single submodule:

```bash
mvn clean compile -pl packages/domain/backend/sirius-components-domain-emf -am
```
