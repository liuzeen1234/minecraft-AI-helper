# AI Builder 用户手册

> 版本 1.6.0 | Minecraft 1.20.4 | Fabric Mod

## 安装与环境要求

| 项目 | 要求 |
|---|---|
| Minecraft 版本 | 1.20.4 |
| Mod 加载器 | Fabric Loader ≥ 0.15.0 |
| Java 版本 | ≥ 17 |
| 前置 Mod | Fabric API（必须） |

1. 安装 Fabric Loader 和 Fabric API。
2. 将 `ai-builder-1.6.0.jar` 放入 `.minecraft/mods/`。
3. 启动游戏，按 `K` 打开 AI Builder 设置。

## 首次配置

首次启动时，Mod 会创建 `ai-helper/config/ai-builder.properties`。使用 AI 前必须设置 API 密钥。

```text
/aiconfig api_base_url 你的 API 地址
/aiconfig api_key 你的 API 密钥
/aiconfig model 你的模型名称
```

也可以按 `K` → **AI 聊天设置** → **AI API 设置**进行可视化配置。Mod 支持 OpenAI 兼容接口和 Anthropic Messages 接口；默认 `api_format=auto` 会根据地址自动选择格式，必要时可直接编辑配置文件指定 `openai` 或 `anthropic`，然后执行 `/aiconfig reload`。

## 快捷键

| 按键 | 功能 |
|---|---|
| `K`（默认，可重绑） | 打开 Mod 设置主菜单 |
| `Enter` | 在 AI 聊天界面发送消息；在结构浏览器放置结构或进入文件夹 |
| `Escape` | 关闭当前 Mod 界面 |
| `Page Up` / `Page Down`、鼠标滚轮 | 滚动聊天或文件列表 |
| `↑` / `↓`、`Backspace`、`Delete` | 在统一结构浏览器导航、返回上级或删除选中文件 |

`K` 可在原版「选项 → 控制 → 按键绑定」的 **AI Builder** 分类中修改。其余按键是界面内固定操作。

## 命令

### AI 与工具

| 命令 | 说明 |
|---|---|
| `/ai <消息>` | 与 AI 对话；AI 可按回复中的受支持指令执行建造操作 |
| `/aiblueprints` | 列出已加载的 TXT 蓝图 |
| `/ainew` | 清空对话历史，开始新话题 |
| `/aistop` | 终止当前 AI 请求 |
| `/aireject` | 拒绝 AI 当前所有待确认的操作请求，无需输入请求 ID（等同点击聊天框 [否]，AI 会收到"用户已拒绝"并据此续跑） |

### 配置

| 命令 | 说明 |
|---|---|
| `/aiconfig show` | 显示 API、模型、联网搜索与 Tavily 配置 |
| `/aiconfig api_base_url <值>` | 设置 API 地址 |
| `/aiconfig api_key <值>` | 设置 API 密钥 |
| `/aiconfig model <值>` | 设置模型名称 |
| `/aiconfig tavily_api_key <值>` | 设置 Tavily API 密钥 |
| `/aiconfig reload` | 重新加载手动编辑后的配置文件 |

`/aiconfig` 不是通用的“任意键值”命令；`screenshot_enabled`、`context_enabled`、`stream_output_enabled` 请在设置界面调整，`api_format`、`rag_*` 请直接编辑配置文件后重载。界面显示语言不可配置，自动跟随当前 Minecraft 游戏语言。

### 结构

NBT / Litematica 结构的浏览、查看与放置请使用图形化浏览器：`K` → **加载结构**。（原 `/ainbt` 命令已移除。）

> 聊天框日志显示和“生成测试日志”由可选的 debug-menu 模组提供，不属于 AI Builder 命令。

## 功能详解

### AI 聊天与建造

按 `K` → **AI 聊天**，或使用 `/ai <消息>`。聊天界面支持最多 20 条消息（10 轮）上下文、最多 1024 字符输入、TXT 蓝图引用、清除历史、取消请求及增量流式显示。截图功能默认关闭；开启后会在发送时截取缩放后的游戏画面。

AI 可放置、填充或清除方块，给予物品，生成实体，设置时间/天气和生成/放置蓝图，还能主动查询指定区域的地形（`[QUERY_REGION]`）。单次填充或清除建议不超过约 10,000 方块（无硬性限制，超出可能影响服务器性能）、给予最多 64 件物品、生成最多 20 个实体；单次地形查询建议不超过约 30,000 方块体积，范围过大请拆分成多次查询。`execute_command` 不会自动执行任何命令——AI 只能把建议的命令文本预填到你的聊天输入框，需要你自己看清楚内容后手动按回车才会发送，真正执行时的权限完全由你自己当前的游戏权限决定；不要将其描述为可自动执行或提升权限执行原版命令。

V1/V2 蓝图坐标为相对坐标：X 向东、Y 向上、Z 向南，原点在玩家脚下。V1、MCBLUEPRINT v2 和 v3 TXT 格式均可加载，且支持自定义放置原点（相对玩家朝向偏移，或绝对坐标），放置前会弹出确认界面供你编辑原点坐标。

### RAG 知识库

资料库位于 `ai-helper/knowledge/`，默认包含 `info/`（资料）、`structure/`（结构参考）和 `workflow.txt`（AI 工作流程，默认为空，供用户自定义）。基础资料使用规则固定写入提示词。每次 AI 调用都会重新读取 `workflow.txt` 全文和完整文件夹/文件结构并加入提示词，修改后无需重启。资料正文可通过 `[KNOWLEDGE_FILE]相对路径[/KNOWLEDGE_FILE]` 按需读取；`info/` 中的 Markdown 文档也支持 `[KNOWLEDGE]文档名[/KNOWLEDGE]` 查询。工作流程和目录注入不受 `rag_enabled` 控制，资料读取工具仍受该设置控制。默认不附带百科资料；若旧的 `basic_info_en/` 存在且 `info/` 尚不存在，会将旧目录迁移为 `info/`，保留原有文件。

### AI 权限设置

按 `K` → **AI 聊天设置** → **AI 权限设置**（从原 AI 聊天设置中拆分出的独立页面）可控制：

- **允许 AI 使用原版命令**（`vanilla_commands_enabled`，默认开启）：关闭后 system prompt 不再包含 `execute_command` 相关说明，AI 只能使用 mod 自带的具体功能（放置方块、给物品、生成实体、蓝图等），即使 AI 仍尝试生成该指令也会被直接拒绝。
- **工具批准**（`tool_approval_mode`，新安装默认 `as_needed`）：点击在「无需批准 / 按需批准 / 均需批准」之间循环切换。按需模式下只读工具免批准，修改状态、蓝图和摄像机截图需要批准；均需模式下只读工具也要批准。原版命令在所有模式下均预填聊天框，由玩家手动按回车执行。批准请求 60 秒未处理自动取消。旧确认开关关闭映射为无需批准，开启映射为均需批准。
- **最大工具调用轮数**（原聊天设置中的 `max_tool_rounds`，现迁移到本页面）：控制多轮 agentic 工具调用循环的上限，0 表示禁用多轮调用。

> 开发者备注：聊天框 [是]/[否] 按钮通过 `ClickEvent.RUN_COMMAND` 执行命令——`[是]` → `/aiconfirm <请求ID>`，`[否]` → `/aireject <请求ID>`，命令处理器调用 `PendingActionConfirmation.resolve(...)`。`/aireject` 不带参数则拒绝当前玩家所有待确认请求。

摄像机截图（`camera_shot_enabled`，默认开启）可在 AI 权限设置中独立控制。AI 使用 `[CAMERA_SHOT]x,y,z,yaw,pitch[/CAMERA_SHOT]` 从指定世界坐标与视角截图，用于视觉自查；此开关独立于发送消息时的 `screenshot_enabled`。同一页面的**打开资料库文件夹**按钮会打开当前游戏实例的 `ai-helper/knowledge/`（开发客户端为 `run/ai-helper/knowledge/`）。

### 统一结构浏览器

按 `K` → **加载结构**可浏览、搜索、删除和放置以下文件，支持子文件夹：

- `ai-helper/structures/nbts/`：标准 `.nbt` 结构；
- `ai-helper/structures/litematic/`：`.litematic` 结构；
- `ai-helper/structures/txts/`：V1/V2/V3 `.txt` 蓝图。

NBT/Litematica 放置会跳过 `air` 与 `structure_void`，并保留方块状态、方块实体数据和结构实体；旧告示牌数据会转换为 1.20+ 格式。AI 生成的 TXT 蓝图保存到 `ai-helper/structures/txts/ai-generated/`。

在结构浏览器顶部点击**转换**，可批量将 `.nbt`、`.litematic`、`.txt` 文件互相转换为任意目标格式（NBT/Litematic/TXT 六个方向均支持）：选择若干源文件、目标格式和目标文件夹后即可转换，输出文件与源文件同名（扩展名按目标格式替换）。转换会完整保留方块状态、容器物品（箱子、桶等）以及告示牌正反面文字（含旧版 1.19 及以前的 `Text1`~`Text4` 格式），转换结果可直接用对应格式的放置管线使用或交给 AI 编辑。

### 选区工具

按 `K` → **选区工具**。设置两个对角坐标（或使用当前位置）并确认后，游戏会显示高亮框；关闭界面时草稿会保留。分析/导出界面可统计方块并在服务端导出：

- **TXT / MCBLUEPRINT v2**：始终包含容器物品和非空告示牌正反面文字；
- **NBT**：保留方块实体数据；
- **Litematica**：保留方块实体数据，可选择是否包含实体。

### 联网搜索、网页抓取与截图

联网搜索需要 `web_search_enabled=true` 且已配置 `tavily_api_key`。AI 判断需要搜索时最多取得 5 条 Tavily 结果；搜索超时为 120 秒。AI 也可根据回复标签抓取指定网页，抓取超时为 30 秒且网页文本会截断为最多 8,000 字符。

开启 `screenshot_enabled` 后，聊天界面发送消息会在关闭界面后延迟 2 tick 截图，图片最大宽度为 512 px，临时保存于 `ai-helper/screenshots/ai_chat_temp.png`；`/ai` 截图路径为 `ai_temp.png`。

## 配置项

配置文件：`ai-helper/config/ai-builder.properties`

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `api_base_url` | `https://api.kimi.com/coding/v1/messages` | API 端点 |
| `api_key` | `your-api-key-here` | API 密钥，必须设置 |
| `model` | `kimi-for-coding` | 模型名称 |
| `screenshot_enabled` | `false` | 是否在 AI 聊天中发送截图 |
| `context_enabled` | `true` | 是否启用多轮对话上下文 |
| `web_search_enabled` | `true` | 是否允许 Tavily 联网搜索 |
| `tavily_api_key` | 空 | Tavily API 密钥 |
| `stream_output_enabled` | `true` | 是否增量显示 AI 回复 |
| `max_tool_rounds` | `3` | 多轮工具调用循环的最大轮数，0=禁用多轮 |
| `vanilla_commands_enabled` | `true` | 是否允许 AI 使用原版命令（`execute_command`） |
| `tool_approval_mode` | `as_needed` | `never` 无需批准、`as_needed` 按需批准、`always` 均需批准；旧 `confirm_before_execute_enabled` 自动迁移 |
| `camera_shot_enabled` | `true` | 是否允许 AI 摄像机截图自查，独立于消息截图 |
| `api_format` | `auto` | `auto`、`openai` 或 `anthropic` |
| `rag_enabled` | `true` | 是否启用资料读取与目录查询（`[KNOWLEDGE]`、`[KNOWLEDGE_TREE]`、`[KNOWLEDGE_FILE]`） |
| `rag_max_docs` | `8` | 单次 `[KNOWLEDGE]` 请求最多允许点名的文档数量 |
| `rag_max_chars` | `20000` | 回填给 AI 的知识库正文总字符数上限 |

按 `K` → **AI 聊天设置**可修改截图、上下文、联网搜索、流式输出等布尔开关；`K` → **AI 聊天设置** → **AI 权限设置**可修改原版命令开关、工具批准模式和最大工具调用轮数；API 设置界面可修改 API 地址、密钥、模型和 Tavily 密钥。手动编辑任何配置后使用 `/aiconfig reload` 生效。界面显示语言无配置项，自动跟随当前 Minecraft 游戏语言。

## 蓝图格式

推荐使用 V2：

以下示例基于运行目录中的 `run/ai-helper/structures/txts/example2.txt`；将蓝图名称设为 `example`，其余内容保持不变：

```text
# MCBLUEPRINT v2
# name: example
# size: 5x6x5
# origin: 0,0,0
# 坐标原点在结构西北角最低层，x向东，y向上，z向南
# 格式：x,y,z  block_id  [key=value ...]

## BLOCKS

# --- 第 1 层 (y=0) ---
2,0,2   oak_log   axis=y
3,0,2   short_grass
0,0,3   short_grass
2,0,4   short_grass
4,0,4   short_grass

# --- 第 2 层 (y=1) ---
2,1,2   oak_log   axis=y

# --- 第 3 层 (y=2) ---
0,2,0   oak_leaves   distance=4   persistent=false   waterlogged=false
1,2,0   oak_leaves   distance=3   persistent=false   waterlogged=false
2,2,0   oak_leaves   distance=2   persistent=false   waterlogged=false
3,2,0   oak_leaves   distance=3   persistent=false   waterlogged=false
0,2,1   oak_leaves   distance=3   persistent=false   waterlogged=false
1,2,1   oak_leaves   distance=2   persistent=false   waterlogged=false
2,2,1   oak_leaves   distance=1   persistent=false   waterlogged=false
3,2,1   oak_leaves   distance=2   persistent=false   waterlogged=false
4,2,1   oak_leaves   distance=3   persistent=false   waterlogged=false
0,2,2   oak_leaves   distance=2   persistent=false   waterlogged=false
1,2,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,2,2   oak_log   axis=y
3,2,2   oak_leaves   distance=1   persistent=false   waterlogged=false
4,2,2   oak_leaves   distance=2   persistent=false   waterlogged=false
0,2,3   oak_leaves   distance=3   persistent=false   waterlogged=false
1,2,3   oak_leaves   distance=2   persistent=false   waterlogged=false
2,2,3   oak_leaves   distance=1   persistent=false   waterlogged=false
3,2,3   oak_leaves   distance=2   persistent=false   waterlogged=false
4,2,3   oak_leaves   distance=3   persistent=false   waterlogged=false
1,2,4   oak_leaves   distance=3   persistent=false   waterlogged=false
2,2,4   oak_leaves   distance=2   persistent=false   waterlogged=false
3,2,4   oak_leaves   distance=3   persistent=false   waterlogged=false
4,2,4   oak_leaves   distance=4   persistent=false   waterlogged=false

# --- 第 4 层 (y=3) ---
1,3,0   oak_leaves   distance=3   persistent=false   waterlogged=false
2,3,0   oak_leaves   distance=2   persistent=false   waterlogged=false
3,3,0   oak_leaves   distance=3   persistent=false   waterlogged=false
0,3,1   oak_leaves   distance=3   persistent=false   waterlogged=false
1,3,1   oak_leaves   distance=2   persistent=false   waterlogged=false
2,3,1   oak_leaves   distance=1   persistent=false   waterlogged=false
3,3,1   oak_leaves   distance=2   persistent=false   waterlogged=false
4,3,1   oak_leaves   distance=3   persistent=false   waterlogged=false
0,3,2   oak_leaves   distance=2   persistent=false   waterlogged=false
1,3,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,3,2   oak_log   axis=y
3,3,2   oak_leaves   distance=1   persistent=false   waterlogged=false
4,3,2   oak_leaves   distance=2   persistent=false   waterlogged=false
0,3,3   oak_leaves   distance=3   persistent=false   waterlogged=false
1,3,3   oak_leaves   distance=2   persistent=false   waterlogged=false
2,3,3   oak_leaves   distance=1   persistent=false   waterlogged=false
3,3,3   oak_leaves   distance=2   persistent=false   waterlogged=false
4,3,3   oak_leaves   distance=3   persistent=false   waterlogged=false
0,3,4   oak_leaves   distance=4   persistent=false   waterlogged=false
1,3,4   oak_leaves   distance=3   persistent=false   waterlogged=false
2,3,4   oak_leaves   distance=2   persistent=false   waterlogged=false
3,3,4   oak_leaves   distance=3   persistent=false   waterlogged=false

# --- 第 5 层 (y=4) ---
2,4,1   oak_leaves   distance=1   persistent=false   waterlogged=false
1,4,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,4,2   oak_log   axis=y
3,4,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,4,3   oak_leaves   distance=1   persistent=false   waterlogged=false

# --- 第 6 层 (y=5) ---
2,5,1   oak_leaves   distance=2   persistent=false   waterlogged=false
1,5,2   oak_leaves   distance=2   persistent=false   waterlogged=false
2,5,2   oak_leaves   distance=1   persistent=false   waterlogged=false
3,5,2   oak_leaves   distance=2   persistent=false   waterlogged=false
2,5,3   oak_leaves   distance=2   persistent=false   waterlogged=false
```

V2 的方块行格式为 `x,y,z   方块ID   [属性=值 ...]`。`# name:`、`# size:` 与 `# origin:` 为可选元数据，`#` 开头的行是注释。

V3 使用 `# MCBLUEPRINT v3` 头部，方块行语法与 V2 相同，但坐标为世界绝对坐标；不支持原点平移，`# origin:` 会被忽略。V1/V2 仍使用相对坐标。手动放置 V3 时，放置原点固定为 `0,0,0`，不可编辑，且不显示“回到玩家位置”按钮；始终按文件中的世界绝对坐标放置。


## 文件目录

```text
.minecraft/
├── ai-helper/
│   ├── config/ai-builder.properties
│   ├── structures/
│   │   ├── nbts/
│   │   ├── litematic/
│   │   └── txts/
│   │       └── ai-generated/
│   ├── knowledge/
│   │   ├── info/
│   │   ├── structure/
│   │   └── workflow.txt
│   └── screenshots/
│       ├── ai_temp.png
│       └── ai_chat_temp.png
└── mods/ai-builder-1.6.0.jar
```

所有结构目录均支持任意深度的子文件夹。

## 常见问题与注意事项

**AI 没有回复：** 使用 `/aiconfig show` 检查密钥和 API 地址；确认网络可用。可选 debug-menu 能提供聊天日志辅助排查。

**如何切换流式输出：** 使用 `K` → **AI 聊天设置**；没有对应的 `/aiconfig stream_output_enabled` 命令。界面显示语言不可手动切换，会自动跟随当前 Minecraft 游戏语言。

**手动修改配置后：** 执行 `/aiconfig reload`，无需重启。

**安全与性能：** API 密钥保存在本地配置文件；AI 操作不可撤销，应先备份重要存档。联网搜索和网页抓取会向外部服务发送请求。大型结构放置可能短暂卡顿。

## 许可证

MIT License

**作者：** liuzeen1234  
**源码：** https://github.com/liuzeen1234/minecraft-AI-helper
