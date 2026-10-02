# Paper 26.3 兼容验证

本记录对应 DialogMenu `0.2.0` 正式版。构建目标为 Paper API `26.3.build.142-beta`，使用 JDK 25，插件字节码目标为 Java 21。

## 验证范围

- Gradle `formatSources`、`test`、`build` 与最终 Shadow JAR 检查。
- Paper 26.3 API 编译，覆盖 Dialog API、资源包状态事件、动作、菜单解析、ItemBridge 和 Keystone 重定位。
- 公开配置格式、指令、权限和资源包路径保持兼容。

## 实际结果

- Paper 26.3-142 启动并加载 `DialogMenu 0.2.0`，日志显示 4 个内置菜单加载成功。
- Paper 26.3-142 正常执行 `/stop`，插件完成禁用和数据保存。
- Gradle 测试 176 项通过，格式检查 113 个 Java 文件通过，最终 JAR SHA-256 为 `44b3fd923fe38c5e1586f021744c9a0160674d827cbd14dc0e982bf24ecba479`。

本次沙盒没有安装 CraftEngine、PlaceholderAPI 或代理，只覆盖干净 Paper 服务器的启动、菜单读取、资源包导出和停服；真实客户端画面及可选插件联动仍沿用既有验证记录。
