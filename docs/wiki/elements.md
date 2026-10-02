# canvas 元素与贴图

字段放在 `Pages.<页ID>.Elements.<元素ID>`。元素 ID 必须是小写英文开头的合法 ID，不使用中文；显示文字放 Text。

## 通用字段

| 字段 | 类型 / 默认 | 用途 |
| --- | --- | --- |
| Type | text / button / sprite；默认 text | 元素种类 |
| Position | 必填 [X, Row] | 横向像素、纵向行，每行 9 像素 |
| Text | 单行字符串或字符串列表 | text 正文；button 必须恰好一行 |
| Color | 默认 "#e7deed" | `#RRGGBB`，作用于文字 |
| VisibleWhen | 可省略 | 条件，如 difficulty=hard、"level>=30"，或条件列表；见 [条件语法](variables.md) |
| SelectedWhen | 可省略 | 选中条件 |
| SelectedSprite | 可省略 | 选中时替换的内置贴图，尺寸必须与原贴图相同；可调宽度的按钮会把它拼成同一宽度，见下方 button |
| Permission | 可省略 | button 点击权限；不使元素隐藏 |
| Actions | button 必填，最多 64 条 | [动作参考](actions.md) |

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

每条输入文本最多 2048 字符，不允许换行 / Tab 等控制字符。使用列表处理多段，不使用 `Text: |` 的多行块。普通文字使用 Color，不解析 MiniMessage 或 `&a`。`0.1.22` 的 image / i18n / l10n 文本解析见 [图片标签与独立多语言](text-tags.md)。

## button：可点击区域

```yaml
accept:
  Type: button
  Position: [156, 16]
  Sprite: wide-button
  Text: 接受委托
  Actions: ["message: 你接受了演示委托。", close]
```

button 默认 Sprite 是 button，默认大小由贴图决定：button / selected 宽 108，wide-button 宽 144，高都是 2 行（18 像素）。

| button 专用字段 | 默认 | 限制 |
| --- | --- | --- |
| Width | Sprite 的原宽（108 或 144） | 16–960 像素整数，仍需放得进画布：`X + Width <= Canvas.Width`；只有 Sprite 为 button / selected / wide-button 时可写 |

放入 Elements 的片段：

```yaml
claim-all:
  Type: button
  Position: [156, 16]
  Width: 240
  Text: 领取全部奖励
  Actions: ["message: 演示：已领取。", close]
```

Width 只改变宽度：贴图左右各 2 像素的边框保持原样，中间重复原贴图的中间一列，任何宽度都不会拉伸变形。高度固定 2 行，Rows 对按钮无效。按钮文字在新宽度内居中，可用宽度为 Width − 8，过长裁切；点击区域是整个 Width × 2 行，重叠检查也按新宽度计算。

不写 Width，或 Width 等于贴图原宽时，按钮仍使用原来的整张贴图，发给客户端的内容与旧版完全相同。wide-button 只是原宽 144 的 button，写了 Width 后与 button 同样处理。

close、emblem、divider、reward、panel 等其他贴图的按钮不能写 Width，`/dmenu check` 会报 `Width: 仅 button / selected / wide-button 贴图的按钮可设置宽度`。

SelectedSprite 为 button / selected / wide-button 时，选中贴图按按钮的宽度拼出：写了 Width 用 Width，否则用 Sprite 的原宽。因此任意宽度的按钮、包括 wide-button，都可以配 `SelectedSprite: selected`。

可调宽度依赖资源包中的按钮切片字形。更新插件后须重新合并资源包（CraftEngine：`/ce reload all` 后按现有流程执行 `/ce workflow default`），玩家重新加载资源包或重进服务器后才能正确显示；仍用旧资源包的玩家会看到缺字方块。

按钮文字自动居中，过长裁切。按钮背景和文字都属于点击区域。按钮间不允许重叠，除非双方 VisibleWhen 能证明不会同时成立：同一名称的不同 `=` 值、`=值` 与 `!=值`，或 `"level<30"` 与 `"level>=30"` 这类不相交的数字范围。无法证明时 `/dmenu check` 拒绝。

## sprite：装饰图片

```yaml
portrait:
  Type: sprite
  Position: [24, 3]
  Sprite: emblem
```

不写 Sprite 时默认 emblem。装饰图没有动作；reward 也只是一张图片，不是玩家可以拿走的物品。

## sprite：用图片 ID 显示 CE / IA 图片

```yaml
portrait:
  Type: sprite
  Position: [24, 3]
  Width: 108
  Rows: 12
  Image: "CE:my_pack:elf_calm"
```

Image 的写法与文字中的 `<image:...>` 标签相同：`CE:` 或 `IA:` 前缀，省略时为 CE；CE 图集可写 `CE:命名空间:图片:行:列`。图片 ID 是 CE / IA 注册的图片 ID，不是物品 ID 或 PNG 路径。

| Image 相关字段 | 默认 | 要求 |
| --- | --- | --- |
| Image | 无 | 只用于 sprite，不能与 Font / Glyph / Advance / Sprite 同时写 |
| Width | 108 | 1–256，占位宽度；字宽由插件按图片自动测量，无需 Advance |
| Rows | 12 | 1–28，占位高度；IA 接口不提供图片高度，需要自己预留 |

图片使用来源插件定义的高度和基线，不会被 Width / Rows 缩放。`/dmenu check` 和 `/dmenu reload` 会逐个检查图片是否可用、是否超出占位宽度；来源插件尚未加载时只给警告。打开菜单时仍取不到的图片显示为 `[image:图片ID]`，并在控制台报告。

## sprite：按条件切换图片（Cases）

```yaml
portrait:
  Type: sprite
  Position: [24, 3]
  Width: 108
  Rows: 12
  Image: "CE:my_pack:elf_calm"
  Cases:
    - When: "quest>=3"
      Image: "CE:my_pack:elf_smile"
    - When: mood=angry
      Image: "CE:my_pack:elf_angry"
```

Cases 从上往下取第一条成立的，都不成立时使用元素自己的图。When 的写法与 VisibleWhen 相同，可以引用 Variables 或 [Placeholders](variables.md)，也可以写条件列表。每个元素 1–16 条。

每条 Case 使用与元素默认图相同的写法：

| 默认写法 | 每条 Case 写 | 说明 |
| --- | --- | --- |
| Image | Image | 共用元素的 Width / Rows |
| Font + Glyph | Glyph，可选 Font、Advance | Font 省略时沿用元素字体；Advance 省略时沿用元素的 Advance |
| Sprite（内置贴图） | Sprite | 尺寸必须与默认贴图相同 |

Cases 只用于 sprite，不能与 SelectedSprite / SelectedWhen 同时使用；按钮的选中效果继续用 SelectedSprite。

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

表中是不写 Width 时的尺寸。button、selected、wide-button 用作按钮时可以用 Width 改成 16–960 像素宽（高度不变），其余贴图尺寸固定；sprite 元素的 Width 不会拉伸内置贴图。

按钮的 SelectedSprite: selected 会拼成与按钮相同的宽度，所以 button、wide-button 以及任意 Width 的按钮都可以配 selected。其他贴图之间的 SelectedSprite 仍要求原尺寸相同。

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
| Glyph | 必填 | 单个 BMP 字符；YAML 双引号可用 Unicode 转义，单引号不转义 |
| Width | 108 | 1–256，实际图片占用宽度 |
| Rows | 12 | 1–28，实际高度按每行 9 像素计算 |
| Advance | Width + 1 | 0–1024，字体绘制后的游标前进量 |

Glyph 不要求是私用区字符，字体把哪个字符映射到图片就写哪个；`"\uE001"` 只是 YAML 双引号的转义写法，也可以直接粘贴该字符。写成 `'\uE001'`（单引号）会变成 6 个字符而报错。已在 CE / IA 注册的图片，改用上方的 Image 更省事，不用记码位和 Advance。

Width 与 Advance 不是同一个概念。要按实际字体 metrics 填写，否则图片后续元素可能偏位。插件不能仅凭 YAML 校验客户端字体图片是否存在、尺寸是否正确，需要加载资源包后查看实际效果。
