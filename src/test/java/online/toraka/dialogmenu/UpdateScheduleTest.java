package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DialogMenu's update check goes through Keystone's UpdateChecker with the interval pinned to six
 * hours: whatever an old update-check.yml says in {@code check-interval-hours}, the first check
 * waits the start-up delay plus the per-repository spread and then repeats every 6 h.
 */
class UpdateScheduleTest {
    @TempDir Path folder;

    @Test
    void oldConfigurationCannotShortenOrExtendTheSixHourSchedule() throws Exception {
        assertEquals(6, DialogMenu.UPDATE_CHECK_HOURS);
        for (String oldInterval : new String[] {"1", "168", "invalid"}) {
            Files.writeString(
                    folder.resolve("update-check.yml"),
                    "enabled: true\nstartup-delay-seconds: 60\ncheck-interval-hours: "
                            + oldInterval
                            + "\n");
            Plugin plugin = mock(Plugin.class);
            Server server = mock(Server.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            Logger logger = mock(Logger.class);
            when(plugin.getDataFolder()).thenReturn(folder.toFile());
            when(plugin.getDescription())
                    .thenReturn(
                            new PluginDescriptionFile(
                                    "DialogMenu", "0.1.21-SNAPSHOT", "test.Main"));
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(logger);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            when(server.getScheduler()).thenReturn(scheduler);

            DialogMenu.startUpdateChecks(plugin);

            verify(scheduler)
                    .runTaskTimerAsynchronously(
                            eq(plugin), any(Runnable.class), eq(2060L), eq(432000L));
            verifyNoInteractions(logger);
        }
    }

    @Test
    void disabledCheckingDoesNotScheduleRequests() throws Exception {
        Files.writeString(folder.resolve("update-check.yml"), "enabled: false\n");
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());

        DialogMenu.startUpdateChecks(plugin);

        verify(plugin, never()).getServer();
    }

    @Test
    void guardedUpdateFileUsesDefaultsWithoutReadingOrChangingIt() throws Exception {
        Path file = folder.resolve("update-check.yml");
        String bad = "enabled: wrong\nstartup-delay-seconds: -1\n";
        Files.writeString(file, bad);
        dev.keystone.storage.WriteGuard.block(file.toFile(), "backup denied");
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getDescription())
                .thenReturn(new PluginDescriptionFile("DialogMenu", "paper.4", "test.Main"));
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getScheduler()).thenReturn(scheduler);
        StartupUpdates.startDefaults(plugin);
        verify(scheduler)
                .runTaskTimerAsynchronously(
                        eq(plugin), any(Runnable.class), eq(2060L), eq(432000L));
        verify(plugin, never()).getDataFolder();
        assertEquals(bad, Files.readString(file));
        assertEquals(true, dev.keystone.storage.WriteGuard.isBlocked(file.toFile()));
    }
}
