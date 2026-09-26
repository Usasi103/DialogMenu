# 图标与材质：更换图片与非 CE 用法

DialogMenu 不强制依赖 CraftEngine。先看要改的是哪个字段：物品模型、字体贴图和资源包发送方式分别配置，不能互相替代。

`0.1.22` 可在文本中使用 `<image:CE:namespace:id>`、`<image:IA:namespace:id>`，详见 [图片标签与独立多语言](text-tags.md)。以下原有字段继续兼容。

| 所在位置 | 显示内容 | 更换方法 |
| --- | --- | --- |
| settings 的 `Pages.<页>.Icon` | 左侧分类的内置字体图标 | 换内置名称，或替换资源包中对应 PNG |
| settings 的 `Pages.<页>.Icons` | 本页控件集合 | 复数 Icons 是容器，本身不是材质字段 |
| settings 的 `Icons.<控件>.Display.Material` | 真实物品及其模型 | 原版材质或 `source:插件ID:物品ID` |
| quest-demo 的 `Tasks.<任务>.Icon`、`Rewards[].Icon` | 任务 / 奖励字体图标 | 内置名称或 `Font/Glyph/Width/Advance` |
| canvas 的 `Elements.<元素>`，`Type: sprite` | 装饰贴图 | 内置 `Sprite`，或自定义 `Font/Glyph/Width/Rows/Advance` |

## 原版与其他插件的物品材质

下面是已有 settings 菜单 `Pages.preview` 的**子页片段**。它不是独立菜单文件；添加后按需设置页名和导航。

```yaml
Title: 物品预览
Renderer: items
Layout: [武器]
Icons:
  武器:
    Type: item
    Display:
      Material: "source:IA:my_pack:sword"
      Name: 武器展示
      Fallback: BARRIER
```

只需按实际来源替换 Material：

| 来源 | Material 示例 |
| --- | --- |
| 原版 | `DIAMOND` 或 `minecraft:diamond` |
| CraftEngine | `source:CE:my_pack:sword` |
| ItemsAdder | `source:IA:my_pack:sword` 或 `source:ItemsAdder:my_pack:sword` |
| Nexo | `source:Nexo:my_sword` |
| Oraxen | `source:Oraxen:my_sword` |
| NeigeItems | `source:NI:物品ID` |

自定义物品 ID 是示例，须在对应插件中存在；IA / CE 使用完整的小写 `namespace:id`。只安装实际使用的来源插件，ItemBridge 已内置，无需另外安装。自定义模型仍需来源插件的资源包。

`Fallback: BARRIER` 在来源不可用时显示屏障；如果该控件是带动作的 button，还会停用动作。没有 Fallback 时，来源不可用会使检查 / 重载失败。原生物品图标本身不绑定点击动作，button 使用旁边文字或下方同名按钮操作。

Display.Material 会让**整页**使用原生 Dialog 物品布局，不保留字体画布皮肤与自由坐标，不能与 toggle / slider / dropdown 同页混用。需要画布装饰时使用下面的字体图标。完整规则见 [物品页面与物品源](items.md)。

## 任务菜单的 Icon

编辑 `menus/demo-quests.yml` 中的 `Tasks.<任务>.Icon` 或 `Rewards` 每项的 Icon。内置可用名称是：

`iron_sword`、`fishing_rod`、`nether_star`、`book`、`iron_ingot`、`gold_ingot`、`experience_bottle`、`amethyst_shard`、`cod`、`diamond_chestplate`、`bread`、`oak_sapling`、`compass`。

例如，把已有任务的 `Icon: iron_sword` 改成 `Icon: compass`。这些名称映射到 `dialogmenu_dialogue:quest_items` 字体中的既定字形，不接受任意原版材质名，也不接受 `source:IA:...` 等物品源写法。

自定义 Icon 可替换为以下配置段；奖励图标也使用同样格式：

```yaml
Icon:
  Font: "my_pack:icons"
  Glyph: "\uE001"
  Width: 12
  Advance: 13
```

`Font` 是资源包字体 ID，`Glyph` 是该字体中的单个 BMP 字符，Unicode 转义须写在 YAML 双引号内。`Width` 是横向占位，支持 1–18；`Advance` 必填，使用字形实际绘制后的前进量。这两个字段不会缩放图片；示例中的 12 / 13 必须按实际图片和字体指标调整。建议显示高度 12、ascent 4，并让图形落在 18 像素高的任务条目内。

## 不依赖 CE 的自定义 PNG 字体

可以用已有资源包中的字体，也可以自行定义 Minecraft bitmap 字体。例如，在**资源包根目录**准备：

```text
assets/my_pack/textures/gui/menu_icon.png
assets/my_pack/font/icons.json
```

`menu_icon.png` 由你提供。以下是 icons.json 的完整内容，示例按单个方形图标设置：

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "my_pack:gui/menu_icon.png",
      "height": 12,
      "ascent": 4,
      "chars": ["\uE001"]
    }
  ]
}
```

图片路径相对命名空间下的 textures；`my_pack:icons` 对应 font/icons.json。字体 JSON 的 height 控制显示高度，ascent 控制基线位置。透明留白、图片比例和显示高度会影响实际可见宽度与 Advance，不能只照抄上面的数字。更新字体和 PNG 后，需要重新构建并发送资源包。

已有 IA / Nexo / Oraxen 资源包也可以使用这套原生字体文件，合入其实际资源包源目录并保留 assets 结构；具体合并入口取决于对应插件。物品注册 ID 与字体 ID 是两套名称，DialogMenu 不会自动把物品模型转换成字体贴图。

canvas 中用同一个字体时，将 Font / Glyph 写进 `Type: sprite` 元素，并配置 Position、Width、Rows、Advance。其示例和范围见 [画布元素](elements.md)。

## 设置菜单左侧的 Icon

settings 的 `Pages.<页>.Icon` 只接受内置名称，不接受 Material 或 Font/Glyph 配置段。常用名称与当前资源包贴图对应如下：

| Icon | `assets/dialogmenu_settings/textures/ui/` 下的文件 |
| --- | --- |
| profile-icon | hallow_icon_0.png |
| sound-icon | hallow_icon_1.png |
| particles-icon | hallow_icon_2.png |
| notices-icon | hallow_icon_3.png |
| loot-icon | hallow_icon_4.png |
| appearance-icon | hallow_icon_5.png |

换成已有图标只需改 Icon 名称。换全新图片时，修改合并资源包源目录中的对应 PNG，保留原尺寸和字形指标；字体映射在 `assets/dialogmenu_settings/font/ui.json`。同名图标被其他页面引用时也会一起变化。仅增加 PNG 或编造一个新的 Icon 名称不会自动注册导航图标。

## 完全不用 CE 时发送资源包

把 DialogMenu 配套资源和需要的自定义图标 / 模型合入客户端会加载的资源包。全局 config.yml 的 ResourcePack 使用以下任一方式：

- `Provider: URL`：填写真实资源包 ZIP 直链，玩家通过 `/dmenu pack` 接收。
- `Provider: External`：由 IA / Nexo / Oraxen 等插件或 server.properties 发送，填写**发送方实际使用的 UUID**，而不是随意生成一个 UUID。

完整可复制的 ResourcePack 配置见 [资源包](resource-pack.md)。Provider 只决定检查 / 发送哪个包，不会自动合并文件、生成字体或改写字体 ID；`RequireLoaded: false` 只关闭加载门槛，不会补齐缺失图片。

菜单改完先 `/dmenu check`，通过后 `/dmenu reload`。只有菜单文字或已有图标名称变化时不需要重新打包；新增或替换 PNG、字体、模型时需要更新资源包并让玩家重新加载。
