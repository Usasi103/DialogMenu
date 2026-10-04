# 模板素材

## 字体宽度数据

普通 Unicode 标签、按钮 CJK 与 6–24 号 CJK 文字直接引用客户端的 `minecraft:font/unifont.zip`。菜单资源只保留小型 Latin/符号位图；不携带中文字图集或字库副本。服务器保留原有测量宽度、换行和点击布局，通过专用间距字符及公开 `text.vsh` 入口恢复字号、按钮基线与斜体。实现、生成和合包方式见 [原版 CJK 字体](NATIVE-FONT.md)。源码已附生成结果，正常安装无需 Python。
开发时修改 label 字体或运行皮肤生成工具后，依次执行 `python -B tools/build_title_font.py`、`python -B tools/build_label_metrics.py`（需要 Pillow），同步生成标题/普通字宽、CJK 覆盖范围与半像素间距。之后重新构建插件，并同步资源包。

`design/minecraft-font/unifont.zip` 仍是字宽、覆盖范围和小型符号字体的构建输入，生成器校验其 SHA-256；升级目标客户端时需重新核对官方字库和字宽。保留 `LICENSE-Unifont.txt`，小型符号位图仍使用该字库生成。`build_title_font.py` 清理已被替换的 `button_cjk_*.png` 生成文件。


设置菜单、首领/对话模板和任务控件统一使用原创灰石美术。正式源文件为 `design/original-ui/original-ui.bbmodel`，使用 Blockbench 制作；导出的 PNG、来源清单和制作说明在同一目录。设置图标各为独立的 16×16 图片，按原尺寸显示；面板和控件属于 GUI，保持已有布局尺寸。

菜单默认使用 `Skin: stone`，兼容旧名称 `amethyst` / `parchment` 及其字形编号。可变宽按钮只拼接原创边缘和中段，点击范围随原布局保留。旧外部图集导入参数已移除，重新生成也不会引入第三方服务器素材。

在 Blockbench 修改并导出 PNG 后，使用 JDK 编译 `tools/BuildSkin.java`、`BuildSwitchSkin.java`、`BuildTemplateSkin.java`、`BuildQuestSkin.java` 到工程外的临时目录，再分别以工程路径为唯一参数运行四个类；最后依次执行 `python -B tools/build_title_font.py`、`python -B tools/build_label_metrics.py` 并重新构建插件。生成器只切分字形、测量宽度，不重绘美术。原有受限源图和展示旧图的截图已从当前源码移除，历史版本不改写。

首领奖励字体 `dialogmenu_dialogue:rewards` 直接引用客户端的 `minecraft:item/amethyst_shard.png`、`nether_star.png`、`diamond_chestplate.png`。资源包只包含字体 JSON，不包含这些原版图片的副本。
