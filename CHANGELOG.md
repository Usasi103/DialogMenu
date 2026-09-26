# Changelog

## 未发布

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
