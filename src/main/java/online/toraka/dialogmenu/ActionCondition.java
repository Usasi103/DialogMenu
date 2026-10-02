package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The condition of a reaction block or a {@code {condition=...}} option. It keeps DialogMenu's
 * own clause syntax ({@code name=value}, {@code name>=number}) and adds two TrMenu-style forms:
 * a full PlaceholderAPI token on the left ({@code %player_level% >= 10}) and {@code perm node}.
 * Every clause must hold; {@code not} in front of a clause negates it. A placeholder that cannot
 * be resolved never holds, negated or not.
 */
public record ActionCondition(List<Clause> clauses) {

    private static final Pattern TOKEN = Pattern.compile("%[a-zA-Z0-9_:.\\-]{1,254}%");
    private static final Pattern NODE = Pattern.compile("[a-zA-Z0-9_.\\-*]{1,128}");

    /** One clause; {@code compare} is null for a permission clause. */
    public record Clause(boolean negated, String permission, MenuCondition.Clause compare) {

        boolean placeholder() {
            return compare != null && TOKEN.matcher(compare.name()).matches();
        }
    }

    /**
     * Parses a condition string or list. {@code names} checks a declared name clause and returns
     * an error message, or null when the clause is valid; null {@code names} means the menu
     * declares no names, so only placeholder and permission clauses are allowed.
     */
    public static ActionCondition parse(
            Object raw, String at, Function<MenuCondition.Clause, String> names) {
        List<String> texts = new ArrayList<>();
        if (raw instanceof String text) {
            texts.add(text);
        } else if (raw instanceof List<?> list
                && list.stream().allMatch(it -> it instanceof String)) {
            for (Object item : list) {
                texts.add((String) item);
            }
        } else {
            throw new IllegalArgumentException(at + ": 条件写作字符串或字符串列表");
        }
        Kt.require(texts.size() >= 1 && texts.size() <= 8, () -> at + ": 需要 1–8 条条件");
        List<Clause> clauses = new ArrayList<>();
        for (String text : texts) {
            clauses.add(clause(Kt.trim(text), at, names));
        }
        return new ActionCondition(clauses);
    }

    private static Clause clause(
            String text, String at, Function<MenuCondition.Clause, String> names) {
        Kt.require(
                !text.isEmpty() && text.length() <= 512 && Kt.noControl(text),
                () -> at + ": 条件不能为空，且为单行文字");
        boolean negated = false;
        String body = text;
        if (Kt.lower(body).startsWith("not ")) {
            negated = true;
            body = Kt.trim(body.substring(4));
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.startsWith("perm ") || lower.startsWith("permission ")) {
            // Kether writes literals as *node; accept it so TrMenu conditions paste as-is.
            String node = Kt.removePrefix(Kt.trim(Kt.substringAfter(body, ' ')), "*");
            Kt.require(NODE.matcher(node).matches(), () -> at + ": perm 后面写权限节点：" + text);
            return new Clause(negated, node, null);
        }
        MenuCondition.Clause compare =
                Kt.requireNotNull(
                        MenuCondition.clause(body),
                        () -> at + ": 条件写作 名称=值、%PAPI变量% >= 数字 或 perm 权限节点：" + text);
        Kt.require(!compare.value().startsWith("="), () -> at + ": 比较只用一个 =：" + text);
        if (TOKEN.matcher(compare.name()).matches()) {
            Double number = Kt.toDoubleOrNull(compare.value());
            Kt.require(
                    Kt.setOf("=", "!=").contains(compare.operator())
                            || number != null && Double.isFinite(number),
                    () -> at + ": > >= < <= 右侧需要数字：" + text);
        } else {
            String problem = names != null ? names.apply(compare) : "只能比较完整的 %PAPI变量%，或写 perm 权限节点";
            if (problem != null) {
                throw new IllegalArgumentException(at + ": " + problem + "：" + text);
            }
        }
        return new Clause(negated, null, compare);
    }

    /**
     * {@code permission} answers perm clauses, {@code papi} resolves a full token (null when
     * unavailable) and {@code named} holds the menu's declared values.
     */
    public boolean test(
            Function<String, Boolean> permission,
            Function<String, String> papi,
            Map<String, String> named) {
        for (Clause clause : clauses) {
            if (clause.permission() != null) {
                if (permission.apply(clause.permission()) == clause.negated()) {
                    return false;
                }
                continue;
            }
            MenuCondition.Clause compare = clause.compare();
            Map<String, String> values = named;
            if (clause.placeholder()) {
                String value = papi.apply(compare.name());
                if (value == null) {
                    return false;
                }
                values = Map.of(compare.name(), Kt.trim(value));
            } else if (!named.containsKey(compare.name())) {
                return false;
            }
            if (compare.matches(values) == clause.negated()) {
                return false;
            }
        }
        return true;
    }

    /** Declared names a clause reads, for callers that resolve values lazily. */
    public boolean readsNames() {
        for (Clause clause : clauses) {
            if (clause.compare() != null && !clause.placeholder()) {
                return true;
            }
        }
        return false;
    }
}
