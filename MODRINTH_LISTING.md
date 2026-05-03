# Modrinth 上架信息

> 以下内容可直接用于 Modrinth 项目创建页面填写。

---

## 基本信息

| 字段 | 填写内容 |
|------|----------|
| **项目名称 (Name)** | AI Builder - Minecraft AI Assistant |
| **项目 Slug (URL)** | `ai-builder` |
| **摘要 (Summary)** | 游戏内 AI 助手，支持自然语言对话、AI 指令建造、NBT/蓝图结构管理、选区导出、联网搜索等功能 |
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
# AI Builder - Minecraft AI 助手模组

一个功能丰富的 Fabric 模组，将 AI 对话能力深度集成到 Minecraft 中。你可以通过自然语言与 AI 聊天、让 AI 帮你建造建筑、管理 NBT 结构文件、导出选区，甚至让 AI 联网搜索信息。

## ✨ 核心功能

### 🤖 AI 对话与指令执行
- **`/ai <消息>`** — 在游戏内与 AI 对话，AI 可以理解中文和英文
- AI 可以通过自然语言指令在游戏中执行操作：
  - 放置/填充方块、清除区域
  - 给予物品、生成实体
  - 设置时间和天气、传送玩家
- 支持**多轮对话记忆**，AI 能记住上下文
- 支持**截图发送**，AI 可以"看到"你的游戏画面并做出回应

### 🏗️ 结构管理系统
- **NBT 结构浏览器** — 类似 Litematica 的图形界面，浏览、搜索、放置 `.nbt` 结构文件
- **TXT 蓝图系统** — 支持两种蓝图格式：
  - V1 格式：字符网格 + 图例映射，直观易读
  - V2 格式 (MCBLUEPRINT v2)：显式坐标 + 完整 block state 属性，精确还原
- **`/ai build <名称>`** — 通过命令快速建造已加载的蓝图
- **`/ai blueprints`** — 列出所有可用蓝图

### 📐 选区工具
- 图形化选区界面，支持两点选区
- **选区分析** — 统计选区内方块种类和数量
- **导出为 NBT** — 将选区导出为 `.nbt` 文件（含方块实体数据，如箱子内容、告示牌文字）
- **导出为蓝图** — 将选区导出为 V2 格式蓝图文本
- 选区实时渲染高亮显示

### 🌐 联网能力
- **联网搜索** — AI 可以通过 Tavily API 搜索最新信息
- **网页抓取** — AI 可以访问指定 URL 获取网页内容
- 可根据网页内容自动生成建筑指令

### ⚙️ 配置与设置
- **游戏内设置界面** — 按 `K` 键打开，可切换：
  - AI 聊天截图开关
  - 多轮对话记忆开关
  - 联网搜索开关
- **`/aiconfig`** — 命令行配置 API 地址、密钥、模型等
- **`/ainew`** — 清空对话历史，开始新话题
- **`/ailog`** — 将模组日志转发到聊天框，方便调试

## 📋 命令列表

| 命令 | 说明 |
|------|------|
| `/ai <消息>` | 与 AI 对话 |
| `/ai build <名称>` | 建造指定蓝图 |
| `/ai blueprints` | 列出所有蓝图 |
| `/ai reload_blueprints` | 重新加载蓝图 |
| `/ainew` | 清空对话历史 |
| `/aiconfig show` | 查看当前配置 |
| `/aiconfig <键> <值>` | 修改配置项 |
| `/aipos` | 显示当前坐标 |
| `/ailog [on/off]` | 控制日志显示 |
| `/ainbt` | NBT 相关命令 |

## 🔧 配置说明

首次启动后会在 `config/helloworld.properties` 生成配置文件：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `api_base_url` | AI API 地址 | Kimi API |
| `api_key` | API 密钥 | 需要自行填写 |
| `model` | AI 模型名称 | kimi-for-coding |
| `screenshot_enabled` | 截图功能 | true |
| `context_enabled` | 多轮对话 | true |
| `web_search_enabled` | 联网搜索 | true |
| `tavily_api_key` | Tavily 搜索 API 密钥 | 空 |

> ⚠️ 使用前需要配置你自己的 AI API 密钥。支持兼容 OpenAI 格式的 API。

## 📁 文件结构

- `nbts/` — 存放 `.nbt` 结构文件，支持子文件夹分类
- `txts/` — 存放 `.txt` 蓝图文件
- `config/helloworld.properties` — 模组配置文件

## 🎮 快捷键

| 按键 | 功能 |
|------|------|
| `K` | 打开模组设置界面 |

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/) (≥ 0.15.0)
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将模组 JAR 文件放入 `.minecraft/mods/` 目录
4. 启动游戏，按 `K` 键打开设置，或使用 `/aiconfig` 配置 API 密钥
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
