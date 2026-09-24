# 物品页面与物品源

真实物品展示放在 `Type: settings` 菜单的子页中。该页的 Layout 中存在 Display.Material 或 Type: item，就自动使用原生 Dialog 物品布局；也可显式写 Renderer: items。

这里显示的是实际 ItemStack 的副本，保留原物品模型和悬停提示。它不自动发放或扣除玩家物品。

不强制使用 CE。导航 Icon、任务 Icon、画布 sprite 与这里的 Display.Material 不是同一种字段；更换方法与非 CE 示例见 [图标与材质](icons.md)。

## 页面片段

以下放到 `Pages.preview`，不是独立菜单文件：

```yaml
Title: 物品预览
Renderer: items
Layout: [钻石, 自定义武器, 关闭]
Icons:
  钻石:
    Type: item
    Display:
      Material: minecraft:diamond
      Amount: 3
      Name: 原版钻石
      Lore: [这里只展示，不会发放]
  自定义武器:
    Type: button
    Display:
      Material: "source:CE:my_pack:sword"
      Fallback: BARRIER
      Name: 自定义武器
    Actions: [close]
  关闭:
    Actions: [close]
```

my_pack:sword 是示意物品，需要在 CraftEngine 中存在。未安装或找不到时显示屏障，且该自定义武器按钮停用。

## Display 字段

| 字段 | 类型 / 默认 | 规则 |
| --- | --- | --- |
| Material | 字符串 | 原版材质或 source:插件ID:物品ID |
| Name | 文本 / 双语映射 | 与控件外层 Name 二选一 |
| Lore | 文本或列表 | 与外层 Description 二选一，最多 6 行 |
| Amount | 整数，默认 1 | 1–99；只改展示副本数量 |
| Fallback | 可选字符串 | 必须是有效的原版物品 |

没有任何 Name 时使用控件名。Amount / Fallback 必须与 Material 配合。

Display.Name / Lore 是菜单里的配套文字，不会改写源物品自己的名称、Lore、模型或其他数据。物品原有提示仍可在悬停时查看。

## 页面控件类型

| Type | 用途 | Actions / Permission / RequiresPlugin |
| --- | --- | --- |
| button（默认） | 可点击按钮，可带物品 | 支持，Actions 必填 |
| item | 只展示物品，必须有 Material | 不支持 |
| text | 说明文字 | 不支持 |
| heading | 标题文字 | 不支持 |

原生物品图标本身不绑定点击动作；点击配套名称 / 说明或下方同名按钮操作。原生页面使用 Minecraft 按钮与布局，保留焦点提示，不使用字体画布皮肤、玩家主题或自由坐标。

不要同页混用 toggle / slider / dropdown。需要设置控件时另建普通 settings 子页，用 page 动作连接。

## Material 语法

```text
DIAMOND
minecraft:diamond
source:VANILLA:diamond
source:CE:my_pack:sword
source:ItemsAdder:my_pack:sword
source:IA:my_pack:sword
source:Nexo:my_sword
source:Oraxen:my_sword
source:NI:物品ID
source:MythicMobs:SkeletonKingSword
source:MMOItems:SWORD:my_sword
source:HeadDatabase:123
```

插件 ID 不区分大小写。后面的物品 ID 原样传给适配器；CraftEngine 和 ItemsAdder 要求小写 namespace:id。其他来源的组合 ID 要按对应适配器和插件实际定义填写。

Material 不展开 PAPI，不支持 TrMenu 的 JSON、动画、JS 或头颅表达式。空气不能作为展示物品。

## 支持的来源

内置 ItemBridge 1.0.32 的 39 个插件适配器；无需另装 ItemBridge，只安装实际使用的来源插件。“支持 39 种”不表示服务器安装了全部 39 种。

| 插件 | 插件 ID |
| --- | --- |
| AdvancedItems | advanceditems |
| AzureFlow | azureflow |
| Baikiruto | baikiruto |
| BreweryX | breweryx |
| CraftEngine | craftengine |
| CrazyVouchers | crazyvouchers |
| CustomCrafting | customcrafting |
| CustomFishing | customfishing |
| DragonArmourers | dragonarmourers |
| EcoArmor | ecoarmor |
| EcoCrates | ecocrates |
| EcoItems | ecoitems |
| EcoMobs | ecomobs |
| EcoPets | ecopets |
| EcoScrolls | ecoscrolls |
| EmakiItem | emakiitem |
| ExecutableBlocks | executableblocks |
| ExecutableItems | executableitems |
| HeadDatabase | headdatabase |
| HMCCosmetics | hmccosmetics |
| ItemEdit | itemedit |
| ItemsAdder | itemsadder |
| ItemsXL | itemsxl |
| MagicGem | magicgem |
| MMOItems | mmoitems |
| MythicMobs | mythicmobs |
| NeigeItems | neigeitems |
| Nexo | nexo |
| Nova | nova |
| Oraxen | oraxen |
| PxRpg | pxrpg |
| Ratziel | ratziel |
| Reforges | reforges |
| Sertraline | sertraline |
| Slimefun | slimefun |
| StatTrackers | stattrackers |
| SX-Item | sxitem |
| Talismans | talismans |
| Zaphkiel | zaphkiel |

常用别名：CE → CraftEngine，IA → ItemsAdder，NI → NeigeItems，SI / SX-Item → SX-Item，MM → MythicMobs，MI → MMOItems，HDB → HeadDatabase，CF → CustomFishing，EI → ExecutableItems，EB → ExecutableBlocks。

## 来源失效时

| 情况 | 结果 |
| --- | --- |
| check / reload 时来源不可用，未设 Fallback | 校验失败，保留旧菜单 |
| 来源不可用，设了有效 Fallback | 警告，显示回退物品，动作停用 |
| 运行中构建失败 / 插件停用 | 不执行该物品按钮动作 |
| Fallback 本身无效 | 校验失败 |

Fallback 只解决显示，不把失效物品当成成功商品。点击时也会重新检查来源，不能靠已经打开的界面绕过失效检查。

CraftEngine 重载后会刷新菜单；其他来源修改后可执行 /dmenu reload。每次展示和点击会重新查找物品，构建时传入查看菜单的玩家。

自定义模型仍需对应物品插件的资源包。DialogMenu 资源包只提供菜单资产，不包含服务器所有自定义物品。
