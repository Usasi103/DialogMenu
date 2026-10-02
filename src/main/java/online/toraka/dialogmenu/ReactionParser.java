package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads an {@code Actions} value in TrMenu 3's format: action lines ({@code name: value}, several
 * joined by {@code _||_}), line options ({@code {delay=5}}, {@code {chance=0.5}}, {@code
 * {condition=...}}, {@code {players}}) and condition blocks ({@code condition} / {@code actions}
 * / {@code deny} / {@code priority}). Where TrMenu silently ignores a mistake (an unknown action
 * runs as Kether, an unquoted {@code - tell: hi} vanishes) this parser reports it instead.
 */
public final class ReactionParser {

    private static final int MAX_STEPS = 64;
    private static final int MAX_DEPTH = 8;
    private static final Pattern SEPARATOR = Pattern.compile(" ?(_\\|\\|_|&&&) ?");
    private static final Pattern DELAY =
            Pattern.compile("[{<](delay|wait)[=:] ?([0-9]+)[}>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANCE =
            Pattern.compile(
                    "[{<](chance|rate|rand(om)?)[=:] ?([0-9.]+)[}>]", Pattern.CASE_INSENSITIVE);
    // Brace conditions stop at the closing brace; the angle form stays greedy like TrMenu's,
    // because a condition itself may contain > or <.
    private static final Pattern CONDITION_BRACE =
            Pattern.compile("\\{(condition|requirement)[=:] ?([^{}]*)}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION_ANGLE =
            Pattern.compile("<(condition|requirement)[=:] ?(.+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYERS =
            Pattern.compile("[{<]players(?:[=: ] ?([^{}<>]*))?[}>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_VARIABLE = Pattern.compile("\\{([^}]+)}");
    private static final Pattern TEMPLATE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,47}");
    private static final Pattern BLOCK_CONDITION =
            Pattern.compile("(require(ment)?|cond(ition)?)s?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_ACTIONS =
            Pattern.compile("(list|action|click|execute|cmd)s?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_DENY =
            Pattern.compile("deny(-)?(list|action|click|execute|cmd)?s?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_PRIORITY =
            Pattern.compile("pri(ority)?s?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CLICK_TYPES =
            Kt.setOf(
                    "left",
                    "right",
                    "shift_left",
                    "shift_right",
                    "middle",
                    "drop",
                    "control_drop",
                    "double_click",
                    "offhand",
                    "number_key");

    /** Canonical action IDs and their TrMenu aliases; the first full match wins. */
    private static final Map<String, Pattern> ACTIONS = new LinkedHashMap<>();

    static {
        alias("close", "close|shut|(force|silent)-?(close|shut)");
        alias("delay", "delay|wait");
        alias("return", "return|break");
        alias("sound", "(play)?-?sounds?");
        alias("chat", "chat|send|say");
        alias("tell", "tell|message|msg|talk");
        alias("command", "command|cmd|player|execute");
        alias("title", "(send)?-?(sub)?titles?");
        alias("actionbar", "action(bar)?s?");
        alias("tellraw", "(tell(raw)?|json)s?");
        alias("console", "console");
        alias("connect", "bungee|server|connect");
        alias("open", "opens?|(open)?-?gui|(tr)?menu|(force|silent)-?(open|menu)");
        alias("page", "page");
        alias("refresh", "(icon)?-?refresh|(icon)?-?update");
        alias("set", "set");
        alias("search", "search");
        alias("template", "template");
    }

    private static void alias(String id, String regex) {
        ACTIONS.put(id, Pattern.compile(regex));
    }

    private static final Set<String> FOR_PLAYERS =
            Kt.setOf(
                    "tell", "chat", "title", "actionbar", "tellraw", "command", "sound", "connect");

    /**
     * What the surrounding menu allows. {@code names} validates a declared-name condition clause
     * (null: none declared); {@code commandNames} are the {@code {name}} values a command may
     * use; {@code variables} feed {@code set}; {@code page} and {@code open} turn a target into
     * the stored step, or return null when it does not exist (a null function means the action is
     * not available here).
     */
    public record Scope(
            boolean canvas,
            Function<MenuCondition.Clause, String> names,
            Set<String> commandNames,
            Map<String, List<String>> variables,
            Function<String, Target> page,
            Function<String, Target> open) {}

    /**
     * A resolved navigation step. Canvas menus store a page of a canvas menu as {@code template:
     * menu/page}, the form their links and saved values already use.
     */
    public record Target(String name, String value) {}

    private ReactionParser() {}

    private static final class Counter {
        int steps;
    }

    private record Entry(int priority, List<MenuReaction.Node> nodes) {}

    public static MenuReaction parse(Object raw, String at, Scope scope) {
        Kt.require(raw != null, () -> at + ": 需要动作");
        MenuReaction reaction = reaction(top(raw, at), at, scope, 0, new Counter());
        Kt.require(!reaction.nodes().isEmpty(), () -> at + ": 需要至少一条动作");
        return reaction;
    }

    /** The top level may also be a click-type map; a Dialog only has the plain click. */
    private static Object top(Object raw, String at) {
        Map<String, Object> map = map(raw);
        if (map == null || map.isEmpty() || block(map)) {
            return raw;
        }
        Kt.require(map.size() == 1, () -> at + ": Dialog 只有一种点击，按键分组只能写 all");
        Map.Entry<String, Object> only = map.entrySet().iterator().next();
        String key = Kt.lower(only.getKey());
        if (key.equals("all")) {
            return only.getValue();
        }
        Kt.require(
                !CLICK_TYPES.contains(key) && !key.startsWith("number_key"),
                () -> at + "." + only.getKey() + ": Dialog 只有一种点击，按键分组只能写 all");
        return raw;
    }

    private static MenuReaction reaction(
            Object raw, String at, Scope scope, int depth, Counter counter) {
        Kt.require(depth <= MAX_DEPTH, () -> at + ": 条件块最多嵌套 " + MAX_DEPTH + " 层");
        List<Entry> entries = new ArrayList<>();
        if (raw instanceof String text) {
            entries.add(new Entry(-1, line(text, at, scope, counter)));
        } else if (raw instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                Object item = list.get(index);
                String itemAt = at + "[" + (index + 1) + "]";
                if (item instanceof String text) {
                    entries.add(new Entry(index, line(text, itemAt, scope, counter)));
                } else {
                    Map<String, Object> map = map(item);
                    Kt.require(
                            map != null, () -> itemAt + ": 动作需要写成字符串，或 condition/actions/deny 条件块");
                    entries.add(branch(map, index, itemAt, scope, depth, counter));
                }
            }
        } else {
            Map<String, Object> map = map(raw);
            Kt.require(map != null, () -> at + ": 动作需要写成字符串、列表或条件块");
            entries.add(branch(map, -1, at, scope, depth, counter));
        }
        entries.sort(Comparator.comparingInt(Entry::priority));
        List<MenuReaction.Node> nodes = new ArrayList<>();
        for (Entry entry : entries) {
            nodes.addAll(entry.nodes());
        }
        return new MenuReaction(nodes);
    }

    private static Map<String, Object> map(Object raw) {
        Map<?, ?> source;
        if (raw instanceof ConfigurationSection section) {
            source = section.getValues(false);
        } else if (raw instanceof Map<?, ?> map) {
            source = map;
        } else {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static boolean block(Map<String, Object> map) {
        for (String key : map.keySet()) {
            if (role(key) == null) {
                return false;
            }
        }
        return true;
    }

    private static String role(String key) {
        if (BLOCK_CONDITION.matcher(key).matches()) {
            return "condition";
        }
        if (BLOCK_DENY.matcher(key).matches()) {
            return "deny";
        }
        if (BLOCK_ACTIONS.matcher(key).matches()) {
            return "actions";
        }
        if (BLOCK_PRIORITY.matcher(key).matches()) {
            return "priority";
        }
        return null;
    }

    private static Entry branch(
            Map<String, Object> map,
            int index,
            String at,
            Scope scope,
            int depth,
            Counter counter) {
        Map<String, Object> roles = new LinkedHashMap<>();
        for (Map.Entry<String, Object> field : map.entrySet()) {
            String key = field.getKey();
            String role = role(key);
            if (role == null) {
                Kt.require(
                        canonical(key) == null,
                        () -> at + ": 动作行需要加引号，例如 - '" + key + ": " + field.getValue() + "'");
                throw new IllegalArgumentException(
                        at + ": 未知字段 " + key + "，条件块只认 condition / actions / deny / priority");
            }
            Kt.require(!roles.containsKey(role), () -> at + ": " + role + " 重复");
            roles.put(role, field.getValue());
        }
        Kt.require(
                roles.containsKey("actions") || roles.containsKey("deny"),
                () -> at + ": 条件块需要 actions 或 deny");
        Object rawCondition = roles.get("condition");
        ActionCondition condition =
                rawCondition == null || rawCondition instanceof String text && Kt.isBlank(text)
                        ? null
                        : ActionCondition.parse(rawCondition, at + ".condition", scope.names());
        int priority = index;
        if (roles.containsKey("priority")) {
            Kt.require(roles.get("priority") instanceof Integer, () -> at + ".priority: 需要整数");
            priority = (Integer) roles.get("priority");
        }
        MenuReaction actions =
                roles.containsKey("actions")
                        ? reaction(roles.get("actions"), at + ".actions", scope, depth + 1, counter)
                        : MenuReaction.EMPTY;
        if (condition == null) {
            // TrMenu runs a block without a condition unconditionally, so deny could never run.
            Kt.require(!roles.containsKey("deny"), () -> at + ": 没有 condition 时 deny 永远不会执行");
            return new Entry(priority, actions.nodes());
        }
        MenuReaction deny =
                roles.containsKey("deny")
                        ? reaction(roles.get("deny"), at + ".deny", scope, depth + 1, counter)
                        : MenuReaction.EMPTY;
        return new Entry(priority, List.of(new MenuReaction.Branch(condition, actions, deny)));
    }

    private record Part(String text, MenuReaction.Options options) {}

    private static List<MenuReaction.Node> line(
            String text, String at, Scope scope, Counter counter) {
        Kt.require(
                text.length() <= 2048 && Kt.noControl(text) && Kt.isNotBlank(text),
                () -> at + ": 动作需要不含控制字符的单行文字");
        List<Part> parts = new ArrayList<>();
        for (String piece : SEPARATOR.split(text, -1)) {
            parts.add(options(piece, at, scope));
        }
        // TrMenu lends the options of the part that has the most to every part of the line.
        MenuReaction.Options shared = null;
        if (parts.size() > 1) {
            for (Part part : parts) {
                if (shared == null || part.options().count() > shared.count()) {
                    shared = part.options();
                }
            }
        }
        List<MenuReaction.Node> steps = new ArrayList<>();
        for (Part part : parts) {
            counter.steps++;
            Kt.require(counter.steps <= MAX_STEPS, () -> at + ": 一组动作最多 " + MAX_STEPS + " 条");
            steps.add(step(part.text(), shared != null ? shared : part.options(), at, scope));
        }
        return steps;
    }

    private static Part options(String piece, String at, Scope scope) {
        String rest = piece;
        int delay = 0;
        Matcher matcher = DELAY.matcher(rest);
        if (matcher.find()) {
            String value = matcher.group(2);
            Integer ticks = Kt.toIntOrNull(value);
            Kt.require(
                    ticks != null && ticks <= 72000,
                    () -> at + ": {delay=} 需要 0–72000 的整数 tick：" + value);
            delay = ticks;
            rest = matcher.replaceAll("");
        }
        double chance = 1;
        matcher = CHANCE.matcher(rest);
        if (matcher.find()) {
            String value = matcher.group(3);
            Double parsed = Kt.toDoubleOrNull(value);
            // TrMenu accepts any number; 50 meaning "50%" would silently always run, so check the
            // range.
            Kt.require(
                    parsed != null && parsed >= 0 && parsed <= 1,
                    () -> at + ": {chance=} 需要 0–1 的小数（0.5 表示 50%）：" + value);
            chance = parsed;
            rest = matcher.replaceAll("");
        }
        ActionCondition condition = null;
        for (Pattern pattern : List.of(CONDITION_BRACE, CONDITION_ANGLE)) {
            matcher = pattern.matcher(rest);
            if (condition == null && matcher.find()) {
                condition =
                        ActionCondition.parse(
                                Kt.trim(matcher.group(2)), at + " {condition=}", scope.names());
                rest = matcher.replaceAll("");
            }
        }
        boolean players = false;
        ActionCondition audience = null;
        matcher = PLAYERS.matcher(rest);
        if (matcher.find()) {
            players = true;
            String filter = matcher.group(1) != null ? Kt.trim(matcher.group(1)) : "";
            if (!filter.isEmpty()) {
                audience = ActionCondition.parse(filter, at + " {players=}", null);
            }
            rest = matcher.replaceAll("");
        }
        return new Part(
                Kt.trim(rest),
                new MenuReaction.Options(delay, chance, condition, players, audience));
    }

    static String canonical(String key) {
        String lower = Kt.lower(Kt.trim(key));
        for (Map.Entry<String, Pattern> entry : ACTIONS.entrySet()) {
            if (entry.getValue().matcher(lower).matches()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static MenuReaction.Step step(
            String text, MenuReaction.Options options, String at, Scope scope) {
        int colon = text.indexOf(':');
        String key = Kt.trim(colon >= 0 ? text.substring(0, colon) : text);
        String value = colon >= 0 ? Kt.trim(text.substring(colon + 1)) : "";
        String name =
                Kt.requireNotNull(
                        canonical(key),
                        () ->
                                at
                                        + ": 不支持动作 "
                                        + key
                                        + "；可用 tell、chat、title、actionbar、tellraw、command、console、"
                                        + "connect、sound、delay、return、close、open、page、refresh"
                                        + (scope.canvas() ? "、set" : "、search"));
        Kt.require(
                !options.players() || FOR_PLAYERS.contains(name),
                () -> at + ": {players} 不能用于 " + name);
        Kt.require(
                options.delay() == 0 || !Kt.setOf("delay", "return").contains(name),
                () -> at + ": " + name + " 不接受 {delay=}");
        Kt.require(
                options.condition() == null || !name.equals("delay"),
                () -> at + ": delay 不接受 {condition=}，请用条件块包住");
        Target target = null;
        String stored =
                switch (name) {
                    case "tell", "chat", "actionbar", "tellraw" -> notBlank(name, value, at);
                    case "title" -> {
                        notBlank(name, value, at);
                        wrap(at, () -> ActionValues.title(value));
                        yield value;
                    }
                    case "command", "console" -> command(name, value, at, scope);
                    case "connect" -> {
                        notBlank(name, value, at);
                        Kt.require(value.indexOf(' ') < 0, () -> at + ": connect 只写服务器名：" + value);
                        yield value;
                    }
                    case "sound" -> {
                        notBlank(name, value, at);
                        List<ActionValues.SoundSpec> sounds =
                                wrap(at, () -> ActionValues.sounds(value));
                        if (Bukkit.getServer() != null) {
                            for (ActionValues.SoundSpec sound : sounds) {
                                Kt.require(
                                        sound.key()
                                                || ReactionRunner.soundField(sound.name()) != null,
                                        () -> at + ": 未知音效 " + sound.name());
                            }
                        }
                        yield value;
                    }
                    case "delay" -> {
                        wrap(at, () -> ActionValues.delay(value));
                        yield value;
                    }
                    case "return", "close", "refresh" -> empty(name, value, at);
                    case "search" -> {
                        Kt.require(!scope.canvas(), () -> at + ": canvas 菜单不支持 search");
                        yield empty(name, value, at);
                    }
                    case "page" -> {
                        Kt.require(scope.page() != null, () -> at + ": 这里不支持 page 跳转");
                        notBlank(name, value, at);
                        target =
                                Kt.requireNotNull(
                                        scope.page().apply(value), () -> at + ": 页面不存在 " + value);
                        yield target.value();
                    }
                    case "open" -> {
                        Kt.require(
                                scope.open() != null,
                                () -> at + ": 这里不支持 open，只能在 menus/ 目录的菜单中使用");
                        notBlank(name, value, at);
                        target =
                                Kt.requireNotNull(
                                        scope.open().apply(value),
                                        () -> at + ": 菜单或页面不存在 " + value + "（写作 菜单ID 或 菜单ID:页面ID）");
                        yield target.value();
                    }
                    case "set" -> set(value, at, scope);
                    case "template" -> {
                        Kt.require(scope.canvas(), () -> at + ": 只有 canvas 菜单支持 template");
                        List<String> ids = Kt.split(value, '/');
                        Kt.require(
                                ids.size() <= 2
                                        && ids.stream()
                                                .allMatch(id -> TEMPLATE_ID.matcher(id).matches()),
                                () -> at + ": 无效目标页面");
                        yield value;
                    }
                    default -> throw new IllegalStateException(name);
                };
        return new MenuReaction.Step(target != null ? target.name() : name, stored, options);
    }

    private static <T> T wrap(String at, java.util.function.Supplier<T> parse) {
        try {
            return parse.get();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(at + ": " + error.getMessage(), error);
        }
    }

    private static String notBlank(String name, String value, String at) {
        Kt.require(Kt.isNotBlank(value), () -> at + ": " + name + " 需要内容");
        return value;
    }

    private static String empty(String name, String value, String at) {
        Kt.require(value.isEmpty(), () -> at + ": " + name + " 不接受参数");
        return value;
    }

    private static String set(String value, String at, Scope scope) {
        Kt.require(scope.canvas(), () -> at + ": 只有 canvas 菜单支持 set");
        String variable = Kt.trim(Kt.substringBefore(value, '='));
        String assigned = Kt.trim(Kt.substringAfter(value, '=', ""));
        List<String> options = scope.variables().get(variable);
        Kt.require(
                value.indexOf('=') >= 0 && options != null && options.contains(assigned),
                () -> at + ": set 只能把 Variables 中的变量设为其列表值：" + value);
        return variable + "=" + assigned;
    }

    /** {@code ;} separates commands; it is split before any value is filled in. */
    private static String command(String name, String value, String at, Scope scope) {
        notBlank(name, value, at);
        for (String raw : Kt.split(value, ';')) {
            String command = Kt.trim(raw);
            Kt.require(
                    !command.isEmpty() && !command.startsWith("/") && command.length() <= 512,
                    () -> at + ": 指令不加 /，不能为空，最长 512 字符：" + value);
            Matcher match = COMMAND_VARIABLE.matcher(command);
            while (match.find()) {
                String variable = match.group(1);
                String token = match.group();
                Kt.require(
                        scope.commandNames().contains(variable),
                        () ->
                                at
                                        + ": 指令中的 "
                                        + token
                                        + " 未声明；可用 "
                                        + String.join("、", scope.commandNames())
                                        + " 或 %PAPI变量%");
            }
        }
        return value;
    }
}
