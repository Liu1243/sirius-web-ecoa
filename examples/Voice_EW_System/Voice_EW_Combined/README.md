# Voice_EW_Combined — 语音-电子自卫系统(ECOA 单工程联动版)

> 本文档根据 [`examples/需求分析.docx`](../../需求分析.docx) 的需求,介绍在 **Eclipse Sirius Web(ECOA 扩展)** 平台上建模并生成的 `Voice_EW_Combined` 项目。项目位于 `examples/Voice_EW_System/Voice_EW_Combined/`。

---

## 1. 需求概述

需求分析文档描述了一个由**三个 ECOA 应用**构成的语音-电子自卫系统。三个应用是**三个不同的 ECOA 工程**,部署在**不同的 platform**(计算平台)上,通过"中间件"(ECOA 平台间通信链路)互联:

- **YYY 应用** —— 一个嵌入式设备。两个功能:
  1. **周期性**(5s)向电子自卫应用发送**版本化数据**(struct2);
  2. 接收电子自卫应用发来的**事件数据**(struct1),据此**改变设备自身状态**。
- **XXX 应用** —— 一个智能设备(例如具备语音功能的设备)。两个功能:向 YYY 设备发送**查询指令**、**改变 YYY 设备状态**,通过语音消息驱动。
- **电子自卫应用(EW)** —— 周期性接收 YYY 应用的版本化数据,并根据版本化数据**修改自身的结构体数据**(该数据存储设备状态信息,修改即代表修改设备状态)。

需求中的三条数据流(图中标注):
1. **数据流(1)**:YYY → 电子自卫,版本化数据 struct2(周期 5s);
2. **数据流(2)**:XXX 语音控制 → 大模型 → 语音管理 → YYY,事件数据 struct1;
3. **数据流(3)**:XXX 语音查询 → 大模型 → 语音管理,查询 struct2 对应数据。

---

## 2. 设计总览

`Voice_EW_Combined` 将需求的"三个不同 ECOA 工程"以**单工程多平台**的方式实现:四个组件(其中电子自卫应用拆为**大模型 LLMModel** 与 **语音管理 VoiceManagement** 两个组件)部署在三个 `platform` 上,跨平台数据流经 **TCP 点对点 ELI 链路**(即需求中的"中间件")互联。

| 需求应用 | 项目组件 | 部署平台 |
|---|---|---|
| YYY 应用(嵌入式设备) | `compYYYDevice` | `Platform_YYY` |
| 电子自卫应用 — 语音管理 | `compVoiceMgmt` | `Platform_EW` |
| 电子自卫应用 — 大模型 | `compLLMModel` | `Platform_EW` |
| XXX 应用(智能语音设备) | `compXXXDevice` | `Platform_XXX` |

### 2.1 数据模型(0-Types)

`Common_lib.types.xml` 定义了 4 种公共类型:

| 类型 | 说明 | 字段 |
|---|---|---|
| `event_data`(struct1) | 事件数据,电子自卫应用发布、YYY 应用据此改变自身状态 | `topic_id` + 8×uint16 `status_word_0..7` + 4×uint8 `flag_0..3` |
| `versioned_data`(struct2) | 版本化数据,YYY 应用周期 5s 发送给电子自卫应用缓存 | `version` + 4×uint16 `sample_0..3` + 9×uint8 `channel_0..8` |
| `json_text` | 大模型与语音管理之间交换的 JSON 报文 | char8 数组,最长 1024 |
| `voice_msg` | XXX 应用发送给大模型的语音内容 | char8 数组,最长 512 |

### 2.2 服务接口(1-Services)

| 接口 | 操作 | 方向 | 作用 |
|---|---|---|---|
| `svc_versioned_data` | `versioned_data`(data) | YYY 提供 | 周期性发布 struct2 |
| `svc_event_data` | `event_data`(data) | EW 提供 | 发布 struct1,YYY 订阅 |
| `svc_voice_msg` | `voice_control` / `voice_query`(event) | EW 提供 | XXX → 大模型的语音控制 / 语音查询消息 |
| `svc_json_cmd` | `json_control`(event,含 `topic_id` + JSON) | EW 提供 | 大模型 → 语音管理的 JSON 控制指令 |
| `svc_vd_query` | `query_versioned_json`(request/response) | EW 提供 | 大模型 → 语音管理,查询 struct2 对应 JSON |

### 2.3 组件(2-ComponentDefinitions)

- **YYYDevice**(服务 `svc_versioned_data`,引用 `svc_event_data`):周期发送 struct2,接收 struct1 改变状态;
- **VoiceManagement**(服务 `svc_event_data` / `svc_json_cmd` / `svc_vd_query`,引用 `svc_versioned_data`):缓存 struct2 并据此修改 struct1;按 `topicID` 响应 JSON 控制指令;应答 struct2 JSON 查询;
- **LLMModel**(服务 `svc_voice_msg`,引用 `svc_json_cmd` / `svc_vd_query`):将语音消息解析为与 struct1 对应的 JSON,或转发查询;
- **XXXDevice**(引用 `svc_voice_msg`):发送语音控制 / 语音查询消息。

### 2.4 装配(3-InitialAssembly `demo.composite`)

四条 wire 对应需求三条数据流:

```
(1) compYYYDevice --svc_versioned_data(struct2, 5s)--> compVoiceMgmt
(2) compYYYDevice <--svc_event_data(struct1)---------- compVoiceMgmt
(2) compXXXDevice --svc_voice_msg(语音控制)--> compLLMModel --svc_json_cmd(JSON+topicID)--> compVoiceMgmt
(3) compXXXDevice --svc_voice_msg(语音查询)--> compLLMModel --svc_vd_query(查 struct2 JSON)--> compVoiceMgmt
```

### 2.5 实现(4-ComponentImplementations)

四个组件各有实现(`mycompYYYDevice` / `mycompVoiceMgmt` / `mycompLLMModel` / `mycompXXXDevice`),语言 C/C++ 混合(C 组件与 C++ 组件共存)。

关键实现设计:
- `mycompYYYDevice`:触发 `publish_timer` **周期 5s** 发布 struct2;`event_data_in` 以 `notifying=true` 订阅 struct1,数据更新即激活模块改变设备状态;
- `mycompVoiceMgmt`:`versioned_data_in` 以 `notifying=true` 实时缓存 struct2 并据此修改 struct1;接收带 `topic_id` 的 `json_control`,按 `topicID` 查找 struct1 修改;应答 `query_versioned_json`;
- `mycompXXXDevice`:触发 `voice_timer` **周期 10s** 模拟语音采集,发送 `voice_control` / `voice_query`;
- `mycompLLMModel`:接收 `voice_control`/`voice_query`,发送 `json_control`(异步 request `query_versioned_json`,超时 5s)。

> 注:当前四个模块的入口函数均为 LDP 1.1.0 生成的 **TODO 骨架**(`/* @TODO TODO - To be implemented */`),业务逻辑待实现;接口、装配、部署、生成链路已完整打通。

### 2.6 集成与部署(5-Integration)

- **实现装配** `demo.impl.composite`:组件 → 实现映射;
- **逻辑系统** `cs1.logical-system.xml`:3 个 `logicalComputingPlatform`(ELIPlatformId 1/2/3 对应 YYY/EW/XXX),2 条平台链路 `link_EW_to_YYY`、`link_XXX_to_EW`(TCP transport binding);
- **TCP binding** `tcp.tcp-params.xml`:三平台监听地址 `127.0.0.1`、端口 20001/20002/20003;TCP 的 platform 条目为本包模型对象(`edttcp.TCPPlatform`),导入/导出不会像 UDP binding 那样因跨包序列化而丢失;
- **部署** `demo.deployment.xml`:四个组件分属 `YYY_PD` / `VoiceMgmt_PD` / `LLMModel_PD` / `XXX_PD` 四个保护域;跨平台 wire 映射:
  - `compVoiceMgmt↔compYYYDevice` 两条 wire → `link_EW_to_YYY`;
  - `compXXXDevice → compLLMModel`(语音消息)→ `link_XXX_to_EW`;
  - LLMModel → VoiceMgmt 两条 wire 同平台本地直连,无需映射;
- **节点部署** `nodes_deployment.xml`:本地联动验证时全部指向 `127.0.0.1`,真实分布式部署时改为各节点实际 IP。

---

## 3. 生成产物(6-output-XXX)

由 Siris Web 平台从模型生成三个平台的完整 C/C++ 工程(CMake),可直接编译运行:

```
6-output-EW/   电子自卫平台(Platform_EW):组件 + platform + 日志配置
6-output-XXX/  XXX 平台(Platform_XXX)
6-output-YYY/  YYY 平台(Platform_YYY,含已配置的 build/)
```

每个平台包含:
- `mycomp*/` —— 各组件生成代码(`component_*.c/cpp`、`*__properties.h`);
- `platform/` —— ECOA 运行时(ELI UDP/TCP/DDS 传输、FIFO、触发、日志等 `libecoa`)+ `main.c`、各保护域 `PD_*.c`、`route.h` 路由表、`multi-nodes.py` 多节点部署脚本(SCP 分发到 `/tmp/bin` 并 SSH 启动);
- `platform/log_properties/` —— 各保护域日志配置(properties/zlog)。

---

## 4. 运行与验证

1. **编译**:进入各 `6-output-*` 目录执行 CMake(`cmake .. && make`)生成 `platform`、各 `PD_*.c` 可执行文件;
2. **启动(本地联动)**:使用 `multi-nodes.py` 在 `127.0.0.1` 上同时启动 `platform` 与 `PD_YYY_PD`、`PD_VoiceMgmt_PD`、`PD_LLMModel_PD`、`PD_XXX_PD` 五个进程;
3. **观测**:YYY 每 5s 发布 struct2 到语音管理;XXX 每 10s 发送语音消息经大模型处理后下发 struct1 改变 YYY 状态——三个平台经 TCP 点对点 ELI 链路联动。

---

## 5. 需求 → 设计对照表

| 需求描述 | 项目实现 |
|---|---|
| 三应用部署在不同 platform | `Platform_YYY` / `Platform_EW` / `Platform_XXX` |
| "中间件"互联 | TCP 点对点 ELI 链路(`link_EW_to_YYY`、`link_XXX_to_EW`) |
| (1) YYY 周期发送版本化数据 struct2 | `publish_timer` 周期 5s + `svc_versioned_data` |
| (1) EW 周期接收 struct2 并修改自身结构体 | `VoiceManagement.versioned_data_in`(notifying)缓存并修改 struct1 |
| (2) XXX 改变 YYY 状态 | 语音控制 → 大模型 JSON(带 topicID)→ 语音管理按 topicID 改 struct1 → YYY 收 struct1 改状态 |
| (2) YYY 接收事件数据改变自身状态 | `YYYDevice.event_data_in`(notifying) |
| (3) XXX 查询 YYY | 语音查询 → 大模型 → `svc_vd_query` 返回 struct2 对应 JSON |
