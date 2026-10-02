package online.toraka.dialogmenu;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.keystone.task.Tasks;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Runs a {@link MenuReaction} for the player who clicked. Steps run in order on the main thread;
 * {@code delay: N} pauses the rest of the reaction and {@code {delay=N}} postpones only its own
 * action. The menu is redrawn once, when the part before the first pause ends, unless an action
 * already closed it or moved to another page.
 */
public final class ReactionRunner {

    /** The BungeeCord plugin channel; Paper maps it to {@code bungeecord:main} for Velocity too. */
    static final String PROXY_CHANNEL = "BungeeCord";

    private static final Pattern COMMAND_VALUE =
            Pattern.compile("%[a-zA-Z0-9_:.\\-]+%|\\{([a-zA-Z0-9_-]+)}");

    /** What the running menu provides; canvas and settings menus each implement it. */
    public interface Host {
        Player player();

        /** Display text: built-in values, menu values and PlaceholderAPI, with fallbacks. */
        String text(String source);

        /** A {@code {name}} value for a command, or null when it is unavailable. */
        String commandValue(String name);

        /** Declared names for conditions (canvas Variables and Placeholders). */
        Map<String, String> names();

        /** A whole line with DialogMenu's own tags ({@code <image:...>}, {@code <l10n:...>}). */
        Component rich(String tag);

        void set(String variable, String value);

        void page(String target);

        void open(String target);

        void close();

        void refresh();

        void search();

        void template(String target);

        /** The part before the first pause ended without navigating: redraw the menu. */
        void settle();

        /** A command failed; the rest of the reaction is skipped. */
        void failed();
    }

    public interface Scheduler {
        void later(long ticks, Runnable task);
    }

    private ReactionRunner() {}

    public static void run(MenuReaction reaction, Host host) {
        run(
                reaction,
                host,
                (ticks, task) -> Tasks.later(ticks, task),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    static void run(MenuReaction reaction, Host host, Scheduler scheduler, DoubleSupplier random) {
        Run run = new Run(host, scheduler, random);
        run.stack.push(new Frame(reaction.nodes()));
        run.resume();
    }

    private static final class Frame {
        final List<MenuReaction.Node> nodes;
        int index;

        Frame(List<MenuReaction.Node> nodes) {
            this.nodes = nodes;
        }
    }

    private static final class Run {
        final Host host;
        final Scheduler scheduler;
        final DoubleSupplier random;
        final Deque<Frame> stack = new ArrayDeque<>();
        boolean navigated;
        boolean settled;

        Run(Host host, Scheduler scheduler, DoubleSupplier random) {
            this.host = host;
            this.scheduler = scheduler;
            this.random = random;
        }

        void resume() {
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (frame.index >= frame.nodes.size()) {
                    stack.pop();
                    continue;
                }
                MenuReaction.Node node = frame.nodes.get(frame.index++);
                if (node instanceof MenuReaction.Branch branch) {
                    boolean holds =
                            branch.condition() == null || test(branch.condition(), host.player());
                    MenuReaction next = holds ? branch.actions() : branch.deny();
                    if (!next.nodes().isEmpty()) {
                        stack.push(new Frame(next.nodes()));
                    }
                    continue;
                }
                MenuReaction.Step step = (MenuReaction.Step) node;
                MenuReaction.Options options = step.options();
                if (options.chance() < 1 && random.getAsDouble() > options.chance()) {
                    continue;
                }
                if (step.name().equals("delay")) {
                    int ticks = ActionValues.delay(step.value());
                    if (ticks > 0) {
                        settle();
                        scheduler.later(
                                ticks,
                                () -> {
                                    if (host.player().isOnline()) {
                                        resume();
                                    }
                                });
                        return;
                    }
                    continue;
                }
                boolean allowed =
                        options.condition() == null || test(options.condition(), host.player());
                if (step.name().equals("return")) {
                    if (allowed) {
                        finish();
                        return;
                    }
                    continue;
                }
                if (!allowed) {
                    continue;
                }
                if (options.delay() > 0) {
                    scheduler.later(
                            options.delay(),
                            () -> {
                                if (host.player().isOnline() && !execute(step)) {
                                    host.failed();
                                }
                            });
                    continue;
                }
                if (!execute(step)) {
                    host.failed();
                    finish();
                    return;
                }
            }
            finish();
        }

        void settle() {
            if (!settled) {
                settled = true;
                if (!navigated) {
                    host.settle();
                }
            }
        }

        void finish() {
            stack.clear();
            settle();
        }

        boolean test(ActionCondition condition, Player target) {
            return condition.test(
                    target::hasPermission,
                    token -> MenuPlaceholders.resolve(target, token),
                    condition.readsNames() ? host.names() : Map.of());
        }

        boolean execute(MenuReaction.Step step) {
            if (!step.options().players()) {
                return perform(step, host.player());
            }
            ActionCondition audience = step.options().audience();
            boolean succeeded = true;
            for (Player target : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (audience == null || test(audience, target)) {
                    succeeded &= perform(step, target);
                }
            }
            return succeeded;
        }

        /** Text is always filled in for the clicking player, as in TrMenu. */
        boolean perform(MenuReaction.Step step, Player target) {
            String value = step.value();
            switch (step.name()) {
                case "tell" -> {
                    for (String line : ActionValues.lines(value)) {
                        target.sendMessage(colour(host.text(line)));
                    }
                }
                case "chat" -> {
                    for (String line : ActionValues.lines(value)) {
                        target.chat(host.text(line));
                    }
                }
                case "title" -> {
                    ActionValues.TitleParts parts = ActionValues.title(value);
                    target.showTitle(
                            Title.title(
                                    colour(host.text(parts.title())),
                                    colour(host.text(parts.subtitle())),
                                    Title.Times.times(
                                            ticks(parts.fadeIn()),
                                            ticks(parts.stay()),
                                            ticks(parts.fadeOut()))));
                }
                case "actionbar" -> ActionBars.send(target, colour(host.text(value)));
                case "tellraw" ->
                        target.sendMessage(ActionValues.tellraw(value, host::text, this::colour));
                case "command", "console" -> {
                    for (String raw : Kt.split(value, ';')) {
                        String command = command(Kt.trim(raw));
                        if (command == null) {
                            return false;
                        }
                        boolean succeeded =
                                step.name().equals("console")
                                        ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                                        : target.performCommand(command);
                        if (!succeeded) {
                            return false;
                        }
                    }
                }
                case "connect" -> connect(target, Kt.trim(host.text(value)));
                case "sound" -> {
                    for (ActionValues.SoundSpec sound : ActionValues.sounds(value)) {
                        play(target, sound);
                    }
                }
                case "set" ->
                        host.set(Kt.substringBefore(value, '='), Kt.substringAfter(value, '=', ""));
                case "page" -> {
                    navigate();
                    host.page(value);
                }
                case "open" -> {
                    navigate();
                    host.open(value);
                }
                case "close" -> {
                    navigate();
                    host.close();
                }
                case "refresh" -> {
                    navigate();
                    host.refresh();
                }
                case "search" -> {
                    navigate();
                    host.search();
                }
                case "template" -> {
                    navigate();
                    host.template(value);
                }
                default -> {}
            }
            return true;
        }

        /** A navigation before the first pause replaces the closing redraw. */
        void navigate() {
            if (!settled) {
                navigated = true;
            }
        }

        Component colour(String line) {
            return ActionValues.colour(line, host::rich);
        }

        /**
         * One pass over {@code {name}} and {@code %placeholder%}, so a filled-in value is never
         * expanded again. Any unavailable value cancels the command instead of running it with a
         * literal token.
         */
        String command(String template) {
            boolean[] missing = new boolean[1];
            String result =
                    Kt.replace(
                            COMMAND_VALUE,
                            template,
                            match -> {
                                String name = Kt.group(match, 1);
                                String value =
                                        name.isEmpty()
                                                ? MenuPlaceholders.resolve(
                                                        host.player(), match.group())
                                                : host.commandValue(name);
                                if (value == null) {
                                    missing[0] = true;
                                    return match.group();
                                }
                                return singleLine(value);
                            });
            return missing[0] ? null : result;
        }
    }

    private static String singleLine(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString();
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }

    /** A Bukkit {@code Sound} constant by field name, or null; reads registries, so server only. */
    static Sound soundField(String name) {
        try {
            return Sound.class.getField(name).get(null) instanceof Sound sound ? sound : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static void play(Player target, ActionValues.SoundSpec spec) {
        Location location = target.getLocation();
        if (spec.key()) {
            target.playSound(
                    location, spec.name(), SoundCategory.MASTER, spec.volume(), spec.pitch());
            return;
        }
        Sound sound = soundField(spec.name());
        if (sound == null) {
            MenuLog.warning("未知音效 " + spec.name() + "，已跳过");
            return;
        }
        target.playSound(location, sound, SoundCategory.MASTER, spec.volume(), spec.pitch());
    }

    private static void connect(Player target, String server) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("Connect");
        output.writeUTF(server);
        target.sendPluginMessage(
                DialogMenu.getPluginInstance(), PROXY_CHANNEL, output.toByteArray());
    }
}
