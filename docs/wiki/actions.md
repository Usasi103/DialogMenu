# 动作参考

`Actions` 决定点击后做什么。0.2.0-paper.3 起，settings（含物品页、`MainMenu` 和选项里的 Actions）与 canvas 按钮使用同一套写法，格式照 TrMenu 3 的动作：动作行、行尾选项和条件块。原来的 `message:`、`menu:` 和旧的动作顺序继续有效。

## 动作行

每条动作写作 `名称: 值`，没有值的动作只写名称。带 `:` 的动作行要加引号，因为 YAML 会把 `- tell: 你好` 读成一个映射；`- close` 这类不带值的可以不加：

```yaml
Actions:
  - 'sound: UI_BUTTON_CLICK-0.8-1.4'
  - close
  - 'command: spawn {delay=2}'
```

先播放点击音效、关闭菜单，2 tick 后以玩家身份执行 spawn。spawn 需要服务器实际提供。

- 名称不分大小写，接受下表的 TrMenu 别名。
- 只有一条动作时也可以写成一行字符串：`Actions: close`。
- 一行里可以用 `_||_`（或 `&&&`）连接几条动作，例如 `- 'sound: UI_BUTTON_CLICK _||_ close'`。
- 写错名称时 `/dmenu check` 报 `不支持动作 …`，并列出可用名称；TrMenu 则会把它当作 Kether 脚本执行。

## 动作速查

| 动作 | 别名 | 值 | 说明 |
| --- | --- | --- | --- |
| `tell` | message、msg、talk | 文字 | 给玩家发聊天消息 |
| `chat` | send、say | 文字 | 让玩家自己发一条聊天消息 |
| `title` | subtitle、send-title | `主标题 副标题 淡入 停留 淡出` | 屏幕标题，见下方格式 |
| `actionbar` | action | 文字 | 物品栏上方的动作栏文字 |
| `tellraw` | json | JSON 或简写 | 可悬停、可点击的聊天消息 |
| `command` | cmd、player、execute | 指令 | 以点击玩家身份执行 |
| `console` | — | 指令 | 以控制台身份执行 |
| `connect` | bungee、server | 子服名 | 通过代理传送到其他子服 |
| `sound` | sounds、play-sound | `名称-音量-音调` | 在玩家位置播放音效 |
| `delay` | wait | tick 数 | 暂停其余动作 |
| `return` | break | 无 | 结束整组动作 |
| `close` | shut、force-close、silent-close | 无 | 关闭菜单 |
| `open` | menu、gui、open-gui、trmenu、force-open | `菜单ID` 或 `菜单ID:页面ID` | 打开 menus/ 里的菜单 |
| `page` | — | 页面 ID | 跳到当前菜单的另一页 |
| `refresh` | update、icon-refresh | 无 | 重新绘制当前页并重读变量 |
| `set` | — | `变量=值` | canvas：设置 Variables 枚举变量 |
| `search` | — | 无 | settings：打开页面搜索 |
| `template` | — | `菜单ID/页面ID` | canvas：旧版链接写法 |

`subtitle` 只是 `title` 的别名，格式相同，第一段仍是主标题。

没有 TrMenu 的 op、Kether、JavaScript、经济（money / points）、物品、bossbar、元数据等动作，写了会报 `不支持动作`。这些功能交给业务插件，用 command / console 调用它们的指令。

## settings 与 canvas 的可用范围

| 动作 | settings | canvas |
| --- | --- | --- |
| tell、chat、title、actionbar、tellraw、sound、connect | 可用 | 可用 |
| command、console、delay、return、close、refresh | 可用 | 可用 |
| page | 当前菜单的页面 | 当前菜单的页面 |
| open | menus/ 中任一菜单 | menus/ 中任一菜单 |
| search | 可用 | 不可用 |
| set | 不可用 | 可用 |
| template | 不可用 | 可用（兼容旧写法） |

open 的目标：

- `open: demo-boss` 打开 menus/demo-boss.yml 的默认页。
- `open: demo-boss:confirm` 打开它的 confirm 页。
- 目标菜单或页面不存在时 check 报 `菜单或页面不存在 …（写作 菜单ID 或 菜单ID:页面ID）`。
- 目标可以是 settings 或 canvas 菜单。canvas 打开另一个 canvas 菜单时，和 page 一样带上合法的同名变量；其他情况与 `/dmenu open` 相同，canvas 变量从默认值开始。

旧格式没有 menus/ 菜单目录：Version 2 简化配置和旧 templates/ 独立模板不能用 open，会报 `这里不支持 open，只能在 menus/ 目录的菜单中使用`；独立模板也不能用 page，继续用 `template:` 链接。

`Type: settings-demo` 的普通按钮只允许 close、refresh、search、page、tell、title、actionbar、tellraw、sound、delay、return 和条件块；指令、chat、connect、open 等会在 check 时报错。

## 行尾选项

选项写在动作行里（通常在末尾），执行时去掉。花括号 `{}` 或尖括号 `<>` 都可以，`=` 也可以写成 `:`，名称不分大小写，所以 TrMenu 的 `{Delay=2}` 原样可用。

| 选项 | 别名 | 作用 |
| --- | --- | --- |
| `{delay=N}` | wait | 只把这一条推迟 N tick（0–72000），后面的动作照常立即执行 |
| `{chance=0.5}` | rate、random | 按概率执行这一条（0–1） |
| `{condition=条件}` | requirement | 条件成立才执行这一条，写法见下方条件 |
| `{players}` | — | 对每个在线玩家执行这一条 |
| `{players=条件}` | — | 只对满足条件的在线玩家执行 |

```yaml
Actions:
  - 'tell: &6恭喜，你触发了彩蛋！ {chance=0.1}'
  - 'tell: &a欢迎回来，VIP。 {condition=perm example.vip}'
  - 'tell: &e{player} 打开了活动菜单 {players=perm example.staff}'
  - 'close {delay=20}'
```

- `{players}` 只能用于 tell、chat、title、actionbar、tellraw、command、sound、connect。文字和指令里的值始终按**点击的玩家**填写：上例中每位管理员收到的都是点击者的名字。
- `{players=条件}` 只能用 perm 和 `%PAPI变量%` 条件，按每位接收者分别判断。
- `delay` 与 `return` 不接受 `{delay=}`；`delay` 也不接受 `{condition=}`，需要时用条件块包住。
- 一行用 `_||_` 连接多条动作时，选项最多的那一段的选项会套用到整行每一条（TrMenu 的规则）。需要不同选项时分成多行。
- 花括号写法的条件到第一个 `}` 结束，不能包含 `{` `}`；尖括号写法 `<condition=…>` 读到最后一个 `>`。

## 条件块

列表中的一项可以是条件块。条件成立执行 `actions`，不成立执行 `deny`：

```yaml
Actions:
  - 'sound: UI_BUTTON_CLICK'
  - condition: 'perm example.daily'
    actions:
      - 'console: exampledaily claim {player}'
      - 'title: `&a领取成功` `明天再来` 5 40 10'
    deny:
      - 'tell: &c需要 VIP 才能领取。'
      - 'sound: ENTITY_VILLAGER_NO'
```

exampledaily 是示意指令，需要换成业务插件的实际指令；领取次数和冷却也应由它检查。

| 键 | TrMenu 别名 | 说明 |
| --- | --- | --- |
| `condition` | requirement、cond、conditions | 条件，字符串或条件列表 |
| `actions` | action、list、click、execute、cmd | 条件成立时执行；可以是一行、列表或再嵌套条件块 |
| `deny` | deny-actions、deny-list 等 | 条件不成立时执行 |
| `priority` | pri | 整数，数字小的先执行 |

- 条件块至少要有 actions 或 deny。没有 condition 的块只是把动作分组，照常执行；这时写 deny 会报错，因为它永远不会执行。
- 条件块可以嵌套，最多 8 层。整组 Actions（所有分支合计）最多 64 条动作，每行最多 2048 字符，不能有换行等控制字符。
- 不写 priority 时按列表位置排序（第 1 项为 0，第 2 项为 1……）；写了 priority 的项按数字插入，同值保持原顺序。
- 条件块里只认上面四个键。写了其他键（例如未加引号的 `tell: hi`）时，check 会提示加引号或报 `未知字段`。
- `cmd`、`execute`、`action` 同时是 actions 的别名：未加引号的 `- cmd: spawn` 会被当成条件块，报 `不支持动作 spawn`。加上引号即可。
- `Actions` 顶层也可以写 TrMenu 的 `all:` 分组。Dialog 只有一种点击，`left`、`right`、`shift_left` 等按键分组会报 `Dialog 只有一种点击，按键分组只能写 all`。

需要“或”时，把第二个条件写进 deny：

```yaml
Actions:
  - condition: 'perm example.vip'
    actions: ['tell: 欢迎，VIP。']
    deny:
      - condition: '%example_points% >= 100'
        actions: ['tell: 积分足够，欢迎。']
        deny: ['tell: &c需要 VIP 或 100 积分。']
```

## 条件写法

条件块的 `condition`、`{condition=}` 和 `{players=}` 使用同一套写法。字符串是一条条件，列表表示每一条都要成立（最多 8 条）。

| 写法 | 含义 | 可用位置 |
| --- | --- | --- |
| `perm 节点` | 玩家有这个权限；也可写 `permission 节点`，TrMenu 的 `perm *节点` 照收 | 全部 |
| `%PAPI变量% = 值`、`!=` | 文字相同 / 不同 | 全部 |
| `%PAPI变量% >= 数字`（`>`、`<`、`<=`） | 按数字比较 | 全部 |
| `名称=值`、`名称!=值` | Variables 枚举变量，值须在列表中 | canvas |
| `名称>=数字` 等 | Placeholders 声明的值 | canvas |
| `not 条件` | 这一条取反 | 全部 |

- 运算符两边可以有空格。比较只用一个 `=`，写 `==` 会报错；数字比较右边必须是数字。
- PAPI 变量必须是一枚完整 token，只含字母、数字和 `_ : . -`。含逗号、空格等字符的复杂变量，在 canvas 的 Placeholders 中声明后用名称比较。
- 取值时去掉首尾空格和颜色代码。**取不到的值（未装 PlaceholderAPI、空值、原样返回）让这一条不成立，加了 not 或用 `!=` 也不成立。**
- settings 没有 Variables / Placeholders，写 `difficulty=hard` 这类名称条件会报错，只能用 perm 和 `%PAPI变量%`。
- 不支持 Kether、JavaScript、`||`、`&&` 或括号。“并且”用列表，“或”用 deny 嵌套。

canvas 片段，要求当前页声明了 `Variables: {difficulty: [normal, hard]}` 和 `Placeholders: {level: "%player_level%"}`：

```yaml
hard:
  Type: button
  Position: [144, 11]
  Text: 困难
  Actions:
    - condition: 'level>=30'
      actions:
        - 'set: difficulty=hard'
        - 'tell: 已切换到困难难度。'
      deny: 'tell: &c需要 30 级才能选择困难。'
```

VisibleWhen、SelectedWhen 和 Cases 仍是另一套更窄的写法（只认声明的名称），见 [变量、条件与占位符](variables.md)。

## 执行顺序

1. 点击时先检查 Permission / RequiresPlugin；canvas 还会重新核对按钮的 VisibleWhen。不通过时整组都不执行。
2. 动作从上到下逐条执行。条件块和 `{condition=}` 在**执行到它时**才判断，这时前面的动作已经完成：canvas 的 set 已生效，指令已执行，PAPI 会重新读取。TrMenu 则是点击时预先判断。
3. `delay: N` 暂停其余全部动作 N tick；`{delay=N}` 只推迟它自己这一条。
4. `return` 结束整组动作，包括外层；带 `{condition=}` 时只在条件成立时结束。
5. 以前的位置限制已经取消：close、page、open、refresh、search、template 可以放在任何位置，之后的动作照常执行。

| 写法 | 效果 |
| --- | --- |
| `delay: 40` | 在这里停 2 秒，再继续执行后面的所有动作 |
| `'tell: 你好 {delay=40}'` | 这一条 2 秒后执行，后面的动作不等它 |

```yaml
Actions:
  - close
  - 'title: `&e传送中` `请稍候` 5 30 5'
  - 'delay: 40'
  - 'command: spawn'
```

暂停期间玩家下线，剩下的动作取消。玩家关掉菜单或打开别的界面不会取消；这时后面的 refresh、page、open 会重新打开界面。

### 自动重绘

动作结束后菜单自动重绘一次，显示新的变量和 PAPI 值（settings 在下一 tick 重绘）：

- 重绘发生在第一个 `delay:` 之前的部分结束时；没有 delay 时就是整组结束、遇到 return 或指令失败时。
- 在那之前执行过 page、open、close、refresh、search 或 template 时不重绘，由这些动作负责界面。
- `delay:` 之后的动作不再自动重绘，需要时在末尾写 refresh。`{delay=N}` 推迟的单条动作不影响重绘时机。
- canvas 在跳转之后再 set，新值不会显示在已经打开的页面上；set 要放在跳转之前。

用 command 打开其他插件的界面时，菜单的自动重绘可能盖住它。先 close 再执行指令；打开 DialogMenu 自己的菜单用 open。

### 失败时发生什么

只有 command / console 会失败：指令执行接口返回失败（例如指令不存在），或指令里的值取不到。

- 玩家收到提示（canvas：`DialogMenu：指令执行失败，后续动作已停止。`；settings：`这项设置暂时不可用。`，即语言文件的 setting.failed），其余动作全部跳过，然后按上面的规则重绘。
- 已执行的指令不会回滚。一条动作里用 `;` 分开的多条指令，前面的已经执行。
- `{delay=N}` 推迟的指令失败时只提示，其他动作已经执行过，不受影响。
- `{players}` 的指令只要有一位玩家执行失败就算失败。
- 外部插件只发失败提示、却返回成功时，DialogMenu 无法知道业务失败。扣钱 → 发物品这样的多步业务不能靠几条菜单指令保证原子性，应让业务插件提供一个完成校验与发放的入口。

## 指令身份与占位符

`command`（cmd / player / execute）以点击玩家身份执行，保留玩家自身权限，不临时提权；`console` 由控制台执行。Permission 只检查点击玩家，不改变指令发送者。

- 指令不写开头的 `/`，每条最长 512 字符。`;` 把一条动作分成几条指令，按顺序执行，例如 `'console: say 开始; give {player} stone 1'`。
- 指令中的 `{名称}` 只能是下表列出的名称，其他花括号内容会报 `指令中的 {…} 未声明`；`{ping}`、`{world}` 只用于文字，不能用于指令。

| 位置 | `{名称}` | `%PAPI变量%` |
| --- | --- | --- |
| settings 的 command / console | `{player}`、`{uuid}` | 支持 |
| canvas 的 command / console | `{player}`、`{uuid}`、Variables 枚举变量、Placeholders 名称 | 支持 |
| settings 的文字类动作 | `{player}`、`{uuid}`、`{ping}`、`{world}` | 支持，取不到显示“未接入” |
| canvas 的文字类动作 | 与 Text 相同：上述内置值、Variables、Placeholders 名称 | 支持，取不到显示“未接入” |

文字类动作指 tell、chat、title、actionbar、tellraw 和 connect。

指令里的值这样填入：

- `;` 在填值**之前**拆分，所以值里的 `;` 不会多出一条指令。
- 每个值只填一次，值里再出现的 `%…%` 或 `{…}` 不会继续展开；换行等控制字符变成空格。
- 指令里的 `%变量%` 只认字母、数字和 `_ : . -` 组成的完整 token。复杂变量在 canvas 的 Placeholders 中声明后写 `{名称}`。
- **任何一个值取不到时（未装 PlaceholderAPI、空值、原样返回，或 Placeholders 取不到），这条指令不执行，按失败处理**，不会带着原样的 `%变量%` 去执行。

PAPI 的返回内容不受菜单配置控制，可能含空格而变成多个参数。console 指令里只放格式固定的值（数字、ID、Variables 枚举值），不要拼入玩家能自己修改的文字，例如昵称、称号或签名。

## 各动作的值

### tell、chat、actionbar

- tell 和 actionbar 支持 `&` 颜色代码与 `&#RRGGBB`。单引号里写字面 `\n` 可以换行：`'tell: 第一行\n第二行'`；双引号里的 `\n` 是真换行，会报错。
- 含 `<image:…>`、`<i18n:…>` 或 `<l10n:…>` 标签的一行交给 [图片标签与独立多语言](text-tags.md) 处理，这一行里的 MiniMessage 颜色也会生效。
- chat 让玩家自己发送聊天消息，不处理颜色。以 `/` 开头的内容会被当作玩家指令；要执行指令请用 command，它有失败检查和取值保护。
- 公开版的 actionbar 直接发送原版动作栏，其他插件的动作栏可能覆盖它，它也可能覆盖其他插件的。

### title

`title: 主标题 副标题 淡入 停留 淡出`，用空格分成最多五段，时间单位为 tick，默认 15、20、15。

```yaml
Actions:
  - 'title: 欢迎'
  - 'title: `&a任务完成` `奖励 已发放` 10 40 10'
```

主标题和副标题支持 `&` 颜色，带空格的文字用反引号包住。时间写成非整数时 check 报错，例如 `title 的淡入时间需要 0–72000 的整数 tick`，常见原因是主标题里的空格没包反引号。

### sound

`sound: 名称-音量-音调`，音量、音调可省略，默认都是 1。数字从右边读，所以资源包里带 `-` 的音效 ID 也能用。

| 写法 | 说明 |
| --- | --- |
| `UI_BUTTON_CLICK-0.8-1.4` | Bukkit 音效名，也可写小写 `ui_button_click` |
| `ui.button.click-0.8-1.4` | 原版音效 ID，自动补 `minecraft:` |
| `minecraft:ui.button.click-0.5` | 只写音量 |
| `mypack:ui-click-0.8-1.4` | 资源包音效，ID 本身可以含 `-` |
| `UI_BUTTON_CLICK; ENTITY_PLAYER_LEVELUP-1-2` | `;` 分开，同时播放多个 |

- 在玩家所在位置播放，属于主音量分类。
- Bukkit 音效名写错时 check 报 `未知音效`。带命名空间的 ID 不检查是否存在，资源包里没有就听不到。
- ID 本身以 `-数字` 结尾时（如 `mypack:hit-2`），把音量和音调都写上：`mypack:hit-2-1-1`。

### tellraw

值是 JSON 文本组件时按 JSON 发送，各字符串里的占位符分别填入，值不会破坏 JSON 结构：

```yaml
Actions:
  - 'tellraw: {"text":"点我回城","color":"gold","clickEvent":{"action":"run_command","value":"/spawn"}}'
```

也可以用 TrMenu 的简写：普通文字中间夹 `<文字@类型=内容>` 片段。

```yaml
Actions:
  - 'tellraw: 点击 <这里@hover=打开官网@url=https://example.com> 或 <复制指令@suggest=/spawn>'
```

| 类型 | 作用 |
| --- | --- |
| `hover` | 悬停文字，字面 `\n` 换行 |
| `url`、`open_url` | 点击打开网址 |
| `suggest` | 点击把内容填进聊天栏 |
| `command`、`execute` | 点击执行，内容写 `/指令` |

简写的文字部分支持 `&` 颜色；JSON 写法用 JSON 自己的颜色字段。

### connect

`connect: 子服名` 通过 BungeeCord / Velocity 代理的 `Connect` 插件消息传送玩家，只写服务器名，不能有空格。没有代理的单服上不会有任何效果；Velocity 需要开启 `bungee-plugin-message-channel`（默认开启）。

### delay、return、close、refresh

- `delay: N` 的 N 是 0–72000 的整数 tick，20 tick 为 1 秒；`delay: 2s` 这类写法会报错。
- return、close、refresh 不接受参数，写成 `close: now` 会报 `不接受参数`；行尾选项照常可用，例如 `close {delay=20}`。
- refresh 重新绘制当前页并重新读取 PAPI；canvas 保留当前变量值。

### set、page、template（canvas）

- `set: difficulty=hard`：变量必须在 Variables 中声明，值必须在它的列表中。
- `page: confirm`：跳到本菜单的 confirm 页，保留合法的同名变量。
- `template: demo-boss/confirm`：旧版链接写法，目标页必须存在。新文件推荐 page / open，复制改名更方便。旧独立模板中的 `template: boss-confirm` 属于旧 templates 目录的 ID，不要原样移到 Version 3 后假定仍能找到。

## Permission 与 RequiresPlugin

settings 的 button、toggle、选择控件可以添加 Permission / RequiresPlugin；选择控件把限制带到每个选项动作。内置 Bind 还会保留其必要依赖。

canvas 的按钮支持 Permission，不支持 RequiresPlugin。无权限时按钮不会自动隐藏，点击时拒绝整组动作；隐藏由 VisibleWhen 控制（读取 Variables 与 Placeholders）。想给无权限的玩家一句提示，可以不写 Permission，改用 `condition: 'perm 节点'` 的条件块和 deny。

## 与 TrMenu 的差异

| 情况 | TrMenu | DialogMenu |
| --- | --- | --- |
| 未知动作名 | 当作 Kether 脚本执行 | 加载时报错，列出可用动作 |
| 未加引号的 `- tell: hi` | 静默忽略 | 加载时报错，提示加引号 |
| 条件判断时机 | 点击时预先判断 | 执行到该处才判断，能看到前面动作的结果 |
| 按键分组 | left、right、shift 等 | 只有 all（Dialog 只有一种点击） |
| 条件语法 | Kether / JavaScript | 本页的条件写法，没有脚本 |
| page | 页码 | 当前菜单的页面 ID |
| title 时间写错 | 用默认值 | 加载时报错 |
| 没有 condition 却写 deny | 照收，deny 永远不执行 | 加载时报错 |
| op、Kether、JS、经济、物品、bossbar 等动作 | 有 | 没有，用业务插件的指令 |

另外，DialogMenu 的指令失败时会提示玩家并停止后续动作，见上方“失败时发生什么”。

## 从 TrMenu 迁移

1. 把 TrMenu 图标 `actions` 下的列表复制到 Actions。`all:` 可以保留，也可以去掉；left / right 等分组合并成一组。
2. 动作名大多可原样使用。op、Kether、JS、经济、物品类动作改成业务插件的 command / console 指令。
3. `open: 菜单` 改成 DialogMenu 的菜单 ID（menus/ 里的文件名），需要时加 `:页面ID`；`page: 数字` 改成页面 ID。
4. 条件改成本页写法：

| TrMenu | DialogMenu |
| --- | --- |
| `$ Number(vars("%example_kills%")) >= 150` | `%example_kills% >= 150` |
| `perm *example.vip` | 原样可用，或写 `perm example.vip` |
| `not perm *example.vip` | 原样可用 |
| JS 的“并且”条件 | 拆成条件列表 |
| JS 的“或”条件 | 第二个条件写进 deny 里的条件块 |

5. 执行 `/dmenu check`。未加引号、未知动作、不存在的菜单或页面、未知音效和写错的条件都会报出位置。

TrMenu：

```yaml
actions:
  all:
    - 'sound: UI_BUTTON_CLICK-0.8-1.4'
    - 'close'
    - 'command: shop {Delay=2}'
```

DialogMenu 中对应的 Actions（shop 是示意指令）：

```yaml
Actions:
  - 'sound: UI_BUTTON_CLICK-0.8-1.4'
  - close
  - 'command: shop {Delay=2}'
```
