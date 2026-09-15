# MC Java 1.20.1 原版结构 NBT 数据集

本目录包含 14 个适用于 **Minecraft Java 版 1.20.1** 的原版结构文件（Structure Block 格式，.nbt），直接从官方 1.20.1 客户端 jar（`data/minecraft/structures/`）提取，未经任何修改，可用于模型训练与结构分析。

## 文件清单

| 文件 | 结构 | 类别 | 尺寸(X×Y×Z) | 方块数 | Palette数 | 实体数 |
|---|---|---|---|---|---|---|
| village_plains_big_house_1.nbt | 平原村庄大房子 | 村庄建筑 | 7×11×11 | 816 | 25 | 0 |
| end_city_ship.nbt | 末地城船 | 末地建筑 | 13×24×29 | 9048 | 37 | 0 |
| igloo_top.nbt | 雪屋（地上） | 雪屋 | 7×5×8 | 152 | 11 | 0 |
| igloo_bottom.nbt | 雪屋（地下室） | 雪屋 | 7×6×9 | 244 | 25 | 2 |
| pillager_watchtower.nbt | 掠夺者前哨站瞭望塔 | 前哨站 | 15×21×15 | 4725 | 40 | 0 |
| ancient_city_center_1.nbt | 远古城市中心 | 远古城市 | 18×31×41 | 7966 | 96 | 0 |
| bastion_big_air_full.nbt | 堡垒遗迹藏宝室大组件 | 堡垒遗迹 | 38×48×38 | 27650 | 2 | 0 |
| underwater_ruin_big_brick_1.nbt | 海底废墟（砖） | 海底废墟 | 16×16×16 | 4096 | 8 | 0 |
| woodland_mansion_1x2_a1.nbt | 林地府邸房间 | 林地府邸 | 7×8×15 | 840 | 7 | 0 |
| ruined_portal_1.nbt | 废弃传送门（小） | 废弃传送门 | 6×10×6 | 304 | 18 | 0 |
| fossil_skull_1.nbt | 化石颅骨 | 化石 | 6×5×7 | 86 | 3 | 0 |
| fossil_spine_1.nbt | 化石脊椎 | 化石 | 3×3×13 | 37 | 2 | 0 |
| trail_ruins_group_hall_1.nbt | 考古遗迹大厅 | 考古遗迹（1.20新增） | 15×15×11 | 366 | 12 | 0 |
| shipwreck_full.nbt | 沉船（完整） | 沉船 | 9×9×28 | 662 | 8* | 0 |

\* shipwreck_full.nbt 使用 `palettes`（复数，1.18+ 多生物群系格式）而非 `palette`，两种格式 1.20.1 均支持。

## 格式说明（训练要点）

结构文件是 **gzip 压缩的大端 NBT**，根为 Compound，顶层字段：

- `size`：TAG_List of 3 个 Int —— 结构尺寸 (X, Y, Z)
- `blocks`：TAG_List of Compound —— 每个方块：`pos`（3 个 Int 的 List）、`state`（Int，指向 palette 下标）、可选 `nbt`（方块实体数据）
- `palette` / `palettes`：TAG_List of Compound —— 方块状态定义，含 `Name`（命名空间ID）与可选 `Properties`（方块状态属性）
- `entities`：TAG_List of Compound —— 实体（含 `pos`、`nbt`）
- `DataVersion`：TAG_Int —— 数据版本号（1.20.1 = **3465**）

所有文件 DataVersion=3465，与 1.20.1 完全匹配，可直接被 1.20.1 结构方块加载；也可被更高版本加载（游戏会自动升级数据）。

## 版本一致性说明

- 与 1.20.4 对比：14 个结构中 12 个内容完全一致（仅 DataVersion 不同）；**end_city_ship.nbt 与 igloo_bottom.nbt 在 1.20.1 与 1.20.4 之间有内容差异**，本目录提供的是 1.20.1 版本内容。
- 1.20.1 的 jar 共 1010 个原版结构；1.20.4 新增的 127 个主要是试炼密室（trial_chambers，1.20.3 实验性加入），**1.20.1 中不存在，未提取**。
- 考古遗迹（trail_ruins）为 1.20 更新新增，1.20.1 中存在，已包含。

## 加载方法（游戏内使用）

1. 将 .nbt 文件放入存档目录：`.minecraft/saves/<世界名>/generated/minecraft/structures/`
2. 游戏内输入 `/give @s structure_block` 获得结构方块
3. 结构方块设为「加载（Load）」模式，输入文件名（不含 .nbt 后缀），点击「加载」

## 来源与许可

- 来源：Mojang 官方 1.20.1 客户端 jar（SHA1 `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838`），即游戏原版数据
- 许可：Minecraft 版权归 Mojang Studios；游戏数据遵循 Minecraft EULA，仅限个人/研究用途，禁止商用与再分发

---

## 社区建筑结构（补充素材）

本目录下另有**两个子目录**，包含社区玩家创作的中大型建筑结构，用于补充原版小结构之外的建筑模式参考：

| 子目录 | 格式 | 数量 | 内容 | 说明 |
|---|---|---|---|---|
| `litematic/` | .litematic（Litematica 投影） | 3 | 城堡 / 大教堂 / 黑暗要塞 | 来源 GitHub（lodestone 仓库 demo），详见该目录 README |
| `schem/` | .schem（WorldEdit/Sponge） | 3 | 中世纪屋 / 城堡庄园 / 酒馆客栈 | 来源 schemcraft.com，CC BY / CC BY-NC，详见该目录 README |

- 两类文件与上表的 .nbt 一样是 gzip NBT，但字段结构不同（.litematic 用 `BlockStatePalette`+`BlockStates`，.schem 用 `Palette`+`BlockData`），各子目录 README 已写明解析要点与版本兼容性。
- 仅限本地 RAG 学习/研究使用，不批量公开上传。
