# AI Builder 用户手册

> 版本 1.4.0 | Minecraft 1.20.4 | Fabric Mod

## 安装与环境要求

| 项目 | 要求 |
|---|---|
| Minecraft 版本 | 1.20.4 |
| Mod 加载器 | Fabric Loader ≥ 0.15.0 |
| Java 版本 | ≥ 17 |
| 前置 Mod | Fabric API（必须） |

1. 安装 Fabric Loader 和 Fabric API。
2. 将 `ai-builder-1.4.0.jar` 放入 `.minecraft/mods/`。
3. 启动游戏，按 `K` 打开 AI Builder 设置。

## 首次配置

首次启动时，Mod 会创建 `ai-helper/config/ai-builder.properties`。使用 AI 前必须设置 API 密钥。

```text
/aiconfig api_base_url 你的 API 地址
/aiconfig api_key 你的 API 密钥
/aiconfig model 你的模型名称
```

也可以按 `K` → **AI 聊天设置**进行可视化配置。Mod 支持 OpenAI 兼容接口和 Anthropic Messages 接口；默认 `api_format=auto` 会根据地址自动选择格式，必要时可直接编辑配置文件指定 `openai` 或 `anthropic`，然后执行 `/aiconfig reload`。

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
| `/ai blueprints` | 列出已加载的 TXT 蓝图 |
| `/ai reload_blueprints` | 重新加载 TXT 蓝图 |
| `/ai test_stairs` | 放置用于调试朝向的楼梯样例 |
| `/ainew` | 清空对话历史，开始新话题 |
| `/aistop` | 终止当前 AI 请求 |
| `/aipos` | 显示当前坐标和维度 |

### 配置

| 命令 | 说明 |
|---|---|
| `/aiconfig show` | 显示 API、模型、联网搜索与 Tavily 配置 |
| `/aiconfig api_base_url <值>` | 设置 API 地址 |
| `/aiconfig api_key <值>` | 设置 API 密钥 |
| `/aiconfig model <值>` | 设置模型名称 |
| `/aiconfig web_search <on/off>` | 开启或关闭联网搜索 |
| `/aiconfig tavily_api_key <值>` | 设置 Tavily API 密钥 |
| `/aiconfig reload` | 重新加载手动编辑后的配置文件 |

`/aiconfig` 不是通用的“任意键值”命令；`screenshot_enabled`、`context_enabled`、`stream_output_enabled` 和 `language` 请在设置界面调整，`api_format` 请直接编辑配置文件后重载。

### 结构命令

| 命令 | 说明 |
|---|---|
| `/ainbt list` | 列出 NBT 与 Litematica 结构文件 |
| `/ainbt info <文件名>` | 显示 NBT 或 Litematica 结构详情 |
| `/ainbt all` | 显示全部结构摘要 |
| `/ainbt place <文件名>` | 在玩家脚下放置 NBT 或 Litematica 结构 |

`/ainbt` 没有无参数 GUI。图形化结构管理请使用 `K` → **加载结构**。

> 聊天框日志显示和“生成测试日志”由可选的 debug-menu 模组提供，不属于 AI Builder 命令。

## 功能详解

### AI 聊天与建造

按 `K` → **AI 聊天**，或使用 `/ai <消息>`。聊天界面支持最多 20 条消息（10 轮）上下文、最多 1024 字符输入、TXT 蓝图引用、清除历史、取消请求及增量流式显示。截图功能默认关闭；开启后会在发送时截取缩放后的游戏画面。

AI 可放置、填充或清除方块，给予物品，生成实体，设置时间/天气，传送和生成/放置蓝图。单次填充或清除最多 10,000 方块、给予最多 64 件物品、生成最多 20 个实体。`execute_command` 以权限等级 2 运行，但服务器管理、封禁、踢人、存档停止等危险根命令会被拒绝；不要将其描述为可执行任意原版命令。

蓝图坐标为相对坐标：X 向东、Y 向上、Z 向南，原点在玩家脚下。V1 与 MCBLUEPRINT v2 TXT 格式均可加载。

### 统一结构浏览器

按 `K` → **加载结构**可浏览、搜索、删除和放置以下文件，支持子文件夹：

- `ai-helper/structures/nbts/`：标准 `.nbt` 结构；
- `ai-helper/structures/litematic/`：`.litematic` 结构；
- `ai-helper/structures/txts/`：V1/V2 `.txt` 蓝图。

NBT/Litematica 放置会跳过 `air` 与 `structure_void`，并保留方块状态、方块实体数据和结构实体；旧告示牌数据会转换为 1.20+ 格式。AI 生成的 TXT 蓝图保存到 `ai-helper/structures/txts/ai-generated/`。

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
| `language` | `en_us` | 界面语言：`zh_cn` 或 `en_us` |
| `api_format` | `auto` | `auto`、`openai` 或 `anthropic` |

按 `K` → **AI 聊天设置**可修改四个布尔开关；API 设置界面可修改 API 地址、密钥、模型和 Tavily 密钥。手动编辑任何配置后使用 `/aiconfig reload` 生效。语言可在 `K` → **Mod 语言设置**中切换，但下次客户端启动会重新跟随当前 Minecraft 游戏语言。

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
│   └── screenshots/
│       ├── ai_temp.png
│       └── ai_chat_temp.png
└── mods/ai-builder-1.4.0.jar
```

所有结构目录均支持任意深度的子文件夹。

## 常见问题与注意事项

**AI 没有回复：** 使用 `/aiconfig show` 检查密钥和 API 地址；确认网络可用。可选 debug-menu 能提供聊天日志辅助排查。

**如何切换语言或流式输出：** 分别使用 `K` → **Mod 语言设置**和 `K` → **AI 聊天设置**；没有对应的 `/aiconfig language` 或 `/aiconfig stream_output_enabled` 命令。

**手动修改配置后：** 执行 `/aiconfig reload`，无需重启。

**安全与性能：** API 密钥保存在本地配置文件；AI 操作不可撤销，应先备份重要存档。联网搜索和网页抓取会向外部服务发送请求。大型结构放置可能短暂卡顿。

## 许可证

MIT License

**作者：** liuzeen1234  
**源码：** https://github.com/liuzeen1234/minecraft-AI-helper
