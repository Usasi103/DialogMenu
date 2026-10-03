# 公开版发布状态

仓库：[Usasi103/DialogMenu](https://github.com/Usasi103/DialogMenu)，分支 `main`。

当前正式版本：[v0.2.3](https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.3)。一个 DialogMenu JAR 同时提供 Dialog 菜单和全屏诊断方案；每份菜单用 MenuType 选择显示方式。全屏当前为固定演示布局，使用前配置 fullscreen.yml；无需额外全屏插件或 TorakaHud。

附件为实际构建 JAR、与内置字节相同的 Dialog / 全屏资源包，以及 SHA256SUMS.txt。标签对应累计源码，历史 Releases 和附件保持原样。升级旧菜单需在文件首项补 MenuType: dialog，Version 3 全局配置不加。

验证和限制见[发布记录](../../RELEASE-VALIDATION.md)，完整历史见[更新日志](../../CHANGELOG.md)，使用方式见[全屏指南](../guides/FULLSCREEN.md)。

公开版不包含私有 HUD 实现、衣帽间或私人贴图。Dialog 原创素材源位于 design/original-ui/，全屏包从官方原版 26.3 客户端和公开诊断布局生成；第三方代码引用见 THIRD_PARTY_NOTICES.md。
