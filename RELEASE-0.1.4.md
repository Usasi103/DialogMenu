PlayerSettings 0.1.4 corrects the lower Chinese baseline in mixed button labels such as “开 / Toggle”. Chinese labels now use the same four-pixel upward offset as English, with vanilla glyph shapes and nine-pixel advances preserved.

The resource pack adds an indexed bitmap font for 29,183 full-width CJK characters (approximately 1 MiB of textures). The original GNU Unifont license is included.

Validation includes four JUnit tests with mixed labels, actual 26.2 Dialog widget layout, bitmap metrics and baseline checks, and an isolated live client running the real plugin with Chinese PlaceholderAPI values.

Deliver the new resource pack to apply the font fix. GitHub source synchronization, tag and Release remain pending until GitHub credentials are available.
