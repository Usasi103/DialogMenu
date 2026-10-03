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
