# canvas 画布

`Type: canvas` 把每页作为可自由安排文字、按钮和图片的画布，适合 NPC 对话与首领界面。

## 菜单和页面字段

| 菜单根字段 | 用途 |
| --- | --- |
| Version: 1 | 必填 |
| Type: canvas | 必填 |
| DefaultPage | 默认子页，不填取第一页 |
| Title | 子页未配置标题时使用的公共标题 |
| Skin | 公共皮肤，默认 amethyst |
| Canvas | 公共画布设置 |
| Variables | 公共枚举变量 |
| Pages | 1–64 个页面 |

每个 Pages 子页只支持 Title、Skin、Canvas、Variables、Elements。每页必须有 Elements；Title、Skin、Canvas、Variables 可继承根配置。

**继承以整个字段 / 配置段为单位替换，不做深度合并。** 子页写了 Variables，就替换根部全部变量；子页写了 Canvas，就替换根部整个 Canvas 配置段，未写的子字段重新取解析器默认值。

例如根部 `Canvas: {Width: 700, Rows: 24, Background: none, HideFocusOutline: false}`，子页只写 `Canvas: {Rows: 22}`，不会保留宽度 700。它会回到 Width 552、Background panel、HideFocusOutline true，并因非默认行数而报错。需要覆盖时复制完整段再修改。

## Canvas 全字段

```yaml
Canvas:
  Width: 552
  Rows: 20
  Background: panel
  HideFocusOutline: true
```

| 字段 | 默认 | 范围 / 用途 |
| --- | --- | --- |
| Width | 552 | 整数，180–960 像素 |
| Rows | 20 | 整数，8–28 行；每行 9 像素 |
| Background | panel | 当前 Skin 中的内置贴图名；none 表示无背景 |
| HideFocusOutline | true | 仅 Width 552 且 Rows 20 时可为 true |

默认画布为 **552 × 180 像素**。自定义尺寸时设置 HideFocusOutline: false。背景贴图必须放得下，改变 Width / Rows 不会把 panel 拉伸缩放。

例如小画布配置片段：

```yaml
Canvas:
  Width: 360
  Rows: 12
  Background: none
  HideFocusOutline: false
```

## 位置怎么计算

`Position: [156, 8]` 表示从画布左边向右 156 像素，从顶端向下 8 行，也就是 72 像素。坐标从 0 开始，必须为非负整数。

```text
左上角 [0, 0] ─────────────────→ X 像素
    │
    │ Row 1 = 向下 9 像素
    │ Row 2 = 向下 18 像素
    ↓
   Row

默认有效空间：X 宽 552 像素，Row 高 20 行
```

元素必须完整位于画布中：`X + Width <= Canvas.Width`，`Row + Rows <= Canvas.Rows`。

普通按钮宽 108、高 2 行，因此 `Position: [414, 17]` 可以放下；`[500, 19]` 会越界。

## Skin 和 Theme 的区别

canvas 皮肤仅有 amethyst（紫晶）和 parchment（羊皮纸）。它们尺寸相同，颜色和美术不同。canvas 不读取 settings 的玩家 dark / light 主题偏好。

同一菜单不同子页可以选择不同 Skin。Skin 选择的是已经在资源包里的贴图，不能写本地 PNG 文件路径。

## 标题、关闭与交互

Title 是 Dialog 的外部标题元数据，不能代替画布内的可见标题元素。要在面板上显示“守门人”，添加 Type: text 的 Elements 元素。

canvas 没有自动的左侧导航，也没有底部原生关闭按钮。翻页和 × 都需要自己配置 button；ESC 可以关闭。

没有变量选择或按钮操作时，画面不会自动定时刷新。要更新文字可使用 refresh 或重新打开。

## 焦点框和客户端尺寸

焦点框隐藏依赖配套 gui 着色器的几何匹配，并不能按菜单 ID 识别。其默认区域为 576 × 188；其他同位置、同尺寸白线也可能匹配。

修改画布尺寸时关闭 HideFocusOutline，并注意客户端 GUI 缩放。页面裁切时先把 GUI 缩放调小；增加 Rows 不会自动扩大玩家窗口。

下一步：[元素和图片字段](elements.md) · [变量与条件](variables.md)
