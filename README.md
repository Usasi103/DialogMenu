# DialogMenu

0.1.16 uses one file per menu: `menus/settings.yml`, `menus/demo-dialogue.yml`, `menus/demo-boss.yml`. Each contains its own `Pages`; boss introduction and confirmation are two pages of the same demo menu. Use `/dmenu open <menu> [page]`. Chinese configuration guide: [MENU-CONFIG.md](MENU-CONFIG.md). Legacy formats remain supported.

The native bottom close button is removed from canvas menus; use × or ESC. Resource assets are unchanged from 0.1.15. `config.yml.ResourcePack` selects a CraftEngine pack ID, a downloadable URL, or an externally sent UUID; only the required pack loading successfully unlocks menus. `/dmenu pack` sends a configured URL pack or identifies its external sender. All 39 ItemBridge 1.0.32 plugin adapters are now supported: [ITEM-SOURCES.md](ITEM-SOURCES.md).

Paper 26.2 configurable Dialog menus with YAML pages, optional item sources, Simplified Chinese / English, Dark / Light themes, and the supplied HallowPrison widget skin. The bundled player settings menu is one example. Whole bitmap panels, measured glyph advances, fixed coordinates, and Paper custom-click events keep the artwork and controls aligned after each action.

Use `/dialogmenu` or `/dmenu`. Existing `/playersettings`, `/settings` and `/player-settings` commands remain aliases. Permission: `playersettings.use` (allowed by default); `playersettings.admin` controls check/reload.

Version 0.1.14 renames PlayerSettings to DialogMenu. Remove the old JAR before installing DialogMenu; do not run both. On startup, if the new directory has no config.yml/menu.yml, the plugin imports the old plugins/PlayerSettings directory into plugins/DialogMenu and preserves the originals. Existing new configuration takes precedence. Conflicting files stop migration instead of overwriting edits. See [MIGRATION.md](MIGRATION.md). Existing player preferences and the toraka_settings resource namespace remain compatible.

Open **界面与语言 / Appearance** in the left navigation to choose a menu language and theme from dropdowns styled after the supplied Chat Flag reference. Click a value or arrow to expand, select the green-highlighted current item or another choice, and the menu applies the choice and collapses. Only one dropdown opens at a time. New players default to Simplified Chinese and the dark theme. Preferences survive reconnects and restarts under the existing PDC keys `playersettings:menu_language` and `playersettings:menu_theme`. This changes this player menu; Minecraft and other plugins keep their own language settings.

Version 2 used one file per page. Version 3 keeps the default menu in `config.yml` and one complete menu per file under `menus/`, with pages nested under `Pages`. `Title`, `Layout` and `Icons` organize each page: rearrange names in `Layout`, write labels beside controls, and put click actions directly in `Actions`. Coordinates, state IDs and separate action definitions are handled internally. Chinese comments and the exported `配置说明.md` include ready-to-use examples; see [the configuration guide](MENU-CONFIG.md).

Run `/dialogmenu check` to validate without applying, then `/dialogmenu reload` to apply the main configuration and every enabled page together. Both require `playersettings.admin` (OP by default); the `/settings` aliases also work. Invalid files retain the previous valid snapshot and operator files. Existing v1 `menu.yml` installations remain supported when `config.yml` is absent; see [legacy configuration](LEGACY-CONFIG.md). New installations export the consolidated format. Existing files are never overwritten on startup, and missing referenced pages are reported rather than silently recreated.

`Name` and `Description` accept plain text or an inline `{zh_cn: ..., en_us: ...}` translation map. `Bind: language`, `theme`, `particle-density` and the documented toggle bindings connect existing settings without extra commands. Custom integrations use inline `State` and per-option `Actions`. Ordered player/console commands retain their identity and optional permission/plugin checks; `close` can precede commands, while navigation/search/refresh can end a sequence. Commands accept `{player}` and `{uuid}`; a failed command stops later actions without undoing commands already executed. Controls flow automatically through the two panels; `heading` starts the lower panel, and overflow fails validation. Popups suppress covered controls until collapsed.

Both palettes use the same geometry and font metrics. `tools/BuildSkin.java` compiles the palettes and configurable slider variants into private-use glyphs, so switching themes needs no resource-pack reload. Version 0.1.9 uses the same resource assets as 0.1.8; deploy its matching pack if upgrading from an earlier version.

The server resource pack must contain `toraka_settings:ui`. Copy `resourcepack/assets/toraka_settings` and the two owned `assets/minecraft/shaders/core/gui.*` files into the CraftEngine resource source, then run `/ce workflow default` to generate and send the pack. Existing GUI shaders require a compatibility merge rather than an overwrite.

The screen includes player information, Ambience sound and particle controls, category toggles, horizontal density slider, pickup notices, LootBeam settings, search, and navigation back to the main menu. Existing TrMenu menus remain available as `/settings-classic` and `/player-settings-classic`.

The source artwork used for this requested adaptation is recorded in `design/hallow-source` with its original license. It is not an import of the full HallowPrison pack or its shaders. The font namespace is isolated from other server fonts.

To rebuild the skin from the project root, run `java tools/BuildSkin.java .`, then use the workspace's `tools/format_selfdev.py` and `tools/build_selfdev.py` for formatting, tests and the native TabooLib JAR. The generator updates both bitmap fonts and `src/main/resources/ui-metrics.properties`; deploy those matching outputs together. `resourcepack/pack.mcmeta` targets the 26.2 resource format (88).

After `/ce workflow default`, verify `plugins/CraftEngine/cache/hosted/default/resource_pack.zip`, not only `generated/resource_pack.zip`. Both must contain the new whole-glyph providers (`panel_top` height 81, `panel_bottom` height 126), `labels.json`, and `button_labels.json`. Rejoin and load the offered pack before testing `/settings`.

Dialog width includes the vanilla widget's four-pixel padding on each side. The 450-pixel artwork occupies a 452-pixel measured line inside a 460-pixel body. Preserve that line-end slack: the widget recalculates height at the measured text width. `ClientLayoutProbe.java` consumes the JUnit fixture's `layout.properties` and checks the actual client widget, including both wrapping passes.

The settings screen opts into `FRAMELESS_BODY_WIDTH` (474 pixels) while ordinary dialogs retain their normal widths. The two `assets/minecraft/shaders/core/gui.*` files suppress white edge pixels matching the reserved settings-body geometry and native Dialog placement. This preserves normal dialogs and the search input's focus border, including their keyboard feedback. Do not overwrite another pack's GUI shaders without merging and retesting them.

This is a geometry selector: a resource-pack shader cannot read `/settings` or a dialog identifier. Other white lines at the same selected coordinates and dimensions can also match. The opt-in layout is 29 lines / 269 pixels tall; keep the shader constants synchronized if its layout changes. Suppression is disabled when the GUI is narrower than the reserved width, where the fixed menu itself is clipped. Verified on Minecraft 26.2 OpenGL; other rendering backends and game versions require separate validation.

Button labels raise both ASCII and full-width CJK text by four pixels. `button_cjk` uses the matching vanilla GNU Unifont glyphs, with padded bitmap cells and a preserved nine-pixel advance. `design/minecraft-font` contains the input font and its license; the runtime pack also retains that license. Rebuild and deliver the matching font assets when changing these labels.

The density slider follows the supplied Background Opacity reference: blue arrows, seven tick marks and a pale rectangular thumb. The current value appears before the left arrow. Click the rail to choose off / low / medium / high, or use the arrows to move one step; end arrows disable. This custom Dialog text canvas supports click steps, not continuous dragging. In Ambience YAML, quote the key `'off'` under `particles.density`; an unquoted key can be parsed as `false` and make the off command unavailable.

## 0.1.12 — 物品源

支持 `Display.Material: "source:CE:命名空间:物品ID"`（`CRAFTENGINE` 同义）和 `minecraft:diamond` 原版物品。CE 为软依赖，通过其公开 API 构造真实物品，保留模型和物品提示。中文配置与边界见 [ITEM-SOURCES.md](ITEM-SOURCES.md)，完整样例在 `src/main/resources/examples/items.yml`，插件会导出到 `examples/items.yml`。

带 Material 的页面自动使用原生物品布局，名称/下方按钮处理操作；既有字体画布页面继续保留。原生页面不使用画布皮肤、主题和隐藏焦点功能，不支持自由坐标。使用 `/dialogmenu open <页面ID>` 打开指定已启用页面，`check` / `reload` 同时验证物品源；无有效物品时停用动作。


0.1.16 使用内置 ItemBridge 1.0.32 的全部 39 种自定义物品适配器，并保留原版物品。已有 CE 配置无需修改。详见 [物品源配置](ITEM-SOURCES.md)。
