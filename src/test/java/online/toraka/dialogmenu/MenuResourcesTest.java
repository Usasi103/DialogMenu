package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MenuResourcesTest {
    private static MenuResourcePack parse(String source) {
        return MenuResourcePack.parse(MenuConfigParser.yaml(source, "resource-pack"));
    }

    @Test
    @DisplayName(
            "Auto is opt-in for existing configs and allows explicit UUID or manual installation")
    void autoIsOptInForExistingConfigsAndAllowsExplicitUuidOrManualInstallation() {
        UUID id = UUID.randomUUID();
        MenuResourcePack automatic = parse("Provider: Auto\nRequireLoaded: false\n");
        assertEquals("auto", automatic.provider());
        assertTrue(automatic.autoInstall());
        assertFalse(automatic.requireLoaded());
        assertEquals(id, parse("Provider: Auto\nUUID: " + id + "\nRequireLoaded: true\n").uuid());
        assertFalse(parse("Provider: Auto\nAutoInstall: false\n").autoInstall());
        assertEquals("craftengine", parse("Name: Existing configuration\n").provider());
        assertEquals(MenuResourcePack.legacy(), MenuResourcePack.parse(null));
    }

    @Test
    @DisplayName("CraftEngine pack ID name and loading requirement are configurable")
    void craftEnginePackIdNameAndLoadingRequirementAreConfigurable() {
        MenuResourcePack pack =
                parse("Name: 我的合并包\nProvider: CraftEngine\nPack: lobby\nRequireLoaded: false\n");
        assertEquals("lobby", pack.pack());
        assertEquals("我的合并包", pack.name());
        assertEquals("craftengine", pack.provider());
        assertFalse(pack.requireLoaded());
    }

    @Test
    @DisplayName("URL provider preserves target and accepts optional explicit UUID and SHA1")
    void urlProviderPreservesTargetAndAcceptsOptionalExplicitUuidAndSha1() {
        UUID id = UUID.randomUUID();
        String hash = "a".repeat(40);
        MenuResourcePack pack =
                parse(
                        "Provider: URL\nURL: https://example.com/menu.zip\nUUID: "
                                + id
                                + "\nSHA1: "
                                + hash
                                + "\n");
        assertEquals(id, pack.uuid());
        assertEquals(hash, pack.sha1());
        assertTrue(pack.requireLoaded());
        assertEquals(
                UUID.nameUUIDFromBytes(
                        "https://example.com/menu.zip".getBytes(StandardCharsets.UTF_8)),
                parse("Provider: URL\nURL: https://example.com/menu.zip\n").uuid());
    }

    @Test
    @DisplayName("bad provider UUID hash address and typo are rejected before install")
    void badProviderUuidHashAddressAndTypoAreRejectedBeforeInstall() {
        for (String source :
                List.of(
                        "Provider: unknown",
                        "Provider: External",
                        "UUID: 1-1-1-1-1",
                        "SHA1: abc",
                        "Provider: URL",
                        "URL: file:///menu.zip",
                        "URL: https://user:pass@example.com/menu.zip",
                        "RequireLoaded: 'true'",
                        "AutoInstall: 'true'",
                        "RequrieLoaded: false")) {
            assertThrows(Exception.class, () -> parse(source), source);
        }
    }

    @Test
    @DisplayName("only the specified resource pack unlocks the player and failures revoke it")
    void onlyTheSpecifiedResourcePackUnlocksThePlayerAndFailuresRevokeIt() {
        PackLoadTracker tracker = new PackLoadTracker();
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        tracker.record(player, unrelated, true);
        tracker.record(otherPlayer, target, true);
        assertFalse(tracker.contains(player, Kt.setOf(target)));
        assertFalse(tracker.contains(player, Collections.emptySet()));
        tracker.record(player, target, true);
        assertTrue(tracker.contains(player, Kt.setOf(target)));
        tracker.record(player, target, false);
        assertFalse(tracker.contains(player, Kt.setOf(target)));
        tracker.record(player, target, true);
        tracker.forget(player);
        assertFalse(tracker.contains(player, Kt.setOf(target)));
        assertTrue(tracker.contains(otherPlayer, Kt.setOf(target)));
        tracker.record(otherPlayer, unrelated, true);
        tracker.invalidate(target);
        assertFalse(tracker.contains(otherPlayer, Kt.setOf(target)));
        assertTrue(tracker.contains(otherPlayer, Kt.setOf(unrelated)));
        tracker.clear();
        assertFalse(tracker.contains(otherPlayer, Kt.setOf(unrelated)));
    }

    @Test
    @DisplayName("grouped pack requires all members and external mode requires sender UUID")
    void groupedPackRequiresAllMembersAndExternalModeRequiresSenderUuid() {
        UUID player = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PackLoadTracker tracker = new PackLoadTracker();
        tracker.record(player, a, true);
        assertFalse(tracker.contains(player, Kt.setOf(a, b)));
        tracker.record(player, b, true);
        assertTrue(tracker.contains(player, Kt.setOf(a, b)));
        assertEquals(a, parse("Provider: External\nUUID: " + a + "\n").uuid());
    }
}
