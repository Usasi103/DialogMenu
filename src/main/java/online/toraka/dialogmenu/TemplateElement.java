package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TemplateElement(
        String id,
        String type,
        int x,
        int row,
        int width,
        int rows,
        List<String> lines,
        DialogCanvas.Skin sprite,
        DialogCanvas.Skin selectedSprite,
        int color,
        MenuCondition condition,
        MenuCondition selected,
        String permission,
        MenuReaction reaction,
        int textSize,
        boolean bold,
        MenuImageRequest image,
        List<SpriteCase> cases) {

    /** The pre-reaction form: each {@code verb: argument} string becomes one plain step. */
    public TemplateElement(
            String id,
            String type,
            int x,
            int row,
            int width,
            int rows,
            List<String> lines,
            DialogCanvas.Skin sprite,
            DialogCanvas.Skin selectedSprite,
            int color,
            MenuCondition condition,
            MenuCondition selected,
            String permission,
            List<String> actions,
            int textSize,
            boolean bold,
            MenuImageRequest image,
            List<SpriteCase> cases) {
        this(
                id,
                type,
                x,
                row,
                width,
                rows,
                lines,
                sprite,
                selectedSprite,
                color,
                condition,
                selected,
                permission,
                plain(actions),
                textSize,
                bold,
                image,
                cases);
    }

    /** Kotlin defaults: 8 px regular text, no image and no sprite cases. */
    public TemplateElement(
            String id,
            String type,
            int x,
            int row,
            int width,
            int rows,
            List<String> lines,
            DialogCanvas.Skin sprite,
            DialogCanvas.Skin selectedSprite,
            int color,
            MenuCondition condition,
            MenuCondition selected,
            String permission,
            List<String> actions) {
        this(
                id,
                type,
                x,
                row,
                width,
                rows,
                lines,
                sprite,
                selectedSprite,
                color,
                condition,
                selected,
                permission,
                plain(actions),
                8,
                false,
                null,
                Collections.emptyList());
    }

    private static MenuReaction plain(List<String> actions) {
        List<MenuReaction.Node> steps = new ArrayList<>();
        for (String action : actions) {
            steps.add(
                    new MenuReaction.Step(
                            Kt.trim(Kt.substringBefore(action, ':')),
                            Kt.trim(Kt.substringAfter(action, ':', "")),
                            MenuReaction.Options.NONE));
        }
        return new MenuReaction(steps);
    }

    /** Every action line of the reaction, normalised ({@code page: x} reads {@code template: ...}). */
    public List<String> actions() {
        return reaction.lines();
    }

    /** {@code copy(actions = actions)}. */
    public TemplateElement withActions(List<String> replacement) {
        return new TemplateElement(
                id,
                type,
                x,
                row,
                width,
                rows,
                lines,
                sprite,
                selectedSprite,
                color,
                condition,
                selected,
                permission,
                plain(replacement),
                textSize,
                bold,
                image,
                cases);
    }
}
