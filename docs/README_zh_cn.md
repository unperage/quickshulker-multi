# Unper's QuickShulker

[[English]](../README.md) | 简体中文

直接在背包里打开容器，不用放下。

### 环境要求

- Minecraft **1.20.6 – 26.3**
- Fabric Loader 0.19.3+

### 下载

[**Releases**](https://github.com/unperage/unpers-quickshulker/releases) — 选和你 Minecraft 版本对应的 jar。

### 支持的容器

潜影盒（全部 17 种颜色）· 末影箱 · 工作台 · 切石机 · 铁砧（三个损坏等级）· 织布机 · 锻造台 · 砂轮

每项都能在设置界面里单独关闭。

### 按键

| 功能 | 默认键 | 修改位置 |
|---|---|---|
| 打开手持的容器 | `K` | 选项 → 控制 → 按键绑定 |
| 打开设置界面 | `小键盘 +` | 同上 |
| 允许嵌套容器（按住） | `左 Shift` | 设置界面 |
| 允许记忆容器（按住） | `左 Ctrl` | 设置界面 |

### 鼠标操作

- 右键背包里的容器即可打开
- 手持物品右键容器，放入物品
- 手持容器右键物品，取出物品
- 按住拖拽，批量搬运整组物品

### 可选功能（默认关闭）

- **允许嵌套容器** — 打开位于其他容器内部的容器
- **允许记忆容器** — 打开的容器界面层层叠加，ESC 逐层返回
- **静态指针** — 容器关闭后光标留在原位

### 服务端管理

OP 可以按物品控制全服是否允许快捷打开：

```
/uqs                        列出所有容器及当前状态
/uqs <物品|all> <true|false>  开关单个物品，或全部
```

Tab 补全会给出物品 id 与 `all`。设置保存在 `config/quickshulker-server.properties`。

两端同时允许才能打开：服务端放行 **且** 玩家本地也开启。玩家自己的开关只影响他自己。

### 许可

MIT。基于 [kyrptonaught/quickshulker](https://github.com/kyrptonaught/quickshulker)，由 [MoRanpcy/quickshulker](https://github.com/MoRanpcy/quickshulker) 与 [unperage/unpers-quickshulker](https://github.com/unperage/unpers-quickshulker) 继续维护。
