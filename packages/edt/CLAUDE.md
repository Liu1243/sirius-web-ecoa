# CLAUDE.md — packages/edt

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

`packages/edt` contains the **EDT (ECOA Design Tool) metamodel** — a custom EMF metamodel that represents an ECOA project as a structured tree of design steps. Unlike `packages/ecoa` (which faithfully maps ECOA standard XSD schemas), the EDT metamodel defines tool-specific abstractions (`Step0`–`Step5`, `Steps`, `EDTComponent`, `EDTLibrary`, etc.) that organise all ECOA artefacts inside a single root `Steps` object.

The EDT metamodel imports and cross-references elements from `sirius-components-ecoa` (the standard ECOA 2.0 model), so it acts as a semantic bridge between the ECOA standard and the Sirius Web diagram/tree editors.

This module is consumed by `packages/sirius-web/backend/sirius-web-edt`, which is the Spring Boot application module that wires EDT into the Sirius Web framework.

### Sub-modules

| Artifact | Version | Role |
|---|---|---|
| `sirius-components-edt` | 2025.12.5 | EDT custom EMF model interfaces and `.ecore` schemas |
| `sirius-components-edt-edit` | 2025.12.5 | EMF Edit provider adapters (tree label/icon/children) for all EDT model classes |

There is **no frontend module** in `packages/edt`.

## EDT Model Structure

The root of the EDT model is `edtproject.Steps`, which holds up to 6 `Step` children (`Step0`–`Step5`), each corresponding to a design level:

| Step | Content |
|---|---|
| `Step0` | Type libraries (`edttype.Library`) and predefined / basic types |
| `Step1` | Service interface definitions (`edtinterface.ServiceDefinition`) |
| `Step2` | Component types (services and references) |
| `Step3` | Component implementations (`edtimplementation.ComponentImplementation`) |
| `Step4` | Composite / assembly level (wiring components) |
| `Step5` | Deployment (`edtdeployment.Deployment`) and logical system |

`Steps` also holds an `OutputDirectory` reference pointing to the code generation output folder.

## Core Classes / Interfaces

### `sirius-components-edt` — Java packages

| Java package | EDT concern | Key interfaces |
|---|---|---|
| `edtproject` | Root model and project-level concepts | `Steps`, `Step`, `Step0`–`Step5`, `EDTProjectPackage`, `EDTProjectFactory`, `Component`, `Composite`, `FinalAssembly`, `Contract`, `OutputDirectory` |
| `edtproject.services` | Non-generated service helpers | `StepsExportService` |
| `edttype` | EDT type library (wraps ECOA types) | `EDTTypePackage`, `EDTTypeFactory`, `Library`, `EDTDataType`, `Simple`, `Array`, `FixedArray`, `Record`, `VariantRecord`, `Union`, `Enum`, `EnumValue`, `Constant`, `BasicType`, `PredefinedType` and their `*Predefined` variants |
| `edtinterface` | Service interface definitions | `EDTInterfacePackage`, `EDTInterfaceFactory`, `ServiceDefinition`, `OperationType`, `Event`, `RequestResponse`, `Data`, `Parameter` |
| `edtimplementation` | Component implementation internals | `EdtimplementationPackage`, `EdtimplementationFactory`, `ComponentImplementation`, `ModuleType`, `ModuleInstance`, `TriggerInstance`, `DynamicTriggerInstance`, `ModuleImplementation`, `EventLink*`, `RequestLink*`, `DataLink*`, `VersionedData*`, `PublicPinfo`, `PrivatePinfoValue`, `PropertyValue`, etc. |
| `edtdeployment` | Deployment configuration | `EdtdeploymentPackage`, `EdtdeploymentFactory`, `Deployment`, `PlatformConfiguration`, `ComputingNodeConfiguration`, `ProtectionDomain`, `DeployedModuleInstance`, `DeployedTriggerInstance`, `WireMapping`, `LogPolicy`, `ModuleLog` |
| `edtlogical` | Logical computing system | `EdtlogicalPackage`, `EdtlogicalFactory` |
| `edtbin` | Binary descriptors | `EdtbinPackage`, `EdtbinFactory` |
| `edtqos` | QoS instances (service-level QoS) | `EdtqosPackage`, `EdtqosFactory`, `ServiceInstanceQos`, `Data`, `Event`, `RequestResponse` |
| `edtuid` | UID / ID maps | `EdtuidPackage`, `EdtuidFactory`, `IDMap`, `ID` |
| `edtudp` | UDP binding configuration | `EdtudpPackage`, `EdtudpFactory` |
| `edtdds` | DDS binding configuration | `EdtddsFactory`, `EdtddsPackage`, `DDSBinding` |
| `edttcp` | TCP binding configuration | `EdttcpFactory`, `EdttcpPackage`, `TCPBinding`, `TCPPlatform` |
| `temp` | Temporary / migration model elements | `TempPackage`, `TempFactory` |

Each package follows the standard EMF pattern:
- Top-level interfaces
- `impl/` — `*PackageImpl`, `*FactoryImpl`, `*Impl` classes
- `util/` — `*Switch`, `*AdapterFactory`

### `sirius-components-edt-edit` — Java packages

All under `<package>/provider/`:
- `edtproject/provider/` — `EDTProjectItemProviderAdapterFactory` + per-class item providers
- `edttype/provider/` — item providers for all `edttype` classes
- `edtinterface/provider/`
- `edtimplementation/provider/` — largest provider set; covers all module/link/instance types
- `edtdeployment/provider/`
- `edtlogical/provider/`
- `edtbin/provider/`
- `edtqos/provider/`
- `edtuid/provider/`
- `edtudp/provider/`
- `temp/provider/`
- `com.dassault.edt.model/provider/` — top-level model item provider adapter factory
- `edtimplementation/provider/EDTEditPlugin.java` — OSGi plugin activator for the edit bundle

Icon resources (`.gif` files) for all model classes live in `sirius-components-edt-edit/src/main/resources/icons/full/obj16/`.

## Ecore / GenModel Files

Located in `sirius-components-edt/src/main/resources/model/`:

| File | EDT concern |
|---|---|
| `EDT.genmodel` | Master EMF GenModel for the EDT metamodel |
| `EDTProject2.ecore` | Root `Steps`, `Step0`–`Step5`, project concepts |
| `EDTTypes.ecore` | EDT type library |
| `EDTServices.ecore` | Service interface definitions |
| `EDTImplementation.ecore` | Component implementation internals |
| `EDTDeployment.ecore` | Deployment |
| `EDTLogicalSystem.ecore` | Logical computing system |
| `EDTBinDesc.ecore` | Binary descriptors |
| `EDTQos.ecore` | QoS instances |
| `EDTUID.ecore` | UID / ID maps |
| `UDPBinding.ecore` | UDP binding |
| `DDSBinding.ecore` | DDS binding |
| `TCPBinding.ecore` | TCP binding |
| `toConvert.ecore` | Temporary migration artefacts |

## How to Compile

Compile the EDT model module:

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/edt/backend/sirius-components-edt
mvn clean compile
```

Compile the EDT edit provider module:

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/edt/backend/sirius-components-edt-edit
mvn clean compile
```

Compile both sub-modules together:

```bash
cd /Users/admin/code/sirius-web-ecoa/packages/edt/backend
mvn clean install
```

**Important:** `sirius-components-edt` depends on `sirius-components-ecoa` (version 2026.1.26). If `sirius-components-ecoa` has been changed but not yet installed to the local Maven repository, run `mvn clean install` in `packages/ecoa/backend` first.

Java version: 17.

## Dependency Relationships

`sirius-components-edt` depends on:
- `sirius-components-ecoa` (2026.1.26) — cross-references standard ECOA 2.0 model objects
- `org.eclipse.emf.common`, `org.eclipse.emf.ecore`, `org.eclipse.emf.ecore.xmi`, `org.eclipse.emf.codegen.ecore`
- `org.slf4j:slf4j-api`

`sirius-components-edt-edit` depends on:
- `sirius-components-edt` (same module)
- `sirius-components-ecoa` and `sirius-components-ecoa-edit`
- `org.eclipse.emf.edit`, `org.eclipse.core.runtime`
- `org.slf4j:slf4j-api`

**Consumer of this module:**
- `packages/sirius-web/backend/sirius-web-edt` (artifact `sirius-web-edt`, version 2025.12.5) — the Spring Boot application module that registers EDT model factories, edit providers, and Sirius Web representations. This is the only downstream consumer.
