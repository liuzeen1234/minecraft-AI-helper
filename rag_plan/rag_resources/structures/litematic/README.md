# Litematica 格式建筑结构文件（.litematic）· 1.20.1 适配版

本目录包含 3 个 Litematica（客户端投影 mod）格式的建筑结构文件，**全部已适配为 MC Java 1.20.1 可读**（格式 v6 + MinecraftDataVersion 3465），可直接放入 1.20.1 的 Litematica `schematics/` 文件夹在游戏内加载，也可用于 RAG 素材训练。

## 文件清单

| 文件 | 建筑 | 作者 | 格式版本 | 数据版本 | 尺寸(X×Y×Z) | 方块数 | Palette数 |
|---|---|---|---|---|---|---|---|
| zamek-castle-19640.litematic | Zamek 城堡（中世纪城堡） | Smeszer | v6 | 3465 (1.20.1) | 245×105×161 | 423,358 | 869 |
| cathedral-final-v1.0.litematic | 大教堂（末地石主题） | Tavinral | v6 | 3465 (1.20.1) | 188×131×75 | 111,635 | 318 |
| dark-fortress.litematic | 黑暗要塞（雪原要塞） | Raaamseeel | v6 | 3465 (1.20.1) | 97×156×104 | 200,546 | 417 |

## 1.20.1 适配说明

三个源文件原本的数据版本与格式高于 1.20.1，已按下表转换，**结构、尺寸、方块数与方块位置均保持不变**：

| 文件 | 原格式 | 原数据版本 | 转换内容 |
|---|---|---|---|
| zamek-castle-19640.litematic | v6 | 3218 (1.19.3) | 数据版本更新为 3465；修复源文件 `Size.z` 负数（-161 → 161，原为导出错误） |
| cathedral-final-v1.0.litematic | v7 | 4556 (≥1.21.4) | 格式 v7→v6；数据版本更新为 3465；palette 中 1.20.1 不存在的 4 种方块替换为等价方块 |
| dark-fortress.litematic | v7 | 4325 (1.21.4) | 格式 v7→v6；数据版本更新为 3465；palette 中 1.20.1 不存在的 13 种方块替换为等价方块 |

**方块替换对照**（均为 1.21+ 新增 → 1.20.1 等价物，仅影响视觉材质，不影响结构）：

- pale_oak_shelf → bookshelf；pale_oak_trapdoor → oak_trapdoor；stripped_pale_oak_wood → stripped_oak_wood；waxed_lightning_rod → lightning_rod
- pale_moss_block → moss_block；tuff → stone；polished_tuff → polished_andesite
- tuff_bricks → stone_bricks；tuff_slab → stone_slab；tuff_stairs → stone_stairs；tuff_wall → stone_wall
- tuff_brick_slab → stone_brick_slab；tuff_brick_stairs → stone_brick_stairs；tuff_brick_wall → stone_brick_wall
- polished_tuff_slab → polished_andesite_slab；polished_tuff_stairs → polished_andesite_stairs；polished_tuff_wall → polished_andesite_wall

## 格式说明（训练要点）

.litematic 是 **gzip 压缩的大端 NBT**，根为 Compound，顶层字段：

- `MinecraftDataVersion`：TAG_Int —— 数据版本号（本目录统一 3465 = 1.20.1）
- `Version`：TAG_Int —— Litematica 格式版本（本目录统一 6）
- `Metadata`：TAG_Compound —— Name / Author / TotalBlocks / TotalVolume 等
- `Regions`：TAG_Compound —— 按区域名：
  - `Size` / `Position`：TAG_Compound（x/y/z 三个 Int）
  - `BlockStatePalette`：TAG_List of Compound —— 方块状态定义（`Name` + 可选 `Properties`），**下标即方块编号**
  - `BlockStates`：TAG_LongArray —— 变长整数打包的方块索引（每方块占 `ceil(log2(palette数))` 位，小端按 64 位 long 填充），遍历顺序 Y → Z → X

## 来源与许可

- 来源：GitHub 开源仓库 `mattzh72/lodestone`（Minecraft 结构解析库）的 `demo/public/` 目录，原始文件来自 abfielder.com 公开页面与社区分享。
- 本目录文件为**降级适配版**（方块 palette 有等价替换），仅本地 RAG 学习/研究使用，不批量公开上传、不商用、不重新分发；如需公开使用请联系原作者确认授权。
- 下载日期：2026-09-15；适配日期：2026-09-15。原始文件路径：`https://github.com/mattzh72/lodestone/tree/main/demo/public`
