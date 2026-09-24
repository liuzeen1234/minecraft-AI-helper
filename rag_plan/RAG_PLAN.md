# RAG 知识库实施计划（已实施）

> 状态（2026-09-25 更新）：**代码已实现**。本文件原为设计计划，现补充实施结果说明；下方各章节的设计描述与实际代码基本一致（除个别命名差异见文末更新记录），仍可作为机制参考文档使用。
>
> 当前代码已包含 `KnowledgeBase`（`src/main/java/com/example/helloworld/KnowledgeBase.java`）、`AICommandExecutor` 中的 `[KNOWLEDGE]` 标签说明与目录生成（`getKnowledgeBaseSection()`）、`HelloWorldMod` 中的标签解析与两阶段回填逻辑（`extractKnowledgeDocNames()` 及工具轮询处理）、`ModConfig` 中的 `rag_enabled`/`rag_max_docs`/`rag_max_chars` 配置项，以及内置英文知识库资源 `src/main/resources/assets/helloworld/knowledge/basic_info_en/`。AI 可在 `[SEARCH]` / `[FETCH]` 之外，通过 `[KNOWLEDGE]` 标签查阅这些本地文档。
>
> 目标 MC 版本为 **Java 1.20.4**，与 Mod 运行版本一致。`参考文件.txt` 是候选资料来源，不能代表已实现功能或可直接再分发的资源。

## 一、已确认的设计决策（待实施时复核）

| 项 | 计划决策 |
|---|---|
| 检索方式 | 关键词目录检索版；不做向量检索 |
| 知识与素材边界 | system prompt 保留协议和建筑硬规则；方块、红石和建筑范例移入知识库 |
| 检索机制 | 纯 AI 选择的两阶段检索，不做关键词打分兜底 |
| 运行目录 | `ai-helper/knowledge/`，用户可增删；首次启动可从 Mod 资源释放默认文档 |
| 与 TXT 蓝图关系 | 知识库与 `ai-helper/structures/txts/` 完全分离 |
| 触发方式 | 每条消息提供目录，由 AI 决定是否请求文档 |
| 注入上限 | 默认 `maxDocs=8`、`maxChars=20000` |
| 数据合规 | 仅使用已确认许可或自有内容；不批量公开再分发抓取内容 |

实施前必须重新核验这些决策、外部资料许可和 API 上下文成本。

## 二、目标机制（设计稿）

设计上复用现有 `[SEARCH]` / `[FETCH]` 的“标签触发 → 回填 → 再请求”模式，新增 `[KNOWLEDGE]` 标签：

```text
第 1 轮：用户消息 + 知识库目录（标题、关键词、摘要，不含正文）
  └─ AI 按需输出 [KNOWLEDGE]文档A,文档B[/KNOWLEDGE]
第 2 轮：系统读取被点名文档的正文，应用数量/字符上限并回填
  └─ AI 使用正文生成回答、[BLUEPRINT] 或 [ACTION]
```

闲聊时 AI 不输出标签，不增加第二次请求。AI 未选择资料是该方案接受的风险；v1 不做自动关键词兜底。

## 二点五、中文知识库不随 Mod 打包（重要说明）

`rag_plan/rag_resources/basic_info/basic_info_ch/` 下的中文知识库文档（方块特性、命令、实体生物、游戏机制等）**仅作为规划/校对用资料**，不会被复制进 `src/main/resources/assets/helloworld/knowledge/`，也不会随 jar 包分发。

- 原因：mod 打包体积。已实际打包的英文知识库（`basic_info_en/`）体量已不小，若同时内置一份内容对等的中文版会显著增加 jar 大小，对多数仅需单语言的下载/分发场景是不必要的开销。
- 现状：`src/main/resources/assets/helloworld/knowledge/` 下目前只有 `basic_info_en/` 一套（英文），是唯一随 mod 实际发布、被 `KnowledgeBase.ensureDefaultDocsReleased()` 释放到运行目录的知识库。无论游戏内语言设置为中文还是英文，AI 通过 `[KNOWLEDGE]` 标签查阅到的都是这份英文资料（AI 自己用中文转述给玩家）。
- 中文版 `rag_resources/basic_info_ch/` 的定位：作为编写/校对英文文档时的对照底稿与知识梳理场所，其内容更新后需要人工/AI 辅助翻译、核对、合并进英文版对应文件，而不是简单地并列存在两份资料库。
- 如果未来要改为随 mod 内置中文知识库（例如按玩家语言动态释放对应语言目录），需要重新评估 jar 体积影响，并在 `ModConfig`/`KnowledgeBase` 层面设计按语言选择释放的逻辑；这属于范围外的新功能，未列入当前实施计划。

## 三、目标目录与文档格式（设计稿）

运行目录拟为 `ai-helper/knowledge/`，打包源拟为 `src/main/resources/assets/helloworld/knowledge/`：

```text
knowledge/
├─ basic_info/
├─ buildings/
│  ├─ simple/
│  └─ detailed/
├─ redstone_basic/
└─ redstone_advanced/
```

每篇 Markdown 使用 YAML front matter：

```markdown
---
title: 红石中继器
version: 1.20.4
category: redstone_basic
keywords: [中继器, repeater, 延迟]
summary: 单向传递信号、可设延迟、可锁定。
---

正文……
```

`summary` 与 `keywords` 用于目录展示，正文只在 AI 点名后注入。仓库现有的 `rag_plan/rag_resources/` 不等于此目标目录，也不会被自动复制到运行目录。

## 四、代码改动（已实施）

### 新增

- `KnowledgeDoc`：相对路径、标题、分类、关键词、摘要和正文的数据类；
- `KnowledgeBase`：递归读取 Markdown、解析 front matter、构建目录、按 AI 点名检索正文并执行上限控制；
- 默认资源释放逻辑：仅在 `knowledge/` 为空时复制默认文档，且不得覆盖用户修改。

### 修改

- `ModPaths`：新增 `getKnowledgeDir()`；
- `AICommandExecutor.getSystemPrompt()`：保留协议、建筑硬规则、`[ACTION]`、`[SEARCH]` 和 `[FETCH]` 规则，加入知识库目录与 `[KNOWLEDGE]` 用法；
- `HelloWorldMod`：解析 `[KNOWLEDGE]`，读取正文并发起第二轮请求；
- `ModConfig`：加入 RAG 开关和注入上限。

实际配置项（`ai-builder.properties`，属性名使用下划线命名，与 `ModConfig` 一致）：

```properties
rag_enabled=true
rag_max_docs=8
rag_max_chars=20000
```

根据项目工作流，**修改 `ai-builder.properties` 前必须先取得用户确认**。

## 五、测试与验证（实施时）

在 `src/test/java/com/example/helloworld/` 以 JUnit + Mockito 覆盖：

- 文档加载和 front-matter 解析；
- 目录生成不包含正文；
- 按名称检索及 `maxDocs` / `maxChars` 截断；
- `[KNOWLEDGE]` 标签的多文档、大小写、空格和不存在名称处理；
- 取消与超时在第二次请求中仍生效。

完成后运行：

```bash
./gradlew test
./gradlew build
```

## 六、实施状态

| 阶段 | 状态 |
|---|---|
| 0. 调研现状与既有搜索/抓取链路 | 已完成（调研） |
| 1. 编写/迁移默认知识库文档 | 已完成（英文版 `basic_info_en/`；中文版仅作规划底稿，见第二点五节） |
| 2. 首次释放逻辑（`KnowledgeBase.ensureDefaultDocsReleased()`） | 已完成 |
| 3. `KnowledgeBase`（含文档解析、目录生成、按名检索） | 已完成（未单独拆出 `KnowledgeDoc` 类，逻辑内聚在 `KnowledgeBase` 内） |
| 4. Prompt 与 `[KNOWLEDGE]` 两阶段接入 | 已完成（`AICommandExecutor.getKnowledgeBaseSection()` + `HelloWorldMod` 中的解析/回填/二次请求） |
| 5. RAG 配置（`rag_enabled`/`rag_max_docs`/`rag_max_chars`） | 已完成 |
| 6. JUnit/Mockito 测试与构建验证 | 已完成（`KnowledgeBaseTest.java`、`AICommandExecutorTest.java` 均含相关用例） |
| 7. 更新用户文档 | 待确认：`USER_MANUAL.md`/`USER_MANUAL_EN.md` 是否已同步说明知识库功能，需单独核实 |

## 七、已知局限（实施后仍需关注）

1. 两阶段请求会增加建筑/红石问题的耗时与 API 成本。
2. 取消、超时和流式输出在第二轮请求中的表现建议在实际使用中持续观察。
3. 纯 AI 选择可能漏选资料，需依靠清晰的标题、关键词和摘要降低风险。
4. 蓝图范例可能占用大量上下文，已由 `rag_max_docs`/`rag_max_chars` 注入上限控制，但上限具体取值是否合适仍可根据实际 token 消耗调整。

## 八、更新记录

- 2026-09-25：文档状态由"未实施/0%"更新为"已实施"，修正配置项命名（设计稿 `rag.*` 点号命名 → 实际 `rag_*` 下划线命名），并根据代码现状更新第六、七节。原设计描述（第二至五节）与实际实现基本一致，保留作为机制说明，未逐句改写。
