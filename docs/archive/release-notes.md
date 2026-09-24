# 历史版本发布说明

汇总原先散落在仓库根目录的 0.1.2–0.1.9 发布说明，保留原文。其中的验证结果和待办反映当时状态；当前版本请查看[更新日志](../../CHANGELOG.md)与[发布记录](../development/PUBLICATION.md)。

## 0.1.2

PlayerSettings 0.1.2 replaces the horizontally sliced settings UI with whole bitmap panels and buttons using the supplied HallowPrison skin. Font advances are measured from actual pixels, and a dedicated menu font prevents row alignment from depending on the player's default font.

The six settings pages, search, personal toggles, dropdown, and existing command integration remain available. Button labels and icons are aligned, dropdown options no longer overlap the trigger, and independent icon actions receive explicit hit regions.

Validation: four JUnit tests; native TabooLib build checks; independent bitmap metrics validation; actual Minecraft 26.2 line splitting (29 rows, 450 pixels each); test-server plugin startup. The final visual appearance still needs an in-game check with the newly delivered pack.

Deploy the matching JAR and resource pack together. Rebuild and upload with `/ce workflow default`, then verify the hosted ZIP contains the new font providers. This deployment found that the previously hosted ZIP still contained an older settings font.

On this machine, CraftEngine's workflow hit an existing cache-concurrency exception. The settings namespace was installed into both existing ZIPs directly, with byte-for-byte verification that unrelated entries were preserved; the server then stopped cleanly. The upstream workflow issue remains unresolved. GitHub source synchronization, tag and Release are pending because this machine has no usable GitHub credentials.

## 0.1.3

PlayerSettings 0.1.3 fixes the menu fragmentation and displaced controls shown in the client screenshot. The dialog's declared width previously ignored eight pixels of internal padding, causing automatic wrapping; the widget also performs a second wrap when calculating its height.

The body now measures 460 pixels, with a 452-pixel line containing the 450-pixel artwork. The existing Hallow-based skin and settings integrations are retained. A current 0.1.2 server resource pack remains compatible.

Validation: four JUnit tests, the native TabooLib build, bitmap metrics checks, the real Minecraft 26.2 Dialog text widget (old height 530 pixels, corrected height 269 pixels), and live rendering in an isolated vanilla client.

GitHub source synchronization, tag and Release remain pending due to unavailable credentials and local Git history.

## 0.1.4

PlayerSettings 0.1.4 corrects the lower Chinese baseline in mixed button labels such as “开 / Toggle”. Chinese labels now use the same four-pixel upward offset as English, with vanilla glyph shapes and nine-pixel advances preserved.

The resource pack adds an indexed bitmap font for 29,183 full-width CJK characters (approximately 1 MiB of textures). The original GNU Unifont license is included.

Validation includes four JUnit tests with mixed labels, actual 26.2 Dialog widget layout, bitmap metrics and baseline checks, and an isolated live client running the real plugin with Chinese PlaceholderAPI values.

Deliver the new resource pack to apply the font fix. GitHub source synchronization, tag and Release remain pending until GitHub credentials are available.

## 0.1.5

PlayerSettings 0.1.5 provides a Simplified Chinese settings interface, including navigation, page descriptions, status labels, search and footer controls. Chinese keywords work alongside the existing English search aliases.

The settings canvas uses a reserved layout that opts into focus-outline suppression. The resource-pack GUI shader filters matching white edge pixels, while ordinary Dialog bodies and native input/button focus indicators remain visible. The selector is based on geometry and position, not the command name; other matching line geometry can also be affected. Narrower-than-layout GUIs retain vanilla rendering. Validation targets Minecraft 26.2 OpenGL.

The search dialog now explicitly disables pause when using `after_action: none`, fixing its previous opening error. Validation includes six JUnit tests, the real client text widget, bitmap metrics, and live settings/ordinary-dialog/input-focus comparisons with Chinese search submission.

Deploy the matching JAR and resource pack together and deliver the regenerated pack. The GUI shaders require a compatibility merge if another pack replaces the same files.

GitHub source synchronization, tag and Release remain pending usable GitHub credentials.

## 0.1.6

PlayerSettings 0.1.6 adds personal menu language and theme choices under Appearance (界面与语言). Players can select Simplified Chinese or English and a dark or light palette; changes redraw immediately and persist in their player data across reconnects and server restarts. New players retain the Chinese dark defaults.

Translations cover all seven pages, status values, search and footer controls. Language and theme are scoped to this player menu. Native Minecraft input and footer styling, client language and third-party plugin messages are not changed.

The light palette shares the existing panel dimensions, glyph advances and hit regions. The Chinese baseline fix and scoped outer-focus-outline suppression remain in place. The matching resource pack is required; generate and deliver it with the existing CraftEngine workflow.

Validation: native TabooLib build, nine JUnit tests, bitmap font metrics and live Minecraft 26.2 OpenGL interactions. GitHub source synchronization, tag and Release remain pending a repository checkout and usable write credentials.

## 0.1.7

PlayerSettings 0.1.7 replaces the particle density dropdown with the HallowPrison Background Opacity reference style: blue arrow buttons, a dark ticked rail and a light rectangular thumb. The localized current value appears beside the control.

Click a rail position for off / low / medium / high, or use the arrows to move one step. End arrows disable. This uses the existing Ambience commands and saved player settings, with both menu languages and themes. Continuous dragging is not supported by the custom Dialog text canvas.

Deploy the JAR and matching resource pack together. Quote the Ambience YAML key `'off'` under `particles.density`, because unquoted `off` may be parsed as boolean `false` and reject the off command. The test_server deployment includes this configuration correction, with an external backup.

Validation: native TabooLib build, nine JUnit tests, actual Minecraft 26.2 text-widget geometry and OpenGL client interaction checks. See VALIDATION.md for the completed verification record.

## 0.1.8

The player menu can now be maintained through external YAML. Startup exports `menu.yml`, `languages/zh_cn.yml`, `languages/en_us.yml` and a Chinese `配置说明.md` guide without replacing existing files.

Both **Menu language** and **Menu theme / Dark / Light** use dropdowns like the supplied Chat Flag example. Click to expand, see the current choice highlighted in green, then select to apply and collapse. Saved preferences remain compatible with earlier versions.

Use `/playersettings check` before `/playersettings reload`; both require `playersettings.admin` (OP by default). Invalid configuration preserves the previous valid menu. Pages, labels, coordinates, actions, toggles, dropdowns and 2–8-step sliders are configurable. Slider interactions remain click/arrow steps, without continuous dragging.

Deploy the JAR with the matching resource pack, run the CraftEngine workflow and load the offered pack. The menu retains its Chinese font alignment and 29-line geometry. Focus-outline suppression retains the existing geometry-based OpenGL scope. See [VALIDATION.md](../development/VALIDATION.md) for checks and `src/main/resources/配置说明.md` for examples.

## 0.1.9

Menu configuration now uses `config.yml` plus one file per page in `menus/`, organized as Title / Layout / Icons. Reorder Layout entries, write names and descriptions beside each control, and configure Actions in the same place. Common layouts and built-in setting connections no longer require pixels, separate states or action IDs.

Language and theme dropdowns and the density slider use Bind; direct Chinese text or inline Chinese/English maps are supported. Existing personal preferences remain unchanged. New installs export the simple files and Chinese guide; existing v1 configurations continue to load when config.yml is absent.

Use `/playersettings check` and `/playersettings reload` with `playersettings.admin` (OP default). Invalid configuration preserves the previous menu. The fixed two-panel layout rejects overflow; sliders use clicks/arrows rather than continuous dragging. Ordered commands stop on failure but do not roll back earlier commands.

The resource pack is unchanged from 0.1.8. See SIMPLE-CONFIG.md for editing, LEGACY-CONFIG.md for old files and VALIDATION.md for test coverage.
