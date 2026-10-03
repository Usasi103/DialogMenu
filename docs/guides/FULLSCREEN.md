# 同一插件中的 Dialog 与全屏菜单

DialogMenu 0.2.3 的一个 JAR 同时提供两种显示方式，无需另装全屏插件或客户端 Mod。当前目标是 Paper / 原版客户端 26.3，服务器使用 Java 25。

每份菜单的第一个配置项选择 `MenuType: dialog` 或 `MenuType: fullscreen`。注释可放在它前面，同一文件不能混用两种类型的字段或按钮。Dialog 下的 `Type: settings` / `Type: canvas` 继续表示内容结构。

## 启用全屏演示

首次安装会生成五个示例菜单。旧安装可自行新增 `plugins/DialogMenu/menus/demo-fullscreen.yml`：

```yaml
MenuType: fullscreen
Preset: diagnostic
```

当前全屏只提供固定诊断布局，用于四角覆盖、中央点击、本地光标及返回 Dialog 的测试；尚不支持在 YAML 中自由编辑背景和按钮，也不会把已有 Dialog 布局自动转换为全屏。不能在此文件添加 Dialog 的 `Pages`、`Elements`、`Icons` 或 `Version`。

在 `plugins/DialogMenu/fullscreen.yml` 配置资源包服务：

```yaml
pack-bind: 0.0.0.0
pack-port: 22335
pack-url: "https://packs.example.com/fullscreen"
```

`pack-url` 是玩家能访问的基础地址。示例域名需要替换，并将该 URL 下的请求反向代理到本机的 22335 端口；插件自动追加包哈希和 `.zip`。也可以使用实际可访问的 `http://服务器地址:22335`。默认 URL 留空，必须配置后才能打开本地光标演示。服务器只在首次打开时启动 HTTP 服务。

执行 `/dmenu check`、`/dmenu reload`，再 `/dmenu open demo-fullscreen`。玩家接受并加载资源包后才打开界面；拒绝、下载失败或超时不会打开。只修改配置但未成功重载不会改变正在运行的设置。

| 入口 | 用途 |
| --- | --- |
| `/dmenu open demo-fullscreen` | 通过统一菜单目录打开，需要 `playersettings.use` |
| `/dfullscreen` | 直接打开诊断界面，需要 `dialogmenu.fullscreen.test`（默认 OP） |
| `/dfullscreen legacy` | 对比旧的网络光标模式 |
| `/dfullscreen close` | 关闭或取消待加载请求 |
| `/dfullscreen status` | 查看会话、模拟实体和更新次数，控制台可用 |

第一人称下移动鼠标控制黄色十字，左右键点击按钮，Shift 或 F 退出。单次演示最长五分钟。返回按钮打开默认 Dialog；如果默认入口是全屏，则选取目录中的第一个 Dialog。

## 菜单互相跳转

Dialog 按钮可使用 `Actions: ["open: demo-fullscreen"]`。不同菜单之间通过关闭旧界面、恢复玩家状态，再打开目标界面完成切换；切换到 Dialog 时会取消未完成的全屏资源包请求，迟到的加载回执不会重新弹出全屏。

成功重载关闭全屏会话并释放旧资源服务，下次打开使用新配置。任何菜单类型、引用或配置错误都会取消整次重载，保留旧状态和原文件。

## 分辨率、资源与限制

- 背景原图为 1920×1080，按当前窗口宽高完整铺满；不同宽高比会拉伸。服务器点击坐标与客户端显示均采用 320×180 逻辑坐标，四角按钮贴到窗口边缘。
- 光标和悬停高亮由资源包着色器在本地绘制，鼠标移动不等待服务器回包。点击结果仍受网络延迟和服务器 tick 影响，不能称为所有操作“零延迟”。
- 本地模式每位玩家只发送两个模拟实体，不向世界添加实体；无会话时不运行会话定时器。完整窗口背景为静态资源，没有逐帧传图。
- 40 张字体图集约占 10 MiB 客户端原始 RGBA 存储，驱动实际分配可能不同；这是客户端纹理估算，不是服务器内存测量。服务器仍维护每玩家输入状态、定时确认与点击逻辑，因此普通静态 Dialog 通常更轻。
- 全屏包替换原版 `text` 和 `position_color` 核心着色器。其他文字/HUD 核心着色器包需要专门合并；原有 Dialog 的 BetterHud 支持不代表全屏组合已验证。使用原版第一人称、关闭第三方光影进行测试。
- 当前验证范围与未验证组合见 [发布验证](../../RELEASE-VALIDATION.md)。

## 从源码构建

主工程通过 paperweight 对接 Paper `26.3.build.142-beta`。全屏资源生成需要 Python 3、Pillow、Windows 微软雅黑字体和官方 26.3 客户端 JAR；不从私有 HUD 资源包生成。

```powershell
./gradlew.bat --no-daemon formatSources build -PfullscreenClientJar=C:/path/to/26.3/client.jar
```

最终生产产物只有 `dist/DialogMenu-0.2.3.jar`，内含 Dialog 与全屏两份资源包。`fullscreenProbeJar` 是开发者在隔离服务器中运行的测试探针，不是安装所需的第二个插件。
