# 命令与权限

主命令 `/dialogmenu`，别名 `/dmenu`、`/playersettings`、`/settings`、`/player-settings`。

## 命令表

| 命令 | 执行者 | 效果 |
| --- | --- | --- |
| `/dmenu` | 玩家 | 打开 DefaultMenu 的默认页 |
| `/dmenu open <菜单ID>` | 玩家 | 打开该菜单的默认页 |
| `/dmenu open <菜单ID> <页面ID>` | 玩家 | 打开指定子页 |
| `/dmenu check` | 玩家 / 控制台 | 校验全局配置、全部菜单、跳转和物品源，不应用 |
| `/dmenu reload` | 玩家 / 控制台 | 全部通过后应用并刷新已打开菜单 |
| `/dmenu pack` | 玩家 | URL 模式发送资源包；其他模式提示包名及来源 |
| `/dmenu template <模板ID>` | 玩家 | 兼容模板入口，新配置优先用 open |

控制台输入时去掉 `/`。命令当前没有“替另一名玩家打开”的目标参数；`/dmenu open settings Alice` 中的 Alice 会被当作页面 ID。

```text
/dmenu open settings appearance
/dmenu open demo-dialogue
/dmenu open demo-boss intro
/dmenu open demo-boss confirm
```

## 权限表

| 权限 | 默认 | 用途 |
| --- | --- | --- |
| `playersettings.use` | 允许普通玩家 | 主命令与打开菜单 |
| `playersettings.admin` | 默认 OP 可用 | check / reload 额外检查 |
| 控件里的 Permission | 自己定义 | 限制该控件动作；不是整个菜单的访问权限 |

插件更名后保留了旧权限名，不要改成自行猜测的 dialogmenu.use。没有内置的每菜单权限字段；对特定按钮使用 Permission，业务指令本身也由对应插件检查权限。

## 重载时已打开的界面

settings 下拉框会收起。仍存在的页面会刷新；当前页被删除时回到菜单默认页；整个菜单被删除时关闭。

canvas 仍存在的页面重新绘制，并保留该页仍接受的变量值；页面或菜单不再存在则关闭。修改 ResourcePack 设置会关闭已打开菜单，重新满足加载要求后再打开。

check 不执行按钮里的命令，也不验证外部业务插件是否完成“扣钱”“发奖”等操作。

## 旧入口

旧页面入口如 `/dmenu open particles` 仍有兼容处理，但多菜单下建议总写完整菜单和页名。

| 旧模板名 | 新入口 |
| --- | --- |
| npc-dialogue | `/dmenu open demo-dialogue main` |
| boss-intro | `/dmenu open demo-boss intro` |
| boss-confirm | `/dmenu open demo-boss confirm` |

这些旧名映射要求默认演示菜单及对应页仍存在；复制和改名后使用新的 open 路径。
