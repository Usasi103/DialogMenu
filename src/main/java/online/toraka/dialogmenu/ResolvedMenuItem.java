package online.toraka.dialogmenu;

import org.bukkit.inventory.ItemStack;

public record ResolvedMenuItem(ItemStack item, String problem) {

    public boolean available() {
        return problem == null && item != null;
    }
}
