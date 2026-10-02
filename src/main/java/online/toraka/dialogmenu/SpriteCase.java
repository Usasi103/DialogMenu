package online.toraka.dialogmenu;

/** One alternative look for a sprite; the element's own look is the fallback. */
public record SpriteCase(
        MenuCondition condition, DialogCanvas.Skin sprite, MenuImageRequest image) {}
