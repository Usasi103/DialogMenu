package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed TrMenu-style reaction: action lines and condition blocks, already sorted by priority.
 * Steps run one after another; {@code delay} pauses the rest and {@code return} ends the whole
 * reaction.
 */
public record MenuReaction(List<Node> nodes) {

    public static final MenuReaction EMPTY = new MenuReaction(Collections.emptyList());

    public sealed interface Node permits Step, Branch {}

    /**
     * One action. {@code name} is the canonical action ID (an alias such as {@code msg} is stored
     * as {@code tell}); {@code value} has the options removed and navigation targets resolved.
     */
    public record Step(String name, String value, Options options) implements Node {

        /** The normalised line, used by tests, link checks and the settings demo. */
        public String line() {
            return value.isEmpty() ? name : name + ": " + value;
        }
    }

    /** A condition block: {@code actions} when the condition holds, otherwise {@code deny}. */
    public record Branch(ActionCondition condition, MenuReaction actions, MenuReaction deny)
            implements Node {}

    /**
     * Line options. {@code chance} 1 always runs; {@code players} runs the action for every online
     * player ({@code audience} filters them when set).
     */
    public record Options(
            int delay,
            double chance,
            ActionCondition condition,
            boolean players,
            ActionCondition audience) {

        public static final Options NONE = new Options(0, 1, null, false, null);

        int count() {
            return (delay > 0 ? 1 : 0)
                    + (chance < 1 ? 1 : 0)
                    + (condition != null ? 1 : 0)
                    + (players ? 1 : 0);
        }
    }

    /** Every step in the tree, depth first, both branches included. */
    public List<Step> steps() {
        List<Step> result = new ArrayList<>();
        collect(this, result);
        return result;
    }

    private static void collect(MenuReaction reaction, List<Step> result) {
        for (Node node : reaction.nodes()) {
            if (node instanceof Step step) {
                result.add(step);
            } else if (node instanceof Branch branch) {
                collect(branch.actions(), result);
                collect(branch.deny(), result);
            }
        }
    }

    public List<String> lines() {
        List<String> result = new ArrayList<>();
        for (Step step : steps()) {
            result.add(step.line());
        }
        return result;
    }
}
