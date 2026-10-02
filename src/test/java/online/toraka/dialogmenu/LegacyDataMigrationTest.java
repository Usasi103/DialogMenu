package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDataMigrationTest {
    @TempDir Path root;

    private Path write(String relative, String text) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, text);
    }

    @Test
    @DisplayName("imports old files and preserves edits across restarts")
    void importsOldFilesAndPreservesEditsAcrossRestarts() throws Exception {
        Path config = write("PlayerSettings/config.yml", "# 自定义菜单\nPages: [custom]\n");
        Path menu = write("PlayerSettings/menus/custom.yml", "Title: 我的菜单\n");
        write("PlayerSettings/update-check.yml", "enabled: false\n");
        assertTrue(LegacyDataMigration.migrate(root.resolve("DialogMenu")));
        assertEquals(-1L, Files.mismatch(config, root.resolve("DialogMenu/config.yml")));
        assertEquals(-1L, Files.mismatch(menu, root.resolve("DialogMenu/menus/custom.yml")));
        write("DialogMenu/config.yml", "Pages: [changed]\n");
        assertFalse(LegacyDataMigration.migrate(root.resolve("DialogMenu")));
        assertEquals("Pages: [changed]\n", Files.readString(root.resolve("DialogMenu/config.yml")));
        assertTrue(Files.exists(config));
    }

    @Test
    @DisplayName("existing new configuration takes precedence")
    void existingNewConfigurationTakesPrecedence() throws Exception {
        write("PlayerSettings/menu.yml", "legacy");
        write("DialogMenu/menu.yml", "new");
        assertFalse(LegacyDataMigration.migrate(root.resolve("DialogMenu")));
        assertEquals("new", Files.readString(root.resolve("DialogMenu/menu.yml")));
    }

    @Test
    @DisplayName("refuses conflicts before copying configuration")
    void refusesConflictsBeforeCopyingConfiguration() throws Exception {
        write("PlayerSettings/config.yml", "legacy");
        write("PlayerSettings/menus/custom.yml", "old menu");
        Path edited = write("DialogMenu/menus/custom.yml", "edited menu");
        assertThrows(
                IllegalStateException.class,
                () -> LegacyDataMigration.migrate(root.resolve("DialogMenu")));
        assertEquals("edited menu", Files.readString(edited));
        assertFalse(Files.exists(root.resolve("DialogMenu/config.yml")));
        assertFalse(Files.exists(root.resolve("DialogMenu/.playersettings-import")));
    }

    @Test
    @DisplayName("imports legacy language over freshly generated bundled language")
    void importsLegacyLanguageOverFreshlyGeneratedBundledLanguage() throws Exception {
        write("PlayerSettings/menu.yml", "v1 menu");
        write("PlayerSettings/lang/zh_CN.yml", "管理员自定义提示");
        write("DialogMenu/lang/zh_CN.yml", "bundled");
        Map<String, byte[]> generated = new LinkedHashMap<>();
        generated.put("lang/zh_CN.yml", "bundled".getBytes(StandardCharsets.UTF_8));
        assertTrue(LegacyDataMigration.migrate(root.resolve("DialogMenu"), generated));
        assertEquals("管理员自定义提示", Files.readString(root.resolve("DialogMenu/lang/zh_CN.yml")));
    }

    @Test
    @DisplayName("retries interrupted import after main configuration was copied")
    void retriesInterruptedImportAfterMainConfigurationWasCopied() throws Exception {
        write("PlayerSettings/config.yml", "legacy");
        write("PlayerSettings/menus/custom.yml", "menu");
        write("DialogMenu/config.yml", "legacy");
        write("DialogMenu/.playersettings-import", "in progress");
        assertTrue(LegacyDataMigration.migrate(root.resolve("DialogMenu")));
        assertEquals("menu", Files.readString(root.resolve("DialogMenu/menus/custom.yml")));
        assertFalse(Files.exists(root.resolve("DialogMenu/.playersettings-import")));
    }
}
