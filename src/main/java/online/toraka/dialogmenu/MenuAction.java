package online.toraka.dialogmenu;

import java.util.Collections;
import java.util.List;

/**
 * A compiled settings action. {@code reaction} is set for an Actions list of the simple format;
 * the other types (built-ins, page, compiled player / console / toggle commands) leave it null.
 */
public record MenuAction(
        String type,
        String command,
        String whenTrue,
        String whenFalse,
        String state,
        String value,
        String permission,
        String plugin,
        boolean close,
        List<MenuAction> steps,
        MenuReaction reaction) {

    /** The pre-reaction form. */
    public MenuAction(
            String type,
            String command,
            String whenTrue,
            String whenFalse,
            String state,
            String value,
            String permission,
            String plugin,
            boolean close,
            List<MenuAction> steps) {
        this(
                type,
                command,
                whenTrue,
                whenFalse,
                state,
                value,
                permission,
                plugin,
                close,
                steps,
                null);
    }

    /** Kotlin default: no sequence steps. */
    public MenuAction(
            String type,
            String command,
            String whenTrue,
            String whenFalse,
            String state,
            String value,
            String permission,
            String plugin,
            boolean close) {
        this(
                type,
                command,
                whenTrue,
                whenFalse,
                state,
                value,
                permission,
                plugin,
                close,
                Collections.emptyList(),
                null);
    }

    /** {@code copy(type = type, steps = steps)}. */
    public MenuAction withSequence(String type, List<MenuAction> steps) {
        return new MenuAction(
                type,
                command,
                whenTrue,
                whenFalse,
                state,
                value,
                permission,
                plugin,
                close,
                steps,
                reaction);
    }

    public MenuAction withReaction(MenuReaction replacement) {
        return new MenuAction(
                type,
                command,
                whenTrue,
                whenFalse,
                state,
                value,
                permission,
                plugin,
                close,
                steps,
                replacement);
    }
}
