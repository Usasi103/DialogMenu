# 图片标签与独立多语言（测试版）

从 `0.1.22-text.1-SNAPSHOT` 开始提供。测试包包含 0.1.21 的兼容与资源自动导入功能，以及后续修复合法目录联接被路径检查拒绝的补丁。

CE 已通过真实插件联动验证；IA 适配已实现，尚待真实 IA 服务器验证。完整验证范围见 [测试记录](../development/TEXT-TAGS-TEST.md)。

## 翻译由 DialogMenu 管理

启动后在 `plugins/DialogMenu/` 自动生成：

```text
text.yml
translations/zh_cn.yml
translations/en_us.yml
```

不需要在 CraftEngine 中创建翻译，也不需要安装 CE 才能使用多语言。CE、IA 等插件仍可负责资源包的构建与发送；翻译文件留在 DialogMenu 目录，不放进资源包。

`text.yml`：

```yaml
DefaultLanguage: zh_cn
```

`translations/zh_cn.yml`：

```yaml
menu:
  title: "旅行菜单"
  welcome: "你好，<arg:0>！"
  spawn: "返回主城"
```

`translations/en_us.yml`：

```yaml
menu:
  title: "Travel menu"
  welcome: "Hello, <arg:0>!"
  spawn: "Return to spawn"
```

菜单字段可以写：

```yaml
Title: "<l10n:menu.title>"
```

settings 控件片段：

```yaml
Name: "<l10n:menu.spawn>"
Description:
  - "<i18n:menu.title>"
```

canvas 元素片段：

```yaml
welcome:
  Type: text
  Position: [156, 8]
  Width: 348
  Rows: 2
  Text: "<l10n:menu.welcome:'{player}'>"
```

| 标签 | 语言选择 |
| --- | --- |
| `<i18n:menu.title>` | `text.yml` 的 DefaultLanguage |
| `<l10n:menu.title>` | 当前查看菜单的玩家客户端语言 |
| `<l10n:menu.welcome:'Alex'>` | 翻译内 `<arg:0>` 为第一个参数，序号从 0 开始 |

支持新增 `ja_jp.yml`、`fr_fr.yml` 等语言文件。查找顺序是客户端完整语言（如 `fr_ca`）、仅语言文件（如 `fr.yml`）、默认语言；同样适用于某个键缺失时的回退。全部找不到则显示键名，并在控制台报告。`i18n` 只查默认语言。

现有 `Name: {zh_cn: ..., en_us: ...}` 及玩家保存的菜单语言偏好继续有效；它们不改变 `l10n` 的客户端语言含义。客户端语言切换后重新打开或刷新菜单即可更新。

翻译支持嵌套 `i18n` / `l10n`、图片、颜色和加粗等文字装饰；循环引用受限制。翻译应使用单行字符串，控制字符显示为空格。修改后执行 `/dmenu check`、`/dmenu reload`，不需要重新生成资源包。语法错误保留旧菜单与旧翻译。

## 引用 CE / IA 的字体图片

| 写法 | 来源 |
| --- | --- |
| `<image:my_pack:star>` | 默认 CraftEngine |
| `<image:CE:my_pack:star>` | CraftEngine，别名 CraftEngine |
| `<image:IA:my_pack:star>` | ItemsAdder，别名 ItemsAdder |
| `<image:CE:default:emojis:0:1>` | CE 图集第 0 行、第 1 列 |

插件前缀不区分大小写；图片 ID 使用来源插件注册的完整 ID。CE/IA 是两套图片注册表，图片 ID 不是物品 ID 或 PNG 路径。其他来源前缀暂未接入，现有 ItemBridge 物品源数量不代表图片来源数量。

例如，settings 的 `Name` 或 canvas 的 `Text` 可以写：

```yaml
Text: "<image:IA:my_pack:star> <l10n:menu.title>"
```

需要对应插件已启用、图片已注册，且玩家加载了包含字体和图片的资源包。图片使用来源插件定义的高度和基线；菜单的 FontSize 不缩放图片。大图片应为它预留足够的行高，小图标更适合插入按钮文字。

DialogMenu 在换行、居中、裁切前解析图片与翻译；图片按一个完整字形处理，不把标签原文当作文字宽度。CE 字宽从实际 PNG 的透明边界和字体高度测量，支持已注册的图集单元及图片引用。IA 使用 FontImageWrapper 的图片字符串与宽度 API。

图片缺失、插件未安装或 API 不兼容时显示 `[image:图片ID]`，并报告原因。`/dmenu reload` 会清除图片缓存，CE/IA 的数据重载事件也会刷新已打开菜单。更换图片资源后仍需通过来源插件构建、发送新资源包。

本测试版不解析任意物品为图片，也不支持 CE 图片标签的末尾 format 参数或独立 `<shift>`。原有 `Sprite` 和 `Font/Glyph/Width/Rows/Advance` 继续可用。

## 可用字段与兼容规则

支持 settings 的标题、导航名称、控件名/说明/选项，以及原生物品页面文字；canvas 的标题、正文和按钮文字也支持。图片标签写在文本字段，不能替代 `Icon`、`Sprite`、`Display.Material` 的原有配置格式。canvas 的 `message:` 动作也可使用标签；命令和权限字段保持原语义。

只有包含 `<image:...>`、`<i18n:...>` 或 `<l10n:...>` 的文本才启用这套解析。其他既有文本仍为普通文字，不会突然把 `<red>` 或 `&a` 当作颜色。启用标签的文本支持 MiniMessage 颜色、文字装饰和 reset；不执行文本中的点击命令或悬浮事件。

测试示例见 [独立多语言菜单](examples/text-tags.yml)。将它复制到 `plugins/DialogMenu/menus/text-tags.yml`，执行 `/dmenu reload`、`/dmenu open text-tags`。默认示例只依赖随 JAR 提供的翻译，图片行需要替换成你已有的图片 ID。
