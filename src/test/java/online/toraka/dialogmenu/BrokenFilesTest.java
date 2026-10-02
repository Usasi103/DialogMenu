package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unreadable content files (user rule 2026-09-29): which file has which jar default, the ERROR
 * text, and that a skipped menu stays out of the catalog while the others load.
 */
class BrokenFilesTest {

    @TempDir Path directory;

    private void write(String name, String text) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private void export(String resource, String name) throws IOException {
        write(name, MenuRepository.resource(resource));
    }

    @Test
    void catalogFilesMapToTheCatalogDefaultsEvenWhenConfigNoLongerParses() throws IOException {
        write("config.yml", "Version: 3\nDefaultMenu: [broken\n");
        write("text.yml", "DefaultLanguage: zh_cn\n");
        write("translations/zh_cn.yml", "a: b\n");
        write("translations/ja_jp.yml", "a: b\n");
        write("menus/demo-boss.yml", "x: y\n");
        write("menus/settings.yml", "x: y\n");
        write("menus/custom.yml", "x: y\n");
        write("templates/npc-dialogue.yml", "x: y\n");
        Map<String, String> files = BrokenFiles.candidates(directory.toFile());
        assertEquals("text.yml", files.get("text.yml"));
        assertEquals("translations/zh_cn.yml", files.get("translations/zh_cn.yml"));
        assertTrue(files.containsKey("translations/ja_jp.yml"));
        assertNull(files.get("translations/ja_jp.yml"));
        assertEquals("catalog/config.yml", files.get("config.yml"));
        assertEquals("catalog/menus/demo-boss.yml", files.get("menus/demo-boss.yml"));
        assertEquals("catalog/menus/settings.yml", files.get("menus/settings.yml"));
        assertTrue(files.containsKey("menus/custom.yml"));
        assertNull(files.get("menus/custom.yml"));
        assertFalse(
                files.containsKey("templates/npc-dialogue.yml"), "catalog menus hold templates");
    }

    @Test
    void simpleAndLegacyLayoutsMapToTheirOwnDefaults() throws IOException {
        write("config.yml", "# old layout\nVersion: 2 # simple\n");
        write("menus/profile.yml", "x: y\n");
        write("templates/npc-dialogue.yml", "x: y\n");
        Map<String, String> simple = BrokenFiles.candidates(directory.toFile());
        assertEquals("simple/config.yml", simple.get("config.yml"));
        assertEquals("simple/menus/profile.yml", simple.get("menus/profile.yml"));
        assertEquals("templates/npc-dialogue.yml", simple.get("templates/npc-dialogue.yml"));

        Files.delete(directory.resolve("config.yml"));
        write("menu.yml", "x: y\n");
        write("languages/zh_cn.yml", "x: y\n");
        Map<String, String> legacy = BrokenFiles.candidates(directory.toFile());
        assertEquals("menu.yml", legacy.get("menu.yml"));
        assertEquals("languages/zh_cn.yml", legacy.get("languages/zh_cn.yml"));
        assertFalse(legacy.containsKey("menus/profile.yml"));
    }

    @Test
    void aConfigWithoutAVersionLineHasNoDefault() throws IOException {
        write("config.yml", "DefaultMenu: [broken\n");
        write("menus/demo-boss.yml", "x: y\n");
        Map<String, String> files = BrokenFiles.candidates(directory.toFile());
        assertTrue(files.containsKey("config.yml"));
        assertNull(files.get("config.yml"));
        assertNull(files.get("menus/demo-boss.yml"));
    }

    @Test
    void theErrorNamesThePositionFirst() throws IOException {
        write("menus/custom.yml", "Title: ok\nPages:\n  main: [unclosed\n");
        String problem = BrokenFiles.problem(directory.resolve("menus/custom.yml").toFile());
        assertTrue(problem.startsWith("第 3 行第 9 列："), problem);
        assertTrue(problem.contains("while parsing a flow sequence"), problem);
        assertFalse(problem.contains("reader") || problem.contains("^"), problem);
        assertTrue(problem.contains("main: [unclosed"), "the snippet keeps its colon: " + problem);

        File gbk = directory.resolve("translations/zh_cn.yml").toFile();
        gbk.getParentFile().mkdirs();
        Files.write(gbk.toPath(), "标题: 设置\n".getBytes(Charset.forName("GBK")));
        assertEquals("不是 UTF-8 编码（可能被另存为 GBK）", BrokenFiles.problem(gbk));
    }

    @Test
    void aSkippedMenuStaysOutOfTheCatalogAndTheRestLoads() throws IOException {
        export("catalog/config.yml", "config.yml");
        for (String id : CatalogRepository.defaults()) {
            export("catalog/menus/" + id + ".yml", "menus/" + id + ".yml");
        }
        write("menus/zz-custom.yml", "Type: [unclosed\n");
        CatalogRepository repository = new CatalogRepository(directory.toFile());
        assertThrows(IllegalArgumentException.class, repository::read);
        File broken = directory.resolve("menus/zz-custom.yml").toFile();
        BrokenFiles.Result result =
                new BrokenFiles.Result(Set.of(broken.getAbsoluteFile()), List.of("x"));
        MenuCatalog catalog = repository.read(result);
        assertEquals(Set.copyOf(CatalogRepository.defaults()), catalog.menus().keySet());
        assertTrue(broken.isFile(), "the skipped file is never touched");
    }
}
