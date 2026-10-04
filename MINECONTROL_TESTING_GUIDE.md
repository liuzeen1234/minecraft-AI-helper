# 使用 MineControl 操作开发客户端

## 当前环境

2026-10-04 已在本项目验证 Minecraft 1.20.4、Fabric Loader 0.15.11、Fabric API 0.95.4+1.20.4、Java 17。MineControl 是客户端模组，服务端无需安装。

模组文件：`run/mods/minecontrol-mc1.20.4-1.0.0.jar`。最低 Fabric Loader 为 0.15.11，项目 `gradle.properties` 已升级。CLI 位于相邻项目 `../MineControl/minectl`，以下命令均从 aihelper 项目根目录运行。

## 启动和连接

先检查是否已有本项目测试客户端运行，只保留一个客户端。若需要重新启动，先正常保存退出世界并退出游戏，确认进程结束，再运行：

```bash
./gradlew runClient
```

连接已运行的客户端无需重启：

```bash
../MineControl/minectl ping
../MineControl/minectl status
```

`ping` 应返回 `ok=true`、`pong=true`；`status` 返回当前界面、世界加载状态、玩家位置、yaw/pitch 和按住输入。进入世界需 `world_loaded=true`；移动及转视角前需 `screen=null`。`window_focused=false` 且 `background_control=true` 时仍可操作。

连接拒绝时检查客户端进程、`run/logs/latest.log` 和模组是否加载；权限错误应按沙箱权限流程处理。多个加载 MineControl 的客户端会争用端口，不能仅凭 ping 判断连接的是哪个实例，需同时核对启动日志。

## 界面操作

```bash
../MineControl/minectl ui list
# 用当前列表中实际返回的 ID 替换 3：
../MineControl/minectl ui click 3
../MineControl/minectl status
```

每次切换界面后重新读取列表。普通文本框可用 `ui type`，布尔控件可用 `ui set`，参数以 `../MineControl/minectl ui type --help`、`ui set --help` 为准。自绘列表或未识别控件可用 `mouse goto X Y` 加 `mouse click left`；坐标使用 status 返回的 GUI 尺寸，不使用桌面像素。

进入世界时通过单人游戏菜单选择目标存档。复制好的房屋测试存档位于 `run/saves/MineControl Test 世界/`，它与 `新的世界` 是不同存档，操作前核对世界名称。

## 角色控制

以下是独立动作示例，按当前场景选择执行：

```bash
../MineControl/minectl key tap ESCAPE    # 关闭背包；游戏中则会打开暂停菜单
../MineControl/minectl key tap E         # 打开背包
../MineControl/minectl key tap K         # 默认 AI 设置快捷键，需当前绑定仍为 K
../MineControl/minectl key hold W 10     # 按住前进 10 个客户端 tick
../MineControl/minectl key tap SPACE
../MineControl/minectl mouse move 40 0   # 向右转视角，幅度受鼠标灵敏度影响
../MineControl/minectl key tap 1         # 选择快捷栏第一个槽位
../MineControl/minectl mouse click right # 对准目标后放置或使用
../MineControl/minectl mouse hold left 10
../MineControl/minectl input reset       # 释放所有模拟键鼠输入
../MineControl/minectl status
```

`key down/up`、`mouse down/up` 支持显式按住和释放；按住状态会跨 TCP 连接保留，不能用断开连接替代释放。`hold` 返回时只表示动作已安排，应在到期后查询状态，核对位置、视角及输入释放。20 tick 通常约一秒，卡顿时实际时间可能更长。

用户要求操作角色建造时，只通过移动、转视角、背包选物品、快捷栏和鼠标放置/挖掘完成。不要使用游戏命令、直接放置蓝图或编辑存档替代角色操作。每段动作后读取状态或用 F2 截图确认，异常时立即 `input reset`。

## 截图与保存

```bash
../MineControl/minectl key tap F2
```

截图位于本项目 `run/screenshots/`，结合 `latest.log` 确认实际文件。保存时按 Escape 打开暂停菜单，读取 `ui list`，点击当前“保存并退出”按钮，再检查已回到菜单及日志中的保存结果。复制存档前必须退出世界，避免复制正在写入的数据。

## 协议与边界

MineControl 在 `127.0.0.1:25580` 使用 UTF-8 换行分隔 JSON；没有欢迎消息，每条请求含唯一 `id` 和 `command`，回复包含相同 ID、`ok` 和结果或错误。例如：

```json
{"id":"check-1","command":"status"}
```

完整协议见相邻项目 `../MineControl/docs/PROTOCOL.md`。超时后先查询实际状态，不盲目重复点击或放置。

## 本项目已验证

2026-10-04：升级 Loader 后客户端成功启动，日志确认 MineControl 1.0.0+mc1.20.4 加载；ping/status 成功。进入世界后，后台通过 Escape 关闭创造背包，并执行 `mouse move 40 0`，yaw 从 260.87894° 增至 266.87894°，玩家位置不变、模拟键鼠均释放。该验证未使用游戏命令。

相邻 MineControl 项目的移动、建造和其他测试记录见其 `docs/TESTING.md`；上述本项目验证仅证明连接、界面按键和视角控制，不代表已验证 aihelper 的所有功能。
