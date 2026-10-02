package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.keystone.config.ConfigProblems;
import dev.keystone.config.LoadProblem;
import dev.keystone.config.ReloadTransaction;
import dev.keystone.storage.FileBackup;
import dev.keystone.storage.WriteGuard;
import dev.keystone.lang.Lang;
import dev.keystone.lang.LangNode;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/** Original-byte preflight and business validation, independent of menu rendering or providers. */
class MenuPreflightTest {
    @TempDir Path directory;

    void write(String path, String text) throws IOException {
        Files.createDirectories(directory.resolve(path).getParent());
        Files.writeString(directory.resolve(path), text);
    }

    @BeforeEach
    void seed() throws IOException {
        write("config.yml", MenuRepository.resource("catalog/config.yml"));
        write("text.yml", MenuRepository.resource("text.yml"));
        for (String locale : List.of("zh_cn", "en_us")) {
            String path = "translations/" + locale + ".yml";
            write(path, MenuRepository.resource(path));
        }
        for (String id : CatalogRepository.defaults()) {
            String path = "menus/" + id + ".yml";
            write(path, MenuRepository.resource("catalog/" + path));
        }
    }

    MockedStatic<ItemSources> providers() {
        MockedStatic<ItemSources> mock = mockStatic(ItemSources.class);
        mock.when(ItemSources::previewRegistry).thenReturn(new MenuItemSources(Map.of()));
        mock.when(() -> ItemSources.validate(any(MenuDefinition.class), any(MenuItemSources.class)))
                .thenReturn(List.of());
        return mock;
    }

    List<LoadProblem> apply(MenuFiles files) {
        List<LoadProblem> reported = new ArrayList<>();
        try (MockedStatic<ConfigProblems> mock = mockStatic(ConfigProblems.class)) {
            mock.when(() -> ConfigProblems.report(any()))
                    .thenAnswer(
                            call -> {
                                reported.add(call.getArgument(0));
                                return null;
                            });
            files.applyStartup();
        }
        return reported;
    }

    @Test
    void illegalResourceProviderIsPreflightedThenBackedUpExactly() throws Exception {
        String bad =
                MenuRepository.resource("catalog/config.yml")
                        .replace("Provider: Auto", "Provider: Impossible");
        write("config.yml", bad);
        write("config.yml.tmp", "untouched-journal");
        byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            MenuCandidate candidate = files.startupCandidate();
            assertEquals("auto", candidate.catalog().resourcePack().provider());
            assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
            assertFalse(Files.list(directory).anyMatch(path -> path.toString().endsWith(".bak")));
            LoadProblem report = apply(files).getFirst();
            assertEquals(LoadProblem.Kind.CONTENT, report.kind());
            assertEquals(LoadProblem.Outcome.REPLACED, report.outcome());
            assertArrayEquals(original, Files.readAllBytes(report.backup().toPath()));
            assertEquals(
                    MenuRepository.resource("catalog/config.yml"),
                    Files.readString(directory.resolve("config.yml")));
            assertEquals(1, report.cause().split("第 " + report.line() + " 行", -1).length - 1);
        }
    }

    @Test
    void numericLanguageCannotBeSilentlyCoerced() throws Exception {
        write("text.yml", "DefaultLanguage: 123\n");
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            assertEquals("zh_cn", files.startupCandidate().translations().defaultLanguage());
            assertEquals("DefaultLanguage: 123\n", Files.readString(directory.resolve("text.yml")));
            assertEquals("text.yml", apply(files).getFirst().path());
        }
    }

    @Test
    void badBuiltinWidthRestoresOnlyThatMenu() throws Exception {
        String path = "menus/demo-dialogue.yml";
        write(
                path,
                "Version: 1\nType: canvas\nPages:\n  main:\n    Canvas: {Width: 1}\n    Elements: []\n");
        String settings = Files.readString(directory.resolve("menus/demo-settings.yml"));
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            assertEquals(4, files.startupCandidate().catalog().menus().size());
            LoadProblem problem = apply(files).getFirst();
            assertEquals(path, problem.path());
            assertEquals(5, problem.line(), "inline Width uses the original source mark");
            assertTrue(problem.replaced());
            assertEquals(settings, Files.readString(directory.resolve("menus/demo-settings.yml")));
        }
    }

    @Test
    void customBusinessErrorIsSkippedWithoutChangingItsBytes() throws Exception {
        String path = "menus/custom.yml";
        String bad = "Version: 1\nType: impossible\nPages:\n  main: {Elements: []}\n";
        write(path, bad);
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            assertFalse(files.startupCandidate().catalog().menus().containsKey("custom"));
            LoadProblem problem = apply(files).getFirst();
            assertFalse(problem.replaced());
            assertNull(problem.backup());
            assertEquals(bad, Files.readString(directory.resolve(path)));
        }
    }

    @Test
    void backupFailureDoesNotWriteEvenAHeaderOrRecoverAJournal() throws Exception {
        String bad =
                "Version: 3\nResourcePack: {Provider: impossible}\nDefaultMenu: demo-settings\n";
        write("config.yml", bad);
        write("config.yml.tmp", "journal-original");
        try (var ignored = providers();
                var backup = mockStatic(FileBackup.class)) {
            backup.when(() -> FileBackup.beside(any(), any(byte[].class)))
                    .thenThrow(new IOException("backup denied"));
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            files.startupCandidate();
            LoadProblem report = apply(files).getFirst();
            assertTrue(report.writeBlocked());
            assertEquals(bad, Files.readString(directory.resolve("config.yml")));
            assertEquals("journal-original", Files.readString(directory.resolve("config.yml.tmp")));
            assertTrue(WriteGuard.isBlocked(directory.resolve("config.yml").toFile()));
        }
    }

    @Test
    void unknownVersionIsASettingsSemanticErrorWithACatalogDefault() throws Exception {
        write("config.yml", "Version: 999\n");
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            assertNotNull(files.startupCandidate().catalog());
            assertTrue(apply(files).getFirst().replaced());
        }
    }

    @Test
    void encodingErrorKeepsOriginalGbkBackup() throws Exception {
        byte[] gbk = "DefaultLanguage: 中文\n".getBytes(Charset.forName("GBK"));
        Files.write(directory.resolve("text.yml"), gbk);
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            files.startupCandidate();
            LoadProblem report = apply(files).getFirst();
            assertEquals(LoadProblem.Kind.ENCODING, report.kind());
            assertArrayEquals(gbk, Files.readAllBytes(report.backup().toPath()));
        }
    }

    @Test
    void duplicateRawKeysCannotDisappearInACanonicalYamlRoundTrip() throws Exception {
        write("text.yml", "DefaultLanguage: zh_cn\nDefaultLanguage: en_us\n");
        try (ReloadTransaction tx = ReloadTransaction.begin()) {
            MenuFiles.collect(directory.toFile(), false);
            assertFalse(tx.valid());
            assertTrue(tx.problems().stream().anyMatch(p -> p.path().equals("text.yml")));
        }
        assertEquals(
                "DefaultLanguage: zh_cn\nDefaultLanguage: en_us\n",
                Files.readString(directory.resolve("text.yml")));
    }

    @Test
    void readOnlyCheckDoesNotCommitTabRepairOrLiftAGuard() throws Exception {
        String bad = "Version: 3\nDefaultMenu: demo-settings\nResourcePack:\n\tProvider: Auto\n";
        write("config.yml", bad);
        WriteGuard.block(directory.resolve("config.yml").toFile(), "previous guard");
        try (var ignored = providers();
                ReloadTransaction tx = ReloadTransaction.begin()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), false);
            assertTrue(tx.valid());
            assertNotNull(MenuCandidate.parse(files));
        }
        assertEquals(bad, Files.readString(directory.resolve("config.yml")));
        assertTrue(WriteGuard.isBlocked(directory.resolve("config.yml").toFile()));
    }

    @Test
    void brokenCustomDefaultDependencyDoesNotReplaceGoodSettings() throws Exception {
        String config = "Version: 3\nDefaultMenu: custom\n";
        write("config.yml", config);
        write("menus/custom.yml", "Version: 1\nType: impossible\nPages: {main: {Elements: []}}\n");
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            assertThrows(IllegalArgumentException.class, files::startupCandidate);
            List<LoadProblem> reports = apply(files);
            assertEquals(1, reports.size());
            assertEquals("menus/custom.yml", reports.getFirst().path());
            assertEquals(config, Files.readString(directory.resolve("config.yml")));
        }
    }

    @Test
    void languageBusinessFailureCannotUnblockOrWriteAHeaderAfterBackupFails() throws Exception {
        String path = "lang/zh_CN.yml";
        String bad = "command-help-title: {nested: invalid}\n";
        write(path, bad);
        write(path + ".tmp", "language-journal-original");
        var languageField = Lang.class.getDeclaredField("files");
        languageField.setAccessible(true);
        Object previous = languageField.get(null);
        try (var ignored = providers();
                var backup = mockStatic(FileBackup.class);
                var lang = mockStatic(Lang.class);
                var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            var manager = mock(org.bukkit.plugin.PluginManager.class);
            bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(manager);
            lang.when(Lang::load).thenThrow(new AssertionError("ordinary Lang.load is forbidden"));
            lang.when(() -> Lang.nodes("zh_CN")).thenCallRealMethod();
            lang.clearInvocations();
            backup.when(() -> FileBackup.beside(any(), any(byte[].class)))
                    .thenThrow(new IOException("language backup denied"));
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            files.startupCandidate();
            assertEquals(bad, Files.readString(directory.resolve(path)));
            LoadProblem report = apply(files).getFirst();
            assertEquals(path, report.path());
            files.loadStartupLanguages();
            lang.verify(Lang::load, never());
            assertTrue(Lang.nodes("zh_CN").containsKey("command-help-title"));
            assertInstanceOf(LangNode.Text.class, Lang.nodes("zh_CN").get("command-help-title"));
            assertEquals(bad, Files.readString(directory.resolve(path)));
            assertEquals(
                    "language-journal-original",
                    Files.readString(directory.resolve(path + ".tmp")));
            assertTrue(WriteGuard.isBlocked(directory.resolve(path).toFile()));
        } finally {
            languageField.set(null, previous);
        }
    }

    @Test
    void scalarLanguageCompatibilityAndNestedListRejectionAreExplicit() {
        Map<String, LangNode> nodes =
                StartupLanguages.nodes(
                        "number: 4\nboolean: true\nlist: [yes, null, 7]\n", "lang/custom.yml");
        assertEquals(new LangNode.Text("4"), nodes.get("number"));
        assertEquals(new LangNode.Text("true"), nodes.get("boolean"));
        assertInstanceOf(LangNode.Lines.class, nodes.get("list"));
        assertThrows(
                IllegalArgumentException.class,
                () -> StartupLanguages.nodes("list: [{nested: invalid}]\n", "lang/custom.yml"));
    }

    @Test
    void repeatedActionKeysUseTheActualOriginalPageAndOnlyOnePosition() throws Exception {
        String path = "menus/demo-dialogue.yml";
        write(
                path,
                "Version: 1\nType: canvas\nPages:\n  main:\n    Elements:\n      first:\n        Type: button\n        Position: [12, 0]\n        Text: first\n        Actions: ['tell: valid']\n      second:\n        Type: button\n        Position: [124, 0]\n        Text: second\n        Actions: ['nonsense: invalid']\n");
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            files.startupCandidate();
            LoadProblem problem = apply(files).getFirst();
            assertEquals(15, problem.line());
            assertEquals(9, problem.column());
            assertEquals(1, problem.cause().split("第 15 行", -1).length - 1);
        }
    }

    @Test
    void updateDelayIsNotClampedAndObsoleteIntervalStaysIgnored() throws Exception {
        StartupUpdates.validate(
                "enabled: false\ncheck-interval-hours: invalid\nstartup-delay-seconds: 60\n");
        write("update-check.yml", "enabled: false\nstartup-delay-seconds: -1\n");
        try (var ignored = providers()) {
            MenuFiles files = MenuFiles.collect(directory.toFile(), true);
            files.startupCandidate();
            LoadProblem report = apply(files).getFirst();
            assertEquals("update-check.yml", report.path());
            assertTrue(report.replaced());
            assertEquals(
                    MenuRepository.resource("update-check.yml"),
                    Files.readString(directory.resolve("update-check.yml")));
        }
    }
}
