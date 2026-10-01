# 使用 M1 测试 Minecraft 开发客户端

本文记录本项目在 macOS 上通过 M1 操作真实 Minecraft 客户端的方法，以及已完成的 AI 对话和选区测试。

## 环境与安装

实测环境：Minecraft 1.20.4、Fabric Loader 0.15.6、Fabric API 0.95.4+1.20.4、Java 17。

使用 M1 `0.17.5+1.20` Fabric 版，发布页标注支持 Minecraft 1.20–1.20.4：

- 发布页：https://modrinth.com/mod/m1-machine-one-ai-interface/version/0.17.5+1.20
- 使用说明：https://modrinth.com/mod/m1-machine-one-ai-interface
- 源码与搭建说明：https://github.com/Kishku7/m1/tree/minecraft-1.20-26.3

将 `m1-0.17.5+1.20-fabric.jar` 放入本项目 `run/mods/`，退出游戏并重新启动：

```bash
./gradlew runClient
```

进入游戏前先正常保存并退出世界。仅将 M1 用作开发测试依赖，无需修改本项目的生产依赖或打包它。

本次下载时命令行直连 CDN 遇到证书错误，项目配置的代理不可达，最终通过 Chrome 从发布页下载成功；不要通过关闭证书验证解决下载问题。

## 控制接口

M1 在 `127.0.0.1:26000` 提供 TCP 文本接口，可读取游戏界面和发送操作命令。这次测试直接使用 Python 连接，不需要另行部署 MCP 桥接服务。

协议规则：

1. 每条命令占一行，以换行结束。
2. 连接后先读取欢迎消息。
3. 每次回复以独立的一行 `<<END` 结束。
4. 保留缓冲区内尚未处理的数据，不能假设一次接收恰好对应一次回复。
5. 读取超时应大于 M1 的 10 秒命令执行上限；本例使用 15 秒。
6. 操作后读取新界面，根据当前返回的控件 ID 决定下一步。不同界面的 ID 可以相同，不能跨界面复用。

一个可复用的辅助脚本（保存为 `/tmp/m1-helper-test.py`）：

```python
import socket
import sys

with socket.create_connection(("127.0.0.1", 26000), 5) as connection:
    connection.settimeout(15)
    reader = connection.makefile("r", encoding="utf-8")

    def read_reply():
        lines = []
        while True:
            line = reader.readline()
            if not line:
                raise ConnectionError("M1 在回复结束前断开连接")
            if line.strip() == "<<END":
                return "\n".join(lines)
            lines.append(line.rstrip("\r\n"))

    read_reply()  # 欢迎消息
    for command in sys.argv[1:]:
        connection.sendall((command + "\n").encode("utf-8"))
        print(read_reply())
```

调用示例：

```bash
python3 /tmp/m1-helper-test.py 'describe' 'help'
```

M1 可能在回复前附带 `[agent]` 状态通知，这些不是操作失败提示。

常用命令：

| 命令 | 用途 |
| --- | --- |
| `describe` | 读取当前界面及控件 ID、标签、输入框值 |
| `click <id>` | 点击当前界面控件 |
| `type <id> <text>` | 设置文本输入框 |
| `worlds` | 列出世界选择页面的世界条目 |
| `joinworld <idx>` | 进入指定世界 |
| `where` | 查询位置和当前界面状态 |
| `cmd <command>` | 发送游戏命令，不带开头的 `/`；文档注明需要作弊权限 |
| `screenshot <name>` | 保存截图到 `run/screenshots/` |
| `pause` | 打开暂停菜单，可再通过界面按钮保存退出 |

本例使用的测试世界为允许作弊的创造模式世界。只运行一个启用 M1 的客户端，避免端口冲突。保持接口监听本机回环地址即可。

## 验证 /ai

先读取标题界面，通过“单人游戏”的控件 ID 打开世界选择页；运行 `worlds`，核对世界名称后用 `joinworld <idx>` 进入测试世界。

进入后发送一条纯文本测试：

```bash
python3 /tmp/m1-helper-test.py \
  'where' \
  'cmd ai 请只回复：测试成功。不要调用工具，不要执行任何游戏操作。'
```

检查 `run/logs/latest.log`。本次实测证据：

- 请求发送到游戏现有配置中的 AI 接口。
- HTTP 状态码为 200。
- SSE 流收到 `[DONE]`，正常结束。
- 游戏聊天日志显示 `[AI] 测试成功。`。

仅返回 `OK sent` 不能证明 AI 调用成功，需要继续核对聊天回复和日志。

## 验证选区

### 打开界面

本项目新增了客户端命令 `/click <按键>`。进入世界、关闭其他界面后，可通过 M1 发送 `/click K` 打开 AI Builder 设置，无需玩家手动按键：

```bash
python3 /tmp/m1-helper-test.py \
  'cmd click K'
python3 /tmp/m1-helper-test.py 'describe'
```

命令按当前键位绑定模拟 K 键；如果设置快捷键改过，应使用对应按键名称。按键名不区分大小写。该命令在游戏 tick 中执行，发送后的即时 `describe` 可能仍显示无界面；再次读取，确认返回 `ModSettingsScreen`（“AI 模组设置”）后，找到“选区工具”的当前控件 ID 并点击。

2026-10-01 实测：M1 返回 `OK sent: /click K`，随后读取到设置菜单，点击“选区工具”成功进入 `SelectionScreen`。截图为 `run/screenshots/click-command-open-test.png`。

本次界面 ID 为：坐标输入框 0–5、当前位置按钮 6/7、确认 8、清除 9、分析/导出 10、返回 11。它们仅用于解释本次测试，执行时仍应以最新 `describe` 输出为准。

### 当前位置与反向坐标

点击“坐标1=当前位置”和“坐标2=当前位置”，确认两组输入框均填入玩家方块坐标。本次玩家位置对应 `(-698,37,311)`。

将两点设置为：

- 点 1：`(-697,39,314)`
- 点 2：`(-698,37,311)`

这是一个包含负坐标、三轴方向反转的测试范围。预期尺寸按包含两端计算：`abs(点1 - 点2) + 1`，得到 `2×3×4`，总体积 24 格。

点击“分析/导出选区”，验证未确认时的自动确认流程。通过截图读取分析页中的文字统计；M1 的 `describe` 主要返回控件，不能替代对自绘统计文字的截图检查。

本次结果：

| 项目 | 结果 |
| --- | --- |
| 尺寸 | 2×3×4 |
| 总格数 | 24 |
| 空气 | cave_air ×16 |
| 实心方块 | 8 |
| 实心方块分类 | andesite ×3、diorite ×1、stone ×4 |
| 默认忽略 | cave_air 不导出 |

截图保存为 `run/screenshots/selection-analysis-test.png`。

### TXT 导出

从分析页点击“导出选区”，选择“导出 .txt”。将文件名设置为独立测试名称 `codex_selection_test_20261001`，避免覆盖已有蓝图，保持路径框为空使用默认目录，然后点击导出。

本次产物：

```text
run/ai-helper/structures/txts/codex_selection_test_20261001.txt
```

检查文件与服务端导出日志：

- 文件头为 `MCBLUEPRINT v2`。
- 尺寸为 `2x3x4`。
- 实际导出 8 个实心方块，数量和分类与分析页一致。
- 相对坐标在 X=0–1、Y=0–2、Z=0–3 范围内。
- 空气默认被排除。
- 日志和游戏聊天均提示 TXT 导出完成。

### 草稿、无效输入、清除与高亮

1. 从导出菜单返回分析页，再返回选区页。
2. 返回设置菜单，重新打开选区页；确认六个坐标输入框仍保留原值。
3. 将 X1 输入改为 `invalid`，点击“确认选区”；确认界面未关闭且聊天提示“请输入有效的整数坐标”。
4. 点击“清除选区”；确认六个输入框全部清空。
5. 重新输入测试两点并点击“确认选区”；确认回到游戏，聊天提示选区已设置。
6. 截图检查选区边框。本次截图为 `run/screenshots/selection-highlight-test.png`。

## 测试范围与限制

2026-10-01 实测通过：M1 连接和界面操作、游戏内 `/ai` 回复、选区当前位置填入、负坐标与反向两点、自动确认、尺寸统计、TXT 导出、草稿保留、无效输入提示、清除及确认后边框显示。

尚未验证：NBT / Litematic 导出、容器内容及告示牌数据、实体导出、方块类型 +/- 过滤切换、大范围选区、未加载区块和其他 Minecraft 版本。

本次选区测试读取现有方块，没有放置、破坏或还原建筑；测试结束时保留了测试选区与导出文件。
