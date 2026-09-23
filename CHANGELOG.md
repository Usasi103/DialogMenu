# Changelog

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
