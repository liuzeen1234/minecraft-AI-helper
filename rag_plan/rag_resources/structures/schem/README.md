# WorldEdit/Sponge 格式建筑结构文件（.schem）· 1.20.1 适配版

本目录包含 3 个 WorldEdit / Sponge 格式（.schem）的建筑结构文件，**DataVersion 已统一更新为 3465（1.20.1）**，可直接放入 1.20.1 的 WorldEdit `schematics/` 文件夹在游戏内粘贴使用，也可用于 RAG 素材训练。

## 文件清单

| 文件 | 建筑 | 作者 | 数据版本 | 尺寸(X×Y×Z) | 方块数 | Palette数 | 许可 |
|---|---|---|---|---|---|---|---|
| grand-medieval-fantasy-survival-house.schem | 中世纪奇幻生存屋（含内饰） | BuildSchem | 3465 (1.20.1) | 42×40×52 | 14,981 | 62 | CC BY-NC 4.0 |
| ultimate-medieval-survival-castle-mansion.schem | 中世纪城堡庄园 | BuildSchem | 3465 (1.20.1) | 124×80×124 | 101,294 | 71 | CC BY 4.0 |
| ultimate-medieval-tavern-inn-survival-base.schem | 中世纪酒馆客栈生存基地 | BuildSchem | 3465 (1.20.1) | 96×64×88 | 46,321 | 107 | CC BY 4.0 |

## 1.20.1 适配说明

- 三个源文件原始 DataVersion=2586（1.16.5），已将 `DataVersion` 字段更新为 **3465（1.20.1）**，WorldEdit 1.20.1 加载时不再有版本提示。
- palette 中的方块均为 1.16.5 与 1.20.1 共有的原版方块（grass_block、oak_log、stone_bricks、dark_oak_fence 等），方块状态写法（`snowy`、`axis`、`waterlogged` 等属性）在两版本间一致，**未做任何方块替换**，结构、尺寸、方块数完全保持原样。

## 格式说明（训练要点）

.schem（Sponge 格式）是 **gzip 压缩的大端 NBT**，根为 Compound，顶层字段：

- `Version`：TAG_Int —— 格式版本（此处均为 2）
- `DataVersion`：TAG_Int —— 数据版本号（本目录统一 3465 = 1.20.1）
- `Width` / `Height` / `Length`：TAG_Short —— 尺寸 (X, Y, Z)
- `PaletteMax`：TAG_Int —— palette 大小
- `Palette`：TAG_Compound —— `"方块状态字符串" → Int 编号` 的映射，如 `"minecraft:grass_block[snowy=false]" → 1`
- `BlockData`：TAG_ByteArray —— 逐方块索引（每字节一个 palette 编号），遍历顺序 Y → Z → X，长度 = Width×Height×Length（已验证与本目录所有文件一致）
- `BlockEntities` / `Entities`：TAG_List —— 方块实体与实体（本目录文件为空）

## 来源与许可

- 来源：schemcraft.com（独立 Minecraft schematic 库），作者 BuildSchem。
  - 原页面：`https://schemcraft.com/schematics/<文件名去掉 .schem 后缀>`
  - 下载日期：2026-09-15；DataVersion 适配日期：2026-09-15
- 许可：
  - `grand-medieval-fantasy-survival-house.schem`：**CC BY-NC 4.0**（可免费非商业使用，须署名，商用需另行授权）
  - 其余两个：**CC BY 4.0**（可免费使用含商业，须署名）
- 本目录文件仅作本地 RAG 学习/研究使用；如需公开分发或商用，请遵守上述许可（署名来源 schemcraft.com）。
