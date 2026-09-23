# PlayerSettings → DialogMenu

从 0.1.14 起，插件名、JAR、源码工程、主指令和 GitHub 仓库统一为 DialogMenu。
插件支持多页 Dialog 菜单；附带的玩家设置菜单只是其中一个用途。

## 安装与迁移

1. 停服，备份旧 JAR 和配置到服务器目录外。
2. 移走 PlayerSettings 的 JAR，放入 DialogMenu-0.1.14.jar，不要同时加载两者。
3. 启动服务器。新配置目录没有 config.yml/menu.yml 时，插件自动复制 plugins/PlayerSettings 中的配置到 plugins/DialogMenu；保留旧目录供核对。
4. 运行 /dialogmenu check，再用 /dialogmenu open 页面ID 检查自定义页面。

也可以在停服时自行把 plugins/PlayerSettings 更名为 plugins/DialogMenu。
若新目录已有 config.yml 或 menu.yml，以新目录为准，不重复导入旧配置。
迁移遇到同名而内容不同的文件会停止启用并报告具体路径，请手动合并；不会用默认菜单掩盖冲突。
首次启动时框架刚生成的默认 lang 文件可由旧自定义语言文件替换。
迁移中断会保留 .playersettings-import 标记，下次启动校验已有内容后继续复制。

## 兼容范围

- 主指令：/dialogmenu；简写：/dmenu。
- 旧 /playersettings、/settings、/player-settings 及其 open/check/reload 子命令继续可用。
- 权限仍为 playersettings.use、playersettings.admin，原权限配置无需调整。
- 玩家语言/主题仍读写 playersettings:menu_language、playersettings:menu_theme。
- 资源包字体命名空间 toraka_settings、点击事件和着色器保持不变；无需因改名重新生成资源。
- 内置 ItemBridge 和五种可选物品源沿用 0.1.13 的实现。
- 自定义菜单标题、按钮文字不会因为插件更名被强行改写。

旧目录内已有的配置注释可能仍提及 PlayerSettings；参考随新包提供的配置说明。
GitHub 原私有仓库更名为 Usasi103/DialogMenu，保留提交、标签和历史 Releases。
