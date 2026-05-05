# Modrinth 上架信息

> 以下内容可直接用于 Modrinth 项目创建页面填写。

---

## 基本信息

| 字段 | 填写内容 |
|------|----------|
| **项目名称 (Name)** | AI Builder - Minecraft AI Assistant |
| **项目 Slug (URL)** | `ai-builder` |
| **摘要 (Summary)** | In-game AI assistant mod with natural language chat, AI-powered building, NBT/blueprint structure management, selection export, and web search. / 游戏内 AI 助手，支持自然语言对话、AI 指令建造、NBT/蓝图结构管理、选区导出、联网搜索等功能 |
| **类别 (Categories)** | Utility, Management |
| **许可证 (License)** | MIT |
| **客户端/服务端 (Side)** | Both (客户端 + 服务端) |

---

## 版本信息

| 字段 | 填写内容 |
|------|----------|
| **版本号** | 1.0.0 |
| **Minecraft 版本** | 1.20.4 |
| **模组加载器** | Fabric |
| **Fabric Loader 版本** | ≥ 0.15.0 |
| **Java 版本** | ≥ 17 |
| **依赖** | Fabric API (必需) |

---

## 项目描述 (Description) — Markdown 格式

以下内容直接粘贴到 Modrinth 的 Description 编辑框：

```markdown
# AI Builder - Minecraft AI Assistant Mod

A feature-rich Fabric mod that deeply integrates AI conversational capabilities into Minecraft. You can chat with AI using natural language, have AI build structures for you, manage NBT structure files, export selections, and even let AI search the web for information.

---

# AI Builder - Minecraft AI 助手模组

一个功能丰富的 Fabric 模组，将 AI 对话能力深度集成到 Minecraft 中。你可以通过自然语言与 AI 聊天、让 AI 帮你建造建筑、管理 NBT 结构文件、导出选区，甚至让 AI 联网搜索信息。

## ✨ Features / 核心功能

### 🤖 AI Chat & Command Execution / AI 对话与指令执行
- **`/ai <message>`** — Chat with AI in-game; AI understands both Chinese and English
- AI can execute in-game operations via natural language commands:
  - Place/fill blocks, clear areas
  - Give items, spawn entities
  - Set time and weather, teleport players
- **Multi-turn conversation memory** — AI remembers context
- **Screenshot support** — AI can "see" your game screen and respond accordingly

### 🏗️ Structure Management / 结构管理系统
- **NBT Structure Browser** — A graphical interface similar to Litematica for browsing, searching, and placing `.nbt` structure files
- **TXT Blueprint System** — Supports two blueprint formats:
  - V1: Character grid + legend mapping, intuitive and readable
  - V2 (MCBLUEPRINT v2): Explicit coordinates + full block state properties, precise reproduction
- **`/ai build <name>`** — Quickly build a loaded blueprint via command
- **`/ai blueprints`** — List all available blueprints

### 📐 Selection Tools / 选区工具
- Graphical selection interface with two-point selection
- **Selection Analysis** — Count block types and quantities within selection
- **Export to NBT** — Export selection as `.nbt` file (includes block entity data like chest contents, sign text)
- **Export to Blueprint** — Export selection as V2 format blueprint text
- Real-time selection highlight rendering

### 🌐 Web Capabilities / 联网能力
- **Web Search** — AI can search for the latest information via Tavily API
- **Web Scraping** — AI can access specified URLs to fetch web content
- Can automatically generate building commands based on web content

### ⚙️ Configuration & Settings / 配置与设置
- **In-game Settings UI** — Press `K` to open, toggle:
  - AI chat screenshot
  - Multi-turn conversation memory
  - Web search
- **`/aiconfig`** — Configure API URL, key, model via command
- **`/ainew`** — Clear conversation history, start a new topic
- **`/ailog`** — Forward mod logs to chat for debugging

## 📋 Commands / 命令列表

| Command | Description |
|---------|-------------|
| `/ai <message>` | Chat with AI |
| `/ai build <name>` | Build a specified blueprint |
| `/ai blueprints` | List all blueprints |
| `/ai reload_blueprints` | Reload blueprints |
| `/ainew` | Clear conversation history |
| `/aiconfig show` | View current configuration |
| `/aiconfig <key> <value>` | Modify configuration |
| `/aipos` | Show current coordinates |
| `/ailog [on/off]` | Toggle log display |
| `/ainbt` | NBT-related commands |

## 🔧 Configuration / 配置说明

A configuration file will be generated at `config/helloworld.properties` on first launch:

| Config Key | Description | Default |
|------------|-------------|---------|
| `api_base_url` | AI API endpoint | Kimi API |
| `api_key` | API key | Must be set manually |
| `model` | AI model name | kimi-for-coding |
| `screenshot_enabled` | Screenshot feature | true |
| `context_enabled` | Multi-turn conversation | true |
| `web_search_enabled` | Web search | true |
| `tavily_api_key` | Tavily search API key | empty |

> ⚠️ You need to configure your own AI API key before use. Supports any OpenAI-compatible API.

## 📁 File Structure / 文件结构

- `nbts/` — Store `.nbt` structure files, supports subfolder organization
- `txts/` — Store `.txt` blueprint files
- `config/helloworld.properties` — Mod configuration file

## 🎮 Keybinds / 快捷键

| Key | Function |
|-----|----------|
| `K` | Open mod settings UI |

## 📦 Installation / 安装

1. Install [Fabric Loader](https://fabricmc.net/) (≥ 0.15.0)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the mod JAR file into `.minecraft/mods/` directory
4. Launch the game, press `K` to open settings, or use `/aiconfig` to configure your API key
```

---

## 上传前的 Checklist

- [ ] 运行 `./gradlew build` 生成 JAR 文件
- [ ] 在 `build/libs/` 中找到 `hello-world-mod-1.0.0.jar`（不要上传 `-sources.jar`）
- [ ] 准备一张 512x512 的模组图标 PNG
- [ ] 准备 2-4 张游戏内截图，建议包含：
  - AI 对话界面截图
  - NBT 结构浏览器界面
  - 选区工具使用效果
  - AI 建造建筑的效果
- [ ] 在 Modrinth 注册账号
- [ ] 创建 GitHub 仓库并推送代码（Modrinth 建议提供源码链接）

---

## 建议优化项（上架前可选）

1. **模组 ID 和名称**：当前 `fabric.mod.json` 中的 ID 是 `helloworld`，名称是 "Hello World Mod"。建议改为更有辨识度的名称，如 `ai-builder` / "AI Builder"
2. **描述**：当前描述是"一个简单的模组，玩家加入时在聊天框输出 Hello World"，已经不能反映实际功能了
3. **作者**：当前是 "Developer"，建议改为你的真实用户名
4. **联系方式**：`fabric.mod.json` 中的 `contact` 字段为空，建议添加 GitHub 链接
