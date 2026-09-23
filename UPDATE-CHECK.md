# GitHub Release 更新提示

插件启动后读取本目录的 `update-check.yml`，默认在 60 秒之后再错开 0～119 秒检查 GitHub，此后每 6 小时复查。请求在异步线程执行，连接超时 5 秒、读取超时 10 秒；失败不会影响游戏功能。

发现新版本后，控制台记录当前版本、最新版本和 Release 链接。OP 或持有 `toraka.update.notify` 权限的在线玩家收到相同提示；后来登录的管理员在上线 3 秒后收到已缓存的提示。同一版本每个在线会话提醒一次，控制台每个新标签提醒一次。仅提醒，不下载或替换 JAR。

私有仓库需要 GitHub fine-grained personal access token，仅授予对应仓库 **Contents: Read**。在启动 Java 的环境中配置 `TORAKA_GITHUB_TOKEN`，然后重启服务器；所有插件可共用同一环境变量。不要把 Token 写入 JAR、版本库或聊天。可用 `token-environment` 指定其他环境变量名称，配置中不存储 Token 本身。修改环境或检测配置后需要重启。

公开仓库可设置 `require-token: false` 使用匿名请求。缺少凭据、HTTP 401/403/404/429、超时或错误响应会在控制台报告原因；同一错误连续出现只提示一次，后续仍会按间隔重试。GitHub 限流等待时间会被遵守。

默认通过 GitHub `releases/latest` 检测正式 Release。`include-prereleases: true` 时读取最近 100 个 Release，选取首个非草稿且标签可比较的版本，适用于现有 CraftCosmetics packet 预发布渠道。数字版本逐段比较，支持 `v` 前缀、SemVer 预发布排序和构建元数据；本地版本等于或高于远端时不提示降级。非版本标签会报告无法比较。

代码随各插件打包，无需额外安装插件。共享源码由工作区 `tools/templates/plugin-updates/` 和 `tools/sync_plugin_updates.py` 维护；各仓库独立构建不依赖这些工作区工具。

API 依据：[GitHub Release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。
