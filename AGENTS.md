# 公共版本边界

- 此仓库是公开的通用 DialogMenu。私有 HUD 的实现、配置、专用资源，以及私人贴图不得合入本仓库，也不得出现在公开 JAR、资源包或源码附件中。
- 通用图片标签、本地多语言、资源安装和兼容修复可同步至私有分支；逐项核对差异，保留私有素材，不反向整体复制私有目录。
- 累计同步时核对发布附件中的实际内容；本地验证产物使用独立版本标识，正式 GitHub Release 不标为 Pre-release，保留已有版本和附件。

# 构建

- 源码为纯 Java（`src/main/java`、JUnit 5 测试在 `src/test/java`），不使用 Kotlin 或 TabooLib；共享库 Keystone 放在 `libs/`，打包时重定位到 `online.toraka.dialogmenu.libs.keystone`，ItemBridge 重定位到 `online.toraka.dialogmenu.library.itembridge`。
- 以 Paper 26.3 API/NMS 编译与验证，paperweight 开发包为 26.3.build.142-beta；全屏包从官方 26.3 客户端生成（fullscreenClientJar 属性），不读取私有包；Gradle 命令加 `--no-daemon`，先 `formatSources` 再 `build`。稳定发布版本不使用 `SNAPSHOT` 或 GitHub Pre-release 标记。
- 打包时用 Shadow `minimize` 只保留用到的 Keystone 类（根为 main 源码，Maven 坐标依赖如 ItemBridge 不裁剪）。核对：先 `./gradlew --no-daemon shadowJar --no-minimize-jar` 把不瘦身的 jar 另存，再 `build`，然后 `python -B <工作区>/tools/minimize_check.py dist/<jar> --full <不瘦身的 jar>` 须为 `0 problem(s)`。

# 文件校验与恢复（Keystone 0.3.5）

- 启动：MenuFiles 原字节只读预检，既查 UTF-8 / YAML，也按完整业务解析器检查取值、动作、宽度、资源包和引用。坏设置/内置内容经公共 FileBackup 备份并读回逐字节核验后才完整默认恢复；备份失败原文件/版本头/日志不动，内存默认、WriteGuard 锁写。自建坏内容原样跳过；若其引用导致剩余菜单无法构成有效整体，只在内存用完整内置菜单，不覆盖其他有效文件。
- 语言与 update-check 的业务预检同样先于首次加载、版本头和菜单安装。语言恢复/跳过/备份失败时，安装已预检的内存候选，不能再普通 Lang.load（会 unblock 并恢复日志）；更新设置备份失败时启动内存默认。冻结 0.3.5 尚无这两个候选安装接口，StartupLanguages/StartupUpdates 的最小反射适配绑定最终 314b 库，后续换库必须核对字段/构造器并复测这两条 guard 分支。
- `/dmenu reload`：ReloadTransaction 内解析独立 MenuCandidate，并加载语言候选；任意编码、语法、业务错误整次取消。全部旧模型/翻译/缓存/菜单/资源状态保留，文件不改、不备份。成功 commit 后才安装模型、重置缓存、刷新菜单和导出/安装资源。TAB 修复由公共事务先核验原字节备份再写入。
- `/dmenu check`：只读解析，不 commit、不恢复日志、不提升写保护、不重置缓存。
- 错误用公共 ConfigProblems 统一报告：后台详细、执行者一次简短取消；管理员提醒每人每次开服累计最多三次，间隔120秒。转报已有 LoadProblem 用 kind/cause/position，不对已格式化 cause 再调 content。
- 内容输入范围保留 catalog/simple/legacy；新增内容文件需同时加入 BrokenFiles.candidates 和候选解析。保留公开动作/PAPI/Width、资源资产与公共 API，禁止同步私有素材。
