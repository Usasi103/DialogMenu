package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReactionRunnerTest {

    private static final ReactionParser.Scope CANVAS =
            new ReactionParser.Scope(
                    true,
                    clause -> clause.name().equals("mode") ? null : "未声明",
                    Set.of("player", "uuid", "mode"),
                    Map.of("mode", List.of("easy", "hard")),
                    null,
                    null);

    private Player player;
    private RecordingHost host;
    private final List<Long> delays = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private double roll = 0.5;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Alex");
        when(player.hasPermission("vip.use")).thenReturn(true);
        host = new RecordingHost(player);
    }

    private void run(String yaml) {
        YamlConfiguration config = MenuConfigParser.yaml("Actions:\n" + yaml, "test.yml");
        MenuReaction reaction = ReactionParser.parse(config.get("Actions"), "Actions", CANVAS);
        ReactionRunner.run(
                reaction,
                host,
                (ticks, task) -> {
                    delays.add(ticks);
                    scheduled.add(task);
                },
                () -> roll);
    }

    private void runScheduled() {
        List<Runnable> tasks = new ArrayList<>(scheduled);
        scheduled.clear();
        tasks.forEach(Runnable::run);
    }

    private List<String> told() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, org.mockito.Mockito.atLeast(0)).sendMessage(captor.capture());
        List<String> result = new ArrayList<>();
        for (Component component : captor.getAllValues()) {
            result.add(PlainTextComponentSerializer.plainText().serialize(component));
        }
        return result;
    }

    @Test
    @DisplayName("steps run in order and a condition sees values set earlier")
    void conditionsSeeEarlierSteps() {
        run(
                """
                  - 'set: mode=hard'
                  - condition: 'mode=hard'
                    actions: ['tell: hard {player}']
                    deny: ['tell: easy']
                  - 'tell: &aend'
                """);
        assertEquals(List.of("hard Alex", "end"), told());
        assertEquals(List.of("set mode=hard", "settle"), host.events);
    }

    @Test
    @DisplayName("delay pauses the rest; the menu is redrawn once, at the pause")
    void delayPausesTheRest() {
        run("  - 'tell: a'\n  - 'delay: 20'\n  - 'tell: b'\n  - 'refresh'");
        assertEquals(List.of("a"), told());
        assertEquals(List.of(20L), delays);
        assertEquals(List.of("settle"), host.events);
        runScheduled();
        assertEquals(List.of("a", "b"), told());
        assertEquals(List.of("settle", "refresh"), host.events);
    }

    @Test
    @DisplayName("{delay=N} postpones only its own action")
    void delayOptionPostponesOneAction() {
        run("  - 'tell: later {delay=10}'\n  - 'tell: now'");
        assertEquals(List.of("now"), told());
        runScheduled();
        assertEquals(List.of("now", "later"), told());
        assertEquals(List.of("settle"), host.events);
    }

    @Test
    @DisplayName("a navigation replaces the closing redraw; return ends the whole reaction")
    void navigationAndReturn() {
        run(
                """
                  - condition: 'perm vip.use'
                    actions:
                      - 'template: other/page'
                      - 'return'
                  - 'tell: never'
                """);
        assertEquals(List.of(), told());
        assertEquals(List.of("template other/page"), host.events);
    }

    @Test
    @DisplayName("return with a condition only stops when it holds; chance uses the roll")
    void conditionalReturnAndChance() {
        roll = 0.6;
        run(
                """
                  - 'return {condition=perm admin}'
                  - 'tell: lucky {chance=0.7}'
                  - 'tell: unlucky {chance=0.5}'
                  - 'return {condition=perm vip.use}'
                  - 'tell: never'
                """);
        assertEquals(List.of("lucky"), told());
    }

    @Test
    @DisplayName("a failed command stops the rest and reports once; values fill commands")
    void failedCommandStops() {
        when(player.performCommand("warp hard Alex")).thenReturn(false);
        run("  - 'set: mode=hard'\n  - 'command: warp {mode} {player}'\n  - 'tell: never'");
        verify(player).performCommand("warp hard Alex");
        assertEquals(List.of(), told());
        assertEquals(List.of("set mode=hard", "failed", "settle"), host.events);
    }

    @Test
    @DisplayName("an unavailable command value cancels the command instead of running it raw")
    void unavailableCommandValueCancels() {
        host.unavailable = true;
        run("  - 'command: warp {mode}'");
        verify(player, never()).performCommand(anyString());
        assertEquals(List.of("failed", "settle"), host.events);
    }

    @Test
    @DisplayName("title, sound and chat reach the player in TrMenu's formats")
    void titleSoundAndChat() {
        run(
                """
                  - 'title: `&a完成 了` 副标题 5 30 5'
                  - 'sound: minecraft:ui.button.click-0.8-1.4'
                  - 'chat: hello {player}'
                """);
        ArgumentCaptor<Title> title = ArgumentCaptor.forClass(Title.class);
        verify(player).showTitle(title.capture());
        assertEquals(
                "完成 了",
                PlainTextComponentSerializer.plainText().serialize(title.getValue().title()));
        assertEquals(30 * 50L, title.getValue().times().stay().toMillis());
        verify(player)
                .playSound(
                        (Location) any(),
                        eq("minecraft:ui.button.click"),
                        eq(SoundCategory.MASTER),
                        eq(0.8f),
                        eq(1.4f));
        verify(player).chat("hello Alex");
    }

    /** Records navigation and redraw calls; text fills {player} and {mode}. */
    private static final class RecordingHost implements ReactionRunner.Host {
        private final Player player;
        private final Map<String, String> values = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();
        private boolean unavailable;

        RecordingHost(Player player) {
            this.player = player;
            values.put("mode", "easy");
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public String text(String source) {
            return source.replace("{player}", player.getName())
                    .replace("{mode}", values.get("mode"));
        }

        @Override
        public String commandValue(String name) {
            if (unavailable) {
                return null;
            }
            return name.equals("player") ? player.getName() : values.get(name);
        }

        @Override
        public Map<String, String> names() {
            return values;
        }

        @Override
        public Component rich(String tag) {
            return Component.text(tag);
        }

        @Override
        public void set(String variable, String value) {
            values.put(variable, value);
            events.add("set " + variable + "=" + value);
        }

        @Override
        public void page(String target) {
            events.add("page " + target);
        }

        @Override
        public void open(String target) {
            events.add("open " + target);
        }

        @Override
        public void close() {
            events.add("close");
        }

        @Override
        public void refresh() {
            events.add("refresh");
        }

        @Override
        public void search() {
            events.add("search");
        }

        @Override
        public void template(String target) {
            events.add("template " + target);
        }

        @Override
        public void settle() {
            events.add("settle");
        }

        @Override
        public void failed() {
            events.add("failed");
        }
    }
}
