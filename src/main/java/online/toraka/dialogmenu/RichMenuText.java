package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/** Only the three explicit tags opt text into MiniMessage; existing plain text stays literal. */
public final class RichMenuText {

    private static final Pattern RICH =
            Pattern.compile("<(?:image|i18n|l10n):", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNRESOLVED =
            Pattern.compile("<(?:image|i18n|l10n|arg):", Pattern.CASE_INSENSITIVE);

    private record Parsed(Component component, Map<Key, MenuImage> images) {}

    private final MenuTranslations translations;
    private final Locale locale;
    private final Function<MenuImageRequest, MenuImage> images;
    private final Consumer<String> warning;
    private final Map<String, Parsed> cache = new HashMap<>();
    private final MiniMessage mini =
            MiniMessage.builder()
                    .tags(
                            TagResolver.resolver(
                                    StandardTags.color(),
                                    StandardTags.decorations(),
                                    StandardTags.reset()))
                    .build();

    public RichMenuText() {
        this(new MenuTranslations());
    }

    public RichMenuText(MenuTranslations translations) {
        this(translations, Locale.ENGLISH);
    }

    public RichMenuText(MenuTranslations translations, Locale locale) {
        this(translations, locale, request -> null, message -> {});
    }

    public RichMenuText(
            MenuTranslations translations,
            Locale locale,
            Function<MenuImageRequest, MenuImage> images,
            Consumer<String> warning) {
        this.translations = translations;
        this.locale = locale;
        this.images = images;
        this.warning = warning;
    }

    /** One parse of one source: images found so far and the tag budget. */
    private final class Parse {
        private final Map<Key, MenuImage> found = new LinkedHashMap<>();
        private int count = 0;

        Component deserialize(String text, Set<String> stack, List<String> arguments) {
            Kt.require(stack.size() <= 16 && ++count <= 256, () -> "翻译嵌套过深或标签过多");
            TagResolver imageTag =
                    TagResolver.resolver(
                            "image",
                            (args, context) -> {
                                List<String> parts = new ArrayList<>();
                                while (args.hasNext()) {
                                    parts.add(args.pop().value());
                                }
                                MenuImageRequest request = imageRequest(parts);
                                MenuImage image = images.apply(request);
                                if (image == null) {
                                    warning.accept(
                                            "图片不可用 " + request.provider() + ":" + request.id());
                                    return Tag.selfClosingInserting(
                                            Component.text("[image:" + request.id() + "]"));
                                }
                                Key marker =
                                        Key.key("dialogmenu_internal", "image_" + found.size());
                                found.put(marker, image);
                                return Tag.selfClosingInserting(
                                        Component.text("\uFFFC").font(marker));
                            });
            TagResolver argumentTag =
                    TagResolver.resolver(
                            "arg",
                            (args, context) -> {
                                int index = Integer.parseInt(args.popOr("缺少参数序号").value());
                                String value = Kt.getOrNull(arguments, index);
                                if (value == null) {
                                    throw Kt.error("缺少翻译参数 " + index);
                                }
                                return Tag.selfClosingInserting(
                                        deserialize(value, stack, Collections.emptyList()));
                            });
            return mini.deserialize(
                    text,
                    imageTag,
                    translationTag("i18n", stack),
                    translationTag("l10n", stack),
                    argumentTag);
        }

        private TagResolver translationTag(String name, Set<String> stack) {
            return TagResolver.resolver(
                    name,
                    (args, context) -> {
                        String key = args.popOr("缺少翻译键").value();
                        String identity = name + ":" + key;
                        if (stack.contains(identity)) {
                            warning.accept("翻译循环引用 " + key);
                            return Tag.selfClosingInserting(Component.text("[" + key + "]"));
                        }
                        List<String> values = new ArrayList<>();
                        while (args.hasNext()) {
                            values.add(args.pop().value());
                        }
                        String value = translations.get(key, name.equals("l10n") ? locale : null);
                        if (value == null) {
                            warning.accept("缺少翻译 " + key);
                            return Tag.selfClosingInserting(Component.text(key));
                        }
                        Set<String> nested = new LinkedHashSet<>(stack);
                        nested.add(identity);
                        return Tag.selfClosingInserting(deserialize(value, nested, values));
                    });
        }
    }

    private Parsed parse(String source) {
        Parsed cached = cache.get(source);
        if (cached != null) {
            return cached;
        }
        Parse parse = new Parse();
        Component component;
        if (!RICH.matcher(source).find()) {
            component = Component.text(source);
        } else {
            try {
                component =
                        parse.deserialize(source, Collections.emptySet(), Collections.emptyList());
            } catch (Exception error) {
                warning.accept("文本解析失败：" + error.getMessage());
                // Do not leak raw tags to CE's later packet replacement after measuring
                // this fallback.
                component = Component.text(source.replace('<', '‹').replace('>', '›'));
            }
        }
        Parsed parsed = new Parsed(sanitize(component), parse.found);
        cache.put(source, parsed);
        return parsed;
    }

    private Component sanitize(Component value) {
        Component next = value;
        if (value instanceof TextComponent text && UNRESOLVED.matcher(text.content()).find()) {
            warning.accept("未能解析文本标签：" + text.content());
            next = text.content(text.content().replace('<', '‹').replace('>', '›'));
        }
        List<Component> children = new ArrayList<>();
        for (Component child : next.children()) {
            children.add(sanitize(child));
        }
        return next.children(children);
    }

    public Component component(String source) {
        Parsed parsed = parse(source);
        return restore(parsed, parsed.component());
    }

    private static Component restore(Parsed parsed, Component component) {
        MenuImage image = parsed.images().get(component.font());
        if (image != null) {
            return image.component();
        }
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            children.add(restore(parsed, child));
        }
        return component.children(children);
    }

    public MeasuredText measure(String source, Key font, int size, boolean bold) {
        Parsed parsed = parse(source);
        List<MeasuredGlyph> glyphs = new ArrayList<>();
        visit(
                parsed,
                glyphs,
                parsed.component(),
                Style.style().font(font).decoration(TextDecoration.BOLD, bold).build(),
                font,
                size);
        return new MeasuredText(glyphs);
    }

    private static void visit(
            Parsed parsed,
            List<MeasuredGlyph> glyphs,
            Component component,
            Style inherited,
            Key font,
            int size) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        MenuImage image = parsed.images().get(component.font());
        if (image != null) {
            glyphs.add(
                    new MeasuredGlyph(
                            image.component()
                                    .colorIfAbsent(NamedTextColor.WHITE)
                                    .decoration(TextDecoration.BOLD, false)
                                    .decoration(TextDecoration.ITALIC, false),
                            (float) image.advance()));
            return;
        }
        if (component instanceof TextComponent text) {
            for (int codepoint : text.content().codePoints().toArray()) {
                String raw = new String(Character.toChars(codepoint));
                String value;
                if (Character.isISOControl(codepoint)) {
                    value = " ";
                } else if (size == 8) {
                    value = raw;
                } else {
                    value = TitleFont.normalize(raw);
                }
                boolean weight = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
                float advance =
                        size == 8
                                ? LabelMetrics.width(value, font, weight)
                                : (float)
                                        (TitleFont.width(value, size)
                                                + (weight ? value.length() : 0));
                glyphs.add(new MeasuredGlyph(Component.text(value).style(style), advance));
            }
        }
        for (Component child : component.children()) {
            visit(parsed, glyphs, child, style, font, size);
        }
    }

    public static MenuImageRequest imageRequest(List<String> arguments) {
        Kt.require(!arguments.isEmpty(), () -> "缺少图片 ID");
        boolean explicit =
                arguments.size() >= 3
                        && Kt.setOf("ce", "craftengine", "ia", "itemsadder")
                                .contains(Kt.lower(arguments.get(0)));
        String provider =
                explicit && Kt.setOf("ia", "itemsadder").contains(Kt.lower(arguments.get(0)))
                        ? "IA"
                        : "CE";
        List<String> parts = explicit ? arguments.subList(1, arguments.size()) : arguments;
        Kt.require(
                parts.size() >= 2 && parts.size() <= 4,
                () -> "图片格式：<image:[CE/IA:]namespace:id[:row:column]>");
        String id = parts.get(0) + ":" + parts.get(1);
        Key.key(id);
        String rowText = Kt.getOrNull(parts, 2);
        String columnText = Kt.getOrNull(parts, 3);
        int row = rowText != null ? Integer.parseInt(rowText) : 0;
        int column = columnText != null ? Integer.parseInt(columnText) : 0;
        Kt.require(row >= 0 && column >= 0, () -> "图片行列不可为负数");
        Kt.require(!provider.equals("IA") || parts.size() == 2, () -> "IA 图片不接受行列参数");
        return new MenuImageRequest(provider, id, row, column);
    }
}
