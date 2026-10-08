# 原版 CJK 字体与资源合并

DialogMenu 0.2.5 的统一资源包复用原版 26.3 客户端 Unihex。普通标签继续采用原始 Unicode 字宽；按钮和指定字号的 CJK 保留原先菜单宽度、基线及换行行为。29,326 个字形的覆盖信息集中在 `native-cjk-ranges.txt`，标题度量表只保存 171 个小型位图字形。

`NativeMenuFont` 在已测量的组件中替换 CJK 字体，并补足实际 advance 与菜单 advance 的差值。字号 6–24、普通/按钮基线和斜体使用 76 个负 X 坐标带；布局字符没有图像。公开 `design/font/native_cjk.vsh` 只处理正交 GUI 中 200–275 坐标带，沿用原版 `text.vsh` 和全屏着色器入口。普通 GUI 文字、透视世界文字和其他坐标保持原有处理。

`native_bounds.png` 是 1×1、alpha=1 的不可见边界字形，供原版 GUI 的 CPU 排序保留屏幕内的文字范围。下划线和删除线在原有逻辑宽度和基线上单独绘制，继承颜色与点击事件。

## 生成与构建

在工程根目录执行：

```powershell
java tools/BuildSkin.java .
py -3 -B tools/build_title_font.py
py -3 -B tools/build_label_metrics.py
.\gradlew.bat --no-daemon formatSources build
```

字体生成器读取公开工程的设计字库，校验 SHA-256，写入小型 bitmap、Unihex 引用、位置字体与度量数据；旧 `button_cjk_*.png` 会被清理。24 份字号字体 JSON 使用同一 CJK 引用，不再复制中文字符表。全屏构建仅从官方客户端获取原版 shader，再追加公开的全屏和字体函数。JAR 只内置一个统一 ZIP。

## 与 BetterHud 共用

保留 BetterHud 的生成包和发送方式；现有资源安装器继续检测核心 shader 冲突，不覆盖已有不同内容。CJK 字号和按钮基线需要保留 `dm_native_cjk_vertex()`。只合字体 JSON 而丢掉该入口，会显示原版尺寸和基线。

合包时，可以向 **已生成的** BetterHud `text.vsh` 追加此函数，并在原入口之后调用；公开工具生成独立候选文件，保留原入口的全部逻辑：

```powershell
py -3 -B tools/compose_native_cjk.py --input "<生成的 text.vsh>" --output "<工程外审核目录>/text.vsh"
```

工具不会复制 BetterHud 代码到 DialogMenu 源码或内置 ZIP。保留发送方的 fragment shader 及其原版灰度字体支持，合并 GUI shader，并重新构建、发送实际组合包。全屏的 `dm_local_vertex` / fragment 入口另需保留；添加字体函数不代表完成全屏合并。不同 BetterHud 构建的模板和变体须在最终组合包上验证。

## 验证入口

`tools/validation/NativeFontCodecProbe.java` 用原版 26.3 `GlyphProviderDefinition` codec 解析最终 ZIP，检查 Unihex、字体引用和 bitmap 来源。参数为统一 ZIP 与官方客户端 JAR；运行 classpath 需要匹配客户端的依赖库。

`NativeFontShaderProbe.java` 与 `FullscreenShaderProbe.java` 一起编译，使用匹配客户端的 LWJGL classpath。前者检查 GPU 输出顶点的字号、基线、斜体、半像素偏移和非目标文字隔离；后者检查全部文字/背景变体、GUI 接口和原有全屏光标。验证记录见仓库根 `RELEASE-VALIDATION.md`。

最终包仍需真人原版客户端复核字体观感；实际 BetterHud 组合包的客户端显示验收单独记录。

## 图标动画的合包要求

原有 `compose_native_cjk.py` 仅组合中文字号；它不会加入动画。动画还需组合 `design/animation/icon.vsh` / `icon.fsh`，在顶点原入口之后调用 `dm_animation_vertex()`，在片段原入口之前由 `dm_animation_fragment()` 拦截专用图标。26.3 的动画 varying 使用 location 8，发送方若占用该位置必须统一重新分配两端。第三方整包应同时保留其原入口与 DialogMenu 全屏入口，并实际验证所有 shader 变体。当前只验证随插件发布的公开原版组合包，未验收实际 BetterHud 组合包。
