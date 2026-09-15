# RAG 知识库实施计划（未实施）

> 状态（2026-09-15、AI Builder 1.4.0）：**代码实现 0%**。本文件是待确认的设计计划，不是当前 Mod 功能说明。
>
> 当前发布版没有 `KnowledgeDoc`、`KnowledgeBase`、`[KNOWLEDGE]` 标签处理、`rag.*` 配置、`ModPaths.getKnowledgeDir()`、`ai-helper/knowledge/` 目录或默认知识库资源释放逻辑。AI 目前仍使用代码内置的提示词与既有 `[SEARCH]` / `[FETCH]` 流程。
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

## 四、预期代码改动（未实施）

### 新增

- `KnowledgeDoc`：相对路径、标题、分类、关键词、摘要和正文的数据类；
- `KnowledgeBase`：递归读取 Markdown、解析 front matter、构建目录、按 AI 点名检索正文并执行上限控制；
- 默认资源释放逻辑：仅在 `knowledge/` 为空时复制默认文档，且不得覆盖用户修改。

### 修改

- `ModPaths`：新增 `getKnowledgeDir()`；
- `AICommandExecutor.getSystemPrompt()`：保留协议、建筑硬规则、`[ACTION]`、`[SEARCH]` 和 `[FETCH]` 规则，加入知识库目录与 `[KNOWLEDGE]` 用法；
- `HelloWorldMod`：解析 `[KNOWLEDGE]`，读取正文并发起第二轮请求；
- `ModConfig`：加入 RAG 开关和注入上限。

拟议配置：

```properties
rag.enabled=true
rag.maxDocs=8
rag.maxChars=20000
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
| 1. 编写/迁移默认知识库文档 | 未开始 |
| 2. `getKnowledgeDir()` 与首次释放 | 未开始 |
| 3. `KnowledgeDoc` 与 `KnowledgeBase` | 未开始 |
| 4. Prompt 与 `[KNOWLEDGE]` 两阶段接入 | 未开始 |
| 5. RAG 配置 | 未开始，需先确认 properties 改动 |
| 6. JUnit/Mockito 测试与构建验证 | 未开始 |
| 7. 更新用户文档 | 未开始 |

## 七、已知局限（待实施时验证）

1. 两阶段请求会增加建筑/红石问题的耗时与 API 成本。
2. 需要验证取消、超时和流式输出对第二轮请求同样有效。
3. 纯 AI 选择可能漏选资料，需依靠清晰的标题、关键词和摘要降低风险。
4. 蓝图范例可能占用大量上下文，必须由注入上限控制。
