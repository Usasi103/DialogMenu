package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record MenuDefinition(
        String title,
        String defaultPage,
        MenuLanguage language,
        MenuTheme theme,
        boolean hideFocus,
        int navX,
        int navRow,
        int navStep,
        String footerLabel,
        String footerAction,
        Map<String, String> states,
        Map<String, MenuAction> actions,
        List<MenuWidget> common,
        Map<String, MenuPage> pages,
        Map<MenuLanguage, Map<String, String>> translations,
        boolean showFooter,
        int navTextSize,
        boolean navBold,
        boolean demo) {

    /** Kotlin defaults: no footer, 8 px regular navigation, not a demo. */
    public MenuDefinition(
            String title,
            String defaultPage,
            MenuLanguage language,
            MenuTheme theme,
            boolean hideFocus,
            int navX,
            int navRow,
            int navStep,
            String footerLabel,
            String footerAction,
            Map<String, String> states,
            Map<String, MenuAction> actions,
            List<MenuWidget> common,
            Map<String, MenuPage> pages,
            Map<MenuLanguage, Map<String, String>> translations) {
        this(
                title,
                defaultPage,
                language,
                theme,
                hideFocus,
                navX,
                navRow,
                navStep,
                footerLabel,
                footerAction,
                states,
                actions,
                common,
                pages,
                translations,
                false,
                8,
                false,
                false);
    }

    /** A {@code $key} value reads the language file; any other text is literal. */
    public String text(MenuLanguage language, String value) {
        if (value.startsWith("$")) {
            return Kt.getValue(Kt.getValue(translations, language), Kt.drop(value, 1));
        }
        return value;
    }

    /** Longer matches win: "掉落音效" must resolve to loot, not the general sound page. */
    public String search(String query) {
        if (Kt.isBlank(query)) {
            return null;
        }
        String bestPage = null;
        int bestLength = -1;
        for (MenuPage page : pages.values()) {
            List<String> terms = new ArrayList<>(page.keywords());
            for (MenuLanguage language : MenuLanguage.values()) {
                terms.add(text(language, page.label()));
            }
            Integer longest = null;
            for (String term : terms) {
                if (Kt.containsIgnoreCase(query, term)) {
                    if (longest == null || term.length() > longest) {
                        longest = term.length();
                    }
                }
            }
            if (longest != null && (bestPage == null || longest > bestLength)) {
                bestPage = page.id();
                bestLength = longest;
            }
        }
        return bestPage;
    }

    /** {@code copy(demo = demo)}. */
    public MenuDefinition withDemo(boolean value) {
        return new MenuDefinition(
                title,
                defaultPage,
                language,
                theme,
                hideFocus,
                navX,
                navRow,
                navStep,
                footerLabel,
                footerAction,
                states,
                actions,
                common,
                pages,
                translations,
                showFooter,
                navTextSize,
                navBold,
                value);
    }

    /** {@code copy(pages = pages)}. */
    public MenuDefinition withPages(Map<String, MenuPage> replacement) {
        return new MenuDefinition(
                title,
                defaultPage,
                language,
                theme,
                hideFocus,
                navX,
                navRow,
                navStep,
                footerLabel,
                footerAction,
                states,
                actions,
                common,
                replacement,
                translations,
                showFooter,
                navTextSize,
                navBold,
                demo);
    }

    /** {@code copy(pages = pages, actions = actions)}. */
    public MenuDefinition withPagesAndActions(
            Map<String, MenuPage> replacementPages, Map<String, MenuAction> replacementActions) {
        return new MenuDefinition(
                title,
                defaultPage,
                language,
                theme,
                hideFocus,
                navX,
                navRow,
                navStep,
                footerLabel,
                footerAction,
                states,
                replacementActions,
                common,
                replacementPages,
                translations,
                showFooter,
                navTextSize,
                navBold,
                demo);
    }
}
