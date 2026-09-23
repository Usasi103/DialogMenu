# Validation Record

## 0.1.14

- Native TabooLib build, format checks and all 37 JUnit tests passed. Migration tests cover exact bytes, original-file preservation, existing-new-config precedence, conflict refusal, generated language files and interrupted-copy retry. Old player PDC keys are independently seeded/read/written.
- The final JAR identity is DialogMenu 0.1.14; package paths, embedded relocated ItemBridge, optional dependencies, resources and MIT license were checked.
- Isolated Paper 26.2 startup imported the existing PlayerSettings directory. All imported files match the originals byte for byte; eight pages loaded. /dialogmenu and /dmenu, /playersettings, /settings, /player-settings resolve to the same command; alias check and reload succeeded using the old permissions.
- A real vanilla 26.2 player verified old language/theme keys; CE 26.9.1 and NI 1.21.171 items retain metadata, custom IDs, model and fresh copies. Vanilla item amounts and missing-item fallback remain valid. Oraxen, ItemsAdder and SX-Item actual-plugin tests remain outside this run.
- Resource font/texture/shader bytes are unchanged from 0.1.13; only pack.mcmeta's visible description is renamed. No new resource generation is required on the existing server.
- test_server was stopped before deployment and remains stopped. The new JAR is installed, the data directory is renamed, all menu YAML bytes are preserved, and the Chinese guide branding is refreshed. Old JAR/config backups are outside test_server. Actual startup verification used the isolated server; production has not yet loaded this version.
- The existing private GitHub repository was renamed in place, retaining its repository ID and all eight prior Release IDs. Publication checks verify remote branch/tag, release notes and all attachment SHA-256 digests.

## 0.1.9

- Native TabooLib build and all 23 JUnit tests passed. JAR SHA-256: `63b5a5c7c9139e5d06c384556459ef9c06eecf6166c47844559a03389d95cb8d`.
- New tests cover fresh per-page export, complete multi-file reload rollback, restart preservation, the proposed Title/Layout/Icons example, builtin Bind choices, inline translations, ordered action identities, custom PAPI state, unknown fields, invalid commands/page paths and automatic layout overflow. Legacy tests still pass.
- All simple pages and expanded dropdowns retain 29 lines at 452 pixels in both languages and themes. Native Minecraft 26.2 FocusableTextWidget verification retains 474 x 269. The 326 hosted assets still match the source; the hosted pack is unchanged from 0.1.8.
- The actual JAR was exercised with real Paper 26.2, Ambience 1.4.3 and PlaceholderAPI in an isolated server/client outside test_server. Inline text reload applies; check does not change the active session; malformed appearance.yml retains the prior session/file and names the failed file. Non-OP reload is denied.
- A custom eighth page verified permission denial followed by two ordered console rewards after granting permission, close followed by a player density command, PAPI state readback, and a four-choice dropdown covering another control and crossing the panel boundary. Both language/theme dropdowns work in both palettes. Temporary custom configuration is excluded from deployment.
- Production migration backs up the exact v1 defaults outside test_server and installs config.yml plus seven pages. Existing player PDC values and the visible UTF-8 CMD launcher are retained. The final startup/console and publication results are recorded by the deployment workflow.

## 0.1.8

- Native TabooLib build and all 17 JUnit tests passed. Final JAR SHA-256: `2f561062eca059650a1a3b541591d29f408dba96e77080081d3231ef53336977`.
- Tests cover YAML rollback without overwriting user files, successful reload/restart preservation, duplicate keys, invalid types/references/commands/coordinates, permanent click collisions, custom pages, command permissions and nonrecursive placeholders. All 2–8-step slider choices, dropdown options, languages and themes retain 29 lines of 452 pixels. The actual Minecraft 26.2 FocusableTextWidget remains 474 x 269; bitmap metrics and CJK baseline checks pass.
- An isolated Paper server with the actual plugin, Ambience 1.4.3 and PlaceholderAPI exercised check without apply, atomic reload to an eighth custom page, invalid-YAML preservation of the current page/token/file, non-OP reload denial, permissioned console reward, player command and three-step slider state readback. Successful reload invalidates previous click tokens and falls back when the current page is removed.
- A separate real Minecraft client exercised both Chat Flag-style dropdowns, both languages and both themes, current-choice highlights, automatic collapse on selection, and replacing one open list with the other. The final JAR and optimized hosted assets were rechecked after restart; saved preferences persisted. Screenshots are in `design/validation-0.1.8-language.png` and `design/validation-0.1.8-theme.png`. Tests and backups remain outside test_server.
- CraftEngine initially hit its existing FastUtil cache-update race when its startup cache task overlapped the workflow. A retry after startup completed successfully, including PackSquash and hosting. All 326 settings assets match the actual optimized hosted ZIP by JSON, visible PNG pixels and alpha; generated and hosted bytes are identical. Hosted SHA-1: `1f34749a5ae5aa47523022077273c33f5afaf6f2`.
- Production deployment retains the visible interactive UTF-8 launcher and exports commented menu/language YAML plus the Chinese guide. The existing CJK font, shader geometry selector and its documented rendering-backend limits are unchanged.
- Publication preserves the private repository and historical releases; release verification checks the final main/tag commit, notes and every attachment digest.

## 0.1.7

- Native TabooLib build and all nine JUnit tests passed. JAR SHA-256: `4ec847e596d09e9ebc950bfcb3a85d182cf8f516c22f32298cfcaacd8a72c565`.
- Exhaustive rail hit regions and arrow endpoints passed for both languages, both themes, four selected tiers and an unavailable state. Bitmap advances and CJK baselines pass; the actual Minecraft 26.2 FocusableTextWidget remains 474 x 269 with 29 aligned lines.
- A separate Minecraft 26.2 OpenGL client loaded the actual optimized hosted assets and final JAR. Real Ambience 1.4.3 and PlaceholderAPI (not a simulated density provider) confirmed off, low, medium and high writes; the medium preference survived a server/client restart. Left/right stepping, rail selection and disabled endpoints were exercised.
- Fixed the deployment's existing unquoted Ambience `off` YAML key, which the runtime parsed as `false` and rejected in the off command. Quoting preserves the intended zero multiplier. The external backup retains the original file. No Ambience plugin binary or other settings were changed.
- All 88 settings assets match the hosted optimized pack by JSON structure, visible PNG pixels and alpha. Generated and hosted ZIPs are identical; hosted SHA-1: `35d8fdecffde416e2938bfc9aacc1a6f7c6849e6`.
- Final Chinese light/dark and English light screenshots show aligned slider values and the help sentence inside the lower panel. Settings outer-focus edges have zero white pixels in 520 sampled positions per image. The unchanged geometry-based shader's previously documented renderer limits still apply.
- test_server enabled 0.1.7 using the visible interactive launcher. Both console code pages are 65001; list input and Chinese input/output succeeded. Auxiliary tools, test server and backups remain outside test_server.
- Publication uses the existing private repository, main history and prior v0.1.6 release. Remote branch/tag, Release notes and attachment hashes are verified by the release workflow; see PUBLICATION.md.

## 0.1.6

- Native TabooLib build and all nine JUnit tests passed. Final JAR SHA-256: `4878b4d293cf2395ce47ec1f40fabd0a20d8e226344150d13cc9992c78eeb01d`.
- New tests cover per-player preference isolation, preserving unrelated PDC values, invalid-value defaults, bilingual labels/search and identical 29-line geometry in both themes. Bitmap checks include all light-palette glyphs and the existing Chinese baseline.
- A separate Minecraft 26.2 OpenGL client exercised all seven pages, both languages and both themes. Language switches update navigation, titles, values and the footer immediately. English search for `theme` returns Appearance; the search input keeps its focus border and caret. The light-theme density dropdown uses aligned, readable controls.
- A real restart of the disposable Paper server and client preserved `en_us` / `light` in the same player's PDC. A second restart retained the choices again. Final profile layout places the world name inside the light panel.
- CraftEngine's default workflow completed. The generated and hosted ZIPs match; all 70 settings assets were compared with source, including every visible PNG pixel and alpha value (fully transparent RGB may be optimized). Hosted SHA-1: `9b24f86fa2bd3b623508d0ad70c3cab05e983343`. Optimized font assets and GUI shaders were also loaded into the verification client.
- Clicking blank settings content preserves the hidden outer focus frame in light and dark modes. The geometry selector and shaders are unchanged from 0.1.5; its documented scope and rendering-backend limits still apply.
- `test_server` loaded and enabled PlayerSettings 0.1.6 through its visible, interactive `启动.bat` console. Console input/output use code page 65001. Only the target plugin and its CraftEngine resource namespace were updated; backups and all test tools remain outside the server.
- GitHub remains incomplete: this source directory has no Git checkout, and the noninteractive credential check found no usable GitHub credential. No remote commit, tag, Release or attachment upload is claimed.

## 0.1.5

- Native TabooLib build and all six JUnit tests passed. JAR SHA-256: `2845c874816c555245172a6a69f0f5042099fa580d6557967fe18d6bed7b7ff5`.
- The real Minecraft 26.2 text widget still produces a 269-pixel body with 29 lines at the opt-in width of 474 pixels. Bitmap metrics and the Chinese baseline validation passed.
- Live OpenGL checks used the actual plugin and a Chinese PlaceholderAPI fixture. Clicking blank settings content left no visible outer white border; an ordinary 460-pixel dialog with the same content retained its border. The final PackSquash-processed shaders were reloaded into the client and retested: 0/520 white samples on the settings edge versus 520/520 on the ordinary edge.
- The search input retained its white focus border and caret. Entering `掉落音效` and submitting selected `掉落光柱`. The former search-window pause exception was reproduced before the fix and did not recur afterward.
- Resizing to a 1000x650 client window and scrolling validated the clipped border case. The same shader preserves normal rendering when the GUI is narrower than the reserved body width.
- The selector uses geometry and position, not a command/dialog identity. Other matching white line segments can also match. Validation covers Minecraft 26.2 OpenGL; other backends/versions remain unverified.
- CraftEngine's default workflow completed successfully, and the hosted ZIP matches the generated ZIP. GUI shader minification was accounted for by testing the hosted shader bytes.
- GitHub source synchronization, tag and Release remain pending credentials.

## 0.1.4

- Native TabooLib build and four JUnit tests passed. JAR SHA-256: `d9fa3fe3264f4a5bcc737d8554fc69d11998e5ad6ec2d819bbe91e1008f6d24e`.
- The canvas fixture now includes mixed labels (`开 / Toggle`, `高 / Medium`). Bitmap validation checks that 开、关、高、中、低 retain their nine-pixel advance and share the ASCII button font's -4 pixel offset.
- The real Minecraft 26.2 text widget still reports 29 lines and a 269-pixel body; the original narrow body still reproduces the 530-pixel wrapping regression.
- An isolated server loaded the actual plugin and PlaceholderAPI, with a local expansion supplying Chinese values. The actual client screenshot in `design/validation-0.1.4.png` confirms that the Chinese text aligns with English and is centered inside the controls.
- CraftEngine's `default` workflow completed successfully. The generated and hosted ZIPs are identical. All 29 optimized CJK atlas images retain their source alpha data, including the invisible pixels that preserve full-width glyph advances. Hosted SHA-1: `146ccd1d9fc560fc7d2904d9bf3cb8f70654bc15`.
- The font source is the locally installed Minecraft 26.2 GNU Unifont 17.0.01 distribution, with its license preserved. The supported full-width ranges are U+3001–U+9FFF and U+F900–U+FAFF.
- GitHub source synchronization, tag and Release remain pending authorization.

## 0.1.3

- Native TabooLib build and all four JUnit tests passed. JAR SHA-256: `8c6cbadc782e5cc6b787ef6952f8cab60039b09c50a5ab80773a0c65dab1f3fb`.
- The actual Minecraft 26.2 `FocusableTextWidget` reproduces the old wrapping error (530 pixels tall) and validates the new 460-pixel body (269 pixels tall, 29 lines). The probe uses dimensions emitted by the compiled canvas test, including the 452-pixel measured line.
- The first live vanilla-client check confirmed that the emitted canvas renders with aligned complete panels and labels, without the erroneous scrollbar. An isolated loopback server and separate client directory were used; the user's client installation was not modified.
- A second live check loaded the actual release JAR and invoked `SettingsDialog.show` for the isolated player. Real custom clicks opened the density dropdown (`density`) and switched to Help (`tab_help`). The resulting in-game screenshots show aligned panels, labels and controls with no unwanted wrapping. `design/validation-0.1.3.png` records the real plugin renderer. The isolated server omits Ambience/PAPI, so the screenshot's setting values intentionally display `N/A`.
- The existing CraftEngine hosted pack's font JSON and bitmap alpha bounds match the source. The user's subsequent CraftEngine workflow completed successfully; this code-only fix preserves that pack.
- GitHub source synchronization, tag and Release remain pending because credentials and the local Git history are unavailable.

## 0.1.2

- Built with the workspace `build_selfdev.py` workflow on JDK 25.0.4.1; native TabooLib dependency verification and all four JUnit tests passed.
- JAR SHA-256: `0a7b52975e9c224a7077c81b83697c292f146b36e8ad98b4f221e3c7ec231fa1`.
- The real Minecraft 26.2 `StringSplitter` processed the emitted component into exactly 29 rows, each 450 pixels wide, without automatic wrapping or horizontal drift.
- `tools/validation/check_skin.py` independently checked bitmap alpha bounds against every compiled glyph advance, font ascent limits, and glyph atlas dimensions. It rendered an offline preview from the emitted component.
- Test server logged `Enabling PlayerSettings v0.1.2` before the final icon selection; the final bitmap metrics and JAR were rebuilt and revalidated afterward. CraftEngine's generated and hosted ZIPs are checked separately in the workspace deployment report.
- CraftEngine's default workflow failed in `updateCachedAssets` with a FastUtil `ArrayIndexOutOfBoundsException`. The settings namespace was patched into both existing ZIPs, preserving the contents of all 23,706 unrelated entries. The server was stopped cleanly. CraftEngine's self-host startup reads the stored ZIP and derives its SHA-1 and pack UUID from its bytes; no stale hash setting was retained. The unrelated cache-concurrency issue remains unresolved.
- Before deployment, the generated ZIP had SHA-1 `abe5c3bce3dddea18349a3c45186cccd266c0610`; the hosted ZIP had SHA-1 `499abadcd7b015b5a0b072f32eafa6fa5f9522ae` and an older settings font. This explains why earlier resource changes were not consistently delivered.
- GitHub publication is pending: this machine has neither the repository's `.git` history nor usable GitHub credentials. No remote commit, tag, or Release is claimed.

## 0.1.1

- Built with `tools/build_selfdev.py --projects PlayerSettings --jdk-home D:\\Java\\jdk-25.0.4.1`.
- TabooLib native dependency verification passed.
- Three `DialogCanvasTest` tests passed: click regions, dropdown overlap, and text clipping.
- The DialogCanvas unit suite covers click regions, dropdown overlap, and text clipping; the previous independent Paper smoke probe also covered the six-page codec and redraw flow before the TabooLib lifecycle migration.
- The deployed JAR SHA-256 is `8786da13d6d8148ab0afeef1b420beccf489fc3fc393b23ac5dd5f437d1c1f8a`.
- CraftEngine workflow `default` completed successfully after the generated `.ce-packsquash` cache was cleared. The generated ZIP contains `assets/toraka_settings/font/ui.json` and all eight custom UI textures.
- The final test server starts with `PlayerSettings v0.1.1` and `enable-rcon=false`.

## Client verification

The server-side Dialog codec, click routing, resource-pack contents, and live redraw paths are validated. A final pixel comparison still requires an actual Minecraft client receiving the generated pack; GUI scale and the client font renderer can change the visual result.


## 0.1.12 item sources (2026-09-24)

- All 28 JUnit tests passed with the workspace formatter/build checks. Five added tests cover aliases/namespaced IDs, strict page parsing and action identity, invalid fields, isolated fresh display copies, and unavailable-source validation/rollback.
- Actual Paper 26.2 build 123 and CraftEngine 26.9.1: rainbow-fish display retains CE ID, item metadata and model; mutation of a displayed copy does not affect subsequent items; vanilla amount and barrier fallback verified.
- Vanilla 26.2 client: rainbow fish, source tooltip, three diamonds, disabled fallback button, successful button navigation to the original canvas, and eighth-page navigation visually checked.
- CE configuration reload changed an already-open item's tooltip without reopening the menu. Invalid missing ID without fallback rejected reload, then restoring fallback produced a warning and a successful reload/check.
- Started the final JAR without CE: optional event binding caused no class-load failure, vanilla items worked, CE fallback remained visible with no actionable route.
- Test server/client/config/cache/logs stayed outside production. The temporary client uses a focused subset of the existing hosted pack: full pack exceeded its 2 GiB heap. CE fixture required its normal image/offset configuration before reload could complete; adding that fixture configuration resolved the test harness failure.
- Limitations: models are native item bodies, actions use captions/buttons; canvas theme/focus suppression/free positioning do not apply. The pack still must be loaded before the public open command. Only CE and vanilla providers are implemented.


## 0.1.13 ItemBridge (2026-09-24)

- Built through the workspace formatter and native TabooLib packaging; 31 JUnit tests passed. Verified ItemBridge classes are actually bundled under the relocated package, with MIT license and all five plugin soft dependencies in the final JAR.
- Added alias/whitelist tests, preserved ID case/Chinese, registry-only validation, forwarding the viewing player with empty context, missing-plugin isolation and API linkage failure fallback.
- Paper 26.2 build 123 + ItemBridge 1.0.32 + CraftEngine 26.9.1 + NeigeItems 1.21.171: actual CE ID/model/metadata and fresh copies passed; NI Chinese ID, diamond-sword type, name and Lore passed; native client rendered both item sources.
- Reload with an invalid ID and no fallback preserved the old menu; fallback recovery and check passed. CE reload reconnected both providers. Separate startup without any of the five plugins retained vanilla item display and disabled custom-source fallbacks.
- Oraxen, ItemsAdder and SX-Item were not available for full-plugin live validation. Their source adapters are supplied by ItemBridge 1.0.32; PlayerSettings tests validate the provider boundary for all five integrations. Provider/plugin API incompatibility is reported and disables the affected action.
- No menu skin, font, shader, slider or dropdown assets changed. Native item menus retain their prior layout and focus behavior.
