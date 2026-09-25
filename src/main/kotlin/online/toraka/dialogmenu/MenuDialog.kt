package online.toraka.dialogmenu

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.set.RegistrySet
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType

object MenuDialog {
    data class View
    @JvmOverloads
    constructor(
        val page: String,
        val dropdown: Int = -1,
        val menu: String = "settings",
        val demo: SettingsDemoSession? = null,
    ) {
        fun toggleDropdown(index: Int) = copy(dropdown = if (dropdown == index) -1 else index)

        fun collapsed() = copy(dropdown = -1)
    }

    private data class Session(
        val token: String,
        val view: View,
        val actions: Set<String>,
        val opened: Long,
        val itemGuards: Map<String, ItemDisplay> = emptyMap(),
    )

    private val sessions = mutableMapOf<UUID, Session>()

    fun forget(uuid: UUID) {
        sessions.remove(uuid)
    }

    fun shutdown() {
        sessions.keys.toList().forEach { Bukkit.getPlayer(it)?.closeDialog() }
        sessions.clear()
    }

    fun reloaded() {
        val views = sessions.mapValues { it.value.view }
        sessions.clear()
        views.forEach { (uuid, view) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            if (
                player.hasPermission("playersettings.use") &&
                    MenuRuntime.settings(view.menu) != null
            )
                show(player, view.collapsed().copy(demo = newDemo(player, view.menu)))
            else player.closeDialog()
        }
    }

    fun open(
        player: Player,
        page: String = MenuRuntime.current.defaultPage,
        menuId: String = "settings",
    ) {
        if (!player.hasPermission("playersettings.use")) return
        val menu = MenuRuntime.settings(menuId)
        if (menu == null || page !in menu.pages) {
            player.sendMessage("DialogMenu: 未启用的页面 $page")
            return
        }
        MenuResources.open(player) {
            player.closeInventory()
            TemplateDialog.forget(player.uniqueId)
            show(player, View(page, menu = menuId, demo = newDemo(player, menuId)))
        }
    }

    @taboolib.common.platform.event.SubscribeEvent
    fun quit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
    }

    @taboolib.common.platform.event.SubscribeEvent
    fun clicked(event: PlayerCustomClickEvent) {
        if (event.identifier.namespace() != "dialogmenu_settings") return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        val route = event.identifier.value()
        val query = event.dialogResponseView?.getText("query")?.take(48)
        taboolib.common.platform.function.submit {
            if (!player.isOnline || !player.hasPermission("playersettings.use")) return@submit
            val session = sessions[player.uniqueId] ?: return@submit
            if (
                !route.startsWith(session.token + "/") ||
                    System.currentTimeMillis() - session.opened > 600_000
            )
                return@submit
            val action = route.substringAfter('/')
            if (action !in session.actions) return@submit
            sessions.remove(player.uniqueId)
            val guard = session.itemGuards[action]
            if (guard != null && !ItemSources.registry.resolve(guard, player).available) {
                player.sendMessage(message(player, "setting.failed"))
                show(player, session.view.collapsed())
                return@submit
            }
            when {
                action == "search_submit" -> {
                    val found = MenuRuntime.settings(session.view.menu)?.search(query.orEmpty())
                    if (found == null) player.sendMessage(message(player, "search.empty"))
                    show(
                        player,
                        session.view.copy(page = found ?: session.view.page, dropdown = -1),
                    )
                }
                action == "search_back" -> show(player, session.view)
                action.startsWith("page/") ->
                    show(
                        player,
                        session.view.copy(page = action.substringAfter('/'), dropdown = -1),
                    )
                action.startsWith("dropdown/") -> {
                    val index = action.substringAfter('/').toIntOrNull() ?: return@submit
                    show(player, session.view.toggleDropdown(index))
                }
                action.startsWith("action/") ->
                    execute(player, session.view.collapsed(), action.substringAfter('/'))
            }
        }
    }

    private fun execute(player: Player, view: View, id: String) {
        val definition = MenuRuntime.settings(view.menu)?.actions?.get(id) ?: return
        if (
            view.demo != null &&
                (definition.permission.isEmpty() || player.hasPermission(definition.permission)) &&
                view.demo.apply(id)
        ) {
            show(player, view)
            return
        }
        executeDefinition(player, view, definition)
    }

    private fun executeDefinition(player: Player, view: View, definition: MenuAction) {
        if (definition.permission.isNotEmpty() && !player.hasPermission(definition.permission)) {
            player.sendMessage(message(player, "setting.denied"))
            show(player, view)
            return
        }
        if (definition.plugin.isNotEmpty() && EffectPlugins.provider(definition.plugin) == null) {
            player.sendMessage(message(player, "setting.failed"))
            show(player, view)
            return
        }
        if (definition.type == "page") {
            show(player, view.copy(page = definition.value, dropdown = -1))
            return
        }
        if (definition.type == "builtin") {
            when {
                definition.value == "close" -> player.closeDialog()
                definition.value == "search" -> searchDialog(player, view)
                definition.value == "refresh" -> show(player, view)
                else -> {
                    val prefs =
                        MenuPreferences.read(
                            player.persistentDataContainer,
                            MenuRuntime.settings(view.menu) ?: return,
                        )
                    val value = definition.value.substringAfter(':')
                    val next =
                        when {
                            definition.value.startsWith("language:") ->
                                prefs.copy(language = MenuLanguage.parse(value))
                            else -> prefs.copy(theme = MenuTheme.parse(value))
                        }
                    next.save(player.persistentDataContainer)
                    show(player, view)
                }
            }
            return
        }
        if (definition.close) player.closeDialog()
        for (step in definition.steps.ifEmpty { listOf(definition) }) {
            if (step.type == "builtin" || step.type == "page") {
                executeDefinition(player, view, step)
                return
            }
            val command =
                if (step.type == "toggle-command") {
                    when (
                        booleanState(
                            state(player, step.state, MenuRuntime.settings(view.menu) ?: return)
                        )
                    ) {
                        true -> step.whenTrue
                        false -> step.whenFalse
                        null -> {
                            player.sendMessage(message(player, "setting.failed"))
                            if (!definition.close) show(player, view)
                            return
                        }
                    }
                } else step.command
            val expanded = CommandTemplate.render(command, player.name, player.uniqueId.toString())
            val succeeded =
                if (step.type == "console-command")
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded)
                else player.performCommand(expanded)
            if (!succeeded) {
                player.sendMessage(message(player, "setting.failed"))
                break
            }
        }
        if (!definition.close) {
            val token = UUID.randomUUID().toString()
            sessions[player.uniqueId] = Session(token, view, emptySet(), System.currentTimeMillis())
            taboolib.common.platform.function.submit(delay = 1L) {
                if (player.isOnline && sessions[player.uniqueId]?.token == token) show(player, view)
            }
        }
    }

    private fun show(player: Player, requested: View) {
        val menu =
            MenuRuntime.settings(requested.menu)
                ?: run {
                    player.closeDialog()
                    return
                }
        val view =
            if (requested.page in menu.pages) requested
            else requested.copy(page = menu.defaultPage, dropdown = -1)
        val prefs =
            view.demo?.preferences ?: MenuPreferences.read(player.persistentDataContainer, menu)
        val token = UUID.randomUUID().toString().replace("-", "")
        val actions = linkedSetOf<String>()
        fun click(action: String): ClickEvent {
            actions += action
            return DialogClicks.custom(Key.key("dialogmenu_settings", "$token/$action"))
        }
        val close =
            if (menu.showFooter)
                ActionButton.create(
                    Component.text(expandText(player, menu.text(prefs.language, menu.footerLabel))),
                    null,
                    210,
                    DialogAction.staticAction(click("action/${menu.footerAction}")),
                )
            else null
        val page = menu.pages.getValue(view.page)
        if (page.itemLayout) {
            val rendered =
                ItemMenuRenderer.render(
                    menu,
                    page,
                    prefs.language,
                    { expandText(player, it) },
                    { ItemSources.registry.resolve(it, player) },
                    ::click,
                )
            sessions[player.uniqueId] =
                Session(token, view, actions.toSet(), System.currentTimeMillis(), rendered.guards)
            player.showDialog(
                Dialog.create { factory ->
                    factory
                        .empty()
                        .base(
                            DialogBase.builder(
                                    Component.text(
                                        expandText(player, menu.text(prefs.language, page.label))
                                    )
                                )
                                .canCloseWithEscape(true)
                                .pause(false)
                                .afterAction(DialogBase.DialogAfterAction.NONE)
                                .body(rendered.bodies)
                                .build()
                        )
                        .type(
                            if (rendered.buttons.isEmpty()) bodyOnlyType(close)
                            else DialogType.multiAction(rendered.buttons, close, 2)
                        )
                }
            )
            return
        }
        val cache = mutableMapOf<String, String?>()
        val canvas =
            MenuRenderer.render(
                menu,
                view.page,
                prefs.language,
                prefs.theme,
                { id ->
                    cache.getOrPut(id) {
                        if (view.demo != null) view.demo.state(id) else state(player, id, menu)
                    }
                },
                { expandText(player, it) },
                ::click,
                view.dropdown,
            )
        val component = canvas.build()
        sessions[player.uniqueId] =
            Session(token, view, actions.toSet(), System.currentTimeMillis())
        player.showDialog(
            Dialog.create { factory ->
                factory
                    .empty()
                    .base(
                        DialogBase.builder(
                                Component.text(
                                    expandText(player, menu.text(prefs.language, menu.title))
                                )
                            )
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.NONE)
                            .body(
                                listOf(
                                    DialogBody.plainMessage(
                                        component,
                                        if (menu.hideFocus) DialogCanvas.FRAMELESS_BODY_WIDTH
                                        else DialogCanvas.BODY_WIDTH,
                                    )
                                )
                            )
                            .build()
                    )
                    .type(bodyOnlyType(close))
            }
        )
    }

    private fun bodyOnlyType(close: ActionButton?): DialogType =
        if (close != null) DialogType.notice(close)
        else DialogType.dialogList(RegistrySet.keySet(RegistryKey.DIALOG)).build()

    private fun searchDialog(player: Player, view: View) {
        val language =
            view.demo?.preferences?.language
                ?: MenuPreferences.read(player.persistentDataContainer).language
        fun t(key: String) = MenuText.get(language, key)
        val token = UUID.randomUUID().toString().replace("-", "")
        sessions[player.uniqueId] =
            Session(token, view, setOf("search_submit", "search_back"), System.currentTimeMillis())
        fun button(label: String, action: String) =
            ActionButton.create(
                Component.text(label),
                null,
                140,
                DialogAction.customClick(Key.key("dialogmenu_settings", "$token/$action"), null),
            )
        player.showDialog(
            Dialog.create { factory ->
                factory
                    .empty()
                    .base(
                        DialogBase.builder(Component.text(t("search.title")))
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.NONE)
                            .inputs(
                                listOf(
                                    DialogInput.text("query", Component.text(t("search.keyword")))
                                        .maxLength(48)
                                        .width(280)
                                        .build()
                                )
                            )
                            .build()
                    )
                    .type(
                        DialogType.confirmation(
                            button(t("search.submit"), "search_submit"),
                            button(t("back"), "search_back"),
                        )
                    )
            }
        )
    }

    private fun newDemo(player: Player, menuId: String): SettingsDemoSession? {
        val menu = MenuRuntime.settings(menuId) ?: return null
        return if (menu.demo)
            SettingsDemoSession(menu, MenuPreferences.read(player.persistentDataContainer, menu))
        else null
    }

    private fun state(player: Player, id: String, menu: MenuDefinition): String? =
        when (val binding = menu.states.getValue(id)) {
            "language" -> MenuPreferences.read(player.persistentDataContainer, menu).language.id
            "theme" -> MenuPreferences.read(player.persistentDataContainer, menu).theme.id
            "pickup" ->
                if (EffectPlugins.provider("PickupNotifier") != null)
                    (!player.persistentDataContainer.has(
                            NamespacedKey("pickupnotifier", "disabled"),
                            PersistentDataType.BYTE,
                        ))
                        .toString()
                else null
            "loot-beams" -> lootState(player, "getBeams")?.toString()
            "loot-sounds" -> lootState(player, "getSounds")?.toString()
            else -> papi(player, binding)
        }

    private fun papi(player: Player, token: String): String? {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return null
        val value = runCatching { PapiAdapter.resolve(player, token) }.getOrNull() ?: return null
        return value
            .takeUnless { it.isBlank() || it == token }
            ?.replace(Regex("[&§][0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
    }

    private fun expandText(player: Player, template: String): String =
        Regex("%[a-zA-Z0-9_:.\\-]+%|\\{(?:player|uuid|ping|world)}")
            .replace(template) { match ->
                when (match.value) {
                    "{player}" -> player.name
                    "{uuid}" -> player.uniqueId.toString()
                    "{ping}" -> player.ping.toString()
                    "{world}" -> player.world.name
                    else -> papi(player, match.value) ?: message(player, "unavailable")
                }
            }
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")

    private fun lootState(player: Player, getter: String): Boolean? = runCatching {
        val plugin = EffectPlugins.provider("LootBeam") ?: return null
        if (!plugin.isEnabled) return null
        val cache = plugin.javaClass.classLoader.loadClass("online.toraka.lootbeam.data.PrefsCache")
        val prefs =
            cache
                .getMethod("peek", UUID::class.java)
                .invoke(cache.getField("INSTANCE").get(null), player.uniqueId) ?: return null
        prefs.javaClass.getMethod(getter).invoke(prefs) as Boolean
    }
        .getOrNull()

    private fun message(player: Player, key: String) =
        MenuText.get(MenuPreferences.read(player.persistentDataContainer).language, key)

    fun findTab(query: String): String? = MenuRuntime.current.search(query)

    fun booleanState(value: String?): Boolean? =
        when (value?.lowercase()) {
            "开",
            "开启",
            "on",
            "enabled",
            "true",
            "1" -> true
            "关",
            "关闭",
            "off",
            "disabled",
            "false",
            "0" -> false
            else -> null
        }

    fun toggleLabel(value: String, language: MenuLanguage = MenuLanguage.CHINESE) =
        MenuText.get(
            language,
            when (booleanState(value)) {
                true -> "on"
                false -> "off"
                null -> "unavailable"
            },
        )

    fun densityLabel(value: String, language: MenuLanguage = MenuLanguage.CHINESE) =
        MenuText.get(
            language,
            when (value.lowercase()) {
                "off",
                "关闭" -> "off"
                "low",
                "低" -> "density.low"
                "medium",
                "中" -> "density.medium"
                "high",
                "高" -> "density.high"
                else -> "unavailable"
            },
        )
}

object CommandTemplate {
    fun render(template: String, player: String, uuid: String): String =
        Regex("\\{player}|\\{uuid}").replace(template) {
            if (it.value == "{player}") player else uuid
        }
}
