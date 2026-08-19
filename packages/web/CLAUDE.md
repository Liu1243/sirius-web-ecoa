# CLAUDE.md — packages/web

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## 模块用途

`packages/web` 是 Sirius Web 的 **HTTP / WebSocket 入口层**。它将 Spring Boot 的 HTTP 请求和 WebSocket 连接翻译为 GraphQL 执行，是外部客户端（浏览器）与 `packages/core` 协作框架之间的唯一网络边界。本包无前端代码。

---

## 后端子模块职责

### `sirius-components-graphql`（`org.eclipse.sirius.components.graphql`）

GraphQL 协议实现层，包含两条传输通道：

- **`GraphQLController`** — Spring MVC `@RestController`，处理 `POST /api/graphql` 的 HTTP Query/Mutation 请求，将请求体反序列化后交给 GraphQL 执行引擎，返回 JSON 响应。
- **`GraphQLWebSocketHandler`** — Spring WebSocket 处理器，实现 `graphql-ws` 子协议，管理订阅生命周期。内部使用 `SubscriptionEntry` 跟踪每个活跃订阅的 Reactor `Flux`。
- **`WebSocketConfiguration`** — 注册 WebSocket 端点（`/subscriptions`）并关联 `GraphQLWebSocketHandler`。
- **`GraphQLCacheClearer`** — 在编辑上下文销毁时清理 GraphQL schema 缓存。
- **`GraphQLPayload`** — HTTP 请求体 DTO（query、variables、operationName）。

WebSocket 消息处理器（实现 `IWebSocketMessageHandler`）：

- `ConnectionInitMessageHandler` — 握手，返回 `ConnectionAcknowledgeMessage`
- `StartMessageHandler` — 启动订阅，订阅 `IEventProcessorSubscriptionProvider` 提供的 Flux
- `StopMessageHandler` — 停止单个订阅
- `ConnectionTerminateMessageHandler` — 关闭整个 WebSocket 会话

扩展点：

- **`IGraphQLWebSocketHandlerListener`** — 监听 WebSocket 会话打开/关闭事件，可注入自定义逻辑。
- **`IWebSocketMessageHandler`** — 实现新的 WebSocket 消息类型处理器（很少需要）。

---

### `sirius-components-web`（`org.eclipse.sirius.components.web`）

Web 基础设施工具层：

- **`DelegatingRequestContextExecutorService`** / **`DelegatingRequestContextRunnable`** / **`DelegatingRequestContextCallable`** — 将 Spring `RequestContext` 传播到异步线程（解决 `RequestContextHolder` 在非请求线程中为空的问题）。
- **`FeedbackMessageService`** — 收集当前请求执行期间产生的反馈消息（`IFeedbackMessageService` 实现），供 Mutation 响应体携带。
- **`TimedConfiguration`** — Micrometer 计时器配置 Bean。

---

## 重要接口 / 扩展点

| 接口 / 类 | 所在位置 | 用途 |
|-----------|----------|------|
| `IGraphQLWebSocketHandlerListener` | sirius-components-graphql | 监听 WS 会话生命周期 |
| `IWebSocketMessageHandler` | sirius-components-graphql | 扩展 WS 消息类型 |
| `GraphQLController` | sirius-components-graphql | HTTP POST 入口，通常无需修改 |
| `GraphQLWebSocketHandler` | sirius-components-graphql | WS 订阅入口，通常无需修改 |
| `FeedbackMessageService` | sirius-components-web | 注入后可在事件处理器中添加用户提示消息 |

---

## 如何编译

```bash
# 编译 web 包全部子模块
cd /Users/admin/code/sirius-web-ecoa/packages/web/backend
mvn clean compile

# 只编译单个子模块（示例）
cd /Users/admin/code/sirius-web-ecoa/packages/web/backend/sirius-components-graphql
mvn clean compile
```

**不要从项目根目录执行全量 `mvn clean compile`，会非常耗时。**
