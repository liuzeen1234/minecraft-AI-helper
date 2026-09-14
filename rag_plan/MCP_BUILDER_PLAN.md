# Plan：让 mod 支持"外部 LLM 实时闭环建造"(复刻 Astra/MCP 效果)

> 参考视频：B 站《【Minecraft】GPT-6 Astra 实时建造高完成度江南园林》(UP 主 R-D-X)。
> 视频实测结论：ChatGPT(Work 版桌面客户端) + 一个基于 Fabric 的 MCP 集成(视频中称 `Mcpfabric`，工具名 `@minecraft-builder`) + Minecraft。
> 模型通过 MCP 工具**实时**读取坐标、截图识别画面、放方块、批量填充，并**分阶段建造 → 截图自查 → 自动修正**，最终用时约 11 分 34 秒建成 50×50 的江南园林"听雨园"。

---

## 一、目标与两条路线

目标 = 让外部/内置强模型能够**实时调用"读坐标 / 截图 / 放方块 / 填充 / 检查"这些工具，边建边看边改**。

### 路线 A(推荐)：把 mod 变成一个 MCP Server
在 mod 内嵌一个 HTTP 服务，按 MCP 协议对外暴露工具(tool)。用户在 ChatGPT / Claude Desktop 里连上这个 server，模型自主调用。

- 优点：真正复刻视频效果；模型是外部强模型；闭环、可自我纠错；与视频架构一致。
- 代价：需实现 MCP 协议(JSON-RPC over HTTP/SSE)；需处理线程与玩家上下文。

### 路线 B(轻量)：在现有内置 AI 里做"工具循环"
不引入 MCP，把现在"AI 回复 → 解析标签 → 执行"的一次性流程，改造成"执行后把结果(含新截图)自动回灌给 AI，循环多轮直到完成"。

- 优点：改动小，复用现有 `callKimiApi` + 截图链路；用户无需配置外部客户端。
- 缺点：不是真 MCP，依赖所配模型能力；但实际体验可非常接近视频。

### 建议
先做**路线 B** 打通"闭环 + 自我纠错"内核(投入小、见效快)，再在其上包一层**路线 A** 的 MCP Server 对外开放。两条路线的"手"(建造能力)完全共用，B 做的封装 A 直接能用。

---

## 二、现状盘点(已确认)

项目已具备约 80% 的"手"：

- **建造能力**(服务端、纯方法)：`AICommandExecutor` 的 `place_block / fill_blocks / clear_area / execute_command`，以及 `BlueprintBuilder.build`、`NbtStructurePlacer.place`，均走 `world.setBlockState`。
- **读环境**：`player.getBlockPos()` 拿坐标；`SelectionManager` 拿选区；`ServerSelectionExporter.exportTxt` 可把区域方块导出成文本(相当于"读方块")。
- **视觉**：`AiChatScreen.doScreenshotAndSend` 已能截 framebuffer → 缩放 → 存 png → 服务端转 base64 喂多模态模型。
- **LLM 接入**：`ModConfig` 已支持 OpenAI / Anthropic 双协议，`api_base_url` / `api_key` / `model` 可配。
- **测试**：JUnit 5 + Mockito 已就位，`./gradlew test`，测试目录 `src/test/java/com/example/helloworld/`。

缺的 20%：

- **没有工具循环闭环**(现在是一次性)。
- **没有 MCP server**。
- **建造动作大多以玩家位置为原点、缺绝对坐标能力**(闭环建造需要模型能指定绝对坐标)。

---

## 三、实施步骤

### 阶段 0：抽离"建造能力"为纯服务层(两条路线共用地基)
新建 `BuilderTools`(服务端)，把散落在 `AICommandExecutor` 私有方法里的能力提成公有、参数化、**支持绝对坐标**的方法：

- `placeBlock(world, absPos, blockId, props)`
- `fillBlocks(world, from, to, blockId)`（保留体积上限，可配）
- `clearArea(world, from, to)`
- `placeBlueprint(world, origin, blueprintText)` → 复用 `BlueprintBuilder.build`
- `readRegion(world, from, to)` → 复用 `ServerSelectionExporter.exportTxt` 逻辑，返回文本给模型
- `getPlayerContext(player)` → 坐标、朝向、面前方块、生物群系
- `requestScreenshot(player)` → 触发 `TAKE_SCREENSHOT_PACKET`，拿回 base64

所有世界写操作统一用 `server.execute(...)` 回主线程 + `CompletableFuture` 拿结果(现有 handler 已是此模式)。

> 收益：`AICommandExecutor` 现有标签执行改为调用 `BuilderTools`，行为不变；新功能也调它。

### 阶段 1(路线 B)：把一次性调用改造成"工具循环闭环"
在 `HelloWorldMod` 的 AI 调用处，把现在 FETCH/SEARCH 那种"回灌再调一次"扩展成通用的 **agent loop**：

1. 模型输出动作(复用现有 `[ACTION]` / `[BLUEPRINT]` 标签，或新增 `[OBSERVE]` 让它主动请求截图/读方块)。
2. `BuilderTools` 执行，收集结果(文本 + 可选新截图)。
3. 把结果作为新一轮 user 消息回灌给模型。
4. 循环，直到模型输出"完成"标记或达到最大轮数(可配，如 30 轮)。

总控：最大轮数、每轮超时、可随时用现有 `CHAT_CANCEL_PACKET` 中断。

> 做完这一步，游戏内已能让 AI "分阶段建、每阶段截图自查、自动修正"，非常接近视频。

### 阶段 2(路线 A)：在其上包一个 MCP Server
- 内嵌轻量 HTTP server(Java 自带 `com.sun.net.httpserver.HttpServer`，零依赖)，实现 MCP 的 JSON-RPC：`initialize` / `tools/list` / `tools/call`，SSE 传输。
- 把阶段 0 的 `BuilderTools` 方法注册成 MCP tools：`get_player_context`、`take_screenshot`、`place_block`、`fill_blocks`、`clear_area`、`place_blueprint`、`read_region`。
- 在 `HelloWorldMod.onInitialize()` 末尾按配置启动(默认关闭；端口/开关放进 `ai-builder.properties`，如 `mcp_enabled`、`mcp_port`)。
- 用户在 ChatGPT / Claude Desktop 配置该本地 MCP server 即可，和视频里 `@minecraft-builder` 一样。

### 阶段 3：配置与文档
- `ModConfig` 增加 `mcp_enabled` / `mcp_port` / `agent_max_rounds` 等项。
  - **注意**：修改 `ai-builder.properties` 前，先把要加的字段和默认值列出并与用户确认，同意后再动。
- 更新 `USER_MANUAL.md` / `README.md`：如何开启 MCP、如何在外部客户端连接。

### 阶段 4：测试(用现有 JUnit + Mockito)
- 纯逻辑单测：MCP 请求的 JSON-RPC 解析/序列化、agent loop 的轮次控制与终止条件、`BuilderTools` 的坐标/体积校验、蓝图文本拼装。
- 世界写操作因深度耦合 `ServerWorld`，按现有测试风格用 Mockito mock 或只测参数校验层，不启动 Minecraft。
- `./gradlew test` 跑通；`./gradlew runClient` 手动验证实机闭环建造。

---

## 四、风险与取舍
- **MCP 协议实现成本**：MCP 规范持续演进，实现当前主流的 JSON-RPC + SSE 最小可用子集(够 ChatGPT/Claude 调用即可)，不追求全量规范。
- **建造安全**：闭环 + 绝对坐标意味着模型可大范围改世界。保留体积上限、命令黑名单；建议加"操作区域白名单/半径限制"配置。
- **性能**：大量 `setBlockState` 需分批 + 回主线程，避免卡服务器主线程 tick。
- **不改的东西**：现有游戏内聊天、标签体系、蓝图/NBT 格式全部保留，新功能为叠加而非替换。

---

## 五、建议落地顺序
1. 阶段 0：抽 `BuilderTools`(地基，低风险)。
2. 阶段 1：工具循环闭环(最快看到"视频同款"效果)。
3. 阶段 2：MCP Server(对外开放，真正对齐视频架构)。
4. 阶段 3 / 4：配置 + 文档 + 测试。

---

## 六、待确认事项
1. 走**推荐的 B → A 组合**，还是只做其中一条？
2. 阶段 2 的 MCP 打算连哪个外部客户端(ChatGPT Work / Claude Desktop / 自写脚本)？这影响传输实现(HTTP+SSE vs stdio)。
3. 是否先做**阶段 0 + 阶段 1**(闭环内核)，在自配模型上先跑起来看效果？

---

## 附：关键代码位置(供实施参考)
- `src/main/java/com/example/helloworld/HelloWorldMod.java` — 服务端主入口：网络包定义、所有 handler、大模型 HTTP 调用链(`callKimiApi` / `callKimiApiStreaming`)、请求构造与 FETCH/SEARCH 工具回灌闭环。嵌入 MCP server 的首选宿主。
- `src/main/java/com/example/helloworld/AICommandExecutor.java` — AI 输出解析与全部建造动作执行(place/fill/clear/blueprint 均走 `world.setBlockState`)，含超长 system prompt。MCP 工具层最应复用的建造 API。
- `src/main/java/com/example/helloworld/blueprint/BlueprintBuilder.java` — MCBLUEPRINT v2 结构放置核心(`buildV2` 两阶段放置 + 属性/容器/告示牌还原)。
- `src/main/java/com/example/helloworld/AiChatScreen.java` — 客户端聊天 GUI 与 framebuffer 截图实现(`doScreenshotAndSend`)，视觉回传流程起点。
- `src/main/java/com/example/helloworld/ModConfig.java` — `ai-builder.properties` 全部配置项、openai/anthropic 格式判定与端点解析。
