package online.toraka.dialogmenu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface MenuItemSource {
    /** Null means available; no player-dependent item is constructed during validation. */
    String problem(String input);

    ItemStack build(String input, Player player);
}
