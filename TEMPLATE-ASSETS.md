# 模板素材

## 字体宽度数据

普通文字使用资源包自带的 Unihex 字体，避免 Unicode 符号宽度和服务器计算不一致。源码已附生成结果，正常安装无需 Python。
开发时修改 label 字体或运行皮肤生成工具后，再执行 `python -B tools/build_label_metrics.py`（需要 Pillow），同步生成字宽数据与半像素间距。之后重新构建插件，并同步资源包。


紫色边框、分隔线和抽象徽记由项目构建器绘制，属于基础 UI 图元。
木色本机皮肤从用户已下载的 UI_Sprite.png 编译。
原作者：TararebaGani；来源：https://tararebagani.itch.io/pixel-art-uiset

截至 2026-09-24，原页面允许商业项目使用及修改，无须署名；
同时禁止素材再分发（包括修改后的素材）。原图集不纳入源码仓库或源码附件。
对外发布资源包使用构建器的基础几何木色皮肤；用户可从作者页面下载后在本地编译。
本机定制贴图仅用于本次用户已有素材的安装与私有验证，不作为素材库再发布。

字符字体、背景场景及角色立绘不包含在该图集中。示例没有截图中怪物的真实立绘或模型。

首领奖励字体 `dialogmenu_dialogue:rewards` 直接引用客户端的 `minecraft:item/amethyst_shard.png`、`nether_star.png`、`diamond_chestplate.png`。资源包只包含字体 JSON，不包含这些原版图片的副本。
