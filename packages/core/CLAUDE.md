# CLAUDE.md — packages/core

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## 模块用途

`packages/core` 是 Sirius Web 的 **GraphQL 契约层 + 协作事件处理框架**。它定义了所有上层模块必须遵守的 Java API 接口、GraphQL schema 根类型，以及面向前端的 React 核心组件。所有后端实现模块（emf、sirius-web 等）都依赖 core 的接口，不反向依赖。

---

## 后端子模块职责

- **sirius-components-annotations** — 纯 Java 注解，无 Spring 依赖，用于标记领域模型元素。
- **sirius-components-annotations-spring** — Spring GraphQL 注解：`@QueryDataFetcher`、`@MutationDataFetcher`、`@SubscriptionDataFetcher`，驱动数据绑定注册。
- **sirius-components-events** — 最小事件模型，仅含 `ICause` 接口，表示一次变更的原因。
- **sirius-components-representations** — 通用渲染引擎基础：`IComponent`、`IRepresentation`、`IRepresentationDescription`、`VariableManager`、`BaseRenderer`、`IStatus`（`Success`/`Failure`）。
- **sirius-components-core** — 核心领域服务接口（`org.eclipse.sirius.components.core.api`）：
  - `IEditingContext` — 编辑上下文（会话根对象）
  - `IEditService` / `IObjectService` / `IIdentityService` / `ILabelService` — 对象操作门面
  - `IRepresentationDescriptionSearchService` — 表示描述查找
  - `IInput` / `IPayload` — 命令总线输入/输出标记接口
  - `IEditingContextPersistenceService` / `IEditingContextSearchService` — 持久化与查找
- **sirius-components-core-graphql** — GraphQL DTO：`PageInfoWithCount`、`RepresentationMetadataDTO`，供数据获取器使用。
- **sirius-components-graphql-api** — GraphQL 运行时 SPI（`org.eclipse.sirius.components.graphql.api`）：
  - `IDataFetcherWithFieldCoordinates` — 自描述坐标的数据获取器
  - `IEditingContextDispatcher` — 将 `IInput` 派发到编辑上下文
  - `IEventProcessorSubscriptionProvider` — 订阅事件处理器的工厂
  - `ITypeResolverDelegate` — GraphQL Union/Interface 类型解析扩展点
- **sirius-components-collaborative** — 协作事件循环（`org.eclipse.sirius.components.collaborative.api`）：
  - `IEditingContextEventProcessor` — 每个编辑上下文一个实例，串行处理 `IInput`
  - `IRepresentationEventProcessor` — 每个打开的表示一个实例，处理表示级输入并推送更新流
  - `IEditingContextEventHandler` — 处理特定 `IInput` 类型的扩展点
  - `IInputPreProcessor` / `IInputPostProcessor` — 输入前置/后置处理器拦截链
  - `IRepresentationEventProcessorFactory` — 工厂，按表示类型创建对应的处理器
  - `IRepresentationRefreshPolicy` — 控制何时刷新表示

---

## GraphQL Schema（`sirius-components-collaborative/src/main/resources/schema/core.graphqls`）

根类型结构：
```
Query { viewer: Viewer! }
Viewer { editingContext(editingContextId): EditingContext }
EditingContext {
  domains, representation, representationDescriptions,
  rootObjectCreationDescriptions, childCreationDescriptions,
  queryBasedString/Int/Boolean/Object/Objects, actions, object, objects
}
Mutation { createChild, createRepresentation, createRootObject, invokeEditingContextAction }
Subscription { editingContextEvent(input: EditingContextEventInput!): EditingContextEventPayload! }
```
- `Representation` 和 `RepresentationDescription` 均为 GraphQL interface，由各具体模块扩展。
- `ErrorPayload` / `SuccessPayload` 是所有 Mutation 的通用结果类型。

---

## 前端子模块职责

位于 `packages/core/frontend/`，均以 ES Module + UMD 双格式发布：

- **`@eclipse-sirius/sirius-components-core`** — React 基础工具（hooks、类型、上下文）；其他前端模块的 peer dependency。
- **`@eclipse-sirius/sirius-components-impactanalysis`** — 影响分析面板 UI 组件。
- **`@eclipse-sirius/sirius-components-omnibox`** — Omnibox 命令面板 UI 组件。

---

## 重要接口 / 扩展点

| 接口 | 所在模块 | 用途 |
|------|----------|------|
| `IEditingContext` | sirius-components-core | 编辑会话根，包含 ResourceSet（EMF 实现） |
| `IEditingContextEventHandler` | sirius-components-collaborative | 处理新 IInput 类型，需 `@Service` + `canHandle()` |
| `IRepresentationEventProcessor` | sirius-components-collaborative | 实现新表示类型的实时推送 |
| `IRepresentationEventProcessorFactory` | sirius-components-collaborative | 为新表示类型提供处理器实例 |
| `IInputPreProcessor` | sirius-components-collaborative | 在命令执行前修改/增强 IInput |
| `IDataFetcherWithFieldCoordinates` | sirius-components-graphql-api | 为 GraphQL 字段注册数据获取器 |
| `ITypeResolverDelegate` | sirius-components-graphql-api | 解析 GraphQL Union/Interface 的具体 Java 类型 |

---

## 如何编译

```bash
# 只编译 core 所有子模块
cd /Users/admin/code/sirius-web-ecoa/packages/core/backend
mvn clean compile

# 或只编译单个子模块（示例）
cd /Users/admin/code/sirius-web-ecoa/packages/core/backend/sirius-components-collaborative
mvn clean compile
```

**不要从项目根目录执行全量 `mvn clean compile`，会非常耗时。**
