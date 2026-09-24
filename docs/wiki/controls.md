# settings 控件参考

本章字段放在 `Pages.<页ID>.Icons.<控件名>` 下，并把控件名加入该页 Layout。以下均是 **Icons 内的片段**，不是完整菜单。

## 字段总览

| 字段 | 用途 | 适用条件 |
| --- | --- | --- |
| Type | button / text / heading / toggle / slider / dropdown | 不填为 button |
| Name | 显示名称 | 不填取控件名；支持中英映射 |
| Description | 一段或列表形式的说明 | 字体设置页最多 3 行；heading 不使用 |
| Bind | 内置状态与动作绑定 | toggle / slider / dropdown |
| State | 完整的 %PAPI变量% | 无 Bind 的 toggle / slider / dropdown |
| Options | 选项映射，顺序就是显示顺序 | slider / dropdown，2–8 项 |
| Actions | 有序动作列表 | button / 无 Bind 的 toggle |
| Permission | 执行动作需要的玩家权限 | 交互控件；不是隐藏条件 |
| RequiresPlugin | 依赖的插件名 | 交互控件；按实际插件名填写 |

这些是字体设置页的控件字段；物品页另外支持 Display，见 [物品页面](items.md)。

## button：普通按钮

```yaml
回城:
  Type: button
  Name: 返回主城
  Description: 点击返回出生点
  Actions:
    - close
    - "command: spawn"
```

Type 和 Name 都可省略；Actions 必须提供。button 不读取 State，也没有 Options。spawn 需要对应插件或服务器命令支持。

## text：说明文字

```yaml
玩家信息:
  Type: text
  Name: "玩家：{player}"
  Description:
    - "世界：{world}"
    - "延迟：{ping} 毫秒"
```

只使用 Type、Name、Description，不可添加 Actions、Bind、Permission。

## heading：下方面板标题

```yaml
更多设置:
  Type: heading
  Name: 更多设置
```

只使用 Type、Name。它会开始下方面板，而非在当前位置随意插入一个标题。想显示普通小标题，可用 text。

## toggle：开关

内置绑定写法：

```yaml
环境粒子:
  Type: toggle
  Name: 环境粒子
  Bind: particles
```

自定义联动写法：

```yaml
私人消息:
  Type: toggle
  Name: 接收私人消息
  State: "%example_messages_enabled%"
  RequiresPlugin: ExamplePlugin
  Actions: ["command: messages toggle"]
```

第二个例子需要自己的插件提供 PAPI 变量、messages 指令和实际保存逻辑。`%example_messages_enabled%` 是示意变量，不是 DialogMenu 提供的变量。

toggle 识别的真值：开、开启、on、enabled、true、1；假值：关、关闭、off、disabled、false、0。英文字母不区分大小写。未解析或不认识的状态显示未接入。

## slider：离散滑条

```yaml
粒子密度:
  Type: slider
  Name: 粒子密度
  Bind: particle-density
  Options:
    "off": 关闭
    low: 低
    medium: 中
    high: 高
```

按 Options 顺序分配档位，通过点击轨道或两侧箭头切换。它不是可以连续拖动的原生滑条。允许 2–8 个选项。

## dropdown：下拉选择

```yaml
界面主题:
  Type: dropdown
  Name: 界面主题
  Bind: theme
  Options:
    dark: 暗色
    light: 亮色
```

选择后立即应用并收起；同一时间只展开一个下拉框。下拉内容要完全放在画布内，空间不足会使 check 失败。

## 全部内置 Bind

| Bind | 控件类型 | 值 / 功能 | 联动来源 |
| --- | --- | --- | --- |
| language | dropdown / slider | zh_cn、en_us | DialogMenu 个人偏好 |
| theme | dropdown / slider | dark、light | DialogMenu 个人偏好 |
| particle-density | slider / dropdown | off、low、medium、high | Ambience，状态通过 PlaceholderAPI |
| particles | toggle | 环境粒子总开关 | Ambience，状态通过 PlaceholderAPI |
| sounds | toggle | 环境音效 | Ambience，状态通过 PlaceholderAPI |
| leaves | toggle | 落叶 | Ambience，状态通过 PlaceholderAPI |
| firefly | toggle | 萤火虫 | Ambience，状态通过 PlaceholderAPI |
| biome | toggle | 群系粒子 | Ambience，状态通过 PlaceholderAPI |
| pickup | toggle | 拾取提示 | PickupNotifier |
| loot-beams | toggle | 掉落光柱 | LootBeam |
| loot-sounds | toggle | 光柱音效 | LootBeam |

Bind 同时提供状态来源和动作；用了 Bind 就不要再写 State 或控件层 Actions。其必要插件依赖不能被 RequiresPlugin 替换成另一个插件。

选择控件的内置 Options 只能使用该绑定允许的值。例如 theme 不接受 blue。选项显示文字可以自由修改。

## 自定义 dropdown / slider

```yaml
游戏模式:
  Type: dropdown
  Name: 游戏难度
  State: "%example_mode%"
  RequiresPlugin: ExamplePlugin
  Options:
    easy:
      Name: 简单
      Actions: ["command: examplemode easy"]
    hard:
      Name: 困难
      Actions: ["command: examplemode hard"]
```

无 Bind 时，Options 每项需要 Name 和 Actions，而不是一条文字。动作写在选项里，不能写在控件外层。控件的 Permission 和 RequiresPlugin 会用于各选项动作。

读取到的状态要与选项键匹配。DialogMenu 不会替外部插件保存这个模式；执行命令后重新读取 State。

## 常见组合错误

| 错误 | 改法 |
| --- | --- |
| Bind 与 State / Actions 同时写 | 保留 Bind，或完全改成自定义 State + 动作 |
| toggle 使用 Bind: theme | theme 是选择值，使用 dropdown / slider |
| button 配 Options | 改为选择控件，或删掉 Options |
| 自定义 dropdown 的 Options 写纯文字 | 改成每项 Name + Actions |
| heading 带 Description | 删除说明，另用 text |
| 未加引号的 off 变成布尔值 | 写成 `"off"` |
