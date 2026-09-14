# MC RAG 知识库示例文档说明

本目录为「我的世界 RAG 知识库」的示例文档集，按《参考文件.txt》中的目录结构组织，供 AI 理解 MC 原版特性、建筑、红石电路使用。

## 目标版本约束

- 知识库统一标注 **Java 版 1.20.1 / 1.20.4 通用**。
- 已清理所有非 1.20.x 的版本引用与内容（如 26.2、1.21、1.16+ 等来源版本标注，以及仅属于其他版本的机制），全部文档仅描述两个版本共有的机制与方块。
- 1.20.1 与 1.20.4 之间仅涉及漏洞修复，本文档涉及的方块、实体与红石机制在两版本中行为一致。

## 文件清单与来源

### basic_info/（基础信息文档）

| 文件 | 来源 | 说明 |
|---|---|---|
| 实体生物/苦力怕.md | 中文 Minecraft Wiki（Fandom 镜像） | 生物属性、行为、爆炸机制 |
| 方块特性/活塞.md | 中文 Minecraft Wiki（Fandom 镜像） | 活塞方块完整资料 |
| 方块特性/红石方块与红石元件.md | 中文 Minecraft Wiki（Fandom 镜像） | 红石块/压力板/中继器/侦测器/比较器 |
| 游戏机制/刻.md | 中文 Minecraft Wiki（Fandom 镜像） | 游戏刻/区块刻/随机刻/计划刻/红石刻/活塞刻 |

### buildings/（建筑文档）

| 文件 | 来源 | 说明 |
|---|---|---|
| simple_builds/新手小屋(StarterHouse).md | AstroWorld 指南 | 简单建筑：材料清单+分层搭建 |
| simple_builds/中世纪小屋(MedievalHouse).md | AstroWorld 指南 | 都铎风格小屋五步搭建 |
| detailed_builds/城堡(Castle).md | AstroWorld 指南 | 护城墙/塔楼/主楼/城门楼 |
| detailed_builds/雪地木屋(CozyWinterLodge).md | SchemCraft | 带完整内饰+材料清单（CC BY 4.0） |
| detailed_builds/巨型木桶酒馆(GiantBarrelTavern).md | SchemCraft | 带完整内饰+材料清单（CC BY 4.0） |

### redstone_basic/（红石单元）

| 文件 | 来源 | 说明 |
|---|---|---|
| 红石元件总览(AstroWorld).md | AstroWorldMC 红石专题 | 37种元件分类与信号行为 |
| 活塞元件(AstroWorld).md | AstroWorldMC 红石专题 | 活塞合成/信号/延迟 |

### redstone_advanced/（复杂红石）

| 文件 | 来源 | 说明 |
|---|---|---|
| 物品分类机(ItemSorter).md | AstroWorld 指南 | 41物品过滤原理+分步搭建 |
| 活塞门(PistonDoor).md | AstroWorld 指南 | 2×2/3×3 平贴活塞门+时序 |
| 刷铁机52核心(RedenMC).md | RedenMC 红石伊甸园 | 机器描述+适配版本+下载信息 |

### structures/（1.20.1 原版结构 NBT 数据集）

| 文件 | 来源 | 说明 |
|---|---|---|
| 14 个 .nbt + README.md | Mojang 官方 1.20.1 客户端 jar | 原版结构方块格式（村庄/末地城/雪屋/前哨站/远古城市/堡垒/海底废墟/林地府邸/废弃传送门/化石/考古遗迹/沉船），DataVersion=3465，与 1.20.1 完全匹配，可直接用于训练或游戏内结构方块加载 |

## 抓取说明

- 抓取日期：2026-09-13
- 官方 minecraft.wiki 禁止自动抓取（robots.txt），基础信息改用其 Fandom 中文镜像，内容与官方 Wiki 一致度较高但可能滞后。
- 原计划中的 McFun 百科（mcshuo.com）为前端渲染应用，详情页无法直接抓取，未入库。
- 建议后续扩展：补充红石中继器/比较器/火把的专项中文文档、更多 RedenMC 机器（分类机、世吞等）、Technical Minecraft Wiki 的刻时序页面。
