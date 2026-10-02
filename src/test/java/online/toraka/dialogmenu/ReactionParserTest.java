package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReactionParserTest {

    private static final ReactionParser.Scope SETTINGS =
            new ReactionParser.Scope(
                    false,
                    null,
                    Set.of("player", "uuid"),
                    Map.of(),
                    page ->
                            Set.of("home", "help").contains(page)
                                    ? new ReactionParser.Target("page", page)
                                    : null,
                    target ->
                            target.equals("boss")
                                    ? new ReactionParser.Target("template", "boss/intro")
                                    : null);

    private static final ReactionParser.Scope CANVAS =
            new ReactionParser.Scope(
                    true,
                    clause ->
                            clause.name().equals("mode") && clause.value().equals("hard")
                                    ? null
                                    : "未声明 " + clause.name(),
                    Set.of("player", "uuid", "mode"),
                    Map.of("mode", List.of("easy", "hard")),
                    null,
                    null);

    /** Parses YAML like a menu file would, so lists and maps arrive in Bukkit's shapes. */
    private static MenuReaction parse(String yaml, ReactionParser.Scope scope) {
        YamlConfiguration config = MenuConfigParser.yaml("Actions:\n" + yaml, "test.yml");
        return ReactionParser.parse(config.get("Actions"), "Actions", scope);
    }

    private static MenuReaction parse(String yaml) {
        return parse(yaml, SETTINGS);
    }

    private static String error(String yaml, ReactionParser.Scope scope) {
        Exception error = assertThrows(Exception.class, () -> parse(yaml, scope), yaml);
        return Objects.requireNonNullElse(error.getMessage(), "");
    }

    private static MenuReaction.Step only(MenuReaction reaction) {
        return Kt.single(reaction.steps());
    }

    @Test
    @DisplayName("TrMenu aliases map to one action each, tell winning over tellraw")
    void aliasesMapToCanonicalActions() {
        Map<String, String> expected =
                Map.ofEntries(
                        Map.entry("msg: hi", "tell"),
                        Map.entry("talk: hi", "tell"),
                        Map.entry("message: hi", "tell"),
                        Map.entry("tell: hi", "tell"),
                        Map.entry("tellraw: hi", "tellraw"),
                        Map.entry("json: hi", "tellraw"),
                        Map.entry("send: hi", "chat"),
                        Map.entry("say: hi", "chat"),
                        Map.entry("cmd: spawn", "command"),
                        Map.entry("player: spawn", "command"),
                        Map.entry("execute: spawn", "command"),
                        Map.entry("subtitle: hi", "title"),
                        Map.entry("send-title: hi", "title"),
                        Map.entry("action: hi", "actionbar"),
                        Map.entry("bungee: lobby", "connect"),
                        Map.entry("server: lobby", "connect"),
                        Map.entry("play-sound: UI_BUTTON_CLICK", "sound"),
                        Map.entry("sounds: UI_BUTTON_CLICK", "sound"),
                        Map.entry("wait: 5", "delay"),
                        Map.entry("break", "return"),
                        Map.entry("shut", "close"),
                        Map.entry("silent-close", "close"),
                        Map.entry("update", "refresh"),
                        Map.entry("icon-refresh", "refresh"),
                        Map.entry("open: boss", "template"),
                        Map.entry("menu: boss", "template"),
                        Map.entry("page: help", "page"),
                        Map.entry("search", "search"));
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(
                    entry.getValue(),
                    only(parse("  - '" + entry.getKey() + "'")).name(),
                    entry.getKey());
        }
        assertEquals("boss/intro", only(parse("  - 'open: boss'")).value());
    }

    @Test
    @DisplayName("line options use TrMenu's regexes and are removed from the value")
    void lineOptionsAreParsedAndRemoved() {
        MenuReaction.Step step =
                only(parse("  - 'tell: hello {delay=5}{chance=0.25}{condition=perm vip.use}'"));
        assertEquals("hello", step.value());
        assertEquals(5, step.options().delay());
        assertEquals(0.25, step.options().chance());
        assertEquals("vip.use", Kt.single(step.options().condition().clauses()).permission());
        MenuReaction.Step angle = only(parse("  - 'sound: UI_BUTTON_CLICK <Wait: 3> <Rate:0.5>'"));
        assertEquals("UI_BUTTON_CLICK", angle.value());
        assertEquals(3, angle.options().delay());
        assertEquals(0.5, angle.options().chance());
        // A brace condition may compare with > because it ends at the closing brace.
        MenuReaction.Step compare = only(parse("  - 'tell: a {condition=%x% >= 3} b'"));
        assertEquals("a  b", compare.value());
        assertEquals(">=", compare.options().condition().clauses().get(0).compare().operator());
        MenuReaction.Step players = only(parse("  - 'tell: hi {players=perm vip.use}'"));
        assertTrue(players.options().players());
        assertEquals("vip.use", players.options().audience().clauses().get(0).permission());
        assertNull(only(parse("  - 'tell: hi {players}'")).options().audience());
        // Unlike TrMenu, options may follow a bare action word.
        assertEquals(20, only(parse("  - 'close {delay=20}'")).options().delay());
    }

    @Test
    @DisplayName("one line may hold several actions and the richest options are shared")
    void separatedActionsShareTheRichestOptions() {
        MenuReaction reaction =
                parse("  - 'tell: a _||_ sound: UI_BUTTON_CLICK-1-2 {delay=4} &&& close'");
        assertEquals(List.of("tell: a", "sound: UI_BUTTON_CLICK-1-2", "close"), reaction.lines());
        for (MenuReaction.Step step : reaction.steps()) {
            assertEquals(4, step.options().delay(), step.line());
        }
    }

    @Test
    @DisplayName("condition blocks accept TrMenu key aliases, nest and sort by priority")
    void conditionBlocksNestAndSortByPriority() {
        MenuReaction reaction =
                parse(
                        """
                          - 'tell: first'
                          - cond: 'perm vip.use'
                            list:
                              - 'tell: vip'
                              - condition: '%level% >= 10'
                                actions: 'tell: high'
                                deny-actions: ['tell: low', 'return']
                            deny: 'tell: no'
                          - priority: -5
                            actions: ['tell: earliest']
                        """);
        assertEquals("tell: earliest", ((MenuReaction.Step) reaction.nodes().get(0)).line());
        assertEquals("tell: first", ((MenuReaction.Step) reaction.nodes().get(1)).line());
        MenuReaction.Branch branch = (MenuReaction.Branch) reaction.nodes().get(2);
        assertEquals("vip.use", branch.condition().clauses().get(0).permission());
        assertEquals("tell: no", Kt.single(branch.deny().lines()));
        MenuReaction.Branch inner = (MenuReaction.Branch) branch.actions().nodes().get(1);
        assertEquals(List.of("tell: low", "return"), inner.deny().lines());
        assertEquals(
                List.of(
                        "tell: earliest",
                        "tell: first",
                        "tell: vip",
                        "tell: high",
                        "tell: low",
                        "return",
                        "tell: no"),
                reaction.lines());
    }

    @Test
    @DisplayName("a click-type map only accepts all, because a Dialog has one click")
    void clickTypeMapOnlyAcceptsAll() {
        assertEquals(List.of("close"), parse("  all: ['close']").lines());
        assertTrue(error("  left: ['close']", SETTINGS).contains("all"));
        assertTrue(error("  shift_right: 'close'", SETTINGS).contains("all"));
    }

    @Test
    @DisplayName("mistakes TrMenu ignores are reported with the place and a hint")
    void silentTrMenuMistakesAreReported() {
        assertTrue(error("  - tell: hi", SETTINGS).contains("加引号"));
        assertTrue(error("  - 'foo: bar'", SETTINGS).contains("不支持动作 foo"));
        assertTrue(error("  - 'hello world'", SETTINGS).contains("不支持动作"));
        assertTrue(error("  - 'tell: hi {chance=50}'", SETTINGS).contains("0–1"));
        assertTrue(error("  - 'tell: hi {chance=1.5}'", SETTINGS).contains("0–1"));
        assertTrue(error("  - colour: x\n    actions: [close]", SETTINGS).contains("未知字段"));
        assertTrue(error("  - condition: 'perm a'", SETTINGS).contains("actions 或 deny"));
        assertTrue(error("  - deny: ['close']", SETTINGS).contains("deny 永远不会执行"));
        assertTrue(error("  - 'delay: 5 {condition=perm a}'", SETTINGS).contains("delay"));
        assertTrue(error("  - 'return {delay=5}'", SETTINGS).contains("return"));
        assertTrue(error("  - 'close {players}'", SETTINGS).contains("{players}"));
        assertTrue(error("  - 'title: a b slow'", SETTINGS).contains("淡入"));
        assertTrue(error("  - 'delay: soon'", SETTINGS).contains("delay"));
        assertTrue(error("  - 'sound: not a sound'", SETTINGS).contains("音效"));
        assertTrue(error("  - 'connect: two words'", SETTINGS).contains("服务器名"));
        assertTrue(error("  - 'close: now'", SETTINGS).contains("不接受参数"));
        assertTrue(error("  - 'tell:'", SETTINGS).contains("需要内容"));
    }

    @Test
    @DisplayName("menu-specific actions respect the scope")
    void scopeDecidesMenuSpecificActions() {
        assertTrue(error("  - 'set: mode=hard'", SETTINGS).contains("canvas"));
        assertTrue(error("  - 'search'", CANVAS).contains("search"));
        assertTrue(error("  - 'page: help'", CANVAS).contains("page"));
        assertTrue(error("  - 'open: boss'", CANVAS).contains("open"));
        assertTrue(error("  - 'page: nowhere'", SETTINGS).contains("页面不存在"));
        assertTrue(error("  - 'open: nowhere'", SETTINGS).contains("菜单或页面不存在"));
        assertTrue(error("  - 'set: mode=insane'", CANVAS).contains("set 只能"));
        assertEquals("mode=hard", only(parse("  - 'set: mode = hard'", CANVAS)).value());
        assertEquals(
                "template: other/page", only(parse("  - 'template: other/page'", CANVAS)).line());
        assertTrue(
                error("  - condition: 'mode=easy'\n    actions: [close]", CANVAS).contains("未声明"));
        assertEquals(
                "mode",
                ((MenuReaction.Branch)
                                parse("  - condition: 'mode=hard'\n    actions: [close]", CANVAS)
                                        .nodes()
                                        .get(0))
                        .condition()
                        .clauses()
                        .get(0)
                        .compare()
                        .name());
    }

    @Test
    @DisplayName("commands split on ; before anything is filled in and allow PlaceholderAPI")
    void commandsAllowPlaceholdersAndCheckEveryPart() {
        assertEquals(
                "say %player_level%; give {player} stone",
                only(parse("  - 'console: say %player_level%; give {player} stone'")).value());
        assertTrue(error("  - 'console: say a; /op b'", SETTINGS).contains("不加 /"));
        assertTrue(error("  - 'console: say {mode}'", SETTINGS).contains("{mode}"));
        assertEquals("say {mode}", only(parse("  - 'console: say {mode}'", CANVAS)).value());
        assertTrue(error("  - 'console: say a;;b'", SETTINGS).contains("不能为空"));
    }

    @Test
    @DisplayName("a reaction holds at most 64 actions and 8 levels of blocks")
    void sizeLimits() {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            lines.add("  - 'tell: " + index + "'");
        }
        assertTrue(error(String.join("\n", lines), SETTINGS).contains("64"));
        String nested = "'tell: deep'";
        for (int depth = 0; depth < 10; depth++) {
            nested = "{actions: [" + nested + "]}";
        }
        assertTrue(error("  - " + nested, SETTINGS).contains("嵌套"));
        assertFalse(parse("  - 'tell: ok'").nodes().isEmpty());
    }
}
