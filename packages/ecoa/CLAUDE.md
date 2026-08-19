# CLAUDE.md — packages/ecoa

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/ecoa` contains the **ECOA 2.0 standard metamodel** for the Sirius Web ECOA project. It is a pure EMF (Eclipse Modeling Framework) layer that maps the ECOA (European Component Oriented Architecture) 2.0 XSD schemas into Java model interfaces. It has no application logic, no Sirius Web integration, and no frontend.

This module is the lowest-level dependency in the architecture: `packages/edt` depends on it, and `packages/sirius-web/backend/sirius-web-edt` ultimately depends on it transitively.

### Sub-modules

| Artifact | Version | Role |
|---|---|---|
| `sirius-components-ecoa` | 2026.1.26 | EMF model interfaces generated from ECOA XSD schemas |
| `sirius-components-ecoa-edit` | 2026.1.26 | EMF Edit provider adapters (tree label/icon/children) for all ECOA model classes |

There is **no frontend module** in `packages/ecoa`.

## Code Generation

The Java source code in `sirius-components-ecoa` is **EMF-generated** from ECOA 2.0 XSD schemas. The generation pipeline is:

1. XSD files in `sirius-components-ecoa/src/main/resources/schema/` define ECOA 2.0 data structures.
2. `sirius-components-ecoa/src/main/resources/model/ECOADT.genmodel` configures the EMF code generator.
3. `src/codegen/java/com/dassault/ecoa/codegen/EmfGenRunner.java` drives the EMF generator at build time.
4. Generated Java sources land under `src/main/java/` (checked in) and `target/generated-sources/ecoa/`.

See `sirius-components-ecoa/CODE_GENERATION_GUIDE.md` for detailed instructions on re-running code generation.

**Do not hand-edit generated classes** (those matching the standard EMF patterns of `*Package`, `*Factory`, `*Impl`, `*Switch`, `*AdapterFactory`). Only edit custom validators or utility code.

## Core Classes / Interfaces

The `sirius-components-ecoa` module maps ECOA XSD namespaces directly to Java packages:

| Java package | ECOA concern | Key interfaces |
|---|---|---|
| `EcoaCommon20` | Shared ECOA 2.0 types | `EcoaCommon20Package`, `EcoaCommon20Factory`, `UseType`, `DocumentRoot` |
| `technology.ecoa.types._2` | ECOA type library | `typPackage`, `typFactory`, `Library`, `DataTypes`, `Simple`, `Array`, `FixedArray`, `Record`, `VariantRecord`, `Union`, `Enum`, `EnumValue`, `Constant`, `Field` |
| `technology.ecoa.interface_._2` | Service interface definitions | `intPackage`, `intFactory` |
| `technology.ecoa.interface_.qos._2` | QoS for interfaces | — |
| `technology.ecoa.implementation._2` | Component implementation | `impPackage`, `impFactory`, `ComponentImplementation`, `ModuleType`, `ModuleInstance` |
| `technology.ecoa.deployment._2` | Deployment configuration | `depPackage`, `depFactory` |
| `technology.ecoa.project._2` | ECOA project structure | `projPackage`, `projFactory` |
| `technology.ecoa.logicalsystem._2` | Logical computing system | — |
| `technology.ecoa.cross.platforms.view._2` | Cross-platform view | — |
| `technology.ecoa.insertion.policy._2` | Insertion policies | — |
| `technology.ecoa.module.behaviour._2` | Module behaviour FSM | — |
| `technology.ecoa.udpbinding._2` | UDP binding | — |
| `technology.ecoa.bin.desc._2` | Binary descriptor | — |
| `technology.ecoa.uid._2` | UID / ID maps | `uidPackage`, `uidFactory`, `ID`, `IDMap` |
| `technology.ecoa.sca.extension.scaExt` | ECOA SCA extensions | — |
| `org.open.oasis.docs.ns.opencsa.sca.sca` | SCA core | — |
| `org.w3._2001.xml.xsd` | XML Schema primitives | — |

Each package follows the standard EMF pattern:
- Top-level interfaces (e.g., `Library`, `Simple`)
- `impl/` — `*Impl` classes (generated)
- `util/` — `*Switch`, `*AdapterFactory`, `*ResourceImpl`, `*XMLProcessor`
- `validation/` — custom `*Validator` classes

The `sirius-components-ecoa-edit` module adds `provider/` sub-packages under each ECOA namespace, each containing:
- `*ItemProviderAdapterFactory` — registers item providers with EMF Edit
- `*ItemProvider` — provides label text, icon image, children list per model class

## Ecore / GenModel Files

Located in `sirius-components-ecoa/src/main/resources/model/`:

| File | Purpose |
|---|---|
| `ECOADT.genmodel` | Master EMF GenModel driving all code generation |
| `types.ecore` | ECOA type library metamodel |
| `interface.ecore` | Service interface metamodel |
| `implementation.ecore` | Component implementation metamodel |
| `deployment.ecore` | Deployment configuration metamodel |
| `project.ecore` | Project structure metamodel |
| `logicalSystem.ecore` | Logical system metamodel |
| `view.ecore` | Cross-platform view metamodel |
| `insertionPolicy.ecore` | Insertion policy metamodel |
| `moduleBehaviour.ecore` | Module behaviour metamodel |
| `UDPbinding.ecore` | UDP binding metamodel |
| `BinDesc.ecore` | Binary descriptor metamodel |
| `uid.ecore` | UID metamodel |
| `EcoaCommon20.ecore` | Common shared metamodel |
| `scaExt.ecore` | SCA extension metamodel |
| `sca.ecore` | SCA core metamodel |
| `schema.ecore` | XML Schema metamodel |

## How to Compile

Compile only this module (recommended when modifying validators or utility code):

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/ecoa/backend/sirius-components-ecoa
mvn clean compile
```

Compile the edit provider layer:

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/ecoa/backend/sirius-components-ecoa-edit
mvn clean compile
```

Compile both sub-modules together from the parent:

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/ecoa/backend
mvn clean install
```

Re-run EMF code generation (needed after changing `.ecore` or `.genmodel` files):

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/ecoa/backend/sirius-components-ecoa
mvn clean generate-sources
```

Java version: 17 (set via `sirius-web-parent`).

## Dependency Relationships

`sirius-components-ecoa` has no internal project dependencies. It depends only on:
- `org.eclipse.emf.common`, `org.eclipse.emf.ecore`, `org.eclipse.emf.ecore.xmi`, `org.eclipse.emf.codegen.ecore`

`sirius-components-ecoa-edit` depends on:
- `sirius-components-ecoa` (same module)
- `org.eclipse.emf.edit`, `org.eclipse.core.runtime`

**Consumers of this module:**
- `packages/edt/backend/sirius-components-edt` — depends on `sirius-components-ecoa` to cross-reference standard ECOA model elements from the EDT custom metamodel
- `packages/edt/backend/sirius-components-edt-edit` — depends on both `sirius-components-ecoa` and `sirius-components-ecoa-edit`
- `packages/sirius-web/backend/sirius-web-edt` — the application Spring Boot module that wires everything together
