package online.toraka.dialogmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Value formats of the TrMenu-style actions, kept free of Bukkit so they can be unit tested. */
public final class ActionValues {

    private static final Pattern SENTENCE = Pattern.compile("`(.+?)`");
    private static final Pattern TELLRAW_PART = Pattern.compile("<(.+?)>");
    private static final Pattern RICH_TAG =
            Pattern.compile("<(?:image|i18n|l10n):[^<>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?|\\.\\d+");
    private static final Pattern SOUND_KEY =
            Pattern.compile("[a-z0-9_.\\-]{1,64}:[a-z0-9_.\\-/]{1,128}");
    private static final Pattern SOUND_PATH = Pattern.compile("[a-z0-9_.\\-/]{1,128}");
    private static final Pattern SOUND_FIELD = Pattern.compile("[A-Z0-9_]{1,128}");
    private static final LegacyComponentSerializer AMPERSAND =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private ActionValues() {}

    /** {@code title: <title> [subtitle] [fadeIn] [stay] [fadeOut]}, times in ticks. */
    public record TitleParts(String title, String subtitle, int fadeIn, int stay, int fadeOut) {}

    /**
     * TrMenu's rule: each {@code `...`} group may hold spaces (and {@code \s} for a space), the
     * rest splits on single spaces into at most five parts. A time that is not a whole number is
     * an error here instead of TrMenu's silent default.
     */
    public static TitleParts title(String value) {
        List<String> groups = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(value);
        StringBuilder content = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            content.append(value, last, matcher.start());
            content.append('\0').append(groups.size()).append('\0');
            groups.add(matcher.group(1).replace("\\s", " "));
            last = matcher.end();
        }
        content.append(value.substring(last));
        List<String> parts = Kt.split(content.toString(), ' ', 5);
        return new TitleParts(
                restore(Kt.getOrNull(parts, 0), groups),
                restore(Kt.getOrNull(parts, 1), groups),
                ticks(Kt.getOrNull(parts, 2), 15, "淡入"),
                ticks(Kt.getOrNull(parts, 3), 20, "停留"),
                ticks(Kt.getOrNull(parts, 4), 15, "淡出"));
    }

    private static String restore(String part, List<String> groups) {
        if (part == null) {
            return "";
        }
        String result = part;
        for (int index = 0; index < groups.size(); index++) {
            result = result.replace("\0" + index + "\0", groups.get(index));
        }
        return result;
    }

    private static int ticks(String part, int fallback, String label) {
        if (part == null || part.isEmpty()) {
            return fallback;
        }
        Integer value = Kt.toIntOrNull(part);
        Kt.require(
                value != null && value >= 0 && value <= 72000,
                () -> "title 的" + label + "时间需要 0–72000 的整数 tick，带空格的文字请用 ` ` 包住：" + part);
        return value;
    }

    /**
     * One {@code NAME-volume-pitch} sound. {@code key} is a namespaced sound ID played as-is
     * (vanilla or resource pack); otherwise {@code name} is an {@code org.bukkit.Sound} field.
     */
    public record SoundSpec(String name, boolean key, float volume, float pitch) {}

    /**
     * Numbers are read from the right, so a resource-pack ID containing {@code -} still works:
     * {@code mypack:ui-click-0.8-1.4}. {@code ui.button.click} and {@code minecraft:...} are
     * vanilla IDs; {@code UI_BUTTON_CLICK} / {@code ui_button_click} are Bukkit names.
     */
    public static List<SoundSpec> sounds(String value) {
        List<SoundSpec> result = new ArrayList<>();
        for (String raw : Kt.split(value, ';')) {
            String spec = Kt.trim(raw);
            Kt.require(!spec.isEmpty(), () -> "sound 需要 名称-音量-音调，多个用 ; 分隔");
            List<String> parts = Kt.split(spec, '-');
            int numbers = 0;
            while (numbers < 2
                    && parts.size() - numbers > 1
                    && NUMBER.matcher(parts.get(parts.size() - 1 - numbers)).matches()) {
                numbers++;
            }
            String name = String.join("-", parts.subList(0, parts.size() - numbers));
            float volume = numbers >= 1 ? number(parts, numbers, 0) : 1f;
            float pitch = numbers == 2 ? number(parts, numbers, 1) : 1f;
            result.add(sound(name, volume, pitch, spec));
        }
        return result;
    }

    private static float number(List<String> parts, int numbers, int index) {
        return Float.parseFloat(parts.get(parts.size() - numbers + index));
    }

    private static SoundSpec sound(String name, float volume, float pitch, String spec) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.indexOf(':') >= 0) {
            Kt.require(SOUND_KEY.matcher(lower).matches(), () -> "无效音效 ID：" + spec);
            return new SoundSpec(lower, true, volume, pitch);
        }
        if (lower.indexOf('.') >= 0) {
            Kt.require(SOUND_PATH.matcher(lower).matches(), () -> "无效音效 ID：" + spec);
            return new SoundSpec("minecraft:" + lower, true, volume, pitch);
        }
        String field = name.toUpperCase(Locale.ROOT);
        Kt.require(
                SOUND_FIELD.matcher(field).matches(),
                () -> "音效写作 BLOCK_ANVIL_HIT-1-2、ui.button.click 或 命名空间:ID：" + spec);
        return new SoundSpec(field, false, volume, pitch);
    }

    /** {@code delay: N} waits N ticks; up to an hour. */
    public static int delay(String value) {
        Integer ticks = Kt.toIntOrNull(value);
        Kt.require(
                ticks != null && ticks >= 0 && ticks <= 72000,
                () -> "delay 需要 0–72000 的整数 tick：" + value);
        return ticks;
    }

    /** TrMenu splits multi-line text on a literal backslash-n. */
    public static List<String> lines(String value) {
        return List.of(value.replace("\\r", "\\n").split("\\\\n", -1));
    }

    /**
     * {@code &} colour codes and {@code &#RRGGBB}. A line with DialogMenu's own {@code
     * <image:...>}, {@code <i18n:...>} or {@code <l10n:...>} tags goes to {@code rich} as a whole,
     * as the canvas {@code message:} action always did, so its MiniMessage colours keep working;
     * its {@code &} codes are turned into the matching MiniMessage tags first.
     */
    public static Component colour(String line, Function<String, Component> rich) {
        if (!RICH_TAG.matcher(line).find()) {
            return AMPERSAND.deserialize(line);
        }
        return rich.apply(miniMessage(line));
    }

    private static final Pattern AMPERSAND_CODE =
            Pattern.compile("&(?:#([0-9a-fA-F]{6})|x((?:&[0-9a-fA-F]){6})|([0-9a-fk-orA-FK-OR]))");

    /** {@code &a} becomes {@code <reset><green>}: a legacy colour also clears decorations. */
    static String miniMessage(String line) {
        return Kt.replace(
                AMPERSAND_CODE,
                line,
                match -> {
                    String hex = Kt.group(match, 1);
                    if (hex.isEmpty() && !Kt.group(match, 2).isEmpty()) {
                        hex = Kt.group(match, 2).replace("&", "");
                    }
                    if (!hex.isEmpty()) {
                        return "<reset><#" + Kt.lower(hex) + ">";
                    }
                    char code = Character.toLowerCase(Kt.group(match, 3).charAt(0));
                    return switch (code) {
                        case 'k' -> "<obfuscated>";
                        case 'l' -> "<bold>";
                        case 'm' -> "<strikethrough>";
                        case 'n' -> "<underlined>";
                        case 'o' -> "<italic>";
                        case 'r' -> "<reset>";
                        default -> "<reset><" + LEGACY_NAMES.get(code) + ">";
                    };
                });
    }

    private static final Map<Character, String> LEGACY_NAMES =
            Map.ofEntries(
                    Map.entry('0', "black"),
                    Map.entry('1', "dark_blue"),
                    Map.entry('2', "dark_green"),
                    Map.entry('3', "dark_aqua"),
                    Map.entry('4', "dark_red"),
                    Map.entry('5', "dark_purple"),
                    Map.entry('6', "gold"),
                    Map.entry('7', "gray"),
                    Map.entry('8', "dark_gray"),
                    Map.entry('9', "blue"),
                    Map.entry('a', "green"),
                    Map.entry('b', "aqua"),
                    Map.entry('c', "red"),
                    Map.entry('d', "light_purple"),
                    Map.entry('e', "yellow"),
                    Map.entry('f', "white"));

    public static boolean json(String value) {
        String trimmed = Kt.trim(value);
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }
        try {
            JsonParser.parseString(trimmed);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Raw JSON text, or TrMenu's {@code <text@hover=...@url=...>} segments between plain text.
     * {@code expand} fills placeholders in every JSON string or segment piece separately, so a
     * value can never break the structure.
     */
    public static Component tellraw(
            String value, Function<String, String> expand, Function<String, Component> colour) {
        if (json(value)) {
            JsonElement tree = expandJson(JsonParser.parseString(Kt.trim(value)), expand);
            return GsonComponentSerializer.gson().deserializeFromTree(tree);
        }
        TextComponent.Builder result = Component.text();
        Matcher matcher = TELLRAW_PART.matcher(value);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                result.append(colour.apply(expand.apply(value.substring(last, matcher.start()))));
            }
            result.append(segment(matcher.group(1), expand, colour));
            last = matcher.end();
        }
        if (last < value.length()) {
            result.append(colour.apply(expand.apply(value.substring(last))));
        }
        return result.build();
    }

    private static Component segment(
            String raw, Function<String, String> expand, Function<String, Component> colour) {
        List<String> pieces = Kt.split(raw, '@');
        Component text = colour.apply(expand.apply(pieces.get(0)));
        for (String piece : pieces.subList(1, pieces.size())) {
            int separator = indexOfAny(piece, "=:");
            if (separator < 0) {
                continue;
            }
            String type = piece.substring(0, separator);
            String content = expand.apply(piece.substring(separator + 1));
            switch (type) {
                case "hover" ->
                        text =
                                text.hoverEvent(
                                        HoverEvent.showText(
                                                colour.apply(content.replace("\\n", "\n"))));
                case "open_url", "url" -> text = text.clickEvent(ClickEvent.openUrl(content));
                case "suggest" -> text = text.clickEvent(ClickEvent.suggestCommand(content));
                case "execute", "command" -> text = text.clickEvent(ClickEvent.runCommand(content));
                default -> {}
            }
        }
        return text;
    }

    private static int indexOfAny(String text, String characters) {
        for (int index = 0; index < text.length(); index++) {
            if (characters.indexOf(text.charAt(index)) >= 0) {
                return index;
            }
        }
        return -1;
    }

    private static JsonElement expandJson(JsonElement element, Function<String, String> expand) {
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return new JsonPrimitive(expand.apply(primitive.getAsString()));
        }
        if (element instanceof JsonArray array) {
            JsonArray result = new JsonArray();
            for (JsonElement item : array) {
                result.add(expandJson(item, expand));
            }
            return result;
        }
        if (element instanceof JsonObject object) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                result.add(entry.getKey(), expandJson(entry.getValue(), expand));
            }
            return result;
        }
        return element;
    }
}
