# DialogMenu 中文 Wiki

用 YAML 制作 Minecraft Dialog 菜单：玩家设置、NPC 对话、首领介绍、确认界面和物品预览。

本文基础章节按 **0.1.17** 编写；图标、物品源、资源包及字体命名空间已按当前 **0.1.21-SNAPSHOT** 核对补充，其他章节不代表已覆盖全部后续功能。新版菜单格式从 **0.1.16** 开始使用。全局 `config.yml` 的版本是 `3`，单个菜单文件的版本是 `1`。这两个数字表示配置格式，不是插件版本。

**先记住三层关系：`config.yml` 选默认菜单 → `menus/文件名.yml` 定义一个菜单 → `Pages` 定义它的子页面。**

## 开始阅读

| 你想做什么 | 阅读入口 |
| --- | --- |
| 第一次写菜单，先跑通一个例子 | [快速开始](quick-start.md) |
| 弄清 `menus` 里的文件和字段 | [目录与菜单结构](structure.md) |
| 修改现在的玩家设置 | [settings 菜单与页面](settings.md)、[控件参考](controls.md) |
| 做 NPC 对话或首领界面 | [canvas 画布](canvas.md)、[画布元素](elements.md) |
| 让按钮执行命令、翻页 | [动作参考](actions.md) |
| 选择难度、显示条件文字 | [变量、条件与占位符](variables.md) |
| 展示 CraftEngine / 其他插件物品 | [物品页面与物品源](items.md) |
| 更换图标、使用 IA / Nexo / Oraxen、完全不用 CE | [图标与材质](icons.md)、[资源包](resource-pack.md) |
| 排查加载、方块字、布局错误 | [资源包](resource-pack.md)、[常见问题](troubleshooting.md) |
| 直接复制完整文件 | [示例库](examples.md) |
| 从旧版或 TrMenu 理解新格式 | [迁移与 TrMenu 对照](migration.md) |

全部章节见 [目录](SUMMARY.md)，命令见 [命令与权限](commands.md)。

## 两类菜单怎么选

| 需求 | `Type: settings` | `Type: canvas` |
| --- | --- | --- |
| 布局 | `Layout` 排列控件，自动分配位置 | `Position: [X, Row]` 指定位置 |
| 常用内容 | 设置开关、下拉框、滑条、文字、按钮 | 对话、插图、按钮、自由排列的文字 |
| 页内配置 | `Layout` + `Icons` | `Elements` |
| 多页入口 | 左侧导航；`page:` 翻页 | 自己配置翻页按钮 |
| 外观 | 暗色 / 亮色玩家偏好 | `amethyst` / `parchment` 皮肤 |
| 语言 | 中文、英文行内翻译 | 普通文本或文本列表 |
| 临时枚举变量 | 不支持 `Variables` | 支持变量、条件显示和选中 |
| 真实物品 | 子页切换为 `Renderer: items` | 不支持；sprite 是图片 |

settings 子页的 `Renderer: canvas` 指设置界面的字体画布；它与整个菜单的 `Type: canvas` 是两个不同字段，不能混用其配置。

## 当前服务器从哪里改

当前部署目录是 `F:\OneDrive\toraka_online\test_server\plugins\DialogMenu\`。

| 文件 | 内容 |
| --- | --- |
| `config.yml` | 默认菜单和资源包要求 |
| `menus/demo-settings.yml` | 独立设置演示；模拟开关、滑条和下拉框，真实 settings 菜单继续保留 |
| `menus/demo-dialogue.yml` | NPC 对话演示，默认 main 页 |
| `menus/demo-boss.yml` | 首领演示，intro 介绍和 confirm 确认页 |
| `menus/demo-quests.yml` | 任务列表演示；任务与奖励的 Icon 配置见 [图标与材质](icons.md) |

修改后先执行 `/dmenu check`，通过后执行 `/dmenu reload`。这份 Wiki 和示例存放在源码目录，不会自动修改服务器菜单。

## 阅读和维护

- 双击同目录的 `index.html` 阅读离线网页，支持全文搜索、章节跳转和复制代码，无需启动服务器。
- Markdown 是文档源文件，`SUMMARY.md` 是 GitBook 风格的导航目录。
- 修改 Markdown 后，按 [文档维护说明](MAINTAINING.md) 重新生成网页。
- 文档组织参考了 [TrMenu V3 文档](https://hhhhhy.gitbook.io/trmenu-v3)，字段和行为依据 DialogMenu 自己的解析器与运行代码。TrMenu 的动作、脚本和布局语法不能直接移植。

## 核对依据

主要实现：`MenuCatalog.kt`、`SimpleMenuParser.kt`、`DialogTemplates.kt`、`MenuDialog.kt`、`TemplateDialog.kt`、`ItemMenuPage.kt`、`MenuResources.kt`。旧说明中的“一页一个文件”属于旧格式。

文档核对日期：2026-09-24。示例校验范围见 [维护说明](MAINTAINING.md)。
