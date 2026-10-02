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
