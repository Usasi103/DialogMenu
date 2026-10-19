# DialogMenu 公开版验证记录

## 0.2.8 当前正式候选

- 变更：掉落设置优先读取 ItemLoom 的 LootBeam 模块，保留独立 LootBeam 与旧 Ambience 回退；PickupNotifier 行为不变。
- 已添加 9 项提供方优先级、类加载可见性和回退单测；`tools/build_selfdev.py --projects DialogMenu` 构建通过。
- 产物：`dist/DialogMenu-0.2.8.jar`，SHA-256 `9dc50960e6a8f22a18e5f87b7f6c2070ac89c820e3dcf8ca3e20e6e6a99fc923`。
- 公开 JAR 未包含私有 HUD、衣帽间或验证探针；当前候选尚未发布或部署。

## 0.2.7 历史验证

本页记录 2026-10-14 的 ItemLoom 提供方接入。历史动画、字体、全屏和客户端验证见 [CHANGELOG.md](CHANGELOG.md)；发布状态以最终交付报告为准。公开工程和产物遵守私人素材隔离边界。

### 产物

- JAR：`dist/DialogMenu-0.2.7.jar`
- SHA-256：`fefddbd39d6814b09931f856136902bb80f396f21b970b596c47ea0ee6c3e093`
- 内置统一资源 ZIP SHA-256：`29e0830c404182fbe6adc6b9fd8db4dcf50ff213ea3ee5f4a3e197eb1fc188e8`
- 构建报告：工作区 `tools/reports/gear-completion-20261014/emd-final.json`。205 项测试全部通过，无失败、错误或跳过。
- 精确构建和运行目标：Paper 26.3 build 142 beta、JDK 25，主源码 Java 21 字节码。

### 提供方契约实际验证

工作区 `tools/reports/gear-completion-20261014/public-source-contract-runtime.log` 记录两轮 SourceProbe，各 10 项断言通过，累计计数为 10 和 20，中间执行 `/dmenu check` 和 `/dmenu reload`；无 ERROR 或失败，正常停服。

提供方是外部 `ItemLoom-PublicApiContractFixture.jar`：原样复制七个 ItemLoom 2.8.0 公开 API 源码，注册实际接口服务，生成独立 DIAMOND_SWORD 并派发实际 ItemGenerateEvent；不包含真实 ItemLoom 业务或 NMS 实现。验证只读 ID 目录不生成、按查看者构建独立副本、每次构建一个事件及旧 NI 来源别名。契约及运行证据见外部 `E:/Cache/Temp/gear-itemloom-20261014/public-contract-fixture/artifact-audit.json`。

运行 DialogMenu JAR 的 SHA-256 为 `42721bed0bdb6a2e97de5f37c13c0fdfaaa87c42f0686764feab6c3721f681f4`；最终 JAR 仅更新内置 `配置说明.md`，全部 class 和统一资源 ZIP 字节一致。比较记录在外部 `E:/Cache/Temp/gear-itemloom-20261014/documentation-inputs.json`。

最终 JAR 有且仅有一份统一资源 ZIP；公开 494 项资源不含私人命名空间，未打包衣帽间、TorakaHud、验证探针、原始 Keystone、Kotlin/TabooLib 或 ItemLoom API。

### 验证边界

该结果仅覆盖公开版可选提供方契约。真实 ItemLoom 2.8.0 的 Paper 26.3 生成兼容性未成立，不能以 fixture 通过替代真实实现验证。本轮没有新增真人画面/点击、GPU、第三方 HUD 合包或多人压力验收；其历史证据按对应版本保留。
