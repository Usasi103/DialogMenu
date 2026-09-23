package online.toraka.playersettings

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType

object PlayerSettings : taboolib.common.platform.Plugin() {
    override fun onDisable() {
        SettingsDialog.shutdown()
    }
}

object SettingsDialog {
    enum class Tab(val textKey: String) {
        PROFILE("tab.profile"),
        SOUND("tab.sound"),
        PARTICLES("tab.particles"),
        NOTICES("tab.notices"),
        LOOT("tab.loot"),
        APPEARANCE("tab.appearance"),
        HELP("tab.help"),
    }

    data class View(val tab: Tab = Tab.PARTICLES)

    private data class Session(
        val token: String,
        val view: View,
        val actions: Set<String>,
        val opened: Long,
    )

    private val sessions = mutableMapOf<UUID, Session>()

    fun shutdown() {
        sessions.keys.forEach { Bukkit.getPlayer(it)?.closeDialog() }
        sessions.clear()
    }

    fun open(sender: Player) {
        if (!sender.hasPermission("playersettings.use")) return
        if (
            sender.resourcePackStatus !=
                org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
        ) {
            sender.sendMessage(message(sender, "pack.required"))
            return
        }
        sender.closeInventory()
        show(sender, View())
    }

    @taboolib.common.platform.event.SubscribeEvent
    fun quit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
    }

    @taboolib.common.platform.event.SubscribeEvent
    fun clicked(event: PlayerCustomClickEvent) {
        if (event.identifier.namespace() != "toraka_settings") return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        val route = event.identifier.value()
        val search = event.dialogResponseView?.getText("query")?.take(48)
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
            when {
                action == "close" -> player.closeDialog()
                action == "main" -> {
                    player.closeDialog()
                    player.performCommand("menu")
                }
                action == "search" -> searchDialog(player, session.view)
                action == "search_submit" -> {
                    val found = findTab(search.orEmpty())
                    if (found == null) player.sendMessage(message(player, "search.empty"))
                    show(player, View(found ?: session.view.tab))
                }
                action.startsWith("tab_") ->
                    show(player, View(Tab.valueOf(action.removePrefix("tab_").uppercase())))
                action.startsWith("language_") -> {
                    val prefs = MenuPreferences.read(player.persistentDataContainer)
                    prefs
                        .copy(language = MenuLanguage.parse(action.removePrefix("language_")))
                        .save(player.persistentDataContainer)
                    show(player, session.view)
                }
                action.startsWith("theme_") -> {
                    val prefs = MenuPreferences.read(player.persistentDataContainer)
                    prefs
                        .copy(theme = MenuTheme.parse(action.removePrefix("theme_")))
                        .save(player.persistentDataContainer)
                    show(player, session.view)
                }
                action == "refresh" -> show(player, session.view)
                else -> {
                    val cmd = commandFor(action, player)
                    if (cmd != null && !player.performCommand(cmd))
                        player.sendMessage(message(player, "setting.failed"))
                    val token = UUID.randomUUID().toString()
                    sessions[player.uniqueId] =
                        Session(token, session.view, emptySet(), System.currentTimeMillis())
                    taboolib.common.platform.function.submit(delay = 1L) {
                        if (player.isOnline && sessions[player.uniqueId]?.token == token)
                            show(player, session.view)
                    }
                }
            }
        }
    }

    private fun show(player: Player, view: View) {
        val prefs = MenuPreferences.read(player.persistentDataContainer)
        val language = prefs.language
        val theme = prefs.theme
        fun t(key: String, vararg args: Any) = MenuText.get(language, key, *args)
        val token = UUID.randomUUID().toString().replace("-", "")
        val actions = linkedSetOf("close")
        fun click(action: String): ClickEvent<*> {
            actions += action
            return ClickEvent.custom(Key.key("toraka_settings", "$token/$action"))
        }
        val canvas = DialogCanvas(theme, ::click)
        canvas.sprite(114, 0, DialogCanvas.PANEL_TOP)
        canvas.sprite(114, 10, DialogCanvas.PANEL_BOTTOM)
        canvas.button(0, 0, DialogCanvas.SEARCH, t("search.button"), "search")
        canvas.sprite(6, 0, DialogCanvas.SEARCH_ICON, "search")
        // The small blue corner controls are part of the reference layout.  Keep
        // them live so they also provide a reliable click target for a redraw.
        canvas.sprite(433, 1, DialogCanvas.PANEL_ACTION, "refresh")
        canvas.sprite(433, 11, DialogCanvas.PANEL_ACTION, "refresh")
        Tab.entries.forEachIndexed { index, tab ->
            canvas.button(
                0,
                3 + index * 2,
                if (view.tab == tab) DialogCanvas.SELECTED_NAV else DialogCanvas.NAV,
                t(tab.textKey),
                "tab_${tab.name.lowercase()}",
            )
            val icon =
                when (tab) {
                    Tab.APPEARANCE -> 0xE095
                    Tab.HELP -> 0xE094
                    else -> 0xE090 + index
                }
            canvas.sprite(6, 3 + index * 2, DialogCanvas.Skin(icon, 9, 1))
        }
        canvas.button(0, 19, DialogCanvas.NAV, t("main"), "main")
        canvas.button(0, 22, DialogCanvas.NAV, t("refresh"), "refresh")
        fun title(row: Int, text: String) = canvas.text(122, row, text, theme.heading)
        fun info(row: Int, text: String) =
            canvas.text(123, row, DialogCanvas.fit(text, 316), theme.muted)
        fun control(
            row: Int,
            label: String,
            value: String,
            action: String,
            active: Boolean = false,
        ) {
            canvas.text(123, row + 1, DialogCanvas.fit(label, 201))
            canvas.button(
                330,
                row,
                if (active) DialogCanvas.SELECTED_CONTROL else DialogCanvas.CONTROL,
                value,
                action,
            )
        }
        fun state(id: String) = papi(player, "torakaambience_$id")
        fun isOn(value: String) =
            value.equals("on", true) ||
                value.equals("enabled", true) ||
                value == "1" ||
                value == "true" ||
                value == "开" ||
                value == "开启"
        when (view.tab) {
            Tab.PARTICLES -> {
                title(1, t("tab.particles"))
                control(
                    4,
                    t("particles.show"),
                    toggleLabel(state("particles"), language),
                    "particles",
                    isOn(state("particles")),
                )
                info(7, t("particles.scope"))
                title(11, t("particles.categories"))
                control(
                    13,
                    t("particles.leaves"),
                    toggleLabel(state("category_leaves"), language),
                    "leaves",
                    isOn(state("category_leaves")),
                )
                control(
                    15,
                    t("particles.firefly"),
                    toggleLabel(state("category_firefly"), language),
                    "firefly",
                    isOn(state("category_firefly")),
                )
                control(
                    17,
                    t("particles.biome"),
                    toggleLabel(state("category_biome"), language),
                    "biome",
                    isOn(state("category_biome")),
                )
                canvas.text(123, 20, t("particles.density"), raised = true)
                canvas.densitySlider(280, 19, state("density_id"), language)
                info(22, t("particles.disabled"))
            }
            Tab.SOUND -> {
                title(1, t("tab.sound"))
                control(
                    4,
                    t("sound.label"),
                    toggleLabel(state("sounds"), language),
                    "sounds",
                    isOn(state("sounds")),
                )
                info(7, t("sound.scope"))
                title(11, t("sound.about"))
                info(14, t("sound.saved"))
                info(17, t("sound.others"))
                info(20, t("sound.volume"))
            }
            Tab.PROFILE -> {
                title(1, t("tab.profile"))
                info(4, t("profile.player", player.name))
                info(6, t("profile.level", papi(player, "playerlevel_level"), player.ping))
                title(11, t("profile.assets"))
                info(13, t("profile.coins", papi(player, "excellenteconomy_balance_coin")))
                info(15, t("profile.balance", papi(player, "excellenteconomy_balance_money")))
                info(17, t("profile.fishcoins", papi(player, "excellenteconomy_balance_fishcoin")))
                info(
                    19,
                    t(
                        "profile.collection",
                        papi(player, "handbook_total"),
                        papi(player, "handbook_max"),
                    ),
                )
                info(21, t("profile.world", player.world.name))
            }
            Tab.NOTICES -> {
                val on =
                    !player.persistentDataContainer.has(
                        NamespacedKey("pickupnotifier", "disabled"),
                        PersistentDataType.BYTE,
                    )
                title(1, t("tab.notices"))
                control(4, t("pickup.show"), if (on) t("on") else t("off"), "pickup", on)
                info(7, t("pickup.scope"))
                title(11, t("pickup.about"))
                info(14, t("pickup.disabled"))
                info(17, t("pickup.requires"))
                info(20, t("pickup.saved"))
            }
            Tab.LOOT -> {
                val beams = lootState(player, "getBeams")
                val sounds = lootState(player, "getSounds")
                title(1, t("tab.loot"))
                control(4, t("loot.show"), flag(beams, language), "beam", beams == true)
                info(7, t("loot.scope"))
                title(11, t("loot.sounds"))
                control(14, t("loot.play"), flag(sounds, language), "beam_sounds", sounds == true)
                info(18, t("loot.separate"))
                info(20, t("loot.personal"))
            }
            Tab.APPEARANCE -> {
                title(1, t("appearance.language"))
                MenuLanguage.entries.forEachIndexed { index, option ->
                    control(
                        3 + index * 2,
                        option.label,
                        option.label,
                        "language_${option.id}",
                        language == option,
                    )
                }
                info(7, t("appearance.language_scope"))
                title(11, t("appearance.theme"))
                MenuTheme.entries.forEachIndexed { index, option ->
                    control(
                        13 + index * 2,
                        t("appearance.${option.id}"),
                        t("appearance.${option.id}"),
                        "theme_${option.id}",
                        theme == option,
                    )
                }
                info(18, t("appearance.saved"))
                info(20, t("appearance.personal"))
            }
            Tab.HELP -> {
                title(1, t("menu.title"))
                info(4, t("help.navigation"))
                info(7, t("help.selected"))
                title(11, t("help.pack"))
                info(14, t("help.requires"))
                info(17, t("help.scale"))
                info(20, t("help.close"))
            }
        }
        val component = canvas.build()
        val close =
            ActionButton.create(
                Component.text(t("close")),
                null,
                210,
                DialogAction.staticAction(click("close")),
            )
        sessions[player.uniqueId] =
            Session(token, view, actions.toSet(), System.currentTimeMillis())
        player.showDialog(
            Dialog.create { factory ->
                factory
                    .empty()
                    .base(
                        DialogBase.builder(Component.text(t("menu.title")))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.NONE)
                            .body(
                                listOf(
                                    DialogBody.plainMessage(
                                        component,
                                        DialogCanvas.FRAMELESS_BODY_WIDTH,
                                    )
                                )
                            )
                            .build()
                    )
                    .type(DialogType.notice(close))
            }
        )
    }

    private fun searchDialog(player: Player, view: View) {
        val language = MenuPreferences.read(player.persistentDataContainer).language
        fun t(key: String) = MenuText.get(language, key)
        val token = UUID.randomUUID().toString().replace("-", "")
        sessions[player.uniqueId] =
            Session(token, view, setOf("search_submit", "refresh"), System.currentTimeMillis())
        fun button(label: String, action: String) =
            ActionButton.create(
                Component.text(label),
                null,
                140,
                DialogAction.customClick(Key.key("toraka_settings", "$token/$action"), null),
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
                            button(t("back"), "refresh"),
                        )
                    )
            }
        )
    }

    private fun commandFor(action: String, player: Player): String? =
        when (action) {
            "particles" -> "particles toggle"
            "sounds" -> "particles sounds toggle"
            "leaves",
            "firefly",
            "biome" -> "particles category $action toggle"
            "density_off",
            "density_low",
            "density_medium",
            "density_high" -> "particles density ${action.substringAfter('_')}"
            "pickup" ->
                if (
                    player.persistentDataContainer.has(
                        NamespacedKey("pickupnotifier", "disabled"),
                        PersistentDataType.BYTE,
                    )
                )
                    "pn on"
                else "pn off"
            "beam" ->
                when (lootState(player, "getBeams")) {
                    true -> "lootbeam off"
                    false -> "lootbeam on"
                    null -> null
                }
            "beam_sounds" -> "lootbeam sounds"
            else -> null
        }

    private fun message(player: Player, key: String): String =
        MenuText.get(MenuPreferences.read(player.persistentDataContainer).language, key)

    private fun papi(player: Player, id: String): String {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            return message(player, "unavailable")
        val result = PapiAdapter.resolve(player, "%$id%")
        return if (result.isBlank() || result == "%$id%") message(player, "unavailable")
        else result.replace(Regex("[&§][0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
    }

    private fun lootState(player: Player, getter: String): Boolean? = runCatching {
        val plugin = Bukkit.getPluginManager().getPlugin("LootBeam") ?: return null
        val cache = plugin.javaClass.classLoader.loadClass("online.toraka.lootbeam.data.PrefsCache")
        val prefs =
            cache
                .getMethod("peek", UUID::class.java)
                .invoke(cache.getField("INSTANCE").get(null), player.uniqueId) ?: return null
        prefs.javaClass.getMethod(getter).invoke(prefs) as Boolean
    }
        .getOrNull()

    private fun flag(value: Boolean?, language: MenuLanguage) =
        when (value) {
            true -> MenuText.get(language, "on")
            false -> MenuText.get(language, "off")
            null -> MenuText.get(language, "unavailable")
        }

    fun toggleLabel(value: String, language: MenuLanguage = MenuLanguage.CHINESE): String =
        when (value.lowercase()) {
            "开",
            "开启",
            "on",
            "enabled",
            "true",
            "1" -> MenuText.get(language, "on")
            "关",
            "关闭",
            "off",
            "disabled",
            "false",
            "0" -> MenuText.get(language, "off")
            else -> MenuText.get(language, "unavailable")
        }

    fun densityLabel(value: String, language: MenuLanguage = MenuLanguage.CHINESE): String =
        when (value.lowercase()) {
            "off",
            "关闭" -> MenuText.get(language, "off")
            "low",
            "低" -> MenuText.get(language, "density.low")
            "medium",
            "中" -> MenuText.get(language, "density.medium")
            "high",
            "高" -> MenuText.get(language, "density.high")
            else -> MenuText.get(language, "unavailable")
        }

    fun findTab(query: String): Tab? =
        when {
            query.isBlank() -> null
            listOf("界面", "语言", "主题", "亮色", "暗色", "language", "theme", "light", "dark", "appearance")
                .any { query.contains(it, true) } -> Tab.APPEARANCE
            listOf("掉落", "光柱", "loot", "beam", "drop").any { query.contains(it, true) } -> Tab.LOOT
            listOf("玩家", "信息", "等级", "金币", "余额", "图鉴", "profile", "player", "info", "level", "coin")
                .any { query.contains(it, true) } -> Tab.PROFILE
            listOf("声音", "音效", "鸟鸣", "风声", "sound", "ambience", "audio").any {
                query.contains(it, true)
            } -> Tab.SOUND
            listOf("粒子", "萤火虫", "落叶", "密度", "particle", "firefly", "leaves", "density").any {
                query.contains(it, true)
            } -> Tab.PARTICLES
            listOf("拾取", "提示", "通知", "pickup", "notice", "notification").any {
                query.contains(it, true)
            } -> Tab.NOTICES
            listOf("帮助", "资源包", "缩放", "help", "resource", "scale").any {
                query.contains(it, true)
            } -> Tab.HELP
            else -> null
        }
}
