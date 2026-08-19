# CLAUDE.md — packages/emf

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## 模块用途

`packages/emf` 是 Sirius Web 的 **EMF（Eclipse Modeling Framework）集成层**。它将 `packages/core` 定义的抽象接口（`IEditingContext`、`IObjectService` 等）绑定到具体的 EMF/ECore 实现，并提供 AQL 表达式解释器、自动表单生成和表格导航能力。所有需要操作 EMF 资源集（`ResourceSet`）的上层模块都依赖本包。本包无前端代码。

---

## 后端子模块职责

### `sirius-components-emf`（`org.eclipse.sirius.components.emf`）

核心 EMF 运行时绑定：

- **`IEMFEditingContext`** — 扩展 `IEditingContext`，暴露 `ResourceSet`，是操作 EMF 模型的入口。
- **`JSONResourceFactory`** — 以 JSON 格式序列化/反序列化 EMF 资源（Sirius Web 原生持久化格式）。
- **`EPackageService`** / **`IEditingContextEPackageService`** — 管理编辑上下文中注册的 EPackage。
- **`EObjectIDManager`** / **`IDAdapter`** — 为 EObject 分配和维护稳定 UUID。
- **`MigrationService`** / **`IMigrationParticipant`** — 加载模型时执行版本迁移，扩展点为 `IMigrationParticipant`。
- **`EMFQueryService`** / **`IQueryJavaServiceProvider`** — 在 AQL 查询中提供 Java 服务方法的注册机制。
- **`IEMFKindService`** — 将 EClass 映射为 Sirius Web 的 `kind` 字符串（如 `siriusComponents://semantic?domain=…`）。
- **`IEMFLabelService`** / **`IEMFLabelServiceDelegate`** / **`ComposedEMFLabelService`** — 为 EObject 生成标签，支持代理模式组合多个实现。
- **`DomainClassPredicate`** — 判断字符串是否匹配某个 EClass 的 domain 格式。
- **`ResourceMetadataAdapter`** — 附加在 EMF Resource 上的元数据适配器。
- **事件处理器**（`handlers/` 包）：实现了 `IEditingContextEventHandler`，处理对象查询、子对象创建、领域查询等标准 Mutation。

---

### `sirius-components-interpreter`（`org.eclipse.sirius.components.interpreter`）

AQL（Acceleo Query Language）表达式解释器：

- **`IInterpreter`** — 解释器抽象接口。
- **`AQLInterpreter`** — 核心实现，持有 `EPackage` 集合和 Java 服务类列表，执行 AQL 表达式。
- **`ExpressionConverter`** — 将 Sirius 格式的表达式字符串（如 `aql:self.name`）转换为纯 AQL。
- **值提取器**：`StringValueProvider`、`BooleanValueProvider`、`IntValueProvider` — 从 `Result` 中安全提取特定类型值。
- **`Result`** — 解释结果包装器，含状态（`Status`）和原始值。

---

### `sirius-components-emf-forms`（`org.eclipse.sirius.components.emf.forms`）

基于 EStructuralFeature 自动生成属性面板表单描述：

- **`IEMFFormDescriptionProvider`** — 主扩展点，从编辑上下文中的 EClass 生成 `FormDescription`。
- **`IEMFFormIfDescriptionProvider`** — 为单个 EStructuralFeature 类型生成 widget 描述的 SPI，框架提供以下实现：
  - `EStringIfDescriptionProvider`、`EBooleanIfDescriptionProvider`、`EEnumIfDescriptionProvider`
  - `NumberIfDescriptionProvider`、`LocalDateIfDescriptionProvider`、`InstantIfDescriptionProvider`
  - `NonContainmentReferenceIfDescriptionProvider`
- **`IPropertiesValidationProvider`** — 提供属性字段的验证规则。
- **`IWidgetReadOnlyProvider`** — 控制属性字段的只读状态。

---

### `sirius-components-emf-tables`（`org.eclipse.sirius.components.emf.tables`）

为表格视图提供基于 EMF 树的游标式导航：

- **`CursorBasedNavigationServices`** — 提供基于游标的分页导航服务（供 AQL 调用）。
- **`ForwardTreeIterator`** / **`BackwardTreeIterator`** — EMF containment 树的前向/后向迭代器，支持游标定位。

---

## 重要接口 / 扩展点

| 接口 | 所在模块 | 用途 |
|------|----------|------|
| `IEMFEditingContext` | sirius-components-emf | 获取 `ResourceSet` 以操作 EMF 模型 |
| `IMigrationParticipant` | sirius-components-emf | 实现模型版本迁移步骤 |
| `IQueryJavaServiceProvider` | sirius-components-emf | 向 AQL 注册 Java 服务类 |
| `IEMFLabelServiceDelegate` | sirius-components-emf | 为特定 EObject 类型定制标签显示 |
| `IEditingContextEPackageService` | sirius-components-emf | 注册/查询编辑上下文中的 EPackage |
| `IInterpreter` / `AQLInterpreter` | sirius-components-interpreter | 执行 AQL 表达式，通常直接实例化 `AQLInterpreter` |
| `IEMFFormDescriptionProvider` | sirius-components-emf-forms | 自定义属性面板表单的生成策略 |
| `IEMFFormIfDescriptionProvider` | sirius-components-emf-forms | 为自定义 EDataType 提供对应 widget |

---

## 如何编译

```bash
# 编译 emf 包全部子模块
cd /Users/admin/code/sirius-web-ecoa/packages/emf/backend
mvn clean compile

# 只编译单个子模块（示例）
cd /Users/admin/code/sirius-web-ecoa/packages/emf/backend/sirius-components-emf
mvn clean compile
```

**不要从项目根目录执行全量 `mvn clean compile`，会非常耗时。**
