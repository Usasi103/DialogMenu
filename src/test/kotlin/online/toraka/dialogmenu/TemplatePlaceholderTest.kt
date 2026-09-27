package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TemplatePlaceholderTest {
    private val base =
        """
        Version: 1
        Skin: parchment
        Variables:
          mood: [calm, angry]
        Placeholders:
          rank: "%luckperms_primary_group_name%"
          level: "%player_level%"
          quest: "%quest_stage%"
        Elements:
          portrait:
            Type: sprite
            Position: [24, 3]
            Font: "demo:portraits"
            Width: 108
            Rows: 12
            Advance: 109
            Glyph: "\uE001"
            Cases:
              - When: "quest>=3"
                Glyph: "\uE003"
              - When: [mood=angry, "rank!=vip"]
                Glyph: "\uE002"
                Advance: 100
          badge:
            Type: sprite
            Position: [150, 3]
            Sprite: button
            Cases:
              - When: rank=vip
                Sprite: selected
          photo:
            Type: sprite
            Position: [300, 3]
            Width: 64
            Rows: 8
            Image: "CE:demo:elf_calm"
            Cases:
              - When: "level>=30"
                Image: "IA:demo:elf_smile"
          greeting:
            Position: [150, 14]
            Width: 300
            Text: "{player} {rank} {mood}"
          low:
            Type: button
            Position: [150, 17]
            Text: 等级不足
            VisibleWhen: "level<30"
            Actions: ["message: 需要 30 级 {rank}"]
          accept:
            Type: button
            Position: [150, 17]
            Text: 接受
            VisibleWhen: ["level>=30", "quest=0"]
            Actions: ["console: quest start {player} {mood}", "close"]
        """
            .trimIndent()

    private fun parse(source: String = base) = TemplateParser.parse("npc", source)

    private fun condition(vararg clauses: String) =
        MenuCondition(clauses.map { requireNotNull(MenuCondition.clause(it)) })

    private fun flatten(component: Component): List<Component> =
        listOf(component) + component.children().flatMap(::flatten)

    private fun render(
        template: DialogTemplate,
        values: Map<String, String>,
        rich: RichMenuText = RichMenuText(),
    ) =
        TemplateRenderer.render(template, values, { it }, rich) {
                DialogClicks.custom(Key.key("test", it))
            }
            .build()

    private fun glyphs(component: Component, font: Key) =
        flatten(component).filter { it.font() == font }.map { (it as TextComponent).content() }

    @Test
    fun `conditions compare text and numbers and unresolved placeholders never hold`() {
        val template = parse()
        val low = template.elements.single { it.id == "low" }
        val accept = template.elements.single { it.id == "accept" }
        assertFalse(TemplateRenderer.visible(low, mapOf("mood" to "calm")))
        assertFalse(TemplateRenderer.visible(accept, mapOf("mood" to "calm")))
        assertTrue(TemplateRenderer.visible(low, mapOf("level" to "-3")))
        assertTrue(TemplateRenderer.visible(accept, mapOf("level" to "30", "quest" to "0")))
        assertFalse(TemplateRenderer.visible(accept, mapOf("level" to "29.5", "quest" to "0")))
        assertFalse(TemplateRenderer.visible(accept, mapOf("level" to "30", "quest" to "1")))
        // Formatted numbers are not numbers; neither branch claims them.
        assertFalse(TemplateRenderer.visible(accept, mapOf("level" to "1,000", "quest" to "0")))
        assertFalse(TemplateRenderer.visible(low, mapOf("level" to "1,000")))
        assertEquals(
            listOf("level", "quest"),
            accept.condition!!.clauses.map { it.name },
        )
        assertEquals("rank", template.placeholders.keys.first())
    }

    @Test
    fun `overlapping buttons are accepted only when their conditions provably exclude`() {
        assertTrue(condition("level<30").excludes(condition("level>=30")))
        assertTrue(condition("level<30").excludes(condition("level>30")))
        assertFalse(condition("level<=30").excludes(condition("level>=30")))
        assertTrue(condition("level=30").excludes(condition("level<30")))
        assertFalse(condition("level=30").excludes(condition("level<=30")))
        assertFalse(condition("level=abc").excludes(condition("level<30")))
        assertTrue(condition("rank=vip").excludes(condition("rank!=vip")))
        assertTrue(condition("rank=vip").excludes(condition("rank=mvp")))
        assertFalse(condition("rank!=vip").excludes(condition("rank!=mvp")))
        assertFalse(condition("rank=vip").excludes(condition("level<30")))
        assertTrue(condition("level>=30", "quest=0").excludes(condition("level<30")))
        // Legacy variable conditions keep their original exclusivity rule.
        assertTrue(condition("difficulty=hard").excludes(condition("difficulty=normal")))
        assertThrows(IllegalArgumentException::class.java) {
            parse(base.replace("VisibleWhen: \"level<30\"", "VisibleWhen: \"level<40\""))
        }
        parse(base.replace("VisibleWhen: \"level<30\"", "VisibleWhen: \"level<=29\""))
    }

    @Test
    fun `sprite cases pick the first match and fall back to the default look`() {
        val template = parse()
        val portrait = template.elements.single { it.id == "portrait" }
        assertEquals(listOf(109), portrait.cases[0].sprite!!.advances)
        assertEquals(listOf(100), portrait.cases[1].sprite!!.advances)
        val font = Key.key("demo:portraits")
        fun portrait(values: Map<String, String>) = glyphs(render(template, values), font)
        assertEquals(listOf("\uE001"), portrait(mapOf("mood" to "calm")))
        // rank is unresolved, so rank!=vip does not hold either.
        assertEquals(listOf("\uE001"), portrait(mapOf("mood" to "angry")))
        assertEquals(listOf("\uE002"), portrait(mapOf("mood" to "angry", "rank" to "member")))
        assertEquals(listOf("\uE001"), portrait(mapOf("mood" to "angry", "rank" to "vip")))
        assertEquals(
            listOf("\uE003"),
            portrait(mapOf("mood" to "angry", "rank" to "member", "quest" to "3")),
        )

        val button = TemplateSkins.get("parchment", "button")
        val selected = TemplateSkins.get("parchment", "selected")
        fun badge(values: Map<String, String>) =
            glyphs(render(template, values), TemplateSkins.font).filter {
                it == button.glyph.toChar().toString() || it == selected.glyph.toChar().toString()
            }
        assertEquals(listOf(button.glyph.toChar().toString()), badge(mapOf("mood" to "calm")))
        assertEquals(
            listOf(selected.glyph.toChar().toString()),
            badge(mapOf("mood" to "calm", "rank" to "vip")),
        )
    }

    @Test
    fun `image sprites resolve by id, switch by case and fall back to text`() {
        val template = parse()
        val photo = template.elements.single { it.id == "photo" }
        assertEquals(MenuImageRequest("CE", "demo:elf_calm"), photo.image)
        assertEquals(MenuImageRequest("IA", "demo:elf_smile"), photo.cases.single().image)
        assertEquals(64 to 8, photo.width to photo.rows)
        assertNull(photo.sprite)
        val requests = mutableListOf<MenuImageRequest>()
        val imageFont = Key.key("demo:images")
        val rich =
            RichMenuText(
                images = {
                    requests += it
                    val glyph = if (it.provider == "IA") "\uE102" else "\uE101"
                    MenuImage(Component.text(glyph).font(imageFont), 60)
                }
            )
        assertEquals(
            listOf("\uE101"),
            glyphs(render(template, mapOf("mood" to "calm"), rich), imageFont),
        )
        assertEquals(
            listOf("\uE102"),
            glyphs(render(template, mapOf("mood" to "calm", "level" to "30"), rich), imageFont),
        )
        assertEquals(setOf("CE", "IA"), requests.map { it.provider }.toSet())
        val missing = render(template, mapOf("mood" to "calm"), RichMenuText())
        assertTrue(
            PlainTextComponentSerializer.plainText()
                .serialize(missing)
                .contains("[image:demo:elf_calm]")
        )
        assertEquals(
            "<image:CE:demo:elf_calm:0:0>",
            TemplateRenderer.imageTag(requireNotNull(photo.image)),
        )
        assertEquals(
            "<image:IA:demo:elf_smile>",
            TemplateRenderer.imageTag(requireNotNull(photo.cases.single().image)),
        )
    }

    @Test
    fun `display text expands built-ins, declared names and papi tokens in one pass`() {
        val lookup = mapOf("player" to "Alex", "rank" to "{uuid}", "uuid" to "u-1")
        val text =
            TemplateRenderer.display(
                "{player} {rank} %player_level% %other% {unknown} 50% 与 100%",
                lookup::get,
            ) {
                if (it == "%player_level%") "30" else "%player_name%"
            }
        assertEquals("Alex {uuid} 30 %player_name% {unknown} 50% 与 100%", text)
    }

    private data class Invalid(val old: String, val new: String, val reason: String)

    @Test
    fun `invalid placeholders, conditions, cases and images are rejected`() {
        val invalid =
            listOf(
                Invalid(
                    "  quest: \"%quest_stage%\"",
                    "  quest: \"%quest_stage%\"\n  mood: \"%x%\"",
                    "重名",
                ),
                Invalid(
                    "  quest: \"%quest_stage%\"",
                    "  quest: \"%quest_stage%\"\n  ping: \"%x%\"",
                    "重名",
                ),
                Invalid("  level: \"%player_level%\"", "  level: player_level", "%PAPI变量%"),
                Invalid("  level: \"%player_level%\"", "  level: 5", "%PAPI变量%"),
                Invalid("VisibleWhen: \"level<30\"", "VisibleWhen: \"levle<30\"", "未在 Variables"),
                Invalid("VisibleWhen: \"level<30\"", "VisibleWhen: \"level<abc\"", "需要数字"),
                Invalid(
                    "VisibleWhen: \"level<30\"",
                    "VisibleWhen: \"level<30\"\n    SelectedWhen: \"mood>1\"",
                    "菜单变量只能用",
                ),
                Invalid("VisibleWhen: \"level<30\"", "VisibleWhen: \"level==30\"", "一个 ="),
                Invalid(
                    "{player} {mood}\", \"close\"",
                    "{player} {rank}\", \"close\"",
                    "Placeholders 的值",
                ),
                Invalid(
                    "[\"message: 需要 30 级 {rank}\"]",
                    "[\"set: rank=vip\", \"refresh\"]",
                    "set 只能",
                ),
                Invalid(
                    "    Actions: [\"message: 需要 30 级 {rank}\"]",
                    "    Actions: [\"message: 需要 30 级 {rank}\"]\n" +
                        "    Cases:\n      - When: rank=vip\n        Sprite: selected",
                    "Cases: 仅用于 sprite",
                ),
                Invalid(
                    "    Sprite: button\n    Cases:",
                    "    Sprite: button\n    SelectedWhen: rank=vip\n    Cases:",
                    "Cases 不能与",
                ),
                Invalid("        Glyph: \"\\uE003\"", "        Sprite: button", "默认写法为 Glyph"),
                Invalid("        Sprite: selected", "        Sprite: close", "尺寸必须与默认"),
                Invalid(
                    "    Image: \"CE:demo:elf_calm\"",
                    "    Image: \"CE:demo:elf_calm\"\n    Font: \"demo:x\"",
                    "Image 不能与",
                ),
                Invalid(
                    "    Text: \"{player} {rank} {mood}\"",
                    "    Text: \"{player} {rank} {mood}\"\n    Image: \"demo:x\"",
                    "Image: 仅用于 sprite",
                ),
                Invalid("Image: \"CE:demo:elf_calm\"", "Image: \"only\"", "格式为"),
                Invalid("Image: \"IA:demo:elf_smile\"", "Image: \"IA:demo:elf_smile:1:2\"", "格式为"),
            )
        for (case in invalid) {
            assertEquals(1, base.split(case.old).size - 1, case.old)
            val error =
                assertThrows(
                    IllegalArgumentException::class.java,
                    { parse(base.replace(case.old, case.new)) },
                    case.new,
                )
            assertTrue(
                error.message.orEmpty().contains(case.reason),
                "${case.new} -> ${error.message}",
            )
        }
    }

    @Test
    fun `documented example switches buttons, badge and text by placeholder values`() {
        val source =
            java.nio.file.Path.of("docs/wiki/examples/placeholders.yml").toFile().readText()
        val catalog =
            MenuCatalogParser.parse(
                "Version: 3\nDefaultMenu: placeholders\n",
                mapOf("placeholders" to source),
            )
        val template = catalog.templates.getValue("placeholders/main")
        assertEquals(setOf("level", "mode"), template.placeholders.keys)
        fun output(level: String?): String {
            val context = template.values(emptyMap()) + listOfNotNull(level?.let { "level" to it })
            val lookup = context + mapOf("player" to "Alex", "ping" to "20", "world" to "world")
            val canvas =
                TemplateRenderer.render(
                    template,
                    context,
                    { text ->
                        TemplateRenderer.display(
                            text,
                            { lookup[it] ?: if (it == "mode") "未接入" else null },
                        ) {
                            level ?: "未接入"
                        }
                    },
                ) {
                    DialogClicks.custom(Key.key("test", it))
                }
            return PlainTextComponentSerializer.plainText().serialize(canvas.build())
        }
        val high = output("35")
        assertTrue(high.contains("领取奖励") && high.contains("30 级认证"), high)
        assertFalse(high.contains("等级不足") || high.contains("尚未认证"))
        assertTrue(high.contains("等级 35 · 模式 未接入"))
        assertTrue(high.contains("直接写 PAPI：35"))
        val low = output("3")
        assertTrue(low.contains("等级不足") && low.contains("尚未认证"))
        assertFalse(low.contains("领取奖励"))
        val missing = output(null)
        assertFalse(missing.contains("领取奖励") || missing.contains("等级不足"))
        assertTrue(missing.contains("你好，Alex"))
    }

    @Test
    fun `one file canvas menus accept root placeholders and page overrides`() {
        val menu =
            """
            Version: 1
            Type: canvas
            DefaultPage: main
            Placeholders:
              level: "%player_level%"
            Pages:
              main:
                Elements:
                  hint:
                    Position: [20, 2]
                    Text: "等级 {level}"
                    VisibleWhen: "level>=1"
              other:
                Placeholders:
                  rank: "%vault_rank%"
                Elements:
                  hint:
                    Position: [20, 2]
                    Text: "{rank}"
                    VisibleWhen: rank=vip
            """
                .trimIndent()
        val sources =
            (CatalogRepository.defaults + "settings").associateWith {
                MenuRepository.resource("catalog/menus/$it.yml")
            } + ("npc" to menu)
        val catalog =
            MenuCatalogParser.parse(MenuRepository.resource("catalog/config.yml"), sources)
        val pages = catalog.menus.getValue("npc").canvas
        assertEquals(mapOf("level" to "%player_level%"), pages.getValue("main").placeholders)
        assertEquals(mapOf("rank" to "%vault_rank%"), pages.getValue("other").placeholders)
        assertThrows(IllegalArgumentException::class.java) {
            MenuCatalogParser.parse(
                MenuRepository.resource("catalog/config.yml"),
                sources + ("npc" to menu.replace("rank=vip", "level=vip")),
            )
        }
    }
}
