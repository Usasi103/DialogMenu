package online.toraka.dialogmenu

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import javax.imageio.ImageIO
import kotlin.math.floor
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

/** Optional integrations use each plugin's public API and never load its classes when absent. */
object MenuImages {
    private val cache = mutableMapOf<MenuImageRequest, MenuImage?>()
    private val warnings = linkedSetOf<String>()

    fun warn(message: String) {
        if (warnings.size < 256 && warnings.add(message)) MenuLog.warning(message)
    }

    fun reset() {
        cache.clear()
        warnings.clear()
    }

    fun text(player: Player): RichMenuText =
        RichMenuText(MenuRuntime.translations, player.locale(), ::resolve, ::warn)

    fun resolve(request: MenuImageRequest): MenuImage? {
        if (request in cache) return cache[request]
        val resolved =
            try {
                if (request.provider == "IA") itemsAdder(request) else craftEngine(request)
            } catch (error: Exception) {
                warn(
                    "${request.provider} 图片 ${request.id} 读取失败：${error.cause?.message ?: error.message}"
                )
                null
            } catch (error: LinkageError) {
                warn("${request.provider} 图片 API 不兼容：${error.message}")
                null
            }
        cache[request] = resolved
        return resolved
    }

    private fun invoke(target: Any, name: String): Any =
        target.javaClass.getMethod(name).invoke(target)

    private fun craftEngine(request: MenuImageRequest): MenuImage? {
        val plugin =
            Bukkit.getPluginManager().getPlugin("CraftEngine")?.takeIf { it.isEnabled }
                ?: return null
        val loader = plugin.javaClass.classLoader
        val engineClass = loader.loadClass("net.momirealms.craftengine.core.plugin.CraftEngine")
        val engine = engineClass.getMethod("instance").invoke(null)
        val keyClass = loader.loadClass("net.momirealms.craftengine.core.util.Key")
        fun key(value: String) = keyClass.getMethod("of", String::class.java).invoke(null, value)
        val manager = engineClass.getMethod("fontManager").invoke(engine)
        val image =
            (manager.javaClass.getMethod("imageById", keyClass).invoke(manager, key(request.id))
                    as Optional<*>)
                .orElse(null) ?: return null
        // CE shades Adventure. Transfer its font/glyph as MiniMessage instead of casting it.
        val encoded =
            image.javaClass
                .getMethod(
                    "miniMessageAt",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(image, request.row, request.column) as String
        val component = MiniMessage.miniMessage().deserialize(encoded).compact() as TextComponent
        val font = requireNotNull(component.font())
        val codepoint = component.content().codePointAt(0)
        val bitmap =
            (manager.javaClass
                    .getMethod("bitmapImageByCodepoint", keyClass, Int::class.javaPrimitiveType)
                    .invoke(manager, key(font.asString()), codepoint) as Optional<*>)
                .orElse(null) ?: return null
        val grid = invoke(bitmap, "codepointGrid") as Array<IntArray>
        val row = grid.indexOfFirst { codepoint in it }
        val column = grid[row].indexOf(codepoint)
        val height = invoke(bitmap, "height") as Int
        val texture = Key.key(invoke(bitmap, "file").toString().removeSuffix(".png"))
        require(texture.value().split('/').none { it == ".." }) { "无效图片路径" }
        val packManager = engineClass.getMethod("packManager").invoke(engine)
        val packs = invoke(packManager, "loadedPacks") as Collection<*>
        val paths =
            packs
                .filterNotNull()
                .flatMap { pack ->
                    val roots =
                        try {
                            (invoke(pack, "resourcePackFolders") as Array<*>).filterIsInstance<
                                Path
                            >()
                        } catch (_: NoSuchMethodException) {
                            listOf(invoke(pack, "resourcePackFolder") as Path)
                        }
                    roots.map {
                        it.resolve("assets/${texture.namespace()}/textures/${texture.value()}.png")
                    }
                }
                .filter { Files.isRegularFile(it) }
        require(paths.isNotEmpty()) { "找不到 CE 图片源文件 $texture" }
        val advances =
            paths
                .map { path ->
                    val png = requireNotNull(ImageIO.read(path.toFile())) { "无法读取图片 $path" }
                    bitmapAdvance(png, height, grid.size, grid[0].size, row, column)
                }
                .distinct()
        require(advances.size == 1) { "多个资源包定义 $texture，字宽不一致" }
        return MenuImage(component, advances.single())
    }

    private fun itemsAdder(request: MenuImageRequest): MenuImage? {
        val plugin =
            Bukkit.getPluginManager().getPlugin("ItemsAdder")?.takeIf { it.isEnabled }
                ?: return null
        val type =
            plugin.javaClass.classLoader.loadClass(
                "dev.lone.itemsadder.api.FontImages.FontImageWrapper"
            )
        val wrapper =
            type.getMethod("instance", String::class.java).invoke(null, request.id) ?: return null
        val raw = type.getMethod("getString").invoke(wrapper) as String
        val width = type.getMethod("getWidth").invoke(wrapper) as Int
        require(width in 0..1024) { "无效 IA 图片宽度 $width" }
        val component =
            LegacyComponentSerializer.legacySection()
                .deserialize(raw)
                .font(Key.key("minecraft:default"))
        return MenuImage(component, width + 1)
    }

    /** Minecraft's bitmap provider trims transparent right columns, then adds one pixel. */
    fun bitmapAdvance(
        png: BufferedImage,
        height: Int,
        rows: Int,
        columns: Int,
        row: Int,
        column: Int,
    ): Int {
        require(rows > 0 && columns > 0 && png.width % columns == 0 && png.height % rows == 0)
        require(row in 0 until rows && column in 0 until columns && height in 1..256)
        val cellWidth = png.width / columns
        val cellHeight = png.height / rows
        var inkWidth = 0
        for (x in 0 until cellWidth) for (y in 0 until cellHeight) {
            if ((png.getRGB(column * cellWidth + x, row * cellHeight + y) ushr 24) != 0)
                inkWidth = maxOf(inkWidth, x + 1)
        }
        return floor(inkWidth.toDouble() * height / cellHeight + 0.5).toInt() + 1
    }

    @SubscribeEvent(bind = "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent")
    fun itemsAdderReloaded(event: OptionalEvent) {
        submit {
            reset()
            MenuDialog.reloaded()
            TemplateDialog.reloaded()
        }
    }
}
