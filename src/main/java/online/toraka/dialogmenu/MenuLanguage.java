package online.toraka.dialogmenu;

public enum MenuLanguage {
    CHINESE("zh_cn", "简体中文"),
    ENGLISH("en_us", "English");

    private final String id;
    private final String label;

    MenuLanguage(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Unknown or missing values fall back to Chinese. */
    public static MenuLanguage parse(String value) {
        for (MenuLanguage language : values()) {
            if (language.id.equals(value)) {
                return language;
            }
        }
        return CHINESE;
    }
}
