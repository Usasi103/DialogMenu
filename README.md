# PlayerSettings

Paper 26.2 player settings Dialog with Simplified Chinese / English, Dark / Light themes, and the supplied HallowPrison widget skin. Whole bitmap panels, measured glyph advances, fixed coordinates, and Paper custom-click events keep the artwork and controls aligned after each action.

Use `/playersettings`, `/settings`, or `/player-settings`. Permission: `playersettings.use` (allowed by default).

Open **界面与语言 / Appearance** in the left navigation to choose a menu language and theme from dropdowns styled after the supplied Chat Flag reference. Click a value or arrow to expand, select the green-highlighted current item or another choice, and the menu applies the choice and collapses. Only one dropdown opens at a time. New players default to Simplified Chinese and the dark theme. Preferences survive reconnects and restarts under the existing PDC keys `playersettings:menu_language` and `playersettings:menu_theme`. This changes this player menu; Minecraft and other plugins keep their own language settings.

The complete menu is configured in `plugins/PlayerSettings/menu.yml`, with text in `languages/zh_cn.yml` and `languages/en_us.yml`. Chinese comments and an extracted `配置说明.md` explain pages, coordinates, buttons, toggles, 2–8-step sliders, dropdowns, state bindings and actions. Existing files are never overwritten on startup. See [the Chinese configuration guide](src/main/resources/配置说明.md).

Run `/playersettings check` to validate without applying, then `/playersettings reload` to apply all three YAML files together and refresh open menus. Both require `playersettings.admin` (OP by default); the `/settings` aliases also work. Invalid syntax, references, duplicate keys, overlapping permanent click regions and out-of-bounds widgets preserve the previous valid snapshot. The initial fallback uses bundled defaults while retaining invalid files for repair. Defaults apply only when a player has no valid saved choice.

Actions support existing builtins, page navigation, player commands, permission-gated console commands and state-based toggle commands. Command templates accept `{player}` and `{uuid}`; commands keep their configured execution identity. Optional integrations show unavailable when missing. Adding pages or rearranging existing widgets requires only YAML reload; new artwork or geometry still requires development and a matching resource pack.

Both palettes use the same geometry and font metrics. `tools/BuildSkin.java` compiles the palettes and configurable slider variants into private-use glyphs, so switching themes needs no resource-pack reload. Deploy the 0.1.8 JAR with its matching resource pack.

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
