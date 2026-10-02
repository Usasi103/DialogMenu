package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;

public final class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{([a-zA-Z0-9_-]+)}");
    private static final Pattern DISPLAY =
            Pattern.compile("%[a-zA-Z0-9_:.\\-]+%|\\{([a-zA-Z0-9_-]+)}");

    private TemplateRenderer() {}

    public static boolean visible(TemplateElement element, Map<String, String> values) {
        return element.condition() == null || element.condition().matches(values);
    }

    public static String expand(
            String text, Map<String, String> values, String player, String uuid) {
        return Kt.replace(
                VARIABLE,
                text,
                match -> {
                    String key = Kt.group(match, 1);
                    switch (key) {
                        case "player":
                            return player;
                        case "uuid":
                            return uuid;
                        default:
                            String value = values.get(key);
                            return value != null ? value : match.group();
                    }
                });
    }

    /** One pass, so a substituted value is never expanded again. Unknown names stay literal. */
    public static String display(
            String source, Function<String, String> lookup, Function<String, String> papi) {
        return Kt.replace(
                DISPLAY,
                source,
                match -> {
                    String key = Kt.group(match, 1);
                    if (key.isEmpty()) {
                        return papi.apply(match.group());
                    }
                    String value = lookup.apply(key);
                    return value != null ? value : match.group();
                });
    }

    public static String imageTag(MenuImageRequest request) {
        return "<image:"
                + request.provider()
                + ":"
                + request.id()
                + (request.provider().equals("CE")
                        ? ":" + request.row() + ":" + request.column()
                        : "")
                + ">";
    }

    public static List<String> wrap(String text, int width) {
        return wrap(text, width, 8, false);
    }

    public static List<String> wrap(String text, int width, int textSize) {
        return wrap(text, width, textSize, false);
    }

    public static List<String> wrap(String text, int width, int textSize, boolean bold) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (DialogCanvas.textWidth(line + character, textSize, bold) > width
                    && !line.isEmpty()) {
                lines.add(line);
                line = "";
            }
            line += character;
        }
        lines.add(line);
        return lines;
    }

    public static DialogCanvas render(
            DialogTemplate template,
            Map<String, String> values,
            Function<String, String> expand,
            Function<String, ClickEvent> click) {
        return render(template, values, expand, new RichMenuText(), click);
    }

    public static DialogCanvas render(
            DialogTemplate template,
            Map<String, String> values,
            Function<String, String> expand,
            RichMenuText richText,
            Function<String, ClickEvent> click) {
        DialogCanvas canvas =
                new DialogCanvas(
                        MenuTheme.DARK,
                        template.width(),
                        template.rows(),
                        Key.key("dialogmenu_dialogue:labels"),
                        Key.key("dialogmenu_dialogue:button_labels"),
                        richText,
                        click);
        if (template.background() != null) {
            canvas.sprite(0, 0, template.background());
        }
        for (TemplateElement element : template.elements()) {
            if (!visible(element, values)) {
                continue;
            }
            boolean selected = element.selected() != null && element.selected().matches(values);
            SpriteCase chosen = null;
            for (SpriteCase candidate : element.cases()) {
                if (candidate.condition().matches(values)) {
                    chosen = candidate;
                    break;
                }
            }
            DialogCanvas.Skin sprite;
            if (selected) {
                sprite =
                        element.selectedSprite() != null
                                ? element.selectedSprite()
                                : element.sprite();
            } else {
                sprite =
                        chosen != null && chosen.sprite() != null
                                ? chosen.sprite()
                                : element.sprite();
            }
            MenuImageRequest image =
                    chosen != null && chosen.image() != null ? chosen.image() : element.image();
            switch (element.type()) {
                // Images keep their provider's font and baseline. A missing image falls back
                // to text, clipped at the canvas edge so the row cannot wrap.
                case "sprite" -> {
                    if (image != null) {
                        canvas.text(
                                element.x(),
                                element.row(),
                                canvas.prepare(imageTag(image)).fit(template.width() - element.x()),
                                element.color());
                    } else {
                        canvas.sprite(element.x(), element.row(), Kt.requireNotNull(sprite));
                    }
                }
                case "button" -> {
                    canvas.sprite(
                            element.x(), element.row(), Kt.requireNotNull(sprite), element.id());
                    MeasuredText label =
                            canvas.prepare(
                                            expand.apply(Kt.single(element.lines())),
                                            8,
                                            element.bold(),
                                            true)
                                    .fit(element.width() - 8);
                    canvas.text(
                            element.x() + (element.width() - label.width()) / 2,
                            element.row() + 1,
                            label,
                            element.color(),
                            element.id(),
                            true,
                            8,
                            element.bold());
                }
                case "text" -> {
                    int stride = TitleFont.lineRows(element.textSize());
                    int capacity = element.rows() / stride;
                    List<MeasuredText> lines = new ArrayList<>();
                    for (String line : element.lines()) {
                        lines.addAll(
                                canvas.prepare(
                                                expand.apply(line),
                                                element.textSize(),
                                                element.bold())
                                        .wrap(element.width()));
                    }
                    for (int row = 0; row < Math.min(capacity, lines.size()); row++) {
                        MeasuredText line = lines.get(row);
                        MeasuredText label;
                        if (row == capacity - 1 && lines.size() > capacity) {
                            MeasuredText suffix =
                                    canvas.prepare("..", element.textSize(), element.bold());
                            label = line.fit(element.width() - suffix.width()).plus(suffix);
                        } else {
                            label = line;
                        }
                        canvas.text(
                                element.x(),
                                element.row() + row * stride,
                                label,
                                element.color(),
                                null,
                                false,
                                element.textSize(),
                                element.bold());
                    }
                }
                default -> {}
            }
        }
        return canvas;
    }
}
