# DialogMenu 0.2.5 本地审核候选 · 2026-10-04

公开工程独立版本 `0.2.5`，非 SNAPSHOT。本轮源码已累计同步，交付本地产物见 `dist/0.2.5-review/`；尚未创建版本标签或 Release，未部署公开版到 test_server。下方保留历史发布验证。

## 产物与体积

| 文件 | 实际字节数 | 对比 0.2.4 | SHA-256 |
| --- | ---: | --- | --- |
| DialogMenu-0.2.5.jar | 1,777,401 | 旧 JAR 2,957,871 字节，减少 1,180,470 字节 | `5b7d22a983f60c31ee3158a742559ecadeb9882ab394157cb58d604e00095071` |
| DialogMenu-resourcepack-0.2.5.zip | 282,009 | 旧内置 ZIP 2,938,669 字节，减少 2,656,660 字节（90.404%） | `9a902fec9b1d15f18acf24fe1aaf675bc360dc4f40911e004df60f1930f39ac7` |

可审阅目录为 `dist/0.2.5-review/`，含同份 JAR、统一 ZIP、`SHA256SUMS.txt`、构建报告、产物审计及探针日志。同源码完整 JAR 仅作 minimize 对照，不作为发布产物。

## 本次实际验证

- Paper 开发包 `26.3.build.142-beta`、JDK 25.0.4.1、主源码 Java 21 字节码。`formatSources`、同源码完整 Shadow JAR、统一构建入口 `test build selfdevArtifactManifest` 通过；196 项 JUnit 测试，0 failure、0 error、0 skipped。新增字体测试覆盖混合文字、字号、基线、加粗、换行、裁切、装饰和点击继承。
- 原版 26.3 客户端 `GlyphProviderDefinition` codec 解析最终字体内容：41 个字体、592 个 provider 全部通过。两处 Unihex 均引用 `minecraft:font/unifont.zip`；自定义字体引用完整，bitmap 来自统一包或官方客户端。大间距及 singleton range 的编码通过原版 codec。
- RTX 3070 Ti 的实际 GPU 顶点捕获通过 308 场景：19 个字号、两种基线、斜体、半像素偏移，普通 GUI 与透视文字隔离。统一包 24 种文字、4 种背景变体（含 OIT）及 GUI shader 编译链接通过；576 个原有全屏视角场景、七种分辨率接缝和 112 帧抗锯齿回归通过。
- `minimize_check.py` 对照同源码完整 JAR 为 0 problem(s)，裁剪 149 个未使用 Keystone 类。公共 API 直接调用核对以 0.2.4 为旧提供方、最终 0.2.5 为新提供方及候选调用方，为 0 problem(s)；未扫描第三方插件和任意反射。
- JAR 只有一份内置 ZIP，独立 ZIP 逐字节等于该内置 ZIP；仅包含 `dialogmenu_settings`、`dialogmenu_dialogue`、`dialogmenu_fullscreen` 与 `minecraft` 的公开资源。29 张旧 CJK 生成图移除，439 个已有资源文件字节不变；改动仅为字体 JSON、公开 text.vsh 与 pack metadata。现有面板、物品图标、全屏源图没有重绘。
- 最终 JAR 没有私有 HUD/衣帽间类、私有字体探针、未重定位 Keystone 或 Kotlin/TabooLib 运行库。新增字体实现及 shader 独立位于公开工程，未读取私有素材或整批复制私有代码。
- 公开字体合并工具保留发送方入口，拒绝重复注入、未展开模板和缺少 main 的输入；生成独立候选，不改提供方文件。离线 Wiki 19 页、6 个完整 YAML 示例和 97 个本地链接生成通过。

## 清理与保留

| 区域 | 保留依据 | 删除/替换 | 验证与未决项 |
| --- | --- | --- | --- |
| 文字组件与宽度 | 原菜单位置、换行、裁切和点击使用现有 measured glyphs | CJK bitmap 改为客户端 Unihex 与差值间距 | 196 项单测；codec；308 GPU 场景 |
| 字体与生成器 | Latin/符号 bitmap、Unihex 宽度和 Unifont 许可仍有用途 | 29 CJK atlas、重复中文字符表与旧 CJK 位图生成流程移除；标题度量只剩 171 条 | final ZIP 引用、覆盖范围和字体 codec |
| shader 与资源安装 | 原版/全屏入口、BetterHud 冲突检测和 External 发送仍被使用 | 追加公开字体函数与独立候选合并工具；不引入私有 HUD 依赖 | 合并工具检查通过；实际 BetterHud 组合包尚未验收 |
| 工具与文档 | 累计历史、构建输入、宽度生成工具保留 | 移除失效 bitmap 预览脚本，修正测试的私有构建输出路径，更新生成及合包指南 | 工作树/链接核对；现有 untracked keystone-0.3.4.jar 原样保留 |

## 验证边界

未启动真人图形客户端或临时 Paper 服务器；本轮没有新增 Paper 运行时协议、真人点击延迟、其他 GPU、压力或实际 BetterHud 组合包验收。codec/GPU 结果不等于真人观感验收。BetterHud 的现有冲突保护保留，服主仍需把公开字体函数合入其最终生成 shader，并保留灰度文字与全屏入口；不得直接覆盖其原 shader。

---

# DialogMenu 0.2.4 发布验证 · 2026-10-03

正式版本：[v0.2.4](https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.4)。一个生产 DialogMenu JAR，一份包含 Dialog 与全屏资源的 ZIP。修正 Dialog GUI 着色器的 26.3 DynamicTransforms 布局与显式接口位置；不包含私人素材、衣帽间或 TorakaHud。

## 本次验证

- Paper 26.3、Java 25、主源码 Java 21 字节码；格式、完整构建与 192 项单元测试通过，无失败、错误或跳过。私有 NMS 保留 26.2 的确认加 PosRot，公开 26.3 使用携带坐标的单次传送确认。
- 最终 JAR 的隔离协议探针通过 78 项断言：资源包 HTTP/SHA-1、拒绝加载、两实体、点击与去重、Dialog 往返、成功/失败重载、迟到加载回执、传送/模式/超时/停用恢复和物品数量。两条 ERROR 是故意注入的错误菜单及取消重载报告。
- 最终合并 ZIP 的文字/背景着色器和 Dialog GUI 在实际 GPU 编译、链接通过，GUI uniform 布局与官方 26.3 客户端匹配；576 个视角/顶点/深度场景通过。640×360、960×540、1566×882、1920×1080、1024×768、1280×800、2560×1080 的接缝、四边及 112 帧光标抗锯齿检查通过，质心误差小于 0.2 物理像素。
- 原有资源中 447 个文件字节保持不变；改动仅为 Dialog GUI 两个着色器的 26.3 接口适配及顶层 pack.mcmeta。其余新增内容为全屏资源；私人贴图未重新绘制。
- API 契约对比旧 0.2.3，调用方集合为本机部署插件与候选 JAR；minimize 对照同源码完整 JAR，均为 0 problem(s)。不代表未经扫描的第三方反射兼容。
- JAR 只有一个生产入口和一个内置 ZIP。附加探针、旧全屏插件入口、未重定位 Keystone、Kotlin/TabooLib 运行库均不在交付 JAR 中。导出 ZIP 与内置归档字节相同；HTTP 探针验证实际下载内容的 SHA-1。
- 隔离环境使用干净安装，不包含第三方资源插件；BetterHud、其他核心着色器包与第三方光影的全屏组合未验证。
- 未新增真人图形客户端验收、真实鼠标端到端延迟测量、其他显卡或多人压力测试。点击仍受网络和服务器 tick 影响。不同资源发送方的回执仍分别跟踪，已加载其他服务器包的玩家可能收到一次相同菜单资源的追加请求。

## 清理与保留

| 区域 | 保留依据 | 清理或替换 | 核验 |
| --- | --- | --- | --- |
| 生产入口 | Dialog、全屏与既有菜单在同一生命周期 | 沿用单一插件入口 | 协议切换与停用 |
| 资源构建 | 两种命名空间和各自必需 shader | 两个对外 ZIP 合为一个；重名 shader 显式组合，重复条目报错 | ZIP/JAR 字节与 GPU |
| 配置 | 类型隔离、候选事务、资源发送方设置仍有用途 | 保持既有全屏配置与菜单接口 | 类型及失败重载 |
| 历史与工具 | legacy 对比、旧 CatalogMenu 构造器、累计发布记录 | 更新操作文档与 Wiki；全屏中间包只用于构建，不进入附件或 JAR | API、构建与链接 |

## 附件 SHA-256

| 文件 | SHA-256 |
| --- | --- |
| DialogMenu-0.2.4.jar | `f8be1146f9bed0acee728b2492b3fc8bdbc613a3d4a8502e27991a37fe014f35` |
| DialogMenu-resourcepack-0.2.4.zip | `581f253aaee4983d11c32a5cd92647e8e486fd4f4942c6b46b5cc020613eff92` |

公开版未部署到私有测试服。

---

# DialogMenu 0.2.3 发布验证 · 2026-10-03

正式版本：[v0.2.3](https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.3)。一个生产 JAR 提供 Dialog 和全屏诊断布局，两份资源包分别随 JAR 内置并提供独立下载；没有额外的全屏生产插件。

## 验证与边界

- Paper `26.3.build.142-beta`、Java 25、主源码 Java 21 字节码；`formatSources build fullscreenProbeJar` 完成，192 项单元测试通过，0 失败、0 跳过。
- 隔离 Paper 26.3：最终 JAR 的 78 条协议断言通过。覆盖资源包拒绝/HTTP/SHA-1、仅两个模拟实体、客户端视角与模式、点击去重、移动不发光标元数据、Dialog 往返、传送/模式切换/退出/超时/停用恢复、物品数量、目录路由、成功/失败重载及迟到资源包回执。错误日志仅两条故意注入的无效菜单重载报告，文件与旧会话保持。
- 核对官方 26.3 `ClientPacketListener.handleMovePlayer` 字节码：传送确认直接携带视角坐标，不再发送随后的 PosRot。生产输入处理与探针均使用该行为，已删除旧的两包确认队列。
- 最终全屏 ZIP 在 NVIDIA RTX 3070 Ti / OpenGL 3.3 上编译链接 24 种文字和 4 种背景着色器变体（含 OIT）。576 个视角/顶点顺序/深度场景通过；640×360、960×540、1566×882、1920×1080、1024×768、1280×800、2560×1080 的接缝与四边覆盖通过，112 帧抗锯齿测试质心误差小于 0.2 物理像素。
- `minimize_check` 对照同源码完整 JAR 为 0 problem(s)；公共 API 检查以 0.2.2 为旧提供方，以本机部署插件集合及候选 JAR 为调用方，0 problem(s)。仅覆盖实际扫描的直接调用，不能推断全部第三方反射兼容。
- JAR 单一入口、Mojang 映射、重定位 Keystone、两份内置 ZIP 与独立附件逐字节一致；无测试探针、未重定位 Keystone、Kotlin/TabooLib 运行库、私有 HUD 或衣帽间素材。
- 此次是协议探针和真实 GPU 离屏渲染验证，未完成原版 26.3 图形客户端的人工操作验收，也未测量真实鼠标端到端延迟、多人压力或其他显卡。BetterHud、第三方核心着色器和光影的全屏组合未验证。点击仍受网络/tick 影响。现有部署服务器未替换为公开版。

## 保留与清理

| 区域 | 保留依据 | 删除或替换 | 验证 |
| --- | --- | --- | --- |
| 菜单解析 | catalog、simple、legacy 仍有配置和 API 使用方 | 统一 MenuType；删除无调用占位符私有校验 | 类型/动作/旧配置单测 |
| 全屏输入 | 当前原版 26.3 的确认、移动和点击数据包 | 替换旧双包确认；无独立生产插件入口 | 78 条协议断言 |
| 生命周期 | 切换、重载、离线均须恢复会话并取消请求 | 配置改为候选快照；按需 HTTP/定时任务 | 无效重载与迟到回执场景 |
| 资源 | 公开 Dialog 素材与官方客户端着色器 | 移除私有 HUD 合并输入；公开诊断背景生成 | JAR/ZIP 检查、GPU |
| 兼容与依赖 | 旧公开 CatalogMenu 构造器、旧菜单入口仍属契约 | 删除无用 Kotlin 格式任务，保留冻结 Keystone 反射适配 | API/minimize/既有恢复单测 |
| 文档与生成器 | 配置示例、累计历史、生成工具仍使用 | 更新单 JAR、五个菜单、明确全屏限制与 MenuType | Wiki 19 页、6 个完整示例、95 个链接 |

## 附件 SHA-256

| 文件 | SHA-256 |
| --- | --- |
| DialogMenu-0.2.3.jar | `d74f2e1b9b8cd615ec30746d0bf69d05edad07c8d6eb35776c7d115ff60633e6` |
| DialogMenu-fullscreen-0.2.3.zip | `a1fb2351d8eed5ac0c0d44a172d542dbda7562ec8b90933eb4c68a78391d317c` |
| DialogMenu-resourcepack-0.2.3.zip | `6f3180accdbcfc6f982d2a38219ae249aba9028ab099a60666aa5beeb6254be0` |

以上同份 JAR 已在隔离服务器验证。下载以 Release 中的 SHA256SUMS.txt 核验，历史 Releases、标签和附件保持原样。

---

# DialogMenu 0.2.2 发布验证 · 2026-10-03

正式发布：[v0.2.2](https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.2)。发布收尾仅更新说明与标签，使用任务修复阶段已构建、已验证的同一份 JAR。

- JAR：`DialogMenu-0.2.2.jar`，SHA-256 `636d2ad87cc1b8b3281443bc3b79a24ddbb8c9dd080f028203e4bcd7e90b66e1`。
- 独立资源包：`DialogMenu-resourcepack-0.2.2.zip`，SHA-256 `6f3180accdbcfc6f982d2a38219ae249aba9028ab099a60666aa5beeb6254be0`；逐字节等于 JAR 内置资源包。
- `formatSources`、完整 Shadow JAR、`build` 与 176 项单元测试通过，0 失败、0 错误、0 跳过；瘦身核验 0 问题。
- 隔离 Paper 26.3 build 142 验证翻页、分类、模拟领取、追踪、关闭和重新打开，共捕获 7 次菜单报文，无异常错误。
- 两版公共菜单的 21 个离线绘制场景一致；关闭符号与奖励图标使用已核验的实际字形及基线。未进行真人客户端最终观感复核。
- 当前素材及 JAR 资源与旧菜单贴图逐像素比较无匹配；保留合法字体、Unifont 许可和历史版本凭据。公开与私有资源分别打包。
- 附件 `SHA256SUMS.txt` 覆盖 JAR、独立资源包和最近邻 2× 预览。源码由标签提供；历史附件不覆盖。

详细记录：[原创菜单](docs/development/ORIGINAL-UI-2026-10-03.md)、[任务菜单修正](docs/development/QUEST-UI-2026-10-03.md)。

部署边界：本机 test_server 的私有版文件已在停服时更新；未启动服务器验证新版本运行加载。发布成功与运行加载分别核验。

---

# DialogMenu 0.2.0 发布验证

本页记录 DialogMenu 0.2.0 正式版的 Paper 26.3 验证结果。

- 产物：`DialogMenu-0.2.0.jar`
- SHA-256：`44b3fd923fe38c5e1586f021744c9a0160674d827cbd14dc0e982bf24ecba479`
- Paper API：`26.3.build.142-beta`
- Paper 服务端：`26.3-142-main@1c92a6c`
- 累计功能及验证过程：`CHANGELOG.md`。

- Gradle `test`、`build` 和 `selfdevArtifactManifest` 通过；176 项 JUnit 测试通过，0 failure，0 error，0 skipped。
- 113 个 Java 源文件通过格式检查，0 个需要格式修改。
- Paper 26.3-142 临时沙盒以 Java 25 启动，DialogMenu 0.2.0 正常加载 4 个内置菜单并正常停止。
- 共享库：Keystone 0.3.5，SHA-256 `314b664f3bb14174b9a2d84485a8ca403a1c44e5950a1e0cff3dd46d565f73bb`。

## 验证边界

- 当前最终 JAR 的 176 项测试实际执行通过；此前 8 场精确 JAR 日志的 131 条后条件和 31 个探针 PASS 继续作为累计验证证据，不将后条件等同 131 个业务或真人客户端场景。
- Paper 26.3-142 沙盒没有安装 CraftEngine、PlaceholderAPI 或代理，只验证了干净 Paper 服务器上的插件加载、内置菜单读取、资源包导出和正常停服。
- 动作部分条件为日志字符串/包存在性；失败命令提示数不能单独证明每个失败后的全部副作用缺席。Connect只核对发包，未验证实际Bungee/Velocity转服；四份Dialog JSON对照为结构而非客户端渲染。
- 真人按钮/字体/音效及未安装物品源实际集成未新增验收。41动作/渲染等class、42非版本资源及整资源包与paper.3字节相同，无私有资源。
- StartupLanguages及StartupUpdates使用冻结314b内部反射桥接，未来换库需复核语言/更新backupfail路径；当前单测/minimize/Paper已实际验证。
- 早期guard=false夹具未真正制造备份拒绝，不作通过；最终三类真实Windows独占拒绝已验证，原日志保留。部分自解析内容无法可靠定位时保留文件/路径，不伪造行列。
- 本轮没有将全部最终插件同时运行作为完整联合验收。
