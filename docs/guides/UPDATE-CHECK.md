# GitHub Release 更新提示

插件启动后读取本目录的 `update-check.yml`，默认在 60 秒之后再错开 0～119 秒检查 GitHub，此后每 6 小时复查。请求在异步线程执行，连接超时 5 秒、读取超时 10 秒；失败不会影响游戏功能。

发现新版本后，控制台记录当前版本、最新版本和 Release 链接。OP 或持有 `toraka.update.notify` 权限的在线玩家收到相同提示；后来登录的管理员在上线 3 秒后收到已缓存的提示。同一版本每个在线会话提醒一次，控制台每个新标签提醒一次。仅提醒，不下载或替换 JAR。

所有更新请求均匿名发送，不读取环境变量或配置中的 GitHub Token，也不发送 Authorization 请求头。公开仓库无需配置凭据即可提示更新。旧版 `require-token` / `token-environment` 已废弃，保留它们也不会启用认证。

私有仓库不提示更新。匿名访问返回 HTTP 404（包括私有仓库、仓库不存在、没有正式 Release）时静默跳过，并清除此前缓存的更新通知；以后仓库改为公开并发布 Release，下次检查即可自动恢复。不会将无法访问误报为“已是最新版”。

限流、网络错误和无效响应只在错误发生变化时输出一次说明，并按 GitHub 的退避时间及配置周期重试；不会提示配置 Token。匿名 API 的限流按出口 IP 共享，默认错峰及 6 小时周期用于控制请求量。`enabled: false` 可完全关闭该插件的检测。

默认通过 GitHub `releases/latest` 检测正式 Release。`include-prereleases: true` 时读取最近 100 个 Release，选取首个非草稿且标签可比较的版本，适用于现有 CraftCosmetics packet 预发布渠道。数字版本逐段比较，支持 `v` 前缀、SemVer 预发布排序和构建元数据；本地版本等于或高于远端时不提示降级。非版本标签会报告无法比较。

代码随各插件打包，无需额外安装插件。共享源码由工作区 `tools/templates/plugin-updates/` 和 `tools/sync_plugin_updates.py` 维护；各仓库独立构建不依赖这些工作区工具。

API 依据：[GitHub Release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。
