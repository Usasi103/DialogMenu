package online.toraka.dialogmenu

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.set.RegistrySet
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent

object TemplateDialog {
    private data class Session(
        val token: String,
        val template: String,
        val values: Map<String, String>,
        val actions: Set<String>,
        val opened: Long,
    )

    private val sessions = mutableMapOf<UUID, Session>()

    fun open(player: Player, requested: String) {
        val id = MenuRuntime.resolveTemplate(requested)
        if (!player.hasPermission("playersettings.use")) return
        if (id !in MenuRuntime.templates) {
            player.sendMessage("DialogMenu：模板不存在 $id")
            return
        }
        MenuResources.open(player) {
            MenuDialog.forget(player.uniqueId)
            player.closeInventory()
            show(player, id, emptyMap())
        }
    }

    fun forget(uuid: UUID) {
        sessions.remove(uuid)
    }

    fun shutdown() {
        sessions.keys.toList().forEach { Bukkit.getPlayer(it)?.closeDialog() }
        sessions.clear()
    }

    fun reloaded() {
        val previous = sessions.toMap()
        sessions.clear()
        previous.forEach { (uuid, session) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            if (
                session.template in MenuRuntime.templates &&
                    player.hasPermission("playersettings.use")
            )
                show(player, session.template, session.values)
            else player.closeDialog()
        }
    }

    private fun show(player: Player, id: String, previous: Map<String, String>) {
        val template = MenuRuntime.templates.getValue(id)
        val values = template.values(previous)
        val token = UUID.randomUUID().toString().replace("-", "")
        val actions = linkedSetOf<String>()
        val canvas =
            TemplateRenderer.render(
                template,
                values,
                { TemplateRenderer.expand(it, values, player.name, player.uniqueId.toString()) },
                { action ->
                    actions += action
                    ClickEvent.custom(Key.key("dialogmenu_dialogue", "$token/$action"))
                },
            )
        val scale = canvas.supportedScale(MenuPreferences.readScale(player.persistentDataContainer))
        val content = canvas.build(scale)
        sessions[player.uniqueId] = Session(token, id, values, actions, System.currentTimeMillis())
        player.showDialog(
            Dialog.create { factory ->
                factory
                    .empty()
                    .base(
                        DialogBase.builder(Component.empty())
                            .externalTitle(Component.text(template.title))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.NONE)
                            .body(
                                listOf(
                                    DialogBody.plainMessage(
                                        content,
                                        scale.bodyWidth(template.width, template.hideFocus),
                                    )
                                )
                            )
                            .build()
                    )
                    .type(
                        // An empty dialog list has no native buttons or exit action.
                        // The canvas supplies its own close button; ESC remains available.
                        DialogType.dialogList(RegistrySet.keySet(RegistryKey.DIALOG)).build()
                    )
            }
        )
    }

    @taboolib.common.platform.event.SubscribeEvent
    fun quit(event: PlayerQuitEvent) {
        forget(event.player.uniqueId)
    }

    @taboolib.common.platform.event.SubscribeEvent
    fun clicked(event: PlayerCustomClickEvent) {
        if (event.identifier.namespace() != "dialogmenu_dialogue") return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        val route = event.identifier.value()
        taboolib.common.platform.function.submit {
            if (!player.isOnline || !player.hasPermission("playersettings.use")) return@submit
            val session = sessions[player.uniqueId] ?: return@submit
            val id = route.substringAfter('/')
            if (
                !route.startsWith(session.token + "/") ||
                    id !in session.actions ||
                    System.currentTimeMillis() - session.opened > 600_000
            )
                return@submit
            val template = MenuRuntime.templates[session.template] ?: return@submit
            val element = template.elements.firstOrNull { it.id == id } ?: return@submit
            if (!TemplateRenderer.visible(element, session.values)) return@submit
            sessions.remove(player.uniqueId)
            if (element.permission.isNotEmpty() && !player.hasPermission(element.permission)) {
                player.sendMessage("你没有权限执行此操作。")
                show(player, template.id, session.values)
                return@submit
            }
            val values = session.values.toMutableMap()
            for (action in element.actions) {
                val verb = action.substringBefore(':').trim()
                val argument = action.substringAfter(':', "").trim()
                val expanded =
                    TemplateRenderer.expand(
                        argument,
                        values,
                        player.name,
                        player.uniqueId.toString(),
                    )
                when (verb) {
                    "set" ->
                        values[argument.substringBefore('=').trim()] =
                            argument.substringAfter('=').trim()
                    "message" -> player.sendMessage(expanded)
                    "close" -> {
                        player.closeDialog()
                        return@submit
                    }
                    "template" -> {
                        show(player, argument, values)
                        return@submit
                    }
                    "refresh" -> {
                        show(player, template.id, values)
                        return@submit
                    }
                    "command",
                    "console" -> {
                        val success =
                            if (verb == "console")
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded)
                            else player.performCommand(expanded)
                        if (!success) {
                            player.sendMessage("DialogMenu：指令执行失败，后续动作已停止。")
                            show(player, template.id, values)
                            return@submit
                        }
                    }
                }
            }
            show(player, template.id, values)
        }
    }
}
