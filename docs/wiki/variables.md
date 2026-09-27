# 变量、条件与占位符

## 支持范围速查

| 使用位置 | 内置值 | 自定义值 | PAPI |
| --- | --- | --- | --- |
| settings 显示文本 | player、uuid、ping、world | 外部插件提供的 PAPI | 支持完整 `%变量%` |
| settings State | 使用 Bind 或完整 PAPI token | 外部插件状态 | 支持 |
| settings 命令动作 | player、uuid | 不支持 | 不支持 |
| canvas Text / message | player、uuid、ping、world | Variables 枚举变量、Placeholders 名称 | 支持完整 `%变量%` |
| canvas 命令动作 | player、uuid | Variables 中的枚举变量 | 不支持 |
| canvas VisibleWhen / SelectedWhen / Cases | 无 | Variables 与 Placeholders 名称 | 通过 Placeholders 声明 |
| Display.Material | 无 | 静态物品 ID | 不支持 |

以上内置值写作 `{player}` 等；PAPI 使用 `%example_value%`。

canvas 的 Title 直接作为普通外部标题使用，不做上述替换；需要动态可见标题时使用 text 元素。

## settings 文字中的变量

```yaml
玩家:
  Type: text
  Name: "你好，{player}"
  Description:
    - "世界：{world}"
    - "延迟：{ping} 毫秒"
```

PAPI 必须安装 PlaceholderAPI，并由对应扩展 / 插件提供该变量。未解析、空值或原样返回 token 时显示未接入。解析后的传统颜色代码会去除，控制字符转换为空格。

State 必须是一枚完整 token，例如 `State: "%example_mode%"`；不能写成 `State: "当前：%example_mode%"`。

## canvas Variables

菜单根部或页面中的变量段：

```yaml
Variables:
  difficulty: [normal, hard, overload]
  choice: [pending, accepted, declined]
```

每个变量是枚举：

- 变量名为小写英文字母开头的合法 ID，不能叫 player / uuid。
- 允许值为 1–48 位英文字母、数字、下划线、短横线，大小写保留。
- 每个变量 1–16 个不同值，第一项就是默认值。
- 不允许任意输入、数值运算、中文枚举值或未声明值。中文显示名称写在 Text。

若需要 on / off 作为枚举值，写成 `["on", "off"]`，避免 YAML 布尔转换。

## 条件显示与选中

以下为 Elements 内的片段，要求当前页已声明 difficulty：

```yaml
hard:
  Type: button
  Position: [144, 11]
  Text: 困难
  SelectedWhen: difficulty=hard
  SelectedSprite: selected
  Actions: ["set: difficulty=hard", refresh]
hard-description:
  Type: text
  Position: [282, 11]
  Width: 222
  Rows: 4
  VisibleWhen: difficulty=hard
  Text:
    - 生命与攻击提高
    - 适合熟悉机制的队伍
```

VisibleWhen 决定是否绘制与提供点击区域。SelectedWhen 决定是否改用 SelectedSprite；只写 SelectedWhen、不提供 SelectedSprite，不会自动生成高亮贴图。

## canvas Placeholders：PAPI 值

菜单根部或页面中声明要用到的 PAPI 变量，给每个值起名：

```yaml
Placeholders:
  rank: "%luckperms_primary_group_name%"
  level: "%player_level%"
  quest: "%example_quest_stage%"
```

- 名称规则与 Variables 相同，不能与同页 Variables 重名，也不能叫 player / uuid / ping / world。
- 值必须是一枚完整的 `%变量%`，可以含逗号、括号等参数；每页最多 32 个。
- 与 Variables 一样按整段继承：子页写了 Placeholders，就替换根部全部声明。
- 需要安装 PlaceholderAPI 及提供该变量的扩展。取值时去除传统颜色代码和首尾空格；空值、未解析或原样返回 token 视为取不到。

声明后，Text / message 里写 `{rank}`，条件里写 `rank=vip`。Text / message 也可以直接写 `%player_level%` 这类简单 token；含逗号、空格等字符的复杂变量请先声明再用名称引用。取不到的值在文字中显示未接入。

取值时机：打开菜单、page / menu 跳转、refresh 时各读一次。菜单打开期间不会自动刷新。点击按钮时会重新读取一次，条件不再成立就只刷新界面，不执行动作。

命令动作不能使用 Placeholders 的值或 `%变量%`；PAPI 返回内容不受配置控制，拼进 console 指令有注入风险。需要传给命令的值用 Variables 枚举。

## 条件语法

| 写法 | 含义 | 适用 |
| --- | --- | --- |
| `名称=值` | 文字完全相同 | Variables、Placeholders |
| `名称!=值` | 文字不同 | Variables、Placeholders |
| `名称>数字`、`>=`、`<`、`<=` | 按数字比较 | 仅 Placeholders |

以下为 Elements 内两个元素的条件片段：

```yaml
locked:
  VisibleWhen: "level<30"                 # 单条条件
reward:
  VisibleWhen: ["level>=30", "quest=0"]   # 列表：每条都要成立
```

- 写成列表表示每一条都要成立，最多 8 条。不支持 `||`、括号、权限表达式或 JavaScript。
- Variables 只能用 `=` / `!=`，值必须在它的枚举列表中。
- 数字比较要求 PAPI 返回纯数字；`1,000`、`30级` 等格式化文本不是数字，条件不成立。需要比较时选用扩展提供的原始数值变量。
- **取不到的 Placeholders 值让所有相关条件都不成立**，包括 `!=`。因此 `rank!=vip` 在 PAPI 缺失时也不成立，元素隐藏、Cases 回到默认图。
- 旧写法 `difficulty=hard` 保持原义。

YAML 中含 `>`、`<`、`!` 或以 `%` 开头的值请加引号。完整可复制的示例见 [placeholders.yml](examples/placeholders.yml)。

## 变量能保留多久

| 操作 | 行为 |
| --- | --- |
| set 后刷新 | 保留修改后的值 |
| page / menu / template 跳转 | 目标页声明同名变量且接受该值时保留 |
| 目标页没声明该变量 | 不带入该变量 |
| 目标页声明同名变量，但不接受旧值 | 取目标页枚举第一项 |
| 重新用命令打开 | 从默认值开始 |
| 关闭后重新打开 / 重连 | 不恢复临时变量 |
| 重载仍存在的 canvas 页 | 保留仍合法的变量值 |

多页都需要的变量，建议放到菜单根部，不在子页重写 Variables；这样不用重复声明，也能避免整段覆盖造成变量丢失。

这些不是数据库字段，不能用来记录永久任务进度、购买次数或余额。永久业务状态由对应插件保存。

## 玩家语言和主题是另一种状态

settings 的 language / theme 绑定保存在玩家 PDC，键保留为 `playersettings:menu_language` 与 `playersettings:menu_theme`。它们是跨菜单共享的玩家偏好，不会改变 Minecraft 本身或其他插件的语言。

修改根 Language / Theme 只改变缺少有效偏好的玩家的默认值；已有玩家可通过对应下拉框重新选择。
