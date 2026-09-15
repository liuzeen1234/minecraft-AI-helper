# MC RAG 候选素材说明（当前未接入 Mod）

本目录保存为未来 RAG 功能准备的**离线候选素材**，按《参考文件.txt》的分类组织。它不位于 `src/main/resources/assets/helloworld/knowledge/`，当前代码也没有加载或检索逻辑；因此：

- 当前 AI Builder 不会自动复制或读取此目录；
- 这些 Markdown 不会自动注入给 AI；
- 本目录不构成随 Mod 发布的知识库，也不表示任何素材可以再分发；
- RAG 的拟议实现和实际状态请见 [`../RAG_PLAN.md`](../RAG_PLAN.md)。

## 版本与验证边界

- 未来 RAG 的目标版本是 **Java 版 1.20.4**。
- 本目录含有来自 1.20.1、1.20.4 或未逐项验证来源的历史素材；不可将“1.20.1 / 1.20.4 通用”视作对全部文件的已验证结论。
- 使用任何资料前，应逐项核对机制、方块状态、DataVersion、红石时序和适用版本。

## 文件清单与来源

### `basic_info/`（基础信息）

| 文件 | 标注来源 | 内容 |
|---|---|---|
| `实体生物/苦力怕.md` | 中文 Minecraft Wiki（Fandom 镜像） | 生物属性、行为和爆炸机制候选资料 |
| `方块特性/活塞.md` | 中文 Minecraft Wiki（Fandom 镜像） | 活塞候选资料 |
| `方块特性/红石方块与红石元件.md` | 中文 Minecraft Wiki（Fandom 镜像） | 红石元件候选资料 |
| `游戏机制/刻.md` | 中文 Minecraft Wiki（Fandom 镜像） | 游戏刻和红石刻候选资料 |

### `buildings/`（建筑）

| 文件 | 标注来源 | 内容 |
|---|---|---|
| `simple_builds/新手小屋(StarterHouse).md` | AstroWorld 指南 | 简单建筑候选资料 |
| `simple_builds/中世纪小屋(MedievalHouse).md` | AstroWorld 指南 | 都铎风格小屋候选资料 |
| `detailed_builds/城堡(Castle).md` | AstroWorld 指南 | 城堡候选资料 |
| `detailed_builds/雪地木屋(CozyWinterLodge).md` | SchemCraft | 带内饰建筑候选资料；使用前确认许可 |
| `detailed_builds/巨型木桶酒馆(GiantBarrelTavern).md` | SchemCraft | 带内饰建筑候选资料；使用前确认许可 |

### `redstone_basic/` 与 `redstone_advanced/`

包含 AstroWorld 与 RedenMC 标注来源的红石元件、分类机、活塞门和刷铁机候选资料。它们均需在实际入库前按 1.20.4 和来源许可复核。

### `structures/`（历史结构样本）

| 文件类型 | 标注来源 | 当前使用方式 |
|---|---|---|
| `.nbt` | Mojang 1.20.1 客户端 jar，部分样本 DataVersion=3465 | 历史样本，尚未逐项验证为 1.20.4 可放置 |
| `.litematic` | GitHub `mattzh72/lodestone` demo 等标注来源 | 历史样本，使用前确认作者许可和游戏内兼容性 |
| `.schem` | schemcraft.com 标注来源 | 当前 Mod 不支持直接加载 `.schem`；使用前确认格式转换、许可和兼容性 |

Mod 不会自动安装这些结构。若希望尝试游戏内放置，请先自行备份世界，再手动将 `.nbt` 复制到 `ai-helper/structures/nbts/`，或将 `.litematic` 复制到 `ai-helper/structures/litematic/`，并自行验证结果。

## 合规与采集说明

- 抓取日期：2026-09-13。
- 来源、作者和许可信息只用于追溯；不得据此推断拥有公开发布或再分发权。
- 遵守网站 robots.txt、服务条款和具体作品许可；不要未经授权大批量抓取或公开上传他人内容。
- 官方 minecraft.wiki 不适合自动抓取；历史素材中的 Fandom 镜像内容可能滞后，应以当前版本资料复核。
- McFun 百科为前端渲染应用，历史采集未能直接抓取详情页，未将其内容纳入本目录。
