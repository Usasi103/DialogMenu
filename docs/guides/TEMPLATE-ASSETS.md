# 模板素材

## 字体宽度数据

普通文字通过菜单自己的 Unihex 定义引用客户端的 `minecraft:font/unifont.zip`，已核验 Minecraft 1.21.11 与 26.2 使用同一份字库。保留原有 `size_overrides`，避免 Unicode 符号宽度和服务器计算不一致。发布资源不再携带重复字库。源码已附生成结果，正常安装无需 Python。
开发时修改 label 字体或运行皮肤生成工具后，再执行 `python -B tools/build_label_metrics.py`（需要 Pillow），同步生成字宽数据与半像素间距。之后重新构建插件，并同步资源包。

`design/minecraft-font/unifont.zip` 仍是字宽和位图字体的构建输入，生成器校验其 SHA-256 与已验证的客户端字库一致；升级目标客户端时需重新核对官方字库和字宽。生成器只移除与官方原件一致的旧发布 ZIP，发现自定义修改会报错。保留 `LICENSE-Unifont.txt`，其他位图字体仍使用该字库生成。


设置菜单、首领/对话模板和任务控件统一使用原创灰石美术。正式源文件为 `design/original-ui/original-ui.bbmodel`，使用 Blockbench 制作；导出的 PNG、来源清单和制作说明在同一目录。设置图标各为独立的 16×16 图片，按原尺寸显示；面板和控件属于 GUI，保持已有布局尺寸。

菜单默认使用 `Skin: stone`，兼容旧名称 `amethyst` / `parchment` 及其字形编号。可变宽按钮只拼接原创边缘和中段，点击范围随原布局保留。旧外部图集导入参数已移除，重新生成也不会引入第三方服务器素材。

在 Blockbench 修改并导出 PNG 后，使用 JDK 编译 `tools/BuildSkin.java`、`BuildSwitchSkin.java`、`BuildTemplateSkin.java`、`BuildQuestSkin.java` 到工程外的临时目录，再分别以工程路径为唯一参数运行四个类；最后执行 `python -B tools/build_label_metrics.py` 并重新构建插件。生成器只切分字形、测量宽度，不重绘美术。原有受限源图和展示旧图的截图已从当前源码移除，历史版本不改写。

首领奖励字体 `dialogmenu_dialogue:rewards` 直接引用客户端的 `minecraft:item/amethyst_shard.png`、`nether_star.png`、`diamond_chestplate.png`。资源包只包含字体 JSON，不包含这些原版图片的副本。
