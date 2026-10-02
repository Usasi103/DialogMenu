package online.toraka.dialogmenu;

public record ItemDisplay(ItemReference material, ItemReference fallback, int amount, String path) {

    /** {@code copy(fallback = fallback)}. */
    public ItemDisplay withFallback(ItemReference replacement) {
        return new ItemDisplay(material, replacement, amount, path);
    }
}
