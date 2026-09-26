# canvas 元素与贴图

字段放在 `Pages.<页ID>.Elements.<元素ID>`。元素 ID 必须是小写英文开头的合法 ID，不使用中文；显示文字放 Text。

## 通用字段

| 字段 | 类型 / 默认 | 用途 |
| --- | --- | --- |
| Type | text / button / sprite；默认 text | 元素种类 |
| Position | 必填 [X, Row] | 横向像素、纵向行，每行 9 像素 |
| Text | 单行字符串或字符串列表 | text 正文；button 必须恰好一行 |
| Color | 默认 "#e7deed" | `#RRGGBB`，作用于文字 |
| VisibleWhen | 可省略 | 声明过的变量等值条件，如 difficulty=hard |
| SelectedWhen | 可省略 | 选中条件 |
| SelectedSprite | 可省略 | 选中时替换的内置贴图，尺寸必须与原贴图相同 |
| Permission | 可省略 | button 点击权限；不使元素隐藏 |
| Actions | button 必填，1–16 条 | [canvas 动作](actions.md) |

配置只应使用对应类型有意义的字段。sprite 不绘制 Text，text 不使用 Sprite，Permission 对非按钮不提供可见性控制。

## text：正文与标题

放入 Elements 的片段：

```yaml
dialogue:
  Type: text
  Position: [156, 8]
  Width: 348
  Rows: 5
  Text:
    - "你好，{player}。"
    - "古堡的灯重新亮起，你愿意前往调查吗？"
  Color: "#e7deed"
```

| text 专用字段 | 默认 | 限制 |
| --- | --- | --- |
| Width | Canvas.Width - X - 12 | 1–960，仍需放得进画布 |
| Rows | 1 | 1–28，仍需放得进画布 |

文本按测量宽度换行，最多显示 Rows 行；超出时截断并在末尾显示 `..`。Text 的列表表示多条起始文本，每条仍可自动折行。

每条输入文本最多 2048 字符，不允许换行 / Tab 等控制字符。使用列表处理多段，不使用 `Text: |` 的多行块。普通文字使用 Color，不解析 MiniMessage 或 `&a`。`0.1.22-text.1-SNAPSHOT` 的 image / i18n / l10n 文本解析见 [图片标签与独立多语言](text-tags.md)。

## button：可点击区域

```yaml
accept:
  Type: button
  Position: [156, 16]
  Sprite: wide-button
  Text: 接受委托
  Actions: ["message: 你接受了演示委托。", close]
```

button 默认 Sprite 是 button，大小由贴图决定。设置 Width / Rows 不能把内置按钮拉伸；需要宽按钮时使用 wide-button。

按钮文字自动居中，过长裁切。按钮背景和文字都属于点击区域。按钮间不允许重叠，除非双方 VisibleWhen 是同一个变量的不同值，确保不会同时显示。

## sprite：装饰图片

```yaml
portrait:
  Type: sprite
  Position: [24, 3]
  Sprite: emblem
```

不写 Sprite 时默认 emblem。装饰图没有动作；reward 也只是一张图片，不是玩家可以拿走的物品。

## 内置贴图尺寸

两种 Skin 都提供以下贴图。Rows 是画布行数：

| Sprite | Width | Rows | 像素尺寸 | 常见用途 |
| --- | --- | --- | --- | --- |
| panel | 552 | 20 | 552 × 180 | 整块背景 |
| button | 108 | 2 | 108 × 18 | 标准按钮 |
| selected | 108 | 2 | 108 × 18 | 选中的标准按钮 |
| wide-button | 144 | 2 | 144 × 18 | 宽按钮 |
| close | 18 | 2 | 18 × 18 | 右上角 × |
| divider | 348 | 1 | 348 × 9 | 分隔线 |
| emblem | 108 | 12 | 108 × 108 | 占位徽记 / 插图 |
| reward | 27 | 3 | 27 × 27 | 奖励占位图 |

wide-button 不能配 SelectedSprite: selected，因为 144 与 108 的宽度不同。标准 button 可以配 selected。

## 接入已有资源包字体立绘

```yaml
portrait:
  Type: sprite
  Position: [24, 3]
  Font: "my_pack:portraits"
  Glyph: "\uE001"
  Width: 108
  Rows: 12
  Advance: 109
```

这是示意片段，需要资源包实际提供 `my_pack:portraits` 字体与对应字形。直接填图片文件名不能显示。

不依赖 CE，也不能把 Font 改成 `source:IA:...` 等物品注册 ID。从 PNG 定义原生 bitmap 字体，以及任务 Icon 与物品 Material 的区别，见 [图标与材质](icons.md)。

| 自定义字体字段 | 默认 | 要求 |
| --- | --- | --- |
| Font | 无 | 字体资源 ID，只用于 sprite |
| Glyph | 必填 | 单个 BMP 字符；YAML 双引号可用 Unicode 转义 |
| Width | 108 | 1–256，实际图片占用宽度 |
| Rows | 12 | 1–28，实际高度按每行 9 像素计算 |
| Advance | Width + 1 | 0–1024，字体绘制后的游标前进量 |

Width 与 Advance 不是同一个概念。要按实际字体 metrics 填写，否则图片后续元素可能偏位。插件不能仅凭 YAML 校验客户端字体图片是否存在、尺寸是否正确，需要加载资源包后查看实际效果。
