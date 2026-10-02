package online.toraka.dialogmenu;

public enum MenuTheme {
    DARK("dark", 0xEEEEEE, 0xCCCCCC, 0xFFFFFF),
    LIGHT("light", 0x252525, 0x444444, 0x111111);

    private final String id;
    private final int text;
    private final int muted;
    private final int heading;

    MenuTheme(String id, int text, int muted, int heading) {
        this.id = id;
        this.text = text;
        this.muted = muted;
        this.heading = heading;
    }

    public String id() {
        return id;
    }

    public int text() {
        return text;
    }

    public int muted() {
        return muted;
    }

    public int heading() {
        return heading;
    }

    /** Light panels, controls and slider glyphs live 0x100 above their dark versions. */
    public DialogCanvas.Skin skin(DialogCanvas.Skin skin) {
        int glyph = skin.glyph();
        if (this == LIGHT
                && ((glyph >= 0xE000 && glyph <= 0xE080) || (glyph >= 0xE200 && glyph <= 0xE223))) {
            return skin.withGlyph(glyph + 0x100);
        }
        return skin;
    }

    /** Unknown or missing values fall back to the dark theme. */
    public static MenuTheme parse(String value) {
        for (MenuTheme theme : values()) {
            if (theme.id.equals(value)) {
                return theme;
            }
        }
        return DARK;
    }
}
