package online.toraka.dialogmenu

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MenuResourcesTest {
    private fun parse(source: String) =
        MenuResourcePack.parse(MenuConfigParser.yaml(source, "resource-pack"))

    @Test
    fun `Auto is opt-in for existing configs and allows explicit UUID or manual installation`() {
        val id = UUID.randomUUID()
        val automatic = parse("Provider: Auto\nRequireLoaded: false\n")
        assertEquals("auto", automatic.provider)
        assertTrue(automatic.autoInstall)
        assertFalse(automatic.requireLoaded)
        assertEquals(id, parse("Provider: Auto\nUUID: $id\nRequireLoaded: true\n").uuid)
        assertFalse(parse("Provider: Auto\nAutoInstall: false\n").autoInstall)
        assertEquals("craftengine", parse("Name: Existing configuration\n").provider)
        assertEquals(MenuResourcePack.legacy, MenuResourcePack.parse(null))
    }

    @Test
    fun `CraftEngine pack ID name and loading requirement are configurable`() {
        val pack = parse("Name: 我的合并包\nProvider: CraftEngine\nPack: lobby\nRequireLoaded: false\n")
        assertEquals("lobby", pack.pack)
        assertEquals("我的合并包", pack.name)
        assertEquals("craftengine", pack.provider)
        assertFalse(pack.requireLoaded)
    }

    @Test
    fun `URL provider preserves target and accepts optional explicit UUID and SHA1`() {
        val id = UUID.randomUUID()
        val hash = "a".repeat(40)
        val pack =
            parse("Provider: URL\nURL: https://example.com/menu.zip\nUUID: $id\nSHA1: $hash\n")
        assertEquals(id, pack.uuid)
        assertEquals(hash, pack.sha1)
        assertTrue(pack.requireLoaded)
        assertEquals(
            UUID.nameUUIDFromBytes("https://example.com/menu.zip".toByteArray()),
            parse("Provider: URL\nURL: https://example.com/menu.zip\n").uuid,
        )
    }

    @Test
    fun `bad provider UUID hash address and typo are rejected before install`() {
        for (source in
            listOf(
                "Provider: unknown",
                "Provider: External",
                "UUID: 1-1-1-1-1",
                "SHA1: abc",
                "Provider: URL",
                "URL: file:///menu.zip",
                "URL: https://user:pass@example.com/menu.zip",
                "RequireLoaded: 'true'",
                "AutoInstall: 'true'",
                "RequrieLoaded: false",
            )) {
            assertThrows(Exception::class.java, { parse(source) }, source)
        }
    }

    @Test
    fun `only the specified resource pack unlocks the player and failures revoke it`() {
        val tracker = PackLoadTracker()
        val player = UUID.randomUUID()
        val otherPlayer = UUID.randomUUID()
        val target = UUID.randomUUID()
        val unrelated = UUID.randomUUID()
        tracker.record(player, unrelated, true)
        tracker.record(otherPlayer, target, true)
        assertFalse(tracker.contains(player, setOf(target)))
        assertFalse(tracker.contains(player, emptySet()))
        tracker.record(player, target, true)
        assertTrue(tracker.contains(player, setOf(target)))
        tracker.record(player, target, false)
        assertFalse(tracker.contains(player, setOf(target)))
        tracker.record(player, target, true)
        tracker.forget(player)
        assertFalse(tracker.contains(player, setOf(target)))
        assertTrue(tracker.contains(otherPlayer, setOf(target)))
        tracker.record(otherPlayer, unrelated, true)
        tracker.invalidate(target)
        assertFalse(tracker.contains(otherPlayer, setOf(target)))
        assertTrue(tracker.contains(otherPlayer, setOf(unrelated)))
        tracker.clear()
        assertFalse(tracker.contains(otherPlayer, setOf(unrelated)))
    }

    @Test
    fun `grouped pack requires all members and external mode requires sender UUID`() {
        val player = UUID.randomUUID()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val tracker = PackLoadTracker()
        tracker.record(player, a, true)
        assertFalse(tracker.contains(player, setOf(a, b)))
        tracker.record(player, b, true)
        assertTrue(tracker.contains(player, setOf(a, b)))
        assertEquals(a, parse("Provider: External\nUUID: $a\n").uuid)
    }
}
