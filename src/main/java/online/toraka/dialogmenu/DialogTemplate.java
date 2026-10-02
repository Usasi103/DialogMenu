package online.toraka.dialogmenu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DialogTemplate(
        String id,
        String title,
        int width,
        int rows,
        boolean hideFocus,
        DialogCanvas.Skin background,
        Map<String, List<String>> variables,
        List<TemplateElement> elements,
        Map<String, String> placeholders) {

    /** Kotlin default: no PlaceholderAPI values. */
    public DialogTemplate(
            String id,
            String title,
            int width,
            int rows,
            boolean hideFocus,
            DialogCanvas.Skin background,
            Map<String, List<String>> variables,
            List<TemplateElement> elements) {
        this(
                id,
                title,
                width,
                rows,
                hideFocus,
                background,
                variables,
                elements,
                Collections.emptyMap());
    }

    /** Each variable keeps a still-valid previous value, else its first (default) option. */
    public Map<String, String> values(Map<String, String> previous) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : variables.entrySet()) {
            String value = previous.get(entry.getKey());
            List<String> options = entry.getValue();
            result.put(
                    entry.getKey(),
                    value != null && options.contains(value) ? value : Kt.first(options));
        }
        return result;
    }
}
