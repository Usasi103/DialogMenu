package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FullscreenPackSettingsTest {
    @Test
    void validatesDeliverySettingsWithoutStartingHttp() {
        String base = "pack-bind: 127.0.0.1\npack-port: 22335\npack-url: ''\n";
        assertEquals("", FullscreenPackSettings.parse(base).url());
        assertEquals(
                "https://pack.example.com/fullscreen",
                FullscreenPackSettings.parse(
                                base.replace("''", "https://pack.example.com/fullscreen"))
                        .url());
        for (String bad :
                List.of(
                        base.replace("22335", "0"),
                        base.replace("22335", "'22335'"),
                        base.replace("''", "ftp://host"),
                        base.replace("''", "https://user:pass@host"),
                        base.replace("''", "https://host/?token=value"),
                        base + "MenuType: fullscreen\n",
                        base + "pack-port: 1234\n")) {
            assertThrows(IllegalArgumentException.class, () -> FullscreenPackSettings.parse(bad));
        }
    }
}
