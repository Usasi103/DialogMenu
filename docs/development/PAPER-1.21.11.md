# Paper 1.21.11 兼容与资源安装验证

验证日期：2026-09-25。开发构建：`0.1.21-compat.1-SNAPSHOT`。

## 问题与修复

DialogMenu 0.1.20 在 Paper 1.21.11 上能启用，配置检查和重载也通过，但玩家打开设置、任务、首领或对话菜单时触发：

```text
NoSuchMethodError: ClickEvent.custom(net.kyori.adventure.key.Key)
```

Paper 1.21.11 的 Adventure 4.26.1 没有该单参数工厂。新的 `DialogClicks` 使用 4.26.1 与 Adventure 5 共用的双参数工厂，传入空 NBT compound，保留自定义点击 ID；编译基线同时降为 Paper 1.21.11。构建使用 JDK 25，插件输出 Java 21 字节码。

原资源包只声明格式 88。核对 Mojang 1.21.11 客户端 GUI 着色器接口与 Unicode 字库后，资源包范围改为 75–88；无需替换原有 GUI 着色器或字体尺寸。

## 验证结果

- 完整 Gradle 构建、框架原生依赖检查、源码格式检查通过，91 项 JUnit 测试无失败或跳过。
- 最终 JAR SHA-256：`ea3d44c4dc503f4513a0ed778c5eacac882c4fee9e28c2564e42d7eb20763f6f`。
- JAR 内嵌 ZIP 包含 439 个文件，与资源源目录逐字节一致；含许可证，未携带重复的原版 Unicode 字库 ZIP。
- Paper 1.21.11 build 132 + BetterHud 2.1.0-SNAPSHOT-448 + Java 25：协议玩家真实登录后收到四个演示菜单；点击设置菜单翻页收到更新后的 Dialog。服务端没有原来的接口异常，BetterHud 同会话正常发送 bossbar 数据。
- 测试组合包以原 BetterHud 包为基础，只追加菜单资源；原包的所有条目、覆盖层及 metadata 字节保持不变。
- 第二轮启用 URL 发送与 `RequireLoaded: true`，实际下载 17,872,588 字节组合包，SHA-1 与服务端指定值相符；协议探针发送匹配 UUID 的模拟加载回执后，四菜单与翻页点击再次通过。
- RTX 3070 Ti / OpenGL 3.3：DialogMenu GUI 和原 BetterHud 文本着色器共七种组合实际编译、链接通过。1.21.11 与 26.2 的 `minecraft/font/unifont.zip` SHA-256 相同。
- Paper 26.2 build 123 + CraftEngine 26.9.1：新安装 Auto 实际选中 CE，写入 439 个源文件；重启与重载后文件内容和修改时间均不变。
- 手动修改 CE 中的一份菜单字体后，再次启动保留修改并报告冲突；实际 CE 合包成功，438 个导入资源全部逐字节一致，生成 ZIP 没有重复条目，配套 GUI 着色器保留。
- README 三张截图的原图直链均返回 HTTP 200、image/jpeg，与 Git 跟踪的原文件一致。

## 验证边界

协议客户端能够验证登录、菜单数据和点击回传，但不等于完整 Minecraft GUI 渲染截图验收。GPU 检查证明着色器可编译、链接，尚未完成 1.21.11 的完整画面人工复核。

本次 BetterHud 构建自身需要 Java 25，所以服务端测试使用 Java 25；未用 Java 21 运行该 BetterHud 构建。原 BetterHud 配置中依赖外部任务变量的几项 HUD，在只装两个插件时仍会提示缺少变量，与菜单兼容错误无关。

CraftEngine 已做真实服务端测试。ItemsAdder、Nexo、Oraxen 的导入目录已核对官方文档或源码，并覆盖文件安装测试，尚未逐一做真实插件联机测试。资源自动安装不包含调用提供方全服重载、资源托管或替玩家加载资源；后续步骤由控制台提示。

## 官方资料

- [Paper 下载服务](https://docs.papermc.io/misc/downloads-service/)
- [Paper 1.21.11 构建信息](https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds)
- [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [BetterHud 1.21.11 兼容讨论](https://github.com/toxicity188/BetterHud/pull/427)
