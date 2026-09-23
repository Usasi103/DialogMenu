package online.toraka.playersettings

import java.nio.file.Files
import java.nio.file.Path
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DialogCanvasTest {
    @Test
    fun `whole surfaces and measured text keep every row at the same origin`() {
        val canvas = DialogCanvas { ClickEvent.custom(Key.key("test", it)) }
        canvas.sprite(114, 0, DialogCanvas.PANEL_TOP)
        canvas.sprite(114, 10, DialogCanvas.PANEL_BOTTOM)
        canvas.button(0, 0, DialogCanvas.SEARCH, "搜索设置", "search")
        repeat(6) { i ->
            canvas.button(
                0,
                3 + i * 2,
                if (i == 2) DialogCanvas.SELECTED_NAV else DialogCanvas.NAV,
                listOf("玩家信息", "环境音效", "环境粒子", "拾取提示", "掉落光柱", "使用帮助")[i],
                "tab_$i",
            )
            canvas.sprite(6, 3 + i * 2, DialogCanvas.Skin(0xE090 + i, 9, 1))
        }
        canvas.text(123, 1, "环境粒子")
        canvas.text(123, 11, "粒子分类与密度")
        listOf(4, 13, 15, 17).forEach { row ->
            canvas.button(
                330,
                row,
                DialogCanvas.CONTROL,
                if (row == 4) "开启 / 切换" else "高 / 中",
                "control_$row",
            )
        }
        canvas.densitySlider(280, 19, "medium", MenuLanguage.CHINESE)
        canvas.text(123, 5, "显示环境粒子")
        canvas.text(123, 7, "仅影响你自己看到的环境粒子。")
        val root = canvas.build()
        var width = 0
        var rows = 0
        val glyphs = mutableListOf<Int>()
        for (child in root.children()) {
            val part = child as TextComponent
            if (part.content() == "\n") {
                assertEquals(DialogCanvas.LINE_WIDTH, width, "row $rows width")
                width = 0
                rows++
            } else if (part.font() == DialogCanvas.FONT) {
                val cp = part.content().single().code
                if (cp in 0xE800..0xEC00) width += cp - 0xEA00
                else {
                    glyphs += cp
                    width += DialogCanvas.glyphWidth(cp)
                }
            } else width += DialogCanvas.textWidth(part.content())
            assertTrue(
                width in 0..DialogCanvas.WIDTH + 2,
                "client must not wrap at row $rows, x $width",
            )
        }
        assertEquals(DialogCanvas.LINE_WIDTH, width)
        assertEquals(DialogCanvas.ROWS - 1, rows)
        assertEquals(1, glyphs.count { it == 0xE000 })
        assertEquals(1, glyphs.count { it == 0xE001 })
        assertFalse(glyphs.any { it in 0xE002..0xE011 })
        assertTrue(canvas.hits.all { it.row + it.rows <= DialogCanvas.ROWS })
        val output =
            Path.of(
                System.getProperty("user.home"),
                ".gradle-builds",
                "PlayerSettings",
                "layout-probes",
            )
        Files.createDirectories(output)
        Files.writeString(
            output.resolve("canvas.json"),
            GsonComponentSerializer.gson().serialize(root),
        )
        Files.writeString(
            output.resolve("layout.properties"),
            "bodyWidth=${DialogCanvas.FRAMELESS_BODY_WIDTH}\nlineWidth=${DialogCanvas.LINE_WIDTH}\nrows=${DialogCanvas.ROWS}\n",
        )
    }

    @Test
    fun `Chinese and legacy search terms resolve to the correct settings page`() {
        assertEquals("particles", SettingsDialog.findTab("环境粒子"))
        assertEquals("particles", SettingsDialog.findTab("萤火虫"))
        assertEquals("sound", SettingsDialog.findTab("鸟鸣"))
        assertEquals("notices", SettingsDialog.findTab("拾取提示"))
        assertEquals("loot", SettingsDialog.findTab("掉落光柱"))
        assertEquals("loot", SettingsDialog.findTab("掉落音效"))
        assertEquals("profile", SettingsDialog.findTab("金币"))
        assertEquals("help", SettingsDialog.findTab("资源包"))
        assertEquals("particles", SettingsDialog.findTab("density"))
        assertNull(SettingsDialog.findTab(""))
    }

    @Test
    fun `toggle and density values display Chinese without changing underlying ids`() {
        assertEquals("开启", SettingsDialog.toggleLabel("ON"))
        assertEquals("关闭", SettingsDialog.toggleLabel("disabled"))
        assertEquals("开启", SettingsDialog.toggleLabel("开"))
        assertEquals("未接入", SettingsDialog.toggleLabel("N/A"))
        assertEquals("中", SettingsDialog.densityLabel("medium"))
        assertEquals("高", SettingsDialog.densityLabel("high"))
        assertEquals("关闭", SettingsDialog.densityLabel("off"))
    }

    @Test
    fun `hit spans cover both halves of a button without affecting neighbours`() {
        val canvas = DialogCanvas { ClickEvent.custom(Key.key("test", it)) }
        canvas.button(330, 13, DialogCanvas.CONTROL, "开", "firefly")
        val root = canvas.build()
        val rows = mutableListOf(mutableListOf<TextComponent>())
        for (child in root.children()) {
            val text = child as TextComponent
            if (text.content() == "\n") rows.add(mutableListOf()) else rows.last().add(text)
        }
        assertEquals(DialogCanvas.ROWS, rows.size)
        for ((row, parts) in rows.withIndex()) {
            val clickable = parts.filter { it.clickEvent() != null }
            if (row in 13..14) {
                assertTrue(clickable.isNotEmpty())
                assertTrue(clickable.any { it.content().single().code - 0xEA00 == 114 })
                val prefix = parts.takeWhile { it.clickEvent() == null }
                assertEquals(330, prefix.sumOf { it.content().single().code - 0xEA00 })
            } else assertTrue(clickable.isEmpty())
        }
    }

    @Test
    fun `slider track and bounded arrows select density without gaps or row wrapping`() {
        val ids = listOf("off", "low", "medium", "high")
        for (theme in MenuTheme.entries) for (language in MenuLanguage.entries) {
            for (selected in ids + "unavailable") {
                val canvas = DialogCanvas(theme) { ClickEvent.custom(Key.key("test", it)) }
                canvas.densitySlider(280, 19, selected, language)
                for (row in 19..20) for (x in 302 until 422) {
                    val hit =
                        canvas.hits.single {
                            row in it.row until it.row + it.rows && x in it.x until it.x + it.width
                        }
                    assertEquals("density_${ids[(x - 302) / 30]}", hit.action)
                }
                val previous = canvas.hits.singleOrNull { it.x == 280 }
                val next = canvas.hits.singleOrNull { it.x == 426 }
                val index = ids.indexOf(selected)
                assertEquals(
                    if (index in 1..3) "density_${ids[index - 1]}" else null,
                    previous?.action,
                )
                assertEquals(if (index in 0..2) "density_${ids[index + 1]}" else null, next?.action)
                var width = 0
                var row = 0
                for (component in canvas.build().children()) {
                    val part = component as TextComponent
                    if (part.content() == "\n") {
                        assertEquals(DialogCanvas.LINE_WIDTH, width)
                        width = 0
                        row++
                    } else if (part.font() == DialogCanvas.FONT) {
                        val code = part.content().single().code
                        width +=
                            if (code in 0xE800..0xEC00) code - 0xEA00
                            else DialogCanvas.glyphWidth(code)
                    } else width += DialogCanvas.textWidth(part.content())
                    assertTrue(
                        width in 0..DialogCanvas.LINE_WIDTH,
                        "$theme $language $selected row=$row x=$width",
                    )
                }
                assertEquals(DialogCanvas.LINE_WIDTH, width)
                assertEquals(28, row)
            }
        }
    }

    @Test
    fun `labels remain within the allotted controls`() {
        val fitted = DialogCanvas.fit("自然环境粒子与落叶和萤火虫开关", 102)
        assertTrue(DialogCanvas.textWidth(fitted) <= 102)
        assertTrue(fitted.isNotEmpty())
    }
}
