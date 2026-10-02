package online.toraka.dialogmenu;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Material;

/** Keep the provider separate from its input: a CE input includes its own namespace. */
public record ItemReference(String provider, String input) {

    /** Never shown as an item: an air stack cannot be displayed in a dialog body. */
    static final Set<Material> AIR_MATERIALS =
            Kt.setOf(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern VANILLA = Pattern.compile("[a-z0-9_]+");

    public static ItemReference parse(String value, String path) {
        Kt.require(
                Kt.isNotBlank(value) && value.equals(Kt.trim(value)) && Kt.noControl(value),
                () -> path + ": 需要非空物品名称");
        List<String> parts = Kt.split(value, ':', 3);
        boolean source = Kt.first(parts).equalsIgnoreCase("source");
        String provider;
        if (source) {
            Kt.require(
                    parts.size() == 3 && Kt.isNotBlank(parts.get(2)),
                    () -> path + ": 使用 source:CE:命名空间:物品ID");
            switch (Kt.lower(parts.get(1))) {
                case "minecraft", "vanilla" -> provider = "minecraft";
                default -> {
                    String found = ItemBridgeSources.provider(parts.get(1));
                    if (found == null) {
                        throw Kt.error(
                                path
                                        + ": 未知物品源 "
                                        + parts.get(1)
                                        + "，请使用 ItemBridge 插件 ID（见 ITEM-SOURCES.md）");
                    }
                    provider = found;
                }
            }
        } else {
            provider = "minecraft";
        }
        String input = source ? parts.get(2) : value;
        if (provider.equals("craftengine") || provider.equals("itemsadder")) {
            Kt.require(
                    NAMESPACED.matcher(input).matches(),
                    () -> path + ": " + provider + " 物品必须使用小写的 命名空间:物品ID");
            return new ItemReference(provider, input);
        }
        if (!provider.equals("minecraft")) {
            Kt.require(
                    Kt.isNotBlank(input) && input.equals(Kt.trim(input)) && input.indexOf('%') < 0,
                    () -> path + ": 需要物品 ID，不支持 Material 占位符");
            return new ItemReference(provider, input);
        }
        String vanilla = Kt.removePrefix(Kt.lower(input), "minecraft:");
        Kt.require(
                VANILLA.matcher(vanilla).matches(),
                () -> path + ": 原版物品格式为 minecraft:diamond 或 DIAMOND");
        Material material = Material.matchMaterial(vanilla);
        Kt.require(
                material != null
                        && !AIR_MATERIALS.contains(material)
                        && !material.name().startsWith("LEGACY_"),
                () -> path + ": 未知或空物品 " + input);
        return new ItemReference(provider, vanilla);
    }
}
