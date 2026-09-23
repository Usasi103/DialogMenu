# PlayerSettings 0.1.9

Menu configuration now uses `config.yml` plus one file per page in `menus/`, organized as Title / Layout / Icons. Reorder Layout entries, write names and descriptions beside each control, and configure Actions in the same place. Common layouts and built-in setting connections no longer require pixels, separate states or action IDs.

Language and theme dropdowns and the density slider use Bind; direct Chinese text or inline Chinese/English maps are supported. Existing personal preferences remain unchanged. New installs export the simple files and Chinese guide; existing v1 configurations continue to load when config.yml is absent.

Use `/playersettings check` and `/playersettings reload` with `playersettings.admin` (OP default). Invalid configuration preserves the previous menu. The fixed two-panel layout rejects overflow; sliders use clicks/arrows rather than continuous dragging. Ordered commands stop on failure but do not roll back earlier commands.

The resource pack is unchanged from 0.1.8. See SIMPLE-CONFIG.md for editing, LEGACY-CONFIG.md for old files and VALIDATION.md for test coverage.
