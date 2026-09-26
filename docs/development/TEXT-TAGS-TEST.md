# 0.1.22-text.1-SNAPSHOT 验证记录

日期：2026-09-26。测试版基于 `f3197ceee45022d080e96a760e6bea861ebca0a9`，包含 0.1.21 功能及资源自动安装目录联接路径修复。`BundledResourcePack.kt` 和对应回归测试保持该修复提交的内容。

- 使用项目规定的 `format_selfdev.py` 与 `build_selfdev.py`，JDK 25，Paper 1.21.11 API 编译、Java 21 字节码。106 项测试全部通过，包括 Windows 目录联接、旧菜单布局、按钮点击区域、弹层遮挡、翻译回退/参数/循环、图片尺寸与示例菜单。
- 在部署目录之外的 Paper 26.2 build 123 + CraftEngine 26.9.1 隔离实例启动最终 JAR；验证真实 CE `default:emojis` 图集第 0 行第 1 列、PNG 字宽、显式与默认 CE 标签相同。CE 发行包使用重定位的 Adventure 类，接入通过 MiniMessage 字体/字形字符串转换，避免跨库强制类型转换。
- 运行 `/dmenu check`、`/dmenu reload`；重载前后探针各通过一次。英语、简体中文、日语查看者分别验证 l10n 选择与默认回退；i18n 固定使用 DialogMenu 默认语言；翻译完全来自 DialogMenu 本地文件。
- Wiki 离线页面重新生成，19 个页面、75 个本地链接通过构建检查；新增完整多语言示例通过菜单解析器并实际生成画布组件。

- 2026-09-26 用户在 Minecraft 26.2 客户端提供实机截图：简体中文 l10n、默认中文 i18n、玩家名参数、显式 CE 与默认图片标签均显示正常。此截图不覆盖切换客户端语言后的显示和 IA 图片。

限制：本机没有启用 ItemsAdder，本次 IA 接入依据公开 FontImageWrapper API 实现，尚未在真实 IA 插件验证图片字符串、字体宽度及重载事件；需要持有对应版本 IA 的服务器试用。大图片的基线及行高由资源定义和菜单布局共同决定。

本次累计同步沿用已测试的 JAR，版本为 `0.1.22-text.1-SNAPSHOT`，以 GitHub 预发布提供。源码、资源包与 JAR 附件仅含可公开分发内容。
