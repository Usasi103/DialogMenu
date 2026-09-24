# 独立设置模板

`/dmenu open demo-settings` 打开设置演示；`/dmenu open settings` 继续打开服务器自己维护的真实玩家设置。新装导出 `demo-settings`、`demo-dialogue`、`demo-boss`、`demo-quests` 四个演示，默认进入设置演示；升级不会覆盖已有菜单或全局默认入口。

演示沿用设置菜单的分类、控件、字体和排版，顶层使用 `Type: settings-demo`。所有开关、粒子密度、语言、主题和缩放都能点击体验，不要求安装 Ambience、LootBeam、PickupNotifier 或 PlaceholderAPI。玩家信息中的资产、等级和收集数是固定样例，玩家名、延迟和世界仍来自当前玩家。

选择按本次打开的菜单隔离，翻页、搜索、刷新和展开下拉框保留选择；重新打开或重载配置时重置。语言、主题与缩放仅改变演示本身，不写入玩家真实偏好，也不执行绑定中的业务命令。普通按钮仅允许关闭、刷新、搜索与页内跳转。需要连接真实业务时，使用 `Type: settings` 并配置实际插件与动作。

已有服务器添加此模板时，将源码或 JAR 中的 `catalog/menus/demo-settings.yml` 复制为 `plugins/DialogMenu/menus/demo-settings.yml`，更新插件后执行 `/dmenu check` 与 `/dmenu reload`。保留已有的 `settings.yml` 和 `DefaultMenu: settings` 即可继续使用真实设置入口。

维护默认模板时，先修改 `src/main/resources/catalog/menus/settings.yml` 的布局，再运行 `python -B tools/generate_settings_demo.py` 生成同布局演示；服务器自己的菜单不会被该脚本读写。
