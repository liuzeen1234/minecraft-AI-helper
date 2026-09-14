# RAG 知识库实施计划（关键词/目录检索 v1 · 纯 AI 选择）

> 目标：为 AI 建筑/红石生成提供本地知识库（RAG），提升建筑质量。
> 目标 MC 版本统一为 **Java 1.20.4**（与 mod 运行版本对齐；参考文件里写的 1.20.1 不采用）。
> 本文件仅为计划，经确认后再开工。

---

## 一、最终确定的决策（已与用户敲定）

| 项 | 决策 |
|---|---|
| 检索方式 | 关键词检索版（不做向量检索，向量版暂缓） |
| 知识与素材边界 | system prompt 只保留**协议**（身份 + 输出格式规则）；方块属性、红石原理、建筑范例等**素材知识**全部移入知识库 |
| 检索机制 | **纯 AI 选择（两阶段检索）**，不做关键词打分兜底 |
| 知识库位置 | 运行目录 `ai-helper/knowledge/`（用户可增删），首次启动从 mod 资源自动释放默认文档 |
| 与 txts 关系 | 完全分离：知识库走 `knowledge/`，用户蓝图仍走 `txts/`，互不干扰 |
| 建筑设计硬规则（墙高/门/楼梯/屋顶） | **保留在 system prompt**（每次建筑都必须遵守，不进 RAG，不单独做 building-rules 文档） |
| 检索触发面 | 每条消息都提供知识库目录，由 AI 决定是否取用 |
| 注入上限 | 设宽（默认 maxDocs=8 / maxChars=20000），保障大型建筑蓝图整篇可注入 |
| 数据来源合规 | 抓取的网页内容仅本地 RAG 使用，不批量公开上传；示例文档由平移现有 prompt 内容 + 自造组成 |
| 提交 | 不自动 git commit，改完留用户检查 |

---

## 二、核心机制：纯 AI 选择（两阶段检索）

仿照现有 `[SEARCH]` / `[FETCH]` 标签的「标签触发 → 系统回填 → 再次请求」链路，新增 `[KNOWLEDGE]` 标签。

```
第 1 轮：用户发消息
  system prompt 中附带「知识库目录」（每篇文档的 title + keywords + 一句话摘要，不含正文）
  提示词明确要求：只挑选与当前需求最接近的少数几篇（例如最多 3~5 篇）
      │
      └─→ AI 判断需要哪些知识 → 输出 [KNOWLEDGE]文档A, 文档B[/KNOWLEDGE]

第 2 轮：系统解析 AI 点名的文档 → 读取其正文 → 回填给 AI → 再次请求
      │
      └─→ AI 拿到正文 → 生成 [BLUEPRINT] / [ACTION] / 回答
```

- **纯 AI 选择**：不再做关键词打分自动注入。是否取用、取哪些，完全由 AI 依据目录判断。
- **提示词约束**：明确告诉 AI「只选取与当前需求最接近的几篇，不要贪多」，控制注入量与 token。
- **闲聊场景**：AI 判断无需知识则不输出 `[KNOWLEDGE]`，不产生第二轮往返。
- 复用现有标签回填链路，取消按钮 / 超时对二次请求同样生效（实现时验证）。

---

## 三、知识库文档

### 目录结构（`ai-helper/knowledge/`，打包源在 `resources/.../knowledge/`）

```
knowledge/
├─ basic_info/
│   ├─ block-charging-rules.md      方块充能规则（强/弱充能、透明方块）
│   └─ game-mechanics-ticks.md      红石刻 / 游戏刻 / 时序机制
├─ buildings/
│   ├─ simple/
│   │   ├─ cottage-7x7.md           7x7 村庄小屋（迁移现有完整蓝图示例）
│   │   └─ watchtower.md            哨塔（自造）
│   └─ detailed/
│       └─ manor-small.md           小型庄园/城堡（自造，1.20.4，含内饰描述）
├─ redstone_basic/
│   ├─ redstone-wire.md             红石粉
│   ├─ redstone-torch.md            红石火把
│   ├─ redstone-block.md            红石块
│   ├─ repeater.md                  中继器
│   ├─ comparator.md                比较器
│   ├─ piston.md                    活塞 / 粘性活塞
│   ├─ observer.md                  侦测器
│   ├─ dispenser-dropper.md         投掷器 / 投射器
│   ├─ hopper.md                    漏斗
│   ├─ redstone-lamp.md             红石灯
│   ├─ tnt.md                       TNT
│   ├─ inputs.md                    拉杆 / 按钮 / 压力板
│   ├─ copper-bulb.md               铜灯泡
│   └─ sculk-sensor.md              潜影感测体
└─ redstone_advanced/
    ├─ logic-gates.md               逻辑门（NOT/AND/OR/NOR/XOR/XNOR）
    ├─ pulse-circuits.md            脉冲电路
    ├─ clocks.md                    时钟电路（含火把启动注意事项）
    ├─ memory-circuits.md           存储电路（RS/T/D 锁存、计数器）
    ├─ practical-circuits.md        实用电路（农场/分类机/活塞门等）
    └─ complex-machines.md          复杂机器处理规则（需搜索/抓取的类型）
```

> 大部分内容来自**平移现有 system prompt** 的红石与蓝图段落（保证准确度并顺带给 prompt 瘦身）；哨塔、小型庄园为自造。

### 文档格式（YAML front-matter + 正文）

```markdown
---
title: 红石中继器
version: 1.20.4
category: redstone_basic
keywords: [中继器, repeater, 延迟, 信号延长, 单向, 二极管, 锁存, 时钟]
summary: 单向传递信号、可设 1~4 红石刻延迟、可锁定，用于延时/时钟/隔离。
---

# 红石中继器 repeater
方块ID: repeater
Block States: ...
（正文照搬现有 prompt 对应段落）
```

- `summary`：用于目录，一句话。
- `keywords`：辅助 AI 在目录里识别，中英文/同义词写全。
- `version`：统一 1.20.4。

---

## 四、代码改动

### 新增
- `KnowledgeDoc`：数据类（相对路径 / title / category / keywords / summary / body）。
- `KnowledgeBase`（服务端单例）：
  - `loadAll()`：扫描 `knowledge/` 递归读 `.md`，解析 front-matter，建内存列表。
  - `buildCatalog()`：生成目录文本（title + keywords + summary，**不含正文**），注入 system prompt。
  - `retrieveByNames(List<String> names)`：按 AI 点名（title 或文件名）取正文，应用 maxDocs / maxChars 上限。
  - 无关键词打分方法（纯 AI 选择）。

### 修改
- `ModPaths`：新增 `getKnowledgeDir()` → `ai-helper/knowledge/`。
- 首次释放逻辑：启动时若 `knowledge/` 为空，从 `resources/assets/helloworld/knowledge/` 复制默认文档；写入版本标记文件避免覆盖用户改动。
- `AICommandExecutor.getSystemPrompt()` 瘦身：
  - **删除**：红石方块完整参考、红石机制/时序/电路模式、7x7 蓝图示例、BLUEPRINT 里的「常用方块属性大全」。
  - **保留**：身份、BLUEPRINT 格式协议（v2 格式/坐标系/注释规则）、建筑设计硬规则（墙高/门/楼梯/屋顶）、全部 [ACTION] 指令与使用规则、[SEARCH]/[FETCH] 说明、语言指令。
  - **新增**：知识库目录（`buildCatalog()`）+ `[KNOWLEDGE]` 标签用法说明（含「只选最接近的几篇」约束）。
- `HelloWorldMod`：在 [SEARCH]/[FETCH] 的标签解析与回填处，新增 `[KNOWLEDGE]` 同款处理（解析点名 → 取正文 → 回填 → 再请求）。
- `ModConfig` + `ai-builder.properties`：新增 rag 配置（改 properties 前先与用户确认）。

### 配置项（`ai-builder.properties`）
- `rag.enabled=true`
- `rag.maxDocs=8`
- `rag.maxChars=20000`

---

## 五、测试与验证（JUnit + Mockito，`src/test/java/com/example/helloworld/`）
- `KnowledgeBase` 加载与 front-matter 解析
- `buildCatalog()` 目录生成正确、不含正文
- `retrieveByNames()` 按名取正文、上限截断（maxDocs / maxChars）
- `[KNOWLEDGE]` 标签解析（多文档、大小写/空格容错、不存在的名字忽略）
- `./gradlew test` 全通过
- `./gradlew build` 编译通过

---

## 六、执行阶段

- 阶段 0：现状确认 —— 已完成（system prompt 全文、两处注入点、loadReferencedFiles、ModPaths、[SEARCH]/[FETCH] 链路）。
- 阶段 1：编写/平移知识库文档到 `resources/.../knowledge/`。
- 阶段 2：`ModPaths.getKnowledgeDir()` + 首次释放逻辑。
- 阶段 3：`KnowledgeDoc` + `KnowledgeBase`（加载 / 目录 / 按名取正文）。
- 阶段 4：`getSystemPrompt()` 瘦身 + 目录与 [KNOWLEDGE] 说明；`HelloWorldMod` 接入 [KNOWLEDGE] 标签回填。
- 阶段 5：`ModConfig` + `ai-builder.properties` 配置（改 properties 前确认）。
- 阶段 6：测试 `./gradlew test` + 构建 `./gradlew build`。
- 阶段 7：更新 USER_MANUAL / README（仅在用户要求时）；不自动 commit，留用户检查。

---

## 七、已知局限 / 待实现时验证
1. **两阶段往返成本**：建筑/红石类请求会多一次 API 往返（略慢、多一次费用）；闲聊不受影响。
2. **取消/超时对二次请求生效**：复用现有 [SEARCH] 链路，实现时需验证取消按钮与超时对 [KNOWLEDGE] 触发的二次请求同样有效。
3. **AI 漏选风险**：纯 AI 选择下若 AI 未点名所需文档，则该轮无对应知识（v1 接受此局限，不做关键词兜底；靠目录 summary/keywords 写清晰来缓解）。
4. **范例蓝图注入量**：命中范例类文档（如 7x7 小屋）会注入整篇蓝图，靠已设宽的上限保障；不做额外缓解（用户已确认）。
