# Changelog

## [0.2.5] - 2026-10-04

- 公开菜单复用原版客户端 Unihex：29,326 个 CJK 字形的字号 6–24、按钮基线、混合 Latin、加粗、斜体及逻辑宽度由公开字体入口恢复；换行、裁切和点击事件保留。
- 统一 ZIP 移除 29 张旧中文字图集，24 份字号字体改为共享 CJK 引用；标题度量表从 29,497 条缩到 171 条，覆盖范围集中保存。普通 Unicode 标签保持原有宽度。
- 仅在公开原版 `text.vsh` / 全屏入口追加字体函数；保留 BetterHud 的资源冲突检测和外部发送方式，提供独立候选 shader 合并工具。未引入私有 HUD 实现或私人素材。
- 清理旧 CJK 位图生成器和过时位图预览验证；修正新增布局探针的输出目录，保留已有用户改动与不相关依赖文件。更新双语说明、生成流程与累计验证记录。
- 验证：196 项单测、原版 26.3 codec 的 41 字体/592 provider、308 个 GPU 字体场景及同源码完整 JAR 瘦身核对通过。统一资源包约 282 KB，较 0.2.4 减少约 90.4%。最终产物和测试边界见 `RELEASE-VALIDATION.md`。
- 正式发布 [v0.2.5](https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.5)，包含 JAR、统一资源包及 SHA256SUMS.txt；源码、标签和下载附件均已核验，保留历史 Releases。公开版未部署到私有测试服。

## [0.2.4] - 2026-10-03

- 合并 Dialog 与全屏资源：一个生产 JAR、一个 `DialogMenu-resourcepack-0.2.4.zip`。JAR 内置、自动导出、全屏 HTTP 返回与 Release ZIP 逐字节一致，不再提供第二个 fullscreen ZIP。
- 修正 Dialog GUI 着色器的 26.3 DynamicTransforms 布局与显式接口位置；不包含私人素材、衣帽间或 TorakaHud。
- 菜单文件头继续选择 `MenuType: dialog` / `fullscreen`，禁止同文件混用；全屏仍为固定 diagnostic 布局，可与 Dialog 通过 open 动作互相跳转。
- 删除第二个 ZIP 的生产打包入口；保留仅用于组装的中间任务和测试探针。 更新中英文说明、配置指南、离线 Wiki 与生成器。
- 验证：192 项单元测试、78 项目标 Paper 协议断言、GPU 576 个视角场景及七种分辨率的 112 帧抗锯齿检查通过；API 与瘦身检查 0 问题。未新增真人鼠标延迟测量。详见 [发布验证](RELEASE-VALIDATION.md)。
- 正式发布 [v0.2.4](https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.4)，附 JAR、统一资源包和 SHA256SUMS.txt，历史标签与附件保留。

## [0.2.3] - 2026-10-03

- 一个 DialogMenu JAR 合并 Dialog 菜单与全屏诊断方案，统一 menus 目录、打开动作、重载和生命周期；无需另装全屏插件或 TorakaHud。
- 每份菜单必须在开头声明 MenuType: dialog / fullscreen，拒绝重复、未知或混用类型；旧菜单升级需补 dialog 声明，全局 Version 3 配置不加。
- 新增 demo-fullscreen 固定诊断布局、客户端本地光标与悬停高亮、抗锯齿十字和按窗口铺满的 1920×1080 背景；点击仍由服务器处理。暂不提供任意全屏 YAML 背景或按钮编辑。
- 全屏适配 Paper / 原版客户端 26.3；资源包基于官方客户端与公开诊断素材生成，不含私有 HUD 或私人贴图。首次使用需配置 fullscreen.yml 的资源包基础 URL。
- 成功重载清理全屏会话；失败保留配置快照。Dialog 切换取消待处理的全屏包请求，防止迟到回执重新打开全屏。资源服务和会话定时器按需启动。
- 删除 SimpleMenuParser 无调用的私有占位符检查、正则和导入，以及无用 Kotlin 格式任务；保留正在使用的动作、旧配置解析和公开接口。同步中英文介绍、菜单示例、生成器与离线 Wiki。
- 构建、GPU 与隔离 Paper 协议验证范围见 RELEASE-VALIDATION.md。正式 Release 提供单一 JAR、两份相同内置资源的独立 ZIP 和 SHA256SUMS.txt，历史版本保持原样。

## [0.2.2] - 2026-10-03

- 累计发布原创灰石设置菜单、10 枚独立 16×16 原创图标，以及同步更新的首领、NPC、任务菜单与模板；源 PNG、Blockbench 工程和生成器一并随源码提供。
- 任务列表的关闭符号改为清晰的粗体 X，沿用 18×18 点击范围；奖励图标使用独立基线，与文字垂直居中，横向留白遵循实际图标宽度。
- 复核两版素材及生成源；保留必要的原版字体与历史更新记录，清理失效的旧素材忽略项。
- 更新任务图标基线与部署说明，移除过时的下载外部图集生成皮肤指引。
- 验证：完整构建、176 项单测、打包完整性和隔离 Paper 菜单交互通过，见 `docs/development/QUEST-UI-2026-10-03.md`。
- 正式发布 `v0.2.2`，JAR、独立资源包、任务菜单预览与 `SHA256SUMS.txt` 随 Release 提供；保留历史版本及附件。
- 发布地址：https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.2

## 开发记录 · 0.2.1（已并入 0.2.2）- 2026-10-03

- 设置菜单采用 Blockbench 原创灰石面板、按钮、开关和滑块；10 枚原创图标为独立 16×16，导航留白和浅色按钮文字同步适配。
- 首领、NPC 对话、任务菜单以及随包模板/文档示例统一为 `Skin: stone`。旧 amethyst / parchment 配置、字形编号、可变宽按钮和菜单动作保持兼容。
- 资源生成器改为读取原创源 PNG，移除旧受限源图、旧图标和外部图集导入路径，保留 Blockbench 工程及来源说明。
- 本次累计源码与独立构建产物；未创建新标签或 GitHub Release。
- 验证：完整构建、176 项单测、API/minimize 与隔离 Paper 菜单交互通过，见 `docs/development/ORIGINAL-UI-2026-10-03.md`。

## [0.2.0] - 2026-10-02

### 正式版与 Paper 26.3

- 正式版不使用 `SNAPSHOT`，也不标记为 GitHub Pre-release。
- 编译与测试目标更新为 Paper `26.3.build.142-beta`，继续输出 Java 21 字节码并使用 Java 25 构建。
- 保留公开版配置格式、指令、权限、公开 API、ItemBridge 和资源边界；私有版衣帽间功能不进入本版本。
- 实际 JAR、SHA256SUMS 和 Paper 26.3 构建验证结果以本版本的 `RELEASE-VALIDATION.md` 为准。

## [0.2.0-paper.4-SNAPSHOT] - 2026-10-02

### GitHub 发布收尾（2026-10-02）

- 累计迁移与重构源码随 `v0.2.0-paper.4-SNAPSHOT` 标签同步；以下开发验证记录按原时间保留。
- 发布精确已验证 JAR 与 `SHA256SUMS.txt`；构建与验证范围、产物 SHA-256 见 `RELEASE-VALIDATION.md`。
- 发布地址：https://github.com/Usasi103/DialogMenu/releases/tag/v0.2.0-paper.4-SNAPSHOT


开发候选，以已交接的 paper.3 动作/Width 版本为基线；Keystone 0.3.5 最终库 SHA256 `314b664f3bb14174b9a2d84485a8ca403a1c44e5950a1e0cff3dd46d565f73bb`。

- reload 改为原字节候选解析 → 公共事务 commit → 模型/语言/菜单/缓存/资源安装。编码、YAML、重复键、业务取值或引用错误整次取消，旧状态和磁盘保留，无失败备份；TAB 修复只在整体通过后核验备份再写回。check 只读、无提交。
- startup 预检覆盖实际业务非法值。坏设置/内置内容先公共原字节备份并读回核验，再完整默认；备份失败不加版本头、不恢复日志，内存默认并锁写；自建坏内容保留/跳过。无法解开坏自建引用时，只回退完整内存内置菜单，不覆盖其他有效文件。
- 公共 ConfigProblems 统一详细后台日志、执行者取消摘要和最多三次管理员提醒；转发位置不重复追加。候选物品校验使用独立 ItemBridge，图片检查和资源副作用延至 commit 后。
- paper.3 的动作、条件、PAPI顺序、按钮 Width、公开接口和资源资产保留；未引入私有特性或素材。旧 JAR 作为外部对照保留。本轮不提交、部署、发布。
- 语言和更新设置语义预检提前于加载；语言恢复/跳过或备份失败时直接安装预检候选，避免普通加载清除锁写。真实 Windows 备份读取拒绝验证了配置/语言/更新设置原字节、版本头及写入日志保持；中文默认帮助可用。
- 完整构建与实际执行的176项单测通过；API（paper.3与旧公开0.1.23）、minimize均0问题。最终JAR八个Paper增量场景、131条结果核验通过；四个默认菜单JSON与paper.3排除动态字段后相同，41个动作/Width/PAPI/绘制类及全部原资源字节一致。
- 验证记录见外部 `runs/codex1002-dialogmenu-coordination-tools/REPORT.md`，收尾以实际最终 JAR 和对应 SHA256SUMS 为准。

## [0.2.0-paper.3-SNAPSHOT] - 2026-10-01

开发版（只累计源码，不建标签、不发 Release）。在 0.2.0-paper.2（sha256 `7966fc58…6dda`）之上增加 TrMenu 式动作与 canvas 按钮宽度。

- TrMenu 式动作、条件块与条件（用户 2026-10-01 决定，格式照 TrMenu 3.12.5）：settings（控件、物品页按钮、选项与 `MainMenu`）和 canvas 按钮的 `Actions` 改由同一个解析器 `ReactionParser` 读取、`ReactionRunner` 执行。
  - 动作行 `名称: 值`，一行可用 `_||_`（或 `&&&`）连接多条；名称不分大小写并接受 TrMenu 别名：tell（message、msg、talk）、chat（send、say）、title（subtitle、send-title）、actionbar、tellraw（json）、command（cmd、player、execute）、console、connect（bungee、server）、sound（sounds、play-sound）、delay（wait）、return（break）、close（shut、force-close、silent-close）、open（gui、menu、trmenu、force-open）、page、refresh（update、icon-refresh），以及 DialogMenu 自己的 set（canvas）、search（settings）和兼容用的 template（canvas）。顶层可写单条字符串、列表、条件块或 TrMenu 的 `all:`。
  - 行尾选项：`{delay=N}`（0–72000 tick，只推迟这一条）、`{chance=0.5}`、`{condition=条件}`、`{players}` / `{players=条件}`（对每个在线玩家执行，仅 tell、chat、title、actionbar、tellraw、command、sound、connect；文字和指令里的值仍按点击者填写）；`{}` 或 `<>`、`=` 或 `:` 都可以，所以 `{Delay=2}` 原样可用。一行多条动作时，选项最多的那一段的选项套用到整行（TrMenu 的规则）。
  - 值格式：title `主标题 副标题 淡入 停留 淡出`（tick，默认 15 / 20 / 15，带空格的文字用反引号）；sound `名称-音量-音调`，数字从右读，所以 `mypack:ui-click-0.8-1.4` 这类带 `-` 的资源包 ID 可用，Bukkit 名、原版 ID（`ui.button.click`、`minecraft:…`）和命名空间 ID 都认，`;` 分多个，在玩家位置播放，Bukkit 名写错时 check 报错；tell / title / actionbar 认 `&` 颜色、`&#RRGGBB` 和字面 `\n` 换行，含 image / i18n / l10n 标签的行照旧走标签解析；tellraw 接受 JSON 或 `<文字@hover=…@url=…>` 简写，占位符逐段填入，不会破坏 JSON；connect 发送代理的 `Connect` 插件消息（需 BungeeCord / Velocity），插件启用时注册 `BungeeCord` 发送通道；公开版 actionbar 直接发原版动作栏。
  - 条件块 `condition` / `actions` / `deny` / `priority`（接受 TrMenu 的键别名），可嵌套 8 层，按 priority 从小到大执行，默认按列表位置；一组 Actions 所有分支合计最多 64 条动作（以前 settings 与 canvas 都是 16 条）。条件块没有 condition 却写 deny、写了未知键或 `left` / `right` 等按键分组（Dialog 只有一种点击，只认 `all`）时加载报错。
  - 条件：canvas 沿用 `名称=值`、`名称>=数字`（Variables / Placeholders 声明的名称，规则同 VisibleWhen），另加完整 PAPI token 直接比较（`%player_level% >= 10`）、`perm 节点`（TrMenu 的 `perm *节点` 照收）、`not` 取反，列表表示全部成立，最多 8 条；settings 只有 PAPI 与 perm 两种。取不到的 PAPI 值让条件不成立，取反也不成立。不支持 Kether / JavaScript；TrMenu 的 `$ Number(vars("%x%")) >= 150` 写成 `%x% >= 150`。
  - 指令可以使用 `%PAPI变量%`（以前 settings 与 canvas 都禁止 `%`），canvas 指令也能用 Placeholders 名称（以前报 `指令不能使用 Placeholders 的值`）。`;` 在填值之前把一条动作拆成多条指令，每个值只填一次、控制字符变空格，所以填入的值不会多出指令或再次展开；任何一个值取不到时这条指令不执行、按失败处理，不会带着原样的 token 执行。`{名称}` 仍只限 `{player}`、`{uuid}`（canvas 另加 Variables / Placeholders 名称），未声明的报 `指令中的 {…} 未声明`。
  - 执行严格按顺序：条件在执行到时才判断，能看到前面 set 和指令的结果；`delay: N` 暂停其余动作，玩家下线则取消。取消以前的顺序限制（settings 的 close 只能在首项、page / refresh / search 只能在末尾；canvas 的 close / template / refresh 只能在末尾），这些动作现在可放在任意位置，之后的动作照常执行。重绘规则：第一个 `delay:` 之前的部分结束（或 return、指令失败）时重绘一次，之前执行过 page / open / close / refresh / search / template 则不重绘；settings 仍在下一 tick 重绘。指令失败时提示玩家（canvas `DialogMenu：指令执行失败，后续动作已停止。`，settings `setting.failed`）并跳过其余动作，已执行的不回滚；`{delay=N}` 推迟的指令失败只提示。
  - `open: 菜单ID` / `open: 菜单ID:页面ID` 可打开 menus/ 中的任何菜单：canvas 之间与 page 一样带上合法的同名变量，其他情况按 `/dmenu open` 打开。旧 `menu:` 是它的别名，现在也能打开 settings 菜单（以前要求目标为 canvas）。Version 2 简化配置与 templates/ 独立模板没有菜单目录，不能用 open，独立模板也不能用 page。settings 现在也可以用 tell（含 `message:`）、title、sound 等提示动作；settings-demo 的普通按钮另外允许 tell、title、actionbar、tellraw、sound、delay、return 与条件块。
  - 与 TrMenu 的刻意差异：未知动作名在加载时报错并列出可用动作（TrMenu 当作 Kether 执行）；未加引号的 `- tell: hi` 报错提示加引号（TrMenu 静默忽略）；条件按顺序在执行到时判断（TrMenu 预先判断）；title 时间写错报错（TrMenu 用默认值）；`{chance=}` 须在 0–1 之间（TrMenu 不检查，写成 50 会总是执行）；`page:` 是页面 ID 而不是页码；没有 op、Kether、JavaScript、经济、物品、bossbar 等动作。
  - 兼容：现有菜单不用改，`message:`（tell）、`menu:`（open）和原来的顺序都照旧有效，settings 的 close → 指令 → 刷新 / 跳转与失败处理和以前相同。以下写法的含义变了：指令值里的 `;` 现在拆成多条指令（以前整段作为一条指令）；canvas `message:` 现在解析 `&` 颜色代码和字面 `\n`（以前原样显示）；动作文字中的 `_||_`、`&&&` 以及 `{delay=…}`、`{chance=…}`、`{condition=…}`、`{players}` 和对应的 `<…>` 写法现在是分隔符或选项。按文本检索，test_server 现有的 5 个菜单、插件内置菜单与模板以及 Wiki 示例都没有这些写法。以前报错的写法现在可以加载：单条字符串 Actions、条件块、任意位置的 close / page / refresh、指令中的 `%` 与 Placeholders 名称、settings 中的 `message:` / `menu:`。相关报错文字也换成了新的提示。
  - 代码：新增 `ReactionParser`、`ReactionRunner`、`MenuReaction`、`ActionCondition`、`ActionValues`、`ActionBars`。`MenuAction` 新增 `reaction` 分量，settings 的 Actions 列表不再编译成 `steps` 序列和 `close` 标记（原构造器保留）；`TemplateElement` 的 `actions` 分量换成 `reaction`，`actions()` 仍返回规范化后的动作行（`page: x` 读作 `template: 菜单/x`），原构造器保留；`TemplateParser.parse`、`SimpleMenuParser.parse` 各加一个带跳转解析的重载，原方法不变。
  - 文档：`docs/wiki/actions.md` 重写为完整参考（动作速查、可用范围、行尾选项、条件块、条件写法、执行顺序与重绘、失败处理、指令身份与占位符、各动作的值、与 TrMenu 的差异和迁移步骤）；`variables.md` 的支持范围速查与命令占位符说明，`troubleshooting.md`、`migration.md`、`quick-start.md`、`settings.md`、`elements.md`、`structure.md`、`text-tags.md`、`README.md`（Wiki）、`docs/guides/MENU-CONFIG.md`、`SETTINGS-DEMO.md` 和 jar 内 `配置说明.md` 中过时的动作规则同步更新，`docs/wiki/index.html` 用 `tools/build_wiki.py` 重新生成。
  - 验证（2026-10-01）：测试 125 → 161 项（按钮宽度 8 项、动作 28 项），全部通过；`minimize_check` 0 problem（省 280.4 KB，保留的 Keystone 类与 paper.2 相同）；`api_contract_check` 对 paper.2 为 0 problem（调用方为 test_server 中除私有 DialogMenu 外的全部插件与 Ambience 构建）；jar sha256 `5e1d2dd1…ef9b`，可重复构建。沙盒 `server-dialogmenu-actions`（Paper 26.2，模拟玩家与抓包探针，日志 `keystone-sandbox/runs/dm3final-*`、`dmactions-r*`）：干净安装的 5 个内置菜单 Dialog JSON 与 paper.2 相同（只差会话令牌与实时延迟）；canvas 与 settings 各动作结果符合预期——sound 的 Bukkit 名、原版 ID 与带 `-` 的资源包 ID 都在玩家位置播放，tell 的 `&` / `&#RRGGBB` 与 `
`，title 反引号与默认时间，actionbar，tellraw 的 JSON 与 `<文字@url=…@hover=…>`，command / console 带 PAPI 与 `;` 多条，chat 与以 `/` 开头转为指令，connect 在 `bungeecord:main` 发出 `Connect`，`delay:` 与 `{delay}`，`{chance}`，`{players}` 与 `{players=perm …}`，条件块的 actions / deny 与 priority，return 中止其余动作，set 后的条件与文字读到新值，page / open / close 之后的动作照常执行，旧写法 `message:` / `menu:` 可用，指令失败或 PAPI 取不到时停止并提示；未知动作名、未加引号的动作行、open 目标不存在时启动、`/dmenu check` 与 `/dmenu reload` 都报中文错误（文件、路径、可用写法）。未验证：真实代理下的跨服切换（沙盒没有 BungeeCord / Velocity，只核对了发出的插件消息）、真实客户端中的显示与音效。
- canvas 按钮可设置宽度（用户 2026-10-01 确认）：`Type: button` 且 Sprite 为 button（默认）、selected 或 wide-button 时，`Width` 可设为 16–960 像素，默认仍是贴图原宽（108 / 144），按钮仍须放进画布（`X + Width <= Canvas.Width`）。高度固定 2 行，没有高度选项。
  - 贴图按三段拼接：左右各 2 像素的边框原样保留，中间用 1、2、4……256 像素的中间列切片补足，每个按钮最多 12 个字形，任何宽度都不变形。紫晶与羊皮纸两种皮肤都支持。
  - `SelectedSprite: selected`（及 button / wide-button）按按钮的宽度拼出，任意宽度都能使用；wide-button 不写 Width 时也能配 selected（按 144 拼出，以前会报尺寸不同）。其他贴图之间的 SelectedSprite 仍要求尺寸相同。
  - 文字照旧在新宽度内居中、按 Width − 8 裁切；点击区域为整个 Width × 2 行，按钮重叠检查按新宽度计算。
  - close、emblem、divider、reward、panel 等其他贴图的按钮写 Width 时报错 `Width: 仅 button / selected / wide-button 贴图的按钮可设置宽度`（以前静默忽略；现有内置与 test_server 菜单中没有这样的写法）。sprite 元素的 Width 仍不拉伸内置贴图。
  - 不写 Width 或 Width 等于原宽时仍发送原来的整张贴图：内置 canvas 菜单、旧版模板、Wiki 示例菜单与 settings 页面（两种语言、两种主题）生成的 Dialog JSON 与改动前逐字节相同。
  - **资源包有变化**：`dialogmenu_dialogue:ui` 字体在原有字形之后追加 U+E016–U+E057，新增 `textures/ui/<皮肤>_button-edges.png` 与 `<皮肤>_button-fill-{1,2,4,…,256}.png` 共 20 张贴图，原有贴图和码位不变。更新插件后需让 CraftEngine 重新合并资源包（`/ce reload all` 后执行 `/ce workflow default`），玩家重新加载资源包或重进服务器；使用旧资源包时拉宽的按钮显示为缺字方块。
  - `tools/BuildTemplateSkin.java` 从按钮贴图自动切出边框与中间列并生成上述贴图、字体条目和 `template-skins.properties` 的 `slice.<皮肤>.<贴图>` 数据（每段为 码位,宽度,实测前进量）；`DialogCanvas.Skin` 新增 `slices` 分量（原 6 参数构造器保留）。
  - 文档：`docs/wiki/elements.md`（button 的 Width、选中贴图与内置贴图说明）、`docs/wiki/canvas.md`、`docs/guides/MENU-CONFIG.md`、jar 内 `配置说明.md` 与内置菜单 / 模板的注释同步更新，`docs/wiki/index.html` 重新生成。
  - 验证：新增 `ButtonWidthTest` 8 项（解析、错误提示、重叠、16–960 每个宽度的拼接与字形数、按客户端规则从资源包实测前进量后逐像素比对拼出的图像、点击区域与文字裁切、无 Width 输出不变），全部 133 项测试通过。沙盒 `server-dialogmenu-width`（Paper 26.2，模拟玩家）：含 40 / 108 / 200 / 300 / 选中 200 / wide 144 / wide 250 / 536 宽按钮的两页菜单 `check`、`reload` 通过，打开、点击、选中切换与翻页正常，Dialog 中各按钮的切片字形数与预期一致；关闭按钮写 Width 时启动、`check`、`reload` 均报上述中文错误且不改动文件。独立的原版 26.2 客户端（GUI 缩放 2、菜单模糊 5）截图中两种皮肤、普通与选中状态的 9 个按钮边框与三段拼接的参考图逐像素一致，无接缝；demo-boss 显示不变。真实客户端中按坐标点击未验证（模拟点击只按动作 ID 触发）。

## [0.2.0-paper.2-SNAPSHOT] - 2026-09-30

开发版（按 DialogMenu 发布节奏只累计源码，不建标签、不发 Release）。在 0.2.0-paper.1（sha256 `c1b4cb22…15e0`，对照副本 `keystone-sandbox/data/dialogmenu-jars/`）之上：

- 依赖 Keystone 0.3.4：`libs/keystone-0.3.4.jar`（sha256 `8656a4b5…`）替换 0.3.3，仍重定位到 `online.toraka.dialogmenu.libs.keystone`；本插件没有 `TODO(keystone)` 私有实现可删。换 jar 即生效的变化：
  - 指令报错改为中文提示：未知子指令列出可用子指令，缺参数给出用法，`/dmenu open <菜单> <页面>` 的页面不在候选里时回 `无效的 <page>：…` 并列出可选页面（最多 10 个）；错误片段 `§c§n` 高亮。控制台执行仅限玩家的子指令（`pack`、`open`、`template`）回 `这个指令只能由玩家使用。`。插件自己的帮助（`command-help-*` 语言键）不变。
  - 根指令 `/dialogmenu`（`dmenu` `settings` `playersettings` `player-settings`）被显式拒绝 `playersettings.use` 时回 `你没有权限使用这个指令。`，补全里看不到。它是玩家用的根，不加 `hideWhenDenied()`；`reload` / `check` 照旧由插件检查 `playersettings.admin`。
  - `lang/*.yml` 无法解析时备份为 `<文件名>_yyyyMMddHHmmss.bak` 并在原处写入默认。
  - 只用 Paper Dialog，没有物品栏菜单，0.3.4 的“菜单只认鼠标左右键”不涉及本插件（源码中没有 Keystone 菜单或物品栏点击处理）。
- 无法解析的菜单文件（用户 2026-09-29 决定，新增 `BrokenFiles`）：启动与 `/dmenu reload` 应用前，逐个读一遍当前配置模式用到的 `config.yml`、`menus/*.yml`、`text.yml`、`translations/*.yml`（旧模式另含 `menu.yml`、`languages/`、`templates/`）。只有 YAML 语法错误或字节不是 UTF-8 才算无法解析；能读但校验不通过的文件照旧报错、不改动文件。
  - 有 jar 默认的（`config.yml` 按 `Version: 3` / `2` 行认出 catalog / simple 默认，内置菜单、`text.yml`、内置语言的翻译文件等）：原文件保留为同目录 `<文件名>_yyyyMMddHHmmss.bak`（Keystone 读回核对过的备份），原处写入默认内容并按它加载，控制台 `错误` 行写明出错位置与备份路径，执行 reload 的玩家也收到这一行。
  - 没有 jar 默认的（服主自建的菜单、翻译、模板）：原文件不动，这次不加载（catalog 菜单、翻译与模板目录可以单独略过），`错误` 行写明文件与行列；被别的菜单引用或是 `DefaultMenu` 时，整体校验照旧失败并沿用原有的回退。
  - `/dmenu check` 只检查，从不改写文件。
  - `CatalogRepository.read`、`MenuTranslations.read`、`TemplateRepository.read/initialize` 各加一个带跳过集合的重载，原方法不变。
- 文档：`docs/wiki/troubleshooting.md` 在“配置报错”表格之后新增“文件无法解析”（含运行中改坏文件再 reload 的处理）一节，`docs/wiki/index.html` 用 `tools/build_wiki.py` 重新生成。
- 打包的 Keystone 只保留用到的类（Shadow minimize，用户 2026-09-29 决定），jar 从 2,914.6 KB 减到 2,634.2 KB（Keystone 595 → 446 个类）；ItemBridge 等 Maven 依赖不裁剪。`AGENTS.md` 写明核对方法（`tools/minimize_check.py`）。
- 运行中改坏的文件（用户 2026-10-01 的规则）：`/dmenu reload` 与启动时同样处理——有 jar 默认的换成默认、坏文件留作 `.bak`，没有的原样保留、这次不加载——不再像 0.2.0-paper.1 那样拒绝重载、保留旧菜单（paper.1 回 `配置错误，保留原菜单：config.yml: while parsing a flow sequence`）。执行重载的玩家对每个坏文件收到一行 `DialogMenu 错误：<文件> 无法解析（第 N 行第 M 列：…）；原文件已备份为 <路径>…`，控制台是同一行。`lang/*.yml` 只在启动时读取，`/dmenu reload` 不重读它（paper.1 起如此，未改）。Keystone 0.3.5 的“通知在线管理员”接口应在 `BrokenFiles.repair` 的两处错误行调用，这次不自建广播。
- 验证（2026-09-30 至 10-01，沙盒 server-dialogmenu / 25645，`--heap 1G`；paper.1 对照 jar 与瘦身后的 paper.2 跑同一组场景，日志在 `keystone-sandbox/runs/dialogmenu-034-{p1,p2}-*`）：
  - `formatSources`、`build` 通过；测试 120 → 125 项（新增 `BrokenFilesTest` 5 项），全部通过。jar 没有未重定位的 `dev/keystone/`、`kotlin/`、`taboolib/`；资源与 `plugin.yml` 只差版本号。`minimize_check` 0 problem（省 280.4 KB，去掉 149 个 Keystone 类）。
  - `api_contract_check`：部署版 0.1.23-papi.1 → paper.2、paper.1 → paper.2、不瘦身 → 瘦身均 0 problem；作为 Ambience 1.5.3 / 1.6.0-paper.2、PlaceholderAPI 的调用方 0 problem。
  - 场景：cmderr、content / content2（坏内容文件启动、再次启动）、broken、real、dialogs、dialogs2、ext、interval、fresh、legacy、langold、url、playersettings、12111（Paper 1.21.11）、reloadbroken（+ restart，用户 2026-10-01 的规则）：干净安装启动后，在运行中改坏 `config.yml`、`menus/demo-boss.yml`（有 jar 默认）、`translations/zh_cn.yml`（另存为 GBK）和服主自建的 `menus/zz-owner.yml`，paper.1 回 `配置错误，保留原菜单：config.yml: …` 并保留旧菜单；本版玩家（op）`/dmenu reload` 后前三个换成 jar 默认（逐字节相同）、各留一份 `.bak`，`zz-owner.yml` 原样不加载，玩家收到 4 行 `DialogMenu 错误：…`（文件、行列、备份路径）和 `重载成功：4 个菜单`，菜单照常打开；控制台再次 reload 结果相同（同内容的 `.bak` 不重复）；以结果目录重启，只剩 `zz-owner.yml` 一行错误。p1 与 p2 之间除上面列出的默认变化外没有差别；p2 控制台没有 `NoClassDefFoundError` / `ClassNotFoundException` / `NoSuchMethodError`。
  - 未验证：真实客户端观感（沿用 paper.1 的结论）。

## [0.2.0-paper.1-SNAPSHOT] - 2026-09-28

- 依赖 Keystone 0.3.3（2026-09-29）：共享库换成 `libs/keystone-0.3.3.jar`（仍重定位到 `online.toraka.dialogmenu.libs.keystone`），版本号不变。
  - 删除插件自带的更新检测副本 `updates/UpdateChecker.java`、`GitHubReleases.java`、`ReleaseVersion.java`，改用 Keystone 的 `UpdateChecker.start(plugin, "Usasi103/DialogMenu", 6)`；主类新增常量 `DialogMenu.UPDATE_CHECK_HOURS = 6` 与 `startUpdateChecks(plugin)`（与私有 DialogMenu 同形）。行为不变：固定每 6 小时复查，`update-check.yml` 的 `check-interval-hours` 不读取，启动延迟、开关、预发布与通知设置照旧；`docs/guides/UPDATE-CHECK.md` 同步改写常量名和代码来源。
  - `UpdateScheduleTest` 移到主包，改测 `DialogMenu.startUpdateChecks`：旧配置写 1、168 或非法值时，首次检查仍在 2060 tick（60 秒加按仓库名错开的 43 秒）后，此后每 432000 tick（6 小时）一次；关闭检测时不排任务。
  - 只用 Paper Dialog，没有 Keystone 箱子菜单，0.3.3 的“菜单默认只读”不涉及本插件。
  - 验证：120 项测试通过（与换代前相同）；JAR 不含 Kotlin、TabooLib 或未重定位的类，`plugin.yml` 与换代前相同；新 JAR 对 Paper 1.21.11 API 的静态引用检查无缺失；`api_contract_check` 对公开 0.1.23-papi.1 与对换代前的构建均为 0 问题（调用方为 test_server 中除私有 DialogMenu 外的全部插件），删去的 `updates.*` 类没有调用方。
  - 沙盒 `server-dialogmenu` 用同一组命令对比换代前的构建（`runs/dialogmenu-final-real`、`-final-dialogs`、`-final-dialogs2`、`dialogmenu-new-ext`、`dialogmenu-12111-new` 对 `runs/dialogmenu-033-real-r2`、`-dialogs-r2`、`-dialogs2-r2`、`-ext-r2`、`-12111-r2`）：真实配置、两组对话框页面（含 Ambience 1.5.3）、External 资源包回执与 Paper 1.21.11，控制台与玩家输出、对话框 JSON 除随机会话 ID 与实时延迟 `{ping}` 外逐行相同。`check-interval-hours: 1` 的旧配置下，两版的更新检测任务周期都是 432000 tick（`runs/dialogmenu-033-interval-k032-r2`、`-k033-r2`）；同服不固定周期的 Waystone 1.2.0（同为 Keystone 0.3.3）按同样的配置排成 72000 tick（`runs/dialogmenu-033-interval-control-r3`）。
- 源码由 Kotlin + TabooLib 改为纯 Java 与普通 Paper 工程，共享库 Keystone（最初 0.3.2，现为 0.3.3，见上一条）以 `online.toraka.dialogmenu.libs.keystone` 重定位打包；Kotlin 源码与旧构建脚本存档于 `plugins-dev/_refactor/kotlin-dialogmenu-0.1.23/`。包名、类名、主类静态 `getPluginInstance()`、命令 `/dialogmenu`（`dmenu`、`playersettings`、`settings`、`player-settings`）、权限 `playersettings.use` / `playersettings.admin`、PDC 键、配置与数据文件、菜单布局与点击语义保持不变。
- 仍以 Paper 1.21.11 API 编译，`api-version` 保持 `1.13`；软依赖清单（PlaceholderAPI、Ambience、LootBeam、PickupNotifier 与 ItemBridge 的 39 个物品插件）照旧，ItemBridge 1.0.32 仍重定位到 `online.toraka.dialogmenu.library.itembridge`。帮助文字继续使用 `lang/*.yml`，Keystone 负责读取、补键和逐行发送；菜单自己的 i18n/l10n 翻译系统不变。
- 资源包回执改为 Paper 连接初始化监听加 Netty 观察器 `dialogmenu_resource_pack_observer`：配置阶段（CraftEngine 等在进入游戏前发送）的回执照旧记录，游戏内回执仍走 Paper 事件；CraftEngine 处理器排在前面时另加一个观察器，不替换服务端类，不引入 packetevents。更新检测复查周期仍固定 6 小时（迁移时保留插件自带实现，换 Keystone 0.3.3 后改用 Keystone 的 `UpdateChecker`）。
- 已验证：`build` 通过，120 项 JUnit 5 测试全部通过；JAR 不含 Kotlin、TabooLib 或未重定位的类；新 JAR 与 Keystone 0.3.2 对 Paper 1.21.11 API 的静态引用检查无缺失；`api_contract_check` 为 0 问题，对 Ambience `PrefsCache` / `NoticeEngine` 的反射目标仍存在。沙盒（Paper 26.2，模拟玩家）与旧 JAR 逐项对比：真实配置、全新安装、PlayerSettings 旧配置导入、旧语言文件升级、损坏配置回退、URL 与 External 资源包回执、各菜单页面、开关、密度、语言与主题切换、搜索、任务与设置演示、旧版模板、物品页、PAPI 变量、重载刷新已打开菜单，控制台与玩家输出一致；Paper 1.21.11 沙盒中的启动、帮助、检查、重载、Tab 补全、菜单翻页与模板也与旧 JAR 一致。
- 行为差异：语言文件缺键时插入到对应位置并在控制台报告“已升级，新增 N 项”，文件顶部多出版本头；语言文件无法解析时备份原文件、本次改用内置文字（旧版显示 `{command-help-title}` 等原始键名）；与 PlayerSettings 同装时只输出拒绝启用的提示，不再附带 TabooLib 任务注册异常；Tab 补全按字母排序；参数错误提示改用 Paper 原生格式；启动时不再下载 TabooLib 运行库与 Minecraft 语言文件；JAR 增大约 0.9 MB（Keystone）。
- 未验证：真实客户端的菜单观感与配置阶段资源包回执（沙盒只用模拟连接）、真实 CraftEngine / ItemsAdder / Nexo / Oraxen 联动。

## [0.1.23-papi.1-SNAPSHOT] - 2026-09-27

- canvas 新增 `Placeholders`：在菜单根部或页面中把 PlaceholderAPI 变量声明为名称，文字写 `{名称}`，条件直接引用；Text 与 message 也可直接写 `%变量%` 及 `{ping}`、`{world}`。取值在打开、跳转、刷新时进行，点击时重新核对条件，条件不再成立只刷新界面。
- 条件支持 `=`、`!=`、`>`、`>=`、`<`、`<=` 与条件列表（同时成立）。数字比较仅用于 Placeholders；取不到的 PAPI 值令相关条件均不成立。旧的 `变量=值` 写法不变，重叠按钮可用互斥的等值或不相交数字范围证明不会同时显示。
- sprite 新增 `Image: "CE/IA:命名空间:图片"`，按 CE / IA 注册的图片 ID 显示并自动测量字宽；新增 `Cases` 按条件切换内置贴图、字体字形或图片，首条成立者生效。`/dmenu check`、`/dmenu reload` 报告不可用或超宽的图片，以及缺少 PlaceholderAPI 的菜单。
- 兼容提示：canvas 文字中原本原样显示的 `%英文数字%` 形式（如 `50%-100%` 中的 `%-100%`）现在按 PAPI 变量解析，取不到时显示未接入；`{ping}`、`{world}` 也开始替换。同名 Variables 仍优先于 `{ping}`、`{world}`。
- 命令动作仍只接受 `{player}`、`{uuid}` 与枚举变量，拒绝 Placeholders 值和 `%`，避免 PAPI 输出拼入控制台指令。Wiki 增补条件语法、Image / Cases 字段、Glyph 写法说明与可复制的 placeholders.yml 示例。

## [0.1.22] - 2026-09-26

- 将已验证的图片标签、DialogMenu 本地 i18n/l10n、翻译回退与参数功能纳入正式版；配套 Wiki 与菜单示例同步更新。
- 累计包含 0.1.21 通用兼容和资源自动导入，以及合法输入根目录联接的路径检查修复；保留原有菜单配置和内部越界保护。
- 从已验证的预发布基线构建正式版本号，功能代码与资源保持一致；保留历史预发布与附件。真实 IA 联动的验证限制单独记录。

## [0.1.22-text.1-SNAPSHOT] - 2026-09-26

- 累计测试版发布：保留 0.1.21 全部功能与后续路径修复。已收到游戏内截图，确认中文翻译、玩家名参数、显式与默认 CE 图片显示正常；客户端切换语言与真实 IA 联动仍待用户实测。

- 测试版 `0.1.22-text.1-SNAPSHOT` 在 `f3197ce` 路径修复基础上增加图片标签：默认 CE、显式 CE/CraftEngine、IA/ItemsAdder 前缀，以及 CE 图集行列。先解析再计算画布宽度，图片保持完整字形参与按钮居中、换行、裁切与弹层遮挡。
- 新增独立 `text.yml` 与 `translations/*.yml`：i18n 使用默认语言，l10n 使用玩家客户端语言，支持语言回退、嵌套与参数。翻译不依赖 CE、不写入 CE 配置；旧菜单语言偏好与行内中英映射保持兼容。
- 图片来源可选，缺失时回退并诊断；翻译检查/重载与菜单一起应用。新增测试版 Wiki 说明和可复制的多语言菜单示例。

- 修复资源自动安装拒绝合法目录联接的问题：允许 CraftEngine `resources`、ItemsAdder `contents`、Nexo `pack/external_packs`、Oraxen `pack` 输入根使用目录链接，解析并固定真实目标后再安装。输入根下的文件仍禁止通过链接或重定向改写其他目录。
- 托管文件清单记录实际输入根；链接目标改变时不继承原位置的文件所有权，避免误覆盖或删除新位置的文件。普通目录的旧清单继续支持升级，无法访问的输入根只报告一次。
- 增加 Windows junction / 非 Windows symlink 回归用例，覆盖四种输入根、重复安装、目标切换、旧清单迁移和越界保护；开发产物使用独立版本 `0.1.22-paths.1-SNAPSHOT`。

## [0.1.21] - 2026-09-25

- 修复 Paper 1.21.11 打开菜单时的 `NoSuchMethodError: ClickEvent.custom(Key)`：以 1.21.11 API 编译，使用 Adventure 4.26.1 与较新版本共用的自定义点击接口。配套资源包支持范围从仅 88 调整为 75–88，保留原有着色器和字宽。
- JAR 内置可分发菜单资源包，启动导出 ZIP；新增 Auto 模式，依次检测 CraftEngine、ItemsAdder、Nexo、Oraxen，优先将资源放入 CraftEngine。按文件哈希更新插件管理的资源，保留用户修改并报告同名资源冲突。资源包仍由所选插件构建和发送；新装默认关闭加载回执门槛，已有发送配置与门槛不变。
- 修复中英文 README 三张截图的查看入口，改用已验证的原图直链，避开 GitHub 图片页面加载错误。

- 资源包复用 Minecraft 1.21.11 / 26.2 客户端自带的 Unicode 字库，移除重复的 `dialogmenu_settings/font/unifont.zip`；保留原有字宽覆盖、字号字体和布局。字宽生成工具继续使用经过校验的构建输入，不再复制字库到发布资源；升级时删除部署目录中未被自定义修改的旧字库 ZIP。

- GitHub 更新复查周期固定为源码中的 6 小时，旧配置 `check-interval-hours` 不再覆盖；保留启动错峰、检测开关和限流退避。

- 移除开发版菜单缩放：删除缩放下拉、`/dmenu scale`、`Canvas.MaxScale`、缩放字体和字宽表，所有菜单恢复原始尺寸；不再读取旧缩放偏好，语言与主题偏好继续兼容。升级时一并移除部署配置中的缩放项和资源包 `scaled` 目录，避免遗留资源继续占用体积及客户端内存。
- 修复无底部按钮菜单的焦点白框：匹配原版空 DialogList 的底部留白、额外间距及整数居中位置，覆盖滚动与奇数宽度；仍仅作用于指定菜单画布，不影响普通对话框和输入控件。
- 将默认设置模板独立为 `demo-settings`（`Type: settings-demo`）：沿用玩家设置布局，所有开关、密度、语言和主题在单次演示内独立交互，无外部插件依赖，不写入真实偏好；重新打开重置。原服务器 `settings.yml` 与默认入口保持不变，新装默认进入演示菜单。

- 中英文 README 的游戏内效果展示采用横向三列图库，可点击查看原图；更新 NPC 对话、首领介绍和任务列表三张截图。

- 补齐默认模板与现用菜单的图标 / 物品材质注释，列出原版、IA、Nexo、Oraxen、NI 示例及 URL / External 资源包完整写法。Wiki 新增图标与材质章节，说明导航 Icon、任务字体图标、真实物品布局的区别和自定义 PNG 字体步骤，修正旧命名空间并更新离线网页；配置值保持不变。

- 整理源码文档：根目录保留中英文 README 与 CHANGELOG，使用指南、旧版资料和开发记录归入 docs；八份旧版发布说明合并为历史文档，更新相对链接并新增文档目录。

- 为 settings、对话/首领/任务 demo、旧版模板与物品示例补齐中文字段注释；区分文字截断宽度、画布换行宽度和图标占位，解释坐标、字号、加粗、动作及焦点框限制，附原版 16 色的 HEX 对照。仅补说明，不改变现有配置值。

- settings 新增 Position、LabelPosition、FontSize、Bold、Width、Color；页面标题支持 TitleStyle，分类支持 Navigation。默认仍为自动布局，大字自动预留行高，配置检查会拒绝越界、重叠点击区与不支持的字段。附中文配置说明与现用配置示例。

- settings 默认打开“玩家信息”；修复金币符号等 Unicode 文字宽度估算错误造成的同排分类偏移，准确保留半像素字宽和粗体间距。
- 资源包命名空间统一由 toraka_settings / toraka_dialogue 改为 dialogmenu_settings / dialogmenu_dialogue；字体、贴图、菜单配置和生成工具同步更新。升级需同步新资源包与自定义菜单中的字体引用。

- README 改为介绍插件用途、功能、安装与入门的中文首页，新增独立英文版，保持语言内容分离。
- 新增 `/dmenu help`；玩家与控制台均可查看，管理员显示检查/重载指令。主命令在控制台执行时展示帮助，别名同样支持。
- 帮助文字使用 TabooLib 中英文语言文件，可自定义。

## [0.1.20] - 2026-09-24

- settings 的八个二元选项改为紧凑 On/Off 开关：左侧本地化状态文字，右侧绿色开启／灰色关闭；不可用时显示独立双横线皮肤。
- toggle 新增逐项 Style: switch / button，旧配置省略样式仍保留宽按钮；高级配置对应 toggle-style。语言、主题下拉和粒子密度滑条不变。
- 状态文字和开关两侧复用原有点击动作、权限、可选插件依赖与偏好持久化。中文标签与小开关垂直居中对齐。
- 新增独立开关字体与六张暗／亮色状态贴图，不改旧字形或焦点着色器；升级需同步 0.1.20 资源包。附 SWITCHES.md 中文配置说明。

- 修复 6 号字体的 ascent 超出 height，避免客户端拒绝加载该字号。

## [0.1.19] - 2026-09-24

- 更新检测统一使用匿名 GitHub Release 请求，不读取 GitHub Token；公开仓库默认提示新版本。
- 私有/不可访问或没有 Release 的仓库静默跳过，清除旧提示缓存；旧凭据配置不再生效。
- 保留异步错峰、ETag、限流退避、管理员通知去重及 packet 预发布渠道。
- 首领 demo 的“夜巡者”改为大标题；新增 FontSize: 6–24 与 Bold: true，兼容 TextSize；中文/英文均使用独立字体与实测字宽，正文和副标题保持原字号。
- 源码仓库按要求改为公开，保留全部历史提交、标签和 Releases。

## [0.1.18] - 2026-09-24

- 新增 `/dmenu open demo-quests` 任务列表演示，每页显示 5 个任务，附带 7 个任务、分类筛选、自动分页、详情、进度条和奖励图标。
- 自动增加“已完成”分类：达到进度目标的任务（含待领取和已领取）集中显示，原命名分类只显示未完成任务，“全部”保留所有任务。
- 单文件 `Type: quest-demo` 直接配置 Tasks、Categories、PageSize 和 Layout，编译为已有 canvas 页面；无需重复编写每个任务页面。配置验证同时检查进度、分类、图标、边界与点击重叠。
- 复用玩家会话变量模拟追踪、取消追踪和领取；分类/分页保留状态，重新打开重置，不处理真实任务进度或发放奖励。
- 增加独立任务控件/图标字体，保留原有字形和焦点着色器；原版物品只引用客户端纹理，不复制 PNG。升级需合并新版资源包。
- 新装默认导出四个完整菜单，并附中文任务配置说明；已有配置文件保持不变。

## [0.1.17] - 2026-09-24

- 首领 demo 的“碎 / 印 / 装”占位文字换为紫水晶碎片、下界之星、钻石胸甲图标；新增 dialogmenu_dialogue:rewards 字体，直接引用客户端原版纹理，保留坐标、字形和 advance 配置。升级需更新资源包。
- settings 默认隐藏底部“返回游戏”按钮，保留 ESC、导航和搜索；新增按菜单 ShowFooter 开关，旧版配置对应 footer.enabled。帮助文案同步修正。
- ItemBridge 成功接入提示改为一行汇总，分别列出支持总数、当前接入数量和插件名单；相同接入结果在启动重检及重载时不重复打印，接入变化后重新报告。
- 菜单加载、物品源状态及配置检查/重载的控制台提示采用青色 DialogMenu 前缀、分类标签和分隔线；警告/错误使用红色分类，玩家消息不加控制台样式。
- 使用 Bukkit 控制台消息接口显示颜色，不修改 Paper/TabooLib 的生命周期或依赖下载日志；物品源支持范围仍为 ItemBridge 1.0.32 的全部 39 种。

## [0.1.16] - 2026-09-24

- 移除对话、首领介绍和难度确认模板底部的原生“关闭对话”按钮，保留画布右上角 ×、配置中的关闭动作及 ESC。
- 使用空的原生 Dialog 列表且不设置退出按钮，不创建隐藏的按钮或点击区域；清理不再需要的 builtin/close 路由。
- 新增一个菜单一个 YAML 的格式：settings 合并设置子页，demo-dialogue 与 demo-boss 是独立演示菜单；首领介绍/确认合并到同一文件 Pages，支持局部页名和统一 open 命令，旧配置继续兼容。
- 开放内置 ItemBridge 1.0.32 全部 39 种适配器，完整名称和既有别名可用；软依赖、启动/停用重新检测及回退显示同步覆盖全部来源。
- 全局 ResourcePack 明确配置所需包名、CraftEngine 包 ID / URL / 外部 UUID 和可选 SHA1；新增 /dmenu pack，指定包成功加载才放行，修改要求后关闭旧菜单。
- 字体贴图和焦点着色器不变，已有 0.1.15 资源包可继续使用。

## [0.1.15] - 2026-09-24

- 新增独立 templates/*.yml 与 /dialogmenu template <ID>；附带 NPC 对话、首领介绍、难度确认三份中文模板，不占原设置导航页。
- 开放元素坐标、文本区域、颜色、皮肤、字体立绘、按钮动作及条件显示；枚举变量按玩家会话隔离，跳转保留合法选择。
- 难度选择实时更新选中贴图和说明，确认默认只输出演示信息；支持权限检查、玩家/控制台指令及失败后停止后续动作。
- check/reload 同时验证原菜单和模板，错误时保留全部旧配置；检测目标缺失、越界和按钮点击重叠。
- 新增 552×180 横向画布、模板专用符号字体及 576×188 焦点轮廓选区；修复窄符号导致的整行水平漂移，保留原设置菜单和原生关闭按钮。
- 提供本地 UI_Sprite.png 切图编译器。公开附件仅含原创几何皮肤；用户已下载图集可在本机编译，原图和衍生素材不纳入 GitHub 附件。
- 补充中文模板/素材说明，以及配置校验、变量、排版和重载测试。

## [0.1.14] - 2026-09-24

- PlayerSettings 正式更名为 DialogMenu，明确插件用于配置多页 Dialog 菜单；更新源码包名、构建产物、日志和更新检查仓库。
- 主指令改为 /dialogmenu，新增 /dmenu；保留 /playersettings、/settings、/player-settings 及原有子命令。
- 新配置目录为 plugins/DialogMenu，首次安装自动导入旧 PlayerSettings 配置并保留原文件；已有新配置优先，冲突停止导入，中断可重试。
- 保留 playersettings.use/admin 权限、玩家语言/主题 PDC 键及 dialogmenu_settings 资源命名空间；菜单和资源包无需因改名重做。
- 更新中文迁移说明、配置文档和五种物品源示例；延续旧版本 GitHub 提交、标签和 Releases。

## [0.1.13] - 2026-09-24

- 使用 ItemBridge 1.0.32 统一获取 Oraxen、ItemsAdder、SX-Item、NeigeItems、CraftEngine 物品；只启用这五种集成，均为软依赖，原版物品与旧 CE 写法保持兼容。
- 内置并隔离 ItemBridge 包名，保留 MIT 许可；无需额外安装 ItemBridge 插件，也不打包五种物品插件本体。
- 支持 IA / NI / SI 等别名以及 NI/SX 的中文和区分大小写 ID；验证只查询注册表，展示时传入玩家并克隆返回物品。
- 插件启停及配置重载重新检测来源；不兼容 API 产生明确错误与安全回退，保留 CE 自动重载刷新、点击时复核和动作停用。
- 补充五种来源的配置说明、依赖缺失和 API 不兼容测试。界面资源包及现有白框隐藏规则、滑条和下拉框行为没有变动。


## [0.1.12] - 2026-09-24

- 新增 TrMenu 风格物品源：`source:CE:namespace:id` / `source:CRAFTENGINE:namespace:id`，以及原版 Material 和 `minecraft:id`；CraftEngine 保持可选软依赖。
- 新增按页原生物品布局，真实显示模型、数量及源物品悬浮提示；菜单名称/说明支持中英双语，点击名称或下方按钮执行原有权限保护的 Actions。原有七页画布布局保持兼容。
- 物品每次展示重新生成并克隆，CE 重载刷新菜单；失效时展示可选原版回退图标、停用动作，点击再次检查物品，避免使用过期来源。
- check/reload 验证启用页面的来源和 ID，无回退时拒绝错误配置并保留旧菜单；新增 `/playersettings open <页面>`、中文说明及自动导出的物品源示例。
- 明确原生物品页与字体画布的区别：原生页面保留 Minecraft 样式及焦点，不提供自由坐标，也不能混放画布开关/滑条/下拉框。


## [0.1.11] - 2026-09-23

- 新增 GitHub Release 异步更新检测，启动错峰检查，默认每 6 小时复查，支持 ETag 和限流退避。
- 发现较新版本时提示控制台和在线管理员；管理员上线后提醒，同一版本每个在线会话仅提示一次，支持 `toraka.update.notify` 权限。
- 新增 `update-check.yml` 独立配置；私有仓库通过 `TORAKA_GITHUB_TOKEN` 环境变量读取凭据，认证或网络失败不会被误报为已是最新版。
- 正确比较数字版本、预发布版本和构建元数据；不建议降级，插件卸载后停止检查和通知。

## 0.1.10

- Resolve LootBeam and PickupNotifier settings through Ambience 1.5.0 when their standalone plugins are absent. Existing menu files, commands, permissions and PDC settings remain valid.
- Retain standalone-plugin compatibility and verify the required module class before enabling a control on older Ambience versions.

## 0.1.9

- Added simple per-page configuration: `config.yml` chooses navigation order and defaults; `menus/*.yml` uses Title / Layout / Icons with automatic placement. Pages accept direct text and inline Chinese/English maps without translation-key lookups.
- Added built-in Bind connections for language, theme, particle density and existing toggles. Custom controls support inline PAPI State and Actions; button commands sit beside their labels and can execute in order with permission/plugin checks.
- Preserved existing v1 configurations when no config.yml exists. Fresh installs export the new format, and reload validates all enabled pages before replacing the active snapshot. Missing pages and invalid commands retain the current menu and identify the relevant file/field.
- Automatically place controls into the existing two panels, reject overflow, and hide covered control visuals/hit regions while a dropdown is expanded. Popup rows align across panel backgrounds. No resource-pack changes are required over 0.1.8.
- Added six tests for fresh export, reload rollback, restart preservation, the proposed simple sample, custom actions, binding validation, path/command validation and bilingual dropdown layout. Updated the Chinese guide and retained a separate legacy guide.

## 0.1.8

- Externalized the complete seven-page Dialog into commented `menu.yml`, Chinese/English language YAML files and an extracted Chinese configuration guide. Pages, navigation, text, controls, state bindings and actions can be edited without rebuilding.
- Added `/playersettings check` and `/playersettings reload` with OP-default `playersettings.admin` permission. Reload validates all files before atomically installing them, refreshes open menus, and retains the previous snapshot and operator files after an error. Duplicate keys, invalid types/references, bounds and permanent click collisions are rejected.
- Replaced both menu language and Dark/Light theme buttons with configurable Chat Flag-style dropdowns: up/down arrow, current-choice label, green selected row, one expanded list at a time, immediate selection and collapse.
- Generalized the reference slider to 2–8 named choices with configurable labels and commands; compiled all matching rail and thumb glyphs. Preserved the original Ambience density choices and real state readback.
- Added configurable page, player-command, console-command and state-based toggle actions, with optional permissions and plugin checks. Player command identity and existing PDC preference keys remain unchanged.
- Added rollback, restart-preservation, invalid-configuration, custom-page, command-template and exhaustive bilingual/dual-theme dropdown/slider layout tests. The fixed canvas geometry, CJK baselines and existing focus-outline shader remain compatible.

## 0.1.7

- Replaced the particle density dropdown with the supplied Background Opacity reference style: blue left/right arrows, a dark ticked rail, a light beveled thumb, and the current localized value.
- Track clicks select off / low / medium / high directly; arrows move one step and disable at the ends. Native Dialog text click events do not support continuous dragging. Existing Ambience commands and saved preferences remain in use.
- Compiled the supplied menu arrow sprites and Dialog tile palette into both themes. Retained the 29-line body geometry, Chinese baseline and scoped focus-outline suppression; the lower card has extra room around the slider.
- Added exhaustive track-boundary, arrow-boundary, bilingual and dual-theme layout checks. Deployment quotes the Ambience density key `off` to prevent YAML from interpreting it as boolean `false`.

## 0.1.6

- Added an Appearance page with Simplified Chinese / English menu language and Dark / Light theme choices. Changes redraw immediately and are stored separately in each player's persistent data.
- Localized all seven pages, navigation, state values, search, feedback and footer actions. Chinese remains the default; language selection applies to this menu and does not alter Minecraft or other plugins' languages.
- Added a light palette to the existing skin compiler, preserving panel geometry, glyph advances, Chinese font baselines and click targets. Selected buttons use dark text for readable contrast.
- Kept the opt-in focus-outline geometry unchanged. The palette affects the custom settings canvas; native search fields and footer buttons retain their normal Minecraft appearance and focus feedback.
- Added preference isolation, legacy-data preservation, translation, search and dual-theme layout checks. Kept particle help and player-world text inside their panels and shortened the English search button to avoid clipping.

## 0.1.5

- Translated all six settings pages, navigation, status labels, search controls, feedback and footer actions into Simplified Chinese. Chinese search terms are supported alongside the existing English aliases.
- Added an opt-in focus-outline style for the settings canvas using its reserved 474x269 body geometry. The GUI shader filters matching white edge pixels while retaining ordinary dialog, button and input focus indicators; clipping and vertical scrolling are accounted for.
- Kept the ordinary body width separate from the opt-in width. The selector recognizes geometry, not a command name or dialog identifier; other white lines with the same geometry and location can also match.
- Fixed the search dialog's pause/after-action combination, which previously prevented the search window from opening on Paper 26.2.
- Added localized search/state tests and live checks for settings focus, ordinary dialog focus, text-input focus, Chinese search submission and small-window scrolling.

## 0.1.4

- Aligned Chinese button labels with the raised ASCII baseline. Previously `button_labels` raised only the bitmap ASCII glyphs while its vanilla Unihex fallback left Chinese text four pixels lower.
- Compiled 29,183 full-width CJK glyphs from Minecraft's bundled GNU Unifont into a matching button font. Preserved the nine-pixel advances, original glyph design, and font license; indexed atlases add approximately 1 MiB of textures.
- Added mixed Chinese/English canvas fixtures and checks for the baseline and advances of 开、关、高、中、低.
- Verified the real plugin renderer with Chinese PlaceholderAPI values in a separate Minecraft 26.2 client.

## 0.1.3

- Fixed the actual Dialog wrapping regression: Minecraft's `FocusableTextWidget` subtracts four pixels of padding on each side from `plain_message.width`. The former 452-pixel body left only 444 pixels for a 450-pixel canvas.
- Increased the body to 460 pixels and retained two pixels of slack in each measured line, preventing the widget's second height calculation from wrapping right-edge glyph advances again.
- Replaced splitter-only validation with a probe of the real 26.2 `FocusableTextWidget`. The regression reproduces a 530-pixel body; the corrected body is 269 pixels tall with 29 aligned lines.
- Retained the existing skin and resource namespace; this fix does not require regenerating an already current 0.1.2 resource pack.

## 0.1.2

- Recompiled the supplied HallowPrison widget borders, selected state and icons into the settings Dialog skin.
- Replaced nine-pixel horizontal texture strips with whole panels and buttons; wide panels use only two adjacent glyphs to fit Minecraft's 256-pixel glyph atlas.
- Measured bitmap advances from actual alpha bounds and bundled a separate label font, keeping every row at the same horizontal origin.
- Aligned button labels and icons, shortened clipped navigation labels, and separated the density dropdown from its trigger.
- Added click regions for independent icon actions and retained the existing settings commands and session checks.
- Added bitmap validation, a whole-canvas regression test, and a probe using the actual Minecraft 26.2 client line splitter.
- Deployment verifies the CraftEngine hosted pack as well as the generated ZIP; the previous hosted font was stale.

## 0.1.1

- Fixed Dialog button hit routing by applying the same custom click action to the hit grid, button glyphs, and labels.
- Replaced generic block-texture navigation icons with six pixel icons matching the reference categories.

- Migrated the plugin lifecycle and command registration to TabooLib.
- Rebuilt the Dialog copy with clean English labels matching the reference layout.
- Added six navigation pages, search, player information, Ambience controls, pickup notices, and LootBeam controls.
- Kept live state redraw after every toggle and density dropdown selection.
- Added the custom glyph resource-pack assets used by the fixed coordinate canvas.

## 0.1.0

- Added the first HallowPrison-inspired player settings Dialog layout.
