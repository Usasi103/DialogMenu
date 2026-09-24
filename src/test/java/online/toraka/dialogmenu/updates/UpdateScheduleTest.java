package online.toraka.dialogmenu.updates;

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

class UpdateScheduleTest {
    @TempDir Path folder;

    @Test
    void oldConfigurationCannotShortenOrExtendTheSixHourSchedule() throws Exception {
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

            UpdateChecker.start(plugin, "Usasi103/DialogMenu");

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

        UpdateChecker.start(plugin, "Usasi103/DialogMenu");

        verify(plugin, never()).getServer();
    }
}
