# 变量、条件与占位符

## 支持范围速查

| 使用位置 | 内置值 | 自定义值 | PAPI |
| --- | --- | --- | --- |
| settings 显示文本 | player、uuid、ping、world | 外部插件提供的 PAPI | 支持完整 `%变量%` |
| settings State | 使用 Bind 或完整 PAPI token | 外部插件状态 | 支持 |
| settings 命令动作 | player、uuid | 不支持 | 不支持 |
| canvas Text / message | player、uuid | Variables 中的枚举变量 | 不支持 |
| canvas 命令动作 | player、uuid | Variables 中的枚举变量 | 不支持 |
| canvas VisibleWhen / SelectedWhen | 无 | 声明的枚举变量等值比较 | 不支持 |
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

条件语法只有 `变量=值`。不支持大于、小于、`&&`、`||`、权限表达式、JavaScript 或 PAPI 判断。

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
