# 动作参考

Actions 始终是有序的字符串列表，每组 1–16 条。settings 与 canvas 支持的动作以及 close 的位置不同。

## 动作速查

| 动作 | settings / 物品页 | canvas |
| --- | --- | --- |
| `command: 指令` | 玩家身份执行 | 玩家身份执行 |
| `console: 指令` | 控制台身份执行 | 控制台身份执行 |
| `page: 页ID` | 当前菜单内跳转，放最后 | 当前菜单内跳转，放最后 |
| `menu: 菜单ID` | 不支持 | 跳到另一个 canvas 菜单默认页，放最后 |
| `close` | **只能放第一项** | **只能放最后一项** |
| `refresh` | 刷新当前页，放最后 | 刷新当前页，放最后 |
| `search` | 打开页面搜索，放最后 | 不支持 |
| `message: 文本` | 不支持内置 message 动作 | 给玩家发聊天消息 |
| `set: 变量=值` | 不支持 | 设置已声明枚举变量 |
| `template: 模板ID` | 不支持 | 兼容动作；新版优先 page / menu |

动作名使用小写。没有 TrMenu 的 tell、op、delay、js、kether、give-money 等内置动作；功能可由实际业务插件的命令提供。

## settings 的顺序

以下为控件内的动作片段：

```yaml
Actions:
  - close
  - "command: spawn"
```

先关闭当前界面，再执行玩家命令。spawn 需要服务器提供。

需要刷新或翻页时：

```yaml
Actions:
  - "command: example save"
  - "page: help"
```

example save 是外部命令示意，help 必须是当前菜单实际存在的页。page、search、refresh 只能位于动作末尾。

仅有命令且没有 close 时，动作执行完会刷新菜单。跨到其他界面时通常先 close，避免旧界面自动刷新覆盖新界面。

打开另一个 DialogMenu：

```yaml
Actions:
  - close
  - "command: dmenu open demo-boss"
```

这适用于 settings 控件；目标菜单需要存在，玩家仍需满足打开权限和资源包条件。

## canvas 的顺序

```yaml
Actions:
  - "set: difficulty=hard"
  - "message: 你选择了 {difficulty} 难度。"
  - "page: confirm"
```

difficulty 必须声明 hard 值，confirm 必须存在。动作逐条执行，因此消息读取的是刚设置的新值。

真正进入副本时的示意：

```yaml
Permission: "example.dungeon.enter"
Actions:
  - "console: yourdungeon join {player} {difficulty}"
  - close
```

yourdungeon 是占位命令，必须替换成你的副本插件实际命令。文本中写“消耗钥匙”不会扣除物品，所需物品、次数、费用和发奖应由业务插件检查并处理。

没有 close、page、menu、template、refresh 时，canvas 在动作执行完后自动重绘当前页面。用命令打开其他 UI 时，后续重绘或 close 可能影响刚打开的界面；canvas 之间使用 page / menu 跳转。

## 命令身份与占位符

命令内容不写开头的 `/`，最长 512 字符，不允许控制字符或 `%`。一条列表项表示一次命令调用，不会把分号拆成多条命令。

| 位置 | 可以替换 |
| --- | --- |
| settings 的 command / console | `{player}`、`{uuid}` |
| canvas 的 command / console | `{player}`、`{uuid}`、当前页声明的枚举变量 |
| 两种命令动作 | 都不展开 PlaceholderAPI |

command 保留玩家自身权限，不临时提权；console 由控制台执行。Permission 检查点击玩家的权限，它不改变命令发送者。

## 失败时发生什么

当 Bukkit / 外部命令执行接口返回失败时，停止后续动作；已执行的命令不会回滚。外部插件若仅发送失败提示却返回成功，DialogMenu 无法据此推断业务失败。

因此，扣钱 → 给物品这样的多步业务不能仅靠多个菜单命令保证原子性。应让业务插件提供一个完成校验与发放的入口。

## Permission 与 RequiresPlugin

settings 的 button、toggle、选择控件可以添加 Permission / RequiresPlugin；选择控件把限制带到每个选项动作。内置 Bind 还会保留其必要依赖。

canvas 的按钮支持 Permission，不支持 RequiresPlugin。无权限时按钮不会自动隐藏，点击时拒绝动作；隐藏由 VisibleWhen 控制，而 VisibleWhen 只读取枚举变量。

## 兼容 template

在新版目录中内部页面 ID 为 `菜单ID/页面ID`。canvas 仍能使用例如 `template: demo-boss/confirm`，但新文件推荐 `page: confirm` 或 `menu: demo-boss`，这样同菜单复制改名更方便。

旧独立模板中的 `template: boss-confirm` 属于旧模板目录的 ID；不要原样移到 Version 3 后假定仍会在解析阶段映射。
