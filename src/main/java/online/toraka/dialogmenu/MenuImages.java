package online.toraka.dialogmenu;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Optional integrations use each plugin's public API and never load its classes when absent. */
public final class MenuImages {

    private static final Map<MenuImageRequest, MenuImage> CACHE = new HashMap<>();
    private static final Set<String> WARNINGS = new LinkedHashSet<>();

    private MenuImages() {}

    public static void warn(String message) {
        if (WARNINGS.size() < 256 && WARNINGS.add(message)) {
            MenuLog.warning(message);
        }
    }

    public static void reset() {
        CACHE.clear();
        WARNINGS.clear();
    }

    public static RichMenuText text(Player player) {
        return new RichMenuText(
                MenuRuntime.translations(), player.locale(), MenuImages::resolve, MenuImages::warn);
    }

    public static MenuImage resolve(MenuImageRequest request) {
        if (CACHE.containsKey(request)) {
            return CACHE.get(request);
        }
        MenuImage resolved;
        try {
            resolved = request.provider().equals("IA") ? itemsAdder(request) : craftEngine(request);
        } catch (Exception error) {
            String detail =
                    error.getCause() != null && error.getCause().getMessage() != null
                            ? error.getCause().getMessage()
                            : error.getMessage();
            warn(request.provider() + " 图片 " + request.id() + " 读取失败：" + detail);
            resolved = null;
        } catch (LinkageError error) {
            warn(request.provider() + " 图片 API 不兼容：" + error.getMessage());
            resolved = null;
        }
        CACHE.put(request, resolved);
        return resolved;
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static MenuImage craftEngine(MenuImageRequest request) throws Exception {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> engineClass =
                loader.loadClass("net.momirealms.craftengine.core.plugin.CraftEngine");
        Object engine = engineClass.getMethod("instance").invoke(null);
        Class<?> keyClass = loader.loadClass("net.momirealms.craftengine.core.util.Key");
        Method keyOf = keyClass.getMethod("of", String.class);
        Object manager = engineClass.getMethod("fontManager").invoke(engine);
        Object image =
                ((Optional<?>)
                                manager.getClass()
                                        .getMethod("imageById", keyClass)
                                        .invoke(manager, keyOf.invoke(null, request.id())))
                        .orElse(null);
        if (image == null) {
            return null;
        }
        // CE shades Adventure. Transfer its font/glyph as MiniMessage instead of casting it.
        String encoded =
                (String)
                        image.getClass()
                                .getMethod("miniMessageAt", int.class, int.class)
                                .invoke(image, request.row(), request.column());
        TextComponent component =
                (TextComponent) MiniMessage.miniMessage().deserialize(encoded).compact();
        Key font = Kt.requireNotNull(component.font());
        int codepoint = component.content().codePointAt(0);
        Object bitmap =
                ((Optional<?>)
                                manager.getClass()
                                        .getMethod("bitmapImageByCodepoint", keyClass, int.class)
                                        .invoke(
                                                manager,
                                                keyOf.invoke(null, font.asString()),
                                                codepoint))
                        .orElse(null);
        if (bitmap == null) {
            return null;
        }
        int[][] grid = (int[][]) invoke(bitmap, "codepointGrid");
        int row = -1;
        for (int i = 0; i < grid.length; i++) {
            if (indexOf(grid[i], codepoint) >= 0) {
                row = i;
                break;
            }
        }
        int column = indexOf(grid[row], codepoint);
        int height = (Integer) invoke(bitmap, "height");
        Key texture = Key.key(Kt.removeSuffix(invoke(bitmap, "file").toString(), ".png"));
        Kt.require(!Kt.split(texture.value(), '/').contains(".."), () -> "无效图片路径");
        Object packManager = engineClass.getMethod("packManager").invoke(engine);
        Collection<?> packs = (Collection<?>) invoke(packManager, "loadedPacks");
        List<Path> paths = new ArrayList<>();
        for (Object pack : packs) {
            if (pack == null) {
                continue;
            }
            List<Path> roots = new ArrayList<>();
            try {
                for (Object folder : (Object[]) invoke(pack, "resourcePackFolders")) {
                    if (folder instanceof Path path) {
                        roots.add(path);
                    }
                }
            } catch (NoSuchMethodException missing) {
                roots.add((Path) invoke(pack, "resourcePackFolder"));
            }
            for (Path root : roots) {
                Path candidate =
                        root.resolve(
                                "assets/"
                                        + texture.namespace()
                                        + "/textures/"
                                        + texture.value()
                                        + ".png");
                if (Files.isRegularFile(candidate)) {
                    paths.add(candidate);
                }
            }
        }
        Kt.require(!paths.isEmpty(), () -> "找不到 CE 图片源文件 " + texture);
        Set<Integer> advances = new LinkedHashSet<>();
        for (Path path : paths) {
            BufferedImage png =
                    Kt.requireNotNull(ImageIO.read(path.toFile()), () -> "无法读取图片 " + path);
            advances.add(bitmapAdvance(png, height, grid.length, grid[0].length, row, column));
        }
        Kt.require(advances.size() == 1, () -> "多个资源包定义 " + texture + "，字宽不一致");
        return new MenuImage(component, advances.iterator().next());
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static MenuImage itemsAdder(MenuImageRequest request) throws Exception {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        Class<?> type =
                plugin.getClass()
                        .getClassLoader()
                        .loadClass("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
        Object wrapper = type.getMethod("instance", String.class).invoke(null, request.id());
        if (wrapper == null) {
            return null;
        }
        String raw = (String) type.getMethod("getString").invoke(wrapper);
        int width = (Integer) type.getMethod("getWidth").invoke(wrapper);
        Kt.require(width >= 0 && width <= 1024, () -> "无效 IA 图片宽度 " + width);
        Component component =
                LegacyComponentSerializer.legacySection()
                        .deserialize(raw)
                        .font(Key.key("minecraft:default"));
        return new MenuImage(component, width + 1);
    }

    /** Minecraft's bitmap provider trims transparent right columns, then adds one pixel. */
    public static int bitmapAdvance(
            BufferedImage png, int height, int rows, int columns, int row, int column) {
        Kt.require(
                rows > 0
                        && columns > 0
                        && png.getWidth() % columns == 0
                        && png.getHeight() % rows == 0);
        Kt.require(
                row >= 0
                        && row < rows
                        && column >= 0
                        && column < columns
                        && height >= 1
                        && height <= 256);
        int cellWidth = png.getWidth() / columns;
        int cellHeight = png.getHeight() / rows;
        int inkWidth = 0;
        for (int x = 0; x < cellWidth; x++) {
            for (int y = 0; y < cellHeight; y++) {
                if ((png.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24) != 0) {
                    inkWidth = Math.max(inkWidth, x + 1);
                }
            }
        }
        return (int) Math.floor((double) inkWidth * height / cellHeight + 0.5) + 1;
    }

    /** ItemsAdder rebuilt its font images: drop the cache and redraw open menus. */
    static void itemsAdderReloaded() {
        reset();
        MenuDialog.reloaded();
        TemplateDialog.reloaded();
    }
}
