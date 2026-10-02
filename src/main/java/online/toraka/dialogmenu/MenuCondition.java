package online.toraka.dialogmenu;

import java.util.List;
import java.util.Map;

/** Every clause must hold; a clause naming an unresolved placeholder never holds. */
public record MenuCondition(List<Clause> clauses) {

    private static final List<String> OPERATORS = Kt.listOf("!=", ">=", "<=", "=", ">", "<");

    public record Clause(String name, String operator, String value) {

        public boolean matches(Map<String, String> values) {
            String actual = values.get(name);
            if (actual == null) {
                return false;
            }
            switch (operator) {
                case "=":
                    return actual.equals(value);
                case "!=":
                    return !actual.equals(value);
                default:
                    Double number = Kt.toDoubleOrNull(actual);
                    if (number == null) {
                        return false;
                    }
                    double limit = Double.parseDouble(value);
                    switch (operator) {
                        case ">":
                            return number > limit;
                        case ">=":
                            return number >= limit;
                        case "<":
                            return number < limit;
                        default:
                            return number <= limit;
                    }
            }
        }

        /** {@code =} compares text, so a numeric match also implies that exact number. */
        Range range() {
            Double parsed = Kt.toDoubleOrNull(value);
            if (parsed == null || !Double.isFinite(parsed)) {
                return null;
            }
            double number = parsed;
            double infinity = Double.POSITIVE_INFINITY;
            switch (operator) {
                case "=":
                    return new Range(number, false, number, false);
                case ">":
                    return new Range(number, true, infinity, true);
                case ">=":
                    return new Range(number, false, infinity, true);
                case "<":
                    return new Range(-infinity, true, number, true);
                case "<=":
                    return new Range(-infinity, true, number, false);
                default:
                    return null;
            }
        }
    }

    record Range(double low, boolean lowOpen, double high, boolean highOpen) {

        boolean contains(double value) {
            return (value > low || value == low && !lowOpen)
                    && (value < high || value == high && !highOpen);
        }

        boolean intersects(Range other) {
            double low = Math.max(this.low, other.low);
            double high = Math.min(this.high, other.high);
            return low < high || low == high && contains(low) && other.contains(low);
        }
    }

    public boolean matches(Map<String, String> values) {
        for (Clause clause : clauses) {
            if (!clause.matches(values)) {
                return false;
            }
        }
        return true;
    }

    /** True only when no single set of values can satisfy both conditions. */
    public boolean excludes(MenuCondition other) {
        for (Clause a : clauses) {
            for (Clause b : other.clauses) {
                if (a.name().equals(b.name()) && disjoint(a, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Clause clause(String text) {
        int index = -1;
        for (int i = 0; i < text.length(); i++) {
            if ("!=<>".indexOf(text.charAt(i)) >= 0) {
                index = i;
                break;
            }
        }
        if (index <= 0) {
            return null;
        }
        String rest = text.substring(index);
        String operator = null;
        for (String candidate : OPERATORS) {
            if (rest.startsWith(candidate)) {
                operator = candidate;
                break;
            }
        }
        if (operator == null) {
            return null;
        }
        return new Clause(
                Kt.trim(text.substring(0, index)),
                operator,
                Kt.trim(rest.substring(operator.length())));
    }

    private static boolean disjoint(Clause a, Clause b) {
        if (a.operator().equals("=") && b.operator().equals("=")) {
            return !a.value().equals(b.value());
        }
        if (Kt.setOf(a.operator(), b.operator()).equals(Kt.setOf("=", "!="))) {
            return a.value().equals(b.value());
        }
        Range first = a.range();
        if (first == null) {
            return false;
        }
        Range second = b.range();
        if (second == null) {
            return false;
        }
        return !first.intersects(second);
    }
}
