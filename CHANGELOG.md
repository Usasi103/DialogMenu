# Changelog

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
- 保留 playersettings.use/admin 权限、玩家语言/主题 PDC 键及 toraka_settings 资源命名空间；菜单和资源包无需因改名重做。
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
