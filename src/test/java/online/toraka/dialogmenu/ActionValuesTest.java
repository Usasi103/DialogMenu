package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionValuesTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    @DisplayName("title splits like TrMenu: backtick groups, five parts, default times")
    void titleSplitsLikeTrMenu() {
        assertEquals(new ActionValues.TitleParts("欢迎", "", 15, 20, 15), ActionValues.title("欢迎"));
        assertEquals(
                new ActionValues.TitleParts("&a完成 啦", "奖励 已发放", 10, 40, 10),
                ActionValues.title("`&a完成 啦` `奖励\\s已发放` 10 40 10"));
        assertEquals(
                new ActionValues.TitleParts("a", "b", 1, 2, 3), ActionValues.title("a b 1 2 3"));
        assertThrows(IllegalArgumentException.class, () -> ActionValues.title("a b 1 2 3 4"));
        assertThrows(IllegalArgumentException.class, () -> ActionValues.title("a b -1"));
        // A literal {0} in the text stays as written (TrMenu would substitute a group there).
        assertEquals("{0}", ActionValues.title("`x y` {0}").subtitle());
    }

    @Test
    @DisplayName("sounds read volume and pitch from the right and accept three name styles")
    void soundsReadNumbersFromTheRight() {
        assertEquals(
                List.of(new ActionValues.SoundSpec("BLOCK_ANVIL_HIT", false, 1, 2)),
                ActionValues.sounds("BLOCK_ANVIL_HIT-1-2"));
        assertEquals(
                List.of(new ActionValues.SoundSpec("UI_BUTTON_CLICK", false, 0.8f, 1)),
                ActionValues.sounds("ui_button_click-0.8"));
        assertEquals(
                List.of(
                        new ActionValues.SoundSpec("minecraft:ui.button.click", true, 1, 1),
                        new ActionValues.SoundSpec("mypack:ui-click", true, 0.5f, 1.4f)),
                ActionValues.sounds("ui.button.click; mypack:ui-click-0.5-1.4"));
        assertEquals(
                List.of(new ActionValues.SoundSpec("minecraft:entity.item.break", true, 1, 0)),
                ActionValues.sounds("minecraft:entity.item.break-1-0"));
        for (String bad : List.of("", "a b", "BLOCK ANVIL", "x:Y Z", ";")) {
            assertThrows(IllegalArgumentException.class, () -> ActionValues.sounds(bad), bad);
        }
    }

    @Test
    @DisplayName("delay takes whole ticks up to an hour; text splits on a literal backslash-n")
    void delayAndLines() {
        assertEquals(40, ActionValues.delay("40"));
        assertThrows(IllegalArgumentException.class, () -> ActionValues.delay("2s"));
        assertThrows(IllegalArgumentException.class, () -> ActionValues.delay("72001"));
        assertEquals(List.of("a", "b", "c"), ActionValues.lines("a\\nb\\rc"));
        assertEquals(List.of("one line"), ActionValues.lines("one line"));
    }

    @Test
    @DisplayName("colour reads & codes and &#RRGGBB, then DialogMenu's own tags")
    void colourReadsAmpersandCodesAndRichTags() {
        Component simple = ActionValues.colour("&a好 &#ff8800橙", tag -> Component.text("?"));
        assertEquals("好 橙", plain(simple));
        assertEquals(NamedTextColor.GREEN, simple.children().get(0).color());
        assertEquals(TextColor.color(0xff8800), simple.children().get(1).color());
        // A tagged line goes to the rich renderer whole, with & codes as MiniMessage tags.
        Function<String, Component> rich = Component::text;
        assertEquals(
                "<reset><gray>图<image:demo:x>标",
                plain(ActionValues.colour("&7图<image:demo:x>标", rich)));
        assertEquals("A & B", plain(ActionValues.colour("A & B", rich)));
    }

    @Test
    @DisplayName("& codes become MiniMessage tags; a colour also clears decorations")
    void ampersandCodesBecomeMiniMessage() {
        assertEquals(
                "<bold>粗<reset><green>绿<reset><#ff8800>橙<reset>白",
                ActionValues.miniMessage("&l粗&a绿&#FF8800橙&r白"));
        assertEquals("<reset><#ff8800>x", ActionValues.miniMessage("&x&f&f&8&8&0&0x"));
        assertEquals("A & B &z", ActionValues.miniMessage("A & B &z"));
    }

    @Test
    @DisplayName("tellraw fills JSON strings separately so a value cannot break the JSON")
    void tellrawJsonExpandsStringsOnly() {
        Function<String, String> expand = text -> text.replace("{player}", "Al\"ex");
        Component json =
                ActionValues.tellraw(
                        "{\"text\":\"hi {player}\",\"clickEvent\":{\"action\":\"run_command\","
                                + "\"value\":\"/msg {player}\"}}",
                        expand,
                        Component::text);
        assertEquals("hi Al\"ex", plain(json));
        assertEquals(ClickEvent.runCommand("/msg Al\"ex"), json.clickEvent());
        assertTrue(ActionValues.json("[\"a\"]"));
        assertFalse(ActionValues.json("<a@hover=b>"));
        assertFalse(ActionValues.json("{broken"));
    }

    @Test
    @DisplayName("tellraw shorthand builds hover and click segments between plain text")
    void tellrawShorthandBuildsSegments() {
        Component message =
                ActionValues.tellraw(
                        "点击 <这里@hover=打开\\n网页@url=https://example.com> 或 <复制@suggest=/help>",
                        Function.identity(),
                        Component::text);
        assertEquals("点击 这里 或 复制", plain(message));
        Component link = message.children().get(1);
        assertEquals(ClickEvent.openUrl("https://example.com"), link.clickEvent());
        assertEquals("打开\n网页", plain((Component) link.hoverEvent().value()));
        assertEquals(ClickEvent.suggestCommand("/help"), message.children().get(3).clickEvent());
        assertEquals(
                "plain",
                plain(ActionValues.tellraw("plain", Function.identity(), Component::text)));
    }
}
