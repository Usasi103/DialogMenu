> 0.1.16 新格式以 [MENU-CONFIG.md](../guides/MENU-CONFIG.md) 为准：每个菜单文件含 Pages。本文保留 Version 2 的控件字段参考，原单页内容放入新文件 Pages.<页面ID>。

# 玩家菜单配置（0.1.9）

每个页面一个文件，直接改名字、选项和点击动作即可，不用配置像素坐标。

## 从哪里改

文件都在 `plugins/DialogMenu/`：

- `config.yml`：菜单标题、默认语言/主题、左侧页面顺序、返回主菜单动作。
- `menus/appearance.yml`：菜单语言、暗色/亮色下拉框。
- `menus/particles.yml`：粒子开关、分类和密度滑条。
- `menus/sound.yml`、`notices.yml`、`loot.yml`：音效、拾取提示、掉落光柱。
- `menus/profile.yml`、`help.yml`：玩家信息和帮助。

保存为 UTF-8，使用空格缩进。执行 `/dialogmenu check` 检查，再执行 `/dialogmenu reload` 生效。权限 `playersettings.admin`（默认 OP）；控制台不加 `/`。`/settings check`、`/settings reload` 同样可用。

检查不应用修改。重载会把主配置和启用的页面一起校验，全部正确才刷新菜单；出错则保留之前的菜单和你写的文件。重启不会覆盖已有文件。重载会收起下拉框，删除当前页面后回到默认页。

## 改文字和顺序

Layout 按从上到下的顺序列出 Icons 里的名称。交换两个名字即可调整位置；从 Layout 移除后暂时不显示，Icons 里的定义可以保留。

```yaml
Title: "界面与语言"
Layout: [语言, 主题, 回城]
Icons:
  语言:
    Type: dropdown
    Name: "菜单语言"
    Bind: language
    Options:
      zh_cn: "简体中文"
      en_us: "English"
  主题:
    Type: dropdown
    Name: "界面主题"
    Bind: theme
    Options:
      dark: "暗色"
      light: "亮色"
  回城:
    Type: button
    Name: "返回主城"
    Actions:
      - "close"
      - "command: spawn"
```

Name 省略时使用 Icons 下的名称；Type 省略时是 button。`Description: "说明文字"` 可添加说明，也支持最多三行的列表。文字不写换行或颜色代码，超长会裁切。支持 `{player}`、`{uuid}`、`{ping}`、`{world}` 和完整 `%PAPI变量%`。

默认配置保留双语，文字写在同一处：

```yaml
Name: {zh_cn: "界面主题", en_us: "Menu theme"}
```

也可改成 `Name: "界面主题"`，两种语言都显示同一句。无需填写 `$appearance.theme`；简化格式中的 `$` 也按普通文字显示。Title、Name、Description 和选项文字都支持这种双语写法。

## 开关、滑条和下拉框

Bind 自动读取状态并执行设置，玩家已有偏好继续保留：

| Bind | Type | 用途 |
|---|---|---|
| language | dropdown / slider | zh_cn、en_us |
| theme | dropdown / slider | dark、light |
| particle-density | slider / dropdown | off、low、medium、high |
| particles | toggle | 环境粒子总开关 |
| sounds | toggle | 环境音效 |
| leaves / firefly / biome | toggle | 落叶、萤火虫、群系粒子 |
| pickup | toggle | 拾取提示 |
| loot-beams / loot-sounds | toggle | 掉落光柱、光柱音效 |

例如添加密度控件，同时把“密度”加入该页 Layout：

```yaml
  密度:
    Type: slider
    Name: "粒子密度"
    Bind: particle-density
    Options:
      "off": "关闭"
      low: "低"
      medium: "中"
      high: "高"
```

Options 顺序决定档位/选项顺序，支持 2–8 项；内置设置只能使用表中的对应值。`off`、`on` 等 YAML 布尔关键字用作键或文字时加引号。滑条通过点击和箭头切换，不支持连续拖动。下拉选中后立即生效并收起，每次只展开一个。

## 点击动作

直接写在按钮的 Actions 里，按顺序执行：

```yaml
  奖励:
    Name: "领取奖励"
    Permission: "example.reward"
    Actions:
      - "console: give {player} minecraft:stone 1"
      - "command: rewards info"
```

- `command: 指令`：以点击玩家身份执行，保留原权限，不写开头的 `/`。
- `console: 指令`：以控制台身份执行；建议用 Permission 限制玩家，不会临时给予 OP。
- `close`：关闭界面，放在第一项；适用于传送或打开其他菜单。
- `page: appearance`：跳转到指定页面。
- `refresh` / `search`：刷新当前页面 / 打开搜索。

页面跳转、刷新、搜索只能放在最后。指令失败后停止后续动作，已经执行的指令不会回滚。未设置 close 时，指令执行后自动刷新菜单。命令仅支持 `{player}`、`{uuid}`，不把 PAPI 或搜索输入拼进指令。

Permission 是额外玩家权限；RequiresPlugin 可限制必须启用某个插件。Bind 自带联动插件检查，不能替换其必要依赖。用 Bind 时不用再写 State 或 Actions。

## 加页面与布局

复制一个 menus 文件，例如保存为 `menus/travel.yml`，再在 config.yml 的 Pages 列表加入 `travel`。文件名用小写字母、数字、短横线或下划线，以字母开头。Pages 顺序就是导航顺序，最多启用八页。

Title 是页面标题和导航名称；Icon、Keywords 可省略。Icon 沿用现有文件里的图标名；Keywords 用来搜索。移除页面时同时调整 DefaultPage 或指向它的 page 动作。

控件按 Layout 自动排列，上方面板放不下时进入下方面板。Type: heading 主动开始下方面板，Name 为分组标题。Type: text 显示说明文字。heading 只使用 Type、Name；text 可另加 Description。

两个面板都放不下会提示错误，可以减少说明、调整顺序或拆页。下拉列表也必须能完整展开，展开时临时遮住覆盖范围内的控件和文字。固定画布与字体、白框隐藏配套，简化配置不用修改坐标或资源包；新增贴图和改变整体尺寸仍需要开发。

## 自定义联动（按需使用）

已有 Bind 不够用时，用 State 直接读取 PAPI，把动作写进选项：

```yaml
  模式:
    Type: dropdown
    Name: "难度"
    State: "%example_mode%"
    Options:
      easy:
        Name: "简单"
        Actions: ["command: mode easy"]
      hard:
        Name: "困难"
        Actions: ["command: mode hard"]
```

此例需要你自己的插件提供变量和指令。自定义 toggle 使用 State 和控件本身的 Actions。未知状态显示“未接入”，接入插件负责保存它自己的设置。

## 旧配置兼容

存在 config.yml 时使用简化格式，仅加载 Pages 指定的页面，旧 menu.yml 和 languages 不参与。没有 config.yml 但存在 menu.yml 时继续使用 0.1.8 格式，不自动覆盖或转换已有内容。旧格式说明见源码仓库 LEGACY-CONFIG.md。

Language、Theme 只影响没有有效已存偏好的玩家，不覆盖玩家选择。首次缺少配置时才导出新格式；主配置存在而页面缺失时会报错，不会悄悄恢复被删除的页面。

## 0.1.12 — 物品源

支持 `Display.Material: "source:CE:命名空间:物品ID"`（`CRAFTENGINE` 同义）和 `minecraft:diamond` 原版物品。CE 为软依赖，通过其公开 API 构造真实物品，保留模型和物品提示。中文配置与边界见 [ITEM-SOURCES.md](../guides/ITEM-SOURCES.md)，完整样例在 `src/main/resources/examples/items.yml`，插件会导出到 `examples/items.yml`。

带 Material 的页面自动使用原生物品布局，名称/下方按钮处理操作；既有字体画布页面继续保留。原生页面不使用画布皮肤、主题和隐藏焦点功能，不支持自由坐标。使用 `/dialogmenu open <页面ID>` 打开指定已启用页面，`check` / `reload` 同时验证物品源；无有效物品时停用动作。


0.1.13 使用内置 ItemBridge 1.0.32，只启用 Oraxen、ItemsAdder、SX-Item、NeigeItems、CraftEngine 五种自定义物品源，并保留原版物品。已有 CE 配置无需修改。详见 [物品源配置](../guides/ITEM-SOURCES.md)。
