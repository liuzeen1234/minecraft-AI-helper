# AI Builder

> 一款把 AI 对话能力集成进 Minecraft 的 Fabric Mod。用自然语言与 AI 聊天，让它帮你建造结构、管理 NBT / 蓝图文件、导出选区，还能联网搜索信息 —— 全部在游戏内完成。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](#许可证)

---

## 功能特性

- **AI 聊天与命令执行** — `/ai <消息>` 与 AI 对话，AI 可通过自然语言放置/填充/清除方块、给予物品、生成实体、设置时间天气、传送玩家，甚至执行任意原版命令。支持多轮对话记忆与截图分析。
- **AI 建造** — 用一句话让 AI 生成并放置建筑蓝图，支持相对坐标（前/右/上）与绝对坐标。
- **NBT / Litematica 结构管理** — 图形化浏览、搜索、放置 `.nbt` 与 `.litematic` 结构文件，完整保留箱子内容物、告示牌文字等方块实体数据。
- **TXT 蓝图系统** — 支持 V1（字符网格 + 图例）与 V2（MCBLUEPRINT v2，精确坐标 + 完整方块状态）两种蓝图格式。
- **选区工具** — 两点式图形化选区，实时高亮渲染，可统计方块、导出为 `.nbt`、`.litematic` 或 V2 蓝图文本（含容器内容物与告示牌文字）。
- **联网搜索** — 通过 Tavily API 让 AI 搜索最新信息、抓取网页内容，并据此生成建造指令。
- **游戏内设置界面** — 按 `K` 打开可视化配置，也可用 `/aiconfig` 命令配置 API 地址、密钥和模型。

---

## 环境要求

| 项目 | 要求 |
|------|------|
| Minecraft 版本 | 1.20.4 |
| Mod 加载器 | Fabric Loader ≥ 0.15.0 |
| Java 版本 | ≥ 17 |
| 前置 Mod | [Fabric API](https://modrinth.com/mod/fabric-api)（必须） |

---

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/)（≥ 0.15.0）
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将 `ai-builder-1.3.1.jar` 放入 `.minecraft/mods/` 目录
4. 启动游戏，按 `K` 打开设置或使用 `/aiconfig` 配置你的 API 密钥

---

## 快速开始

首次启动后，Mod 会在 `ai-helper/config/ai-builder.properties` 生成默认配置文件。**你必须配置自己的 AI API 密钥才能使用 AI 功能。** 本 Mod 支持任何 OpenAI 兼容接口（如 OpenAI、Kimi、DeepSeek 等）。

配置方法二选一：

```
# 方法一：游戏内命令
/aiconfig api_base_url 你的API地址
/aiconfig api_key 你的API密钥
/aiconfig model 你的模型名称

# 方法二：按 K 键 → AI 聊天设置，进行可视化配置
```

配置完成后即可开始：

```
/ai 帮我在前方建一栋小木屋
```

---

## 常用命令

| 命令 | 说明 |
|------|------|
| `/ai <消息>` | 与 AI 对话，AI 可自动执行建造等操作 |
| `/ai blueprints` | 列出所有已加载的蓝图 |
| `/ainew` | 清空对话历史，开始新对话 |
| `/aistop` | 终止正在进行的 AI 回复 |
| `/aiconfig show` | 显示当前配置 |
| `/aiconfig reload` | 热加载配置文件 |
| `/ainbt` | 打开 NBT 结构浏览器 |
| `/aipos` | 显示当前玩家坐标 |

> 完整命令、配置项、蓝图格式规范和常见问题请查阅 **[用户手册 (USER_MANUAL.md)](USER_MANUAL.md)**。

---

## 调试指令

以下是上面常用命令中未列出的其余命令，主要用于调试、配置和进阶管理。

| 命令 | 说明 |
|------|------|
| `/ai reload_blueprints` | 重新从磁盘加载蓝图文件 |
| `/ai test_stairs` | 测试放置楼梯方块（调试用） |
| `/aiconfig api_base_url <值>` | 设置 AI API 地址 |
| `/aiconfig api_key <值>` | 设置 AI API 密钥 |
| `/aiconfig model <值>` | 设置 AI 模型名称 |
| `/aiconfig web_search <on/off>` | 开启/关闭联网搜索 |
| `/aiconfig tavily_api_key <值>` | 设置 Tavily 联网搜索 API 密钥 |
| debug-menu 菜单「生成测试日志」 | 触发测试日志，验证聊天框日志显示是否正常（原 `/aitest` 命令已迁移到 debug-menu 调试菜单，默认 M 键） |
| `/ainbt list` | 列出所有 `.nbt` 结构文件 |
| `/ainbt info <文件名>` | 显示指定 NBT 结构的详细信息 |
| `/ainbt all` | 显示所有 NBT 结构的汇总信息 |
| `/ainbt place <文件名>` | 在当前位置放置指定 NBT 结构 |

---

## 快捷键

| 按键 | 功能 |
|------|------|
| `K`（默认，可自定义） | 打开 Mod 设置主菜单 |

---

## 从源码构建

本项目使用 Gradle + Fabric Loom 构建：

```bash
./gradlew build
```

构建产物位于 `build/libs/ai-builder-<version>.jar`（上传发布时请忽略 `-sources.jar`）。

其他常用任务：

```bash
./gradlew runClient   # 启动开发客户端
./gradlew test        # 运行 JUnit + Mockito 单元测试
```

---

## 文档

- **[用户手册 (USER_MANUAL.md)](USER_MANUAL.md)** — 完整功能详解、配置说明、蓝图格式与 FAQ
- **[更新日志 (CHANGELOG.md)](CHANGELOG.md)** — 版本更新记录
- **[Modrinth 发布信息 (MODRINTH_LISTING.md)](MODRINTH_LISTING.md)** — 英文项目描述与发布清单

---

## 隐私与安全提示

- API 密钥存储在本地配置文件中，请勿分享你的配置文件。
- AI 执行的操作（放置方块、填充等）**不可撤销**，重要建筑附近操作前建议备份存档。
- 联网搜索和网页抓取会向外部服务器发送请求，请注意隐私。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

**作者：** liuzeen1234
**源码：** https://github.com/liuzeen1234/minecraft-AI-helper
