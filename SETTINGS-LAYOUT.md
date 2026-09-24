# settings 坐标与字体

`Type: settings` 保留 `Layout` 自动排列，同时支持按项自定义位置与文字样式。无需把菜单转换为 `Type: canvas`，原来的 `Bind`、动作、语言和偏好保存继续有效。修改后运行 `/dmenu check`，通过后 `/dmenu reload`。

## 坐标单位

`Position: [X, Y]` 中 X 是画布像素，Y 是行号，每行 9 像素。例如 `[140, 4]` 对应横向 140 像素、纵向第 4 行。整个 settings 画布为 450 像素 × 29 行，从 0 开始；当前不支持以单像素精度调整 Y。

- text / heading：Position 是文字位置。
- button / toggle / slider / dropdown：Position 是控件左上角。开关、滑条、下拉框的左侧 Name 和下方 Description 默认一起平移。
- `LabelPosition` 单独设置开关、滑条、下拉框左侧 Name 的位置。
- 省略 Position 时保持自动排列；显式指定后，后续自动项目从该项目下方继续排列。
- heading 仍用于开始下方面板；Position 只移动标题，不改变背景面板的位置。

## 文字与控件

下面是 `Pages.profile.Icons` 内的一项：

```yaml
玩家:
  Type: text
  Name: "玩家：{player}"
  Position: [140, 3]
  FontSize: 12
  Bold: true
  Width: 280
  Color: '#FFD966'
```

`FontSize` 默认 8，纯文字、标题支持 6–24；控件贴图为固定高度，按钮、开关、滑条、下拉框支持 6–12。`Bold` 默认为 false，必须填写布尔值。字体变化会影响文字宽度，超出 Width 的内容会裁切；自动排列会为大字和说明预留行数。

`Width` 和 `Color` 仅用于 text / heading。Color 支持 `text`、`muted`、`heading` 或带引号的 `'#RRGGBB'`。控件字号和加粗同时用于 Name、当前值、下拉选项与 Description；贴图本身不会随字号缩放。

```yaml
音效:
  Type: toggle
  Style: switch
  Name: "鸟鸣、风声与自然音效"
  Bind: sounds
  Position: [322, 4]
  LabelPosition: [130, 4]
  FontSize: 10
  Bold: true
```

## 页面标题与左侧分类

在某一页的 Title 旁边设置右侧标题：

```yaml
profile:
  Title: "玩家信息"
  TitleStyle:
    Position: [130, 1]
    FontSize: 12
    Bold: true
    Width: 300
    Color: heading
  # 此处继续填写 Layout 和 Icons
```

在 settings.yml 顶层设置全部分类：

```yaml
Navigation:
  Position: [0, 3]
  Step: 2 # 分类之间的行距，至少 2
  FontSize: 8 # 6–12
  Bold: false
```

这些字段用于字体画布设置页。`Renderer: items` 使用原生 Dialog 物品排列，其图标由物品页面配置控制。背景板与控件尺寸仍由当前皮肤决定；需要更换背景或完全自由的页面结构时使用 canvas 菜单。
