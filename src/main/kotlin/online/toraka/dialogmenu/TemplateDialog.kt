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

    /** Optional providers may still be loading, so missing images and PAPI only warn. */
    fun warnings(templates: Collection<DialogTemplate>): List<String> {
        val result = mutableListOf<String>()
        if (!MenuPlaceholders.enabled && templates.any { it.placeholders.isNotEmpty() })
            result += "未启用 PlaceholderAPI：Placeholders 条件均不成立，对应文字显示为不可用"
        for (template in templates) {
            for (element in template.elements) {
                val requests = listOfNotNull(element.image) + element.cases.mapNotNull { it.image }
                for (request in requests.distinct()) {
                    val at = "${template.id} 元素 ${element.id}"
                    val image = MenuImages.resolve(request)
                    if (image == null)
                        result +=
                            "$at：图片 ${request.provider}:${request.id} 不可用" +
                                "（来源插件未启用、尚未加载或 ID 不存在），将显示 [image:${request.id}]"
                    else if (image.advance > element.width + 1)
                        result +=
                            "$at：图片 ${request.id} 宽 ${image.advance - 1} 像素，超出占位宽度 ${element.width}"
                }
            }
        }
        return result
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

    /** Menu variables plus this moment's declared PlaceholderAPI values; names never overlap. */
    private fun context(player: Player, template: DialogTemplate, values: Map<String, String>) =
        values + MenuPlaceholders.values(player, template.placeholders)

    private fun display(
        player: Player,
        template: DialogTemplate,
        context: Map<String, String>,
    ): (String) -> String {
        val unavailable by lazy { MenuPlaceholders.unavailable(player) }
        return { source ->
            TemplateRenderer.display(
                source,
                { key ->
                    when (key) {
                        "player" -> player.name
                        "uuid" -> player.uniqueId.toString()
                        in template.variables -> context[key]
                        in template.placeholders -> context[key] ?: unavailable
                        "ping" -> player.ping.toString()
                        "world" -> player.world.name
                        else -> null
                    }
                },
            ) { token ->
                MenuPlaceholders.resolve(player, token) ?: unavailable
            }
        }
    }

    private fun show(player: Player, id: String, previous: Map<String, String>) {
        val template = MenuRuntime.templates.getValue(id)
        val values = template.values(previous)
        val context = context(player, template, values)
        val token = UUID.randomUUID().toString().replace("-", "")
        val actions = linkedSetOf<String>()
        val canvas =
            TemplateRenderer.render(
                template,
                context,
                display(player, template, context),
                MenuImages.text(player),
                { action ->
                    actions += action
                    DialogClicks.custom(Key.key("dialogmenu_dialogue", "$token/$action"))
                },
            )
        val content = canvas.build()
        sessions[player.uniqueId] = Session(token, id, values, actions, System.currentTimeMillis())
        player.showDialog(
            Dialog.create { factory ->
                factory
                    .empty()
                    .base(
                        DialogBase.builder(Component.empty())
                            .externalTitle(MenuImages.text(player).component(template.title))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.NONE)
                            .body(
                                listOf(
                                    DialogBody.plainMessage(
                                        content,
                                        template.width + if (template.hideFocus) 24 else 10,
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
            // Placeholder conditions may have changed since the menu was drawn.
            if (!TemplateRenderer.visible(element, context(player, template, session.values))) {
                sessions.remove(player.uniqueId)
                show(player, template.id, session.values)
                return@submit
            }
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
                // Commands only receive validated menu variables, never PlaceholderAPI output.
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
                    "message" -> {
                        val text = display(player, template, context(player, template, values))
                        player.sendMessage(MenuImages.text(player).component(text(argument)))
                    }
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
