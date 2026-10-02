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
