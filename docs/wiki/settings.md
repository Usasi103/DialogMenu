# settings 菜单与页面

`Type: settings` 用于左侧导航、右侧控件的菜单。位置由插件安排，适合玩家设置与常用操作。完整示例见 [快速开始](quick-start.md)。

## 菜单根字段

| 字段 | 类型 | 默认值 / 要求 | 说明 |
| --- | --- | --- | --- |
| Version | 整数 | 必填 1 | 菜单格式版本 |
| Type | 字符串 | 必填 settings | 菜单类型 |
| Title | 文本 / 双语映射 | 内置“玩家设置 / Player Settings” | 整个菜单的标题 |
| DefaultPage | 字符串 | Pages 第一页 | 默认页，必须存在 |
| Language | 字符串 | zh_cn | 新玩家默认语言：zh_cn / en_us |
| Theme | 字符串 | dark | 新玩家默认主题：dark / light |
| HideFocusOutline | 布尔 | true | 固定设置画布的焦点框处理 |
| MainMenu | 动作列表 | 关闭后以玩家身份执行 menu | 共用返回主菜单按钮；可显式写 [close] |
| Pages | 配置段 | 必填，1–8 页 | 顺序也是左侧导航顺序 |

Language、Theme 只给没有有效已存偏好的玩家提供默认值。玩家通过控件选择后保存为个人偏好，重连和重启保留；不会跟随每次配置修改重置。

默认 MainMenu 依赖服务器提供 `/menu` 命令。独立使用时建议显式写 `MainMenu: [close]`，或改为服务器实际的入口命令。

## 子页字段

以下是放到 `Pages.<页面ID>` 下的页面片段：

```yaml
Title: 旅行
Icon: profile-icon
Keywords: [旅行, 主城, travel]
Layout: [介绍, 回城]
Icons:
  介绍:
    Type: text
    Name: 选择你的目的地
  回城:
    Name: 返回主城
    Actions: [close, "command: spawn"]
```

此片段的 spawn 命令需要服务器实际提供。它不是一份可直接放进 menus 根目录的完整菜单。

| 字段 | 类型 | 要求 |
| --- | --- | --- |
| Title | 文本 / 双语映射 | 必填；导航名称和页面标题 |
| Icon | 字符串 | 可省略；左侧导航的 9 像素图标 |
| Keywords | 字符串列表 | 可省略；该页的搜索关键词 |
| Layout | 字符串列表 | 必填；1–30 个不重复的控件名 |
| Icons | 配置段 | 必填；给 Layout 中每个控件定义内容 |
| Renderer | 字符串 | 可省略；canvas / items |

可用导航图标：`profile-icon`、`sound-icon`、`particles-icon`、`notices-icon`、`loot-icon`、`appearance-icon`。它们是内置皮肤图标，不是 Bukkit 材质名，不能写 DIAMOND 替代。

单数 `Icon` 是左侧导航图标，复数 `Icons` 是控件集合。导航 Icon 也不能填写 `source:IA:...` 或任务菜单的 Font/Glyph 配置段；换新 PNG 的资源路径见 [图标与材质](icons.md)。真实物品使用下方物品布局中的 Display.Material。

## Layout 如何排版

Layout 按从上到下的顺序使用控件。Icons 中未列入 Layout 的定义暂时不显示，且不要依赖未启用定义已经被完整校验。

自动排版使用两个面板。上方面板放不下时进入下方面板；`Type: heading` 会主动开始下方面板，作为分组标题。已经进入下方面板后再要求另开一个分组会报布局错误。

Description 也占空间；“最多 30 个控件”是配置数量上限，不代表界面能够容纳 30 个按钮。遇到面板不足时，减少说明、调整顺序或拆分到另一个子页。

下拉列表还需要展开空间。把选项较多的 dropdown 提前，避免放在页面底部。展开时被覆盖区域的控件和文字暂时不响应 / 显示，收起后恢复。

## 文本与双语

```yaml
Name: {zh_cn: "返回主城", en_us: "Return to spawn"}
Description:
  - {zh_cn: "点击传送", en_us: "Click to teleport"}
  - "这行两种语言显示相同文字"
```

翻译映射必须同时包含 zh_cn 和 en_us，不能只填一个。Title、Name、Description 每行以及内置绑定的选项名都可使用这种写法。

纯文本无需加 `$`。简化格式里的 `$foo` 会作为普通文本，不读取旧版 languages 文件中的 foo。

普通文字不解析 MiniMessage / `&a` 颜色语法；控件按皮肤样式显示，长文本按可用宽度裁切。canvas 元素的显式 Color 是另一套字段。

## Renderer 如何选择

未设置 Renderer 时，Layout 中存在 `Display.Material` 或 `Type: item` 会让**整页**自动使用原生物品布局。也可以写 `Renderer: items`。

显式 `Renderer: canvas` 的 settings 子页不能包含物品。原生物品页不支持 toggle、slider、dropdown，详见 [物品页面](items.md)。

需要像素坐标、插图、临时变量时，另建 `Type: canvas` 的菜单。
