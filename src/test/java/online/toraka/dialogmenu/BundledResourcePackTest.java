package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledResourcePackTest {
    @TempDir Path directory;

    private final String font = "assets/toraka_settings/font/ui.json";
    private final String shader = "assets/minecraft/shaders/core/gui.vsh";
    private final MenuResourcePack automatic =
            new MenuResourcePack("Menu", "auto", "default", "", null, "", false);

    @SafeVarargs
    private byte[] archive(Map.Entry<String, String>... assets) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Map.Entry<String, String>> contents = new ArrayList<>();
        contents.add(Map.entry("pack.mcmeta", "{}"));
        contents.add(Map.entry("LICENSE-test.txt", "license"));
        contents.addAll(List.of(assets));
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : contents) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static Map.Entry<String, String> pair(String name, String contents) {
        return Map.entry(name, contents);
    }

    private AvailableResourceProvider provider(ResourcePackProvider type) {
        return new AvailableResourceProvider(type, directory.resolve(type.pluginName()));
    }

    private ResourcePackInstallResult install(
            byte[] bytes, List<AvailableResourceProvider> available) {
        return install(bytes, available, automatic);
    }

    private ResourcePackInstallResult install(
            byte[] bytes, List<AvailableResourceProvider> available, MenuResourcePack config) {
        return new BundledResourcePack(directory.resolve("DialogMenu"), bytes)
                .install(config, available);
    }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private static String quote(Path path) {
        return "'" + path.toAbsolutePath().toString().replace("'", "''") + "'";
    }

    private static void directoryLink(Path link, Path target) throws Exception {
        Files.createDirectories(link.getParent());
        Files.createDirectories(target);
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process process =
                    new ProcessBuilder(
                                    "powershell.exe",
                                    "-NoProfile",
                                    "-NonInteractive",
                                    "-Command",
                                    "New-Item -ItemType Junction -Path "
                                            + quote(link)
                                            + " -Target "
                                            + quote(target)
                                            + " -ErrorAction Stop | Out-Null")
                            .redirectErrorStream(true)
                            .start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertEquals(0, process.waitFor(), output);
        } else {
            Files.createSymbolicLink(link, target.toAbsolutePath());
        }
        assertEquals(target.toRealPath(), link.toRealPath());
    }

    private static boolean anyContains(List<String> conflicts, String text) {
        return conflicts.stream().anyMatch(it -> it.contains(text));
    }

    private static Properties readState(Path stateFile) throws IOException {
        Properties state = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            state.load(input);
        }
        return state;
    }

    private static void writeState(Path stateFile, Properties state, String comment)
            throws IOException {
        try (OutputStream output = Files.newOutputStream(stateFile)) {
            state.store(output, comment);
        }
    }

    @Test
    @DisplayName("provider input roots may be linked to administrator resource storage")
    void providerInputRootsMayBeLinkedToAdministratorResourceStorage() throws Exception {
        Map<ResourcePackProvider, String> inputs = new LinkedHashMap<>();
        inputs.put(ResourcePackProvider.CRAFT_ENGINE, "resources");
        inputs.put(ResourcePackProvider.ITEMS_ADDER, "contents");
        inputs.put(ResourcePackProvider.NEXO, "pack/external_packs");
        inputs.put(ResourcePackProvider.ORAXEN, "pack");
        for (Map.Entry<ResourcePackProvider, String> mapping : inputs.entrySet()) {
            ResourcePackProvider type = mapping.getKey();
            String input = mapping.getValue();
            AvailableResourceProvider provider = provider(type);
            Path storage = directory.resolve("storage-" + type.name());
            Path link = provider.directory().resolve(input);
            directoryLink(link, storage);
            try {
                byte[] bytes = archive(pair(font, "font"), pair(shader, "shader"));
                ResourcePackInstallResult result = install(bytes, List.of(provider));
                assertTrue(result.conflicts().isEmpty(), result.conflicts().toString());
                String resource =
                        switch (type) {
                            case CRAFT_ENGINE, ITEMS_ADDER -> "dialogmenu/resourcepack/" + font;
                            case NEXO -> "dialogmenu/" + font;
                            case ORAXEN -> font;
                        };
                assertEquals("font", Files.readString(storage.resolve(resource)));
                assertEquals(0, install(bytes, List.of(provider)).changed());
            } finally {
                Files.deleteIfExists(link);
            }
        }
    }

    @Test
    @DisplayName(
            "retargeting the input link does not transfer ownership or remove files in the new"
                    + " storage")
    void retargetingTheInputLinkDoesNotTransferOwnershipOrRemoveFilesInTheNewStorage()
            throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        Path link = ce.directory().resolve("resources");
        Path firstStorage = directory.resolve("first-storage");
        Path secondStorage = directory.resolve("second-storage");
        byte[] first = archive(pair(font, "font-v1"), pair(shader, "shader-v1"));
        directoryLink(link, firstStorage);
        try {
            assertTrue(install(first, List.of(ce)).conflicts().isEmpty());
            Files.delete(link);
            write(secondStorage.resolve("dialogmenu/resourcepack/" + font), "font-v1");
            write(secondStorage.resolve("dialogmenu/resourcepack/" + shader), "shader-v1");
            directoryLink(link, secondStorage);
            ResourcePackInstallResult result = install(archive(pair(font, "font-v2")), List.of(ce));
            assertEquals(
                    "font-v1",
                    Files.readString(secondStorage.resolve("dialogmenu/resourcepack/" + font)));
            assertEquals(
                    "shader-v1",
                    Files.readString(secondStorage.resolve("dialogmenu/resourcepack/" + shader)));
            assertEquals(
                    "font-v1",
                    Files.readString(firstStorage.resolve("dialogmenu/resourcepack/" + font)));
            assertTrue(anyContains(result.conflicts(), font));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    @DisplayName(
            "legacy state for ordinary directories still updates managed files and removes retired"
                    + " resources")
    void legacyStateForOrdinaryDirectoriesStillUpdatesManagedFilesAndRemovesRetiredResources()
            throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        install(archive(pair(font, "font-v1"), pair(shader, "shader-v1")), List.of(ce));
        Path stateFile = directory.resolve("DialogMenu/resourcepack/craft_engine-state.properties");
        Properties state = readState(stateFile);
        state.remove("resolved-root");
        writeState(stateFile, state, "legacy state");
        ResourcePackInstallResult result = install(archive(pair(font, "font-v2")), List.of(ce));
        assertTrue(result.conflicts().isEmpty(), result.conflicts().toString());
        assertEquals(
                "font-v2",
                Files.readString(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + font)));
        assertFalse(
                Files.exists(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
    }

    @Test
    @DisplayName(
            "links inside a resource input root never redirect writes or retired file deletion")
    void linksInsideAResourceInputRootNeverRedirectWritesOrRetiredFileDeletion() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        byte[] first = archive(pair(font, "font-v1"), pair(shader, "shader-v1"));
        install(first, List.of(ce));
        Path link =
                ce.directory()
                        .resolve("resources/dialogmenu/resourcepack/assets/toraka_settings/font");
        Files.delete(link.resolve("ui.json"));
        Files.delete(link);
        Path outside = directory.resolve("unrelated-fonts");
        write(outside.resolve("ui.json"), "font-v1");
        directoryLink(link, outside);
        try {
            ResourcePackInstallResult blocked =
                    install(archive(pair(font, "font-v2"), pair(shader, "shader-v2")), List.of(ce));
            assertTrue(anyContains(blocked.conflicts(), font));
            assertEquals("font-v1", Files.readString(outside.resolve("ui.json")));
            assertEquals(
                    "shader-v2",
                    Files.readString(
                            ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
            ResourcePackInstallResult retired =
                    install(archive(pair(shader, "shader-v2")), List.of(ce));
            assertTrue(anyContains(retired.conflicts(), font));
            assertEquals("font-v1", Files.readString(outside.resolve("ui.json")));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    @DisplayName(
            "without a provider export a usable unchanged ZIP and do not invent provider"
                    + " directories")
    void withoutAProviderExportAUsableUnchangedZipAndDoNotInventProviderDirectories()
            throws Exception {
        byte[] bytes = archive(pair(font, "original"), pair(shader, "shader"));
        ResourcePackInstallResult result = install(bytes, Collections.emptyList());
        assertNull(result.provider());
        assertArrayEquals(bytes, Files.readAllBytes(result.exportedZip()));
        assertTrue(result.conflicts().isEmpty());
        assertFalse(Files.exists(directory.resolve("CraftEngine")));
    }

    @Test
    @DisplayName("legacy state does not claim ownership through a linked provider directory")
    void legacyStateDoesNotClaimOwnershipThroughALinkedProviderDirectory() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        String oldFont = "resources/dialogmenu/resourcepack/" + font;
        String oldShader = "resources/dialogmenu/resourcepack/" + shader;
        install(archive(pair(font, "font-v1"), pair(shader, "shader-v1")), List.of(ce));
        Path stateFile = directory.resolve("DialogMenu/resourcepack/craft_engine-state.properties");
        Properties state = readState(stateFile);
        state.remove("resolved-root");
        writeState(stateFile, state, "legacy state");
        Files.move(ce.directory(), directory.resolve("previous-provider"));
        Path other = directory.resolve("other-provider");
        write(other.resolve(oldFont), "font-v1");
        write(other.resolve(oldShader), "shader-v1");
        directoryLink(ce.directory(), other);
        try {
            ResourcePackInstallResult result = install(archive(pair(font, "font-v2")), List.of(ce));
            assertTrue(anyContains(result.conflicts(), font));
            assertEquals("font-v1", Files.readString(other.resolve(oldFont)));
            assertEquals("shader-v1", Files.readString(other.resolve(oldShader)));
        } finally {
            Files.deleteIfExists(ce.directory());
        }
    }

    @Test
    @DisplayName("nested links inside the same input tree are also refused")
    void nestedLinksInsideTheSameInputTreeAreAlsoRefused() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        Path other = ce.directory().resolve("resources/other-pack");
        Path link = ce.directory().resolve("resources/dialogmenu");
        directoryLink(link, other);
        try {
            ResourcePackInstallResult result = install(archive(pair(font, "font")), List.of(ce));
            assertEquals(0, result.changed());
            assertFalse(result.conflicts().isEmpty());
            assertFalse(Files.exists(other.resolve("pack.yml")));
            assertFalse(Files.exists(other.resolve("resourcepack/" + font)));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    @DisplayName("stale state entries outside the input tree cannot remove provider configuration")
    void staleStateEntriesOutsideTheInputTreeCannotRemoveProviderConfiguration() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        byte[] bytes = archive(pair(font, "font"));
        install(bytes, List.of(ce));
        write(ce.directory().resolve("settings.yml"), "administrator settings");
        Path stateFile = directory.resolve("DialogMenu/resourcepack/craft_engine-state.properties");
        Properties state = readState(stateFile);
        StringBuilder hash = new StringBuilder();
        for (byte value :
                MessageDigest.getInstance("SHA-256")
                        .digest("administrator settings".getBytes(StandardCharsets.UTF_8))) {
            hash.append(String.format(Locale.ROOT, "%02x", value));
        }
        state.setProperty("file.settings.yml", hash.toString());
        state.setProperty("file.resources/../settings.yml", hash.toString());
        writeState(stateFile, state, "stale entries");
        ResourcePackInstallResult result = install(bytes, List.of(ce));
        assertEquals(
                "administrator settings", Files.readString(ce.directory().resolve("settings.yml")));
        assertEquals(2, result.conflicts().size());
    }

    @Test
    @DisplayName("CraftEngine wins regardless of provider order and has a proper pack manifest")
    void craftEngineWinsRegardlessOfProviderOrderAndHasAProperPackManifest() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        List<ResourcePackProvider> reversed =
                new ArrayList<>(List.of(ResourcePackProvider.values()));
        Collections.reverse(reversed);
        List<AvailableResourceProvider> others = new ArrayList<>();
        for (ResourcePackProvider type : reversed) {
            others.add(provider(type));
        }
        ResourcePackInstallResult result = install(archive(pair(font, "font")), others);
        assertEquals(ce, result.provider());
        assertEquals(
                "font",
                Files.readString(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + font)));
        assertTrue(
                Files.readString(ce.directory().resolve("resources/dialogmenu/pack.yml"))
                        .contains("namespace: dialogmenu"));
        assertEquals(
                "license",
                Files.readString(
                        ce.directory()
                                .resolve("resources/dialogmenu/resourcepack/LICENSE-test.txt")));
        assertFalse(Files.exists(directory.resolve("Nexo")));
    }

    @Test
    @DisplayName("repeat installation changes nothing and an upgrade preserves modified files")
    void repeatInstallationChangesNothingAndAnUpgradePreservesModifiedFiles() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        byte[] first = archive(pair(font, "font-v1"), pair(shader, "shader-v1"));
        assertTrue(install(first, List.of(ce)).changed() > 0);
        Path target = ce.directory().resolve("resources/dialogmenu/resourcepack/" + font);
        FileTime timestamp = Files.getLastModifiedTime(target);
        assertEquals(0, install(first, List.of(ce)).changed());
        assertEquals(timestamp, Files.getLastModifiedTime(target));
        write(target, "server-custom-font");
        byte[] second = archive(pair(font, "font-v2"), pair(shader, "shader-v2"));
        ResourcePackInstallResult result = install(second, List.of(ce));
        assertEquals("server-custom-font", Files.readString(target));
        assertEquals(
                "shader-v2",
                Files.readString(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
        assertTrue(anyContains(result.conflicts(), font));
        assertEquals(0, install(second, List.of(ce)).changed());
        assertEquals("server-custom-font", Files.readString(target));
    }

    @Test
    @DisplayName("a shader in another CE resource source is never overwritten or duplicated")
    void aShaderInAnotherCeResourceSourceIsNeverOverwrittenOrDuplicated() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        Path other = ce.directory().resolve("resources/betterhud/resourcepack/" + shader);
        write(other, "betterhud-custom-shader");
        ResourcePackInstallResult result =
                install(archive(pair(font, "font"), pair(shader, "menu-shader")), List.of(ce));
        assertEquals("betterhud-custom-shader", Files.readString(other));
        assertFalse(
                Files.exists(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
        assertTrue(
                Files.exists(ce.directory().resolve("resources/dialogmenu/resourcepack/" + font)));
        assertTrue(anyContains(result.conflicts(), shader));
    }

    @Test
    @DisplayName(
            "if another source adds a conflicting shader remove only our unchanged managed copy")
    void ifAnotherSourceAddsAConflictingShaderRemoveOnlyOurUnchangedManagedCopy() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        byte[] bytes = archive(pair(font, "font"), pair(shader, "menu-shader"));
        install(bytes, List.of(ce));
        write(ce.directory().resolve("resources/custom/resourcepack/" + shader), "custom-shader");
        install(bytes, List.of(ce));
        assertFalse(
                Files.exists(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
        assertEquals(0, install(bytes, List.of(ce)).changed());
    }

    @Test
    @DisplayName("provider roots match documented layouts and preserve generator pack metadata")
    void providerRootsMatchDocumentedLayoutsAndPreserveGeneratorPackMetadata() throws Exception {
        for (ResourcePackProvider type :
                List.of(
                        ResourcePackProvider.ITEMS_ADDER,
                        ResourcePackProvider.NEXO,
                        ResourcePackProvider.ORAXEN)) {
            AvailableResourceProvider target = provider(type);
            Path metadata = target.directory().resolve("pack/pack.mcmeta");
            if (type == ResourcePackProvider.ORAXEN) {
                write(metadata, "provider-metadata");
            }
            install(archive(pair(font, type.name())), List.of(target));
            String resource =
                    switch (type) {
                        case ITEMS_ADDER -> "contents/dialogmenu/resourcepack/" + font;
                        case NEXO -> "pack/external_packs/dialogmenu/" + font;
                        default -> "pack/" + font;
                    };
            assertEquals(type.name(), Files.readString(target.directory().resolve(resource)));
            if (type == ResourcePackProvider.ORAXEN) {
                assertEquals("provider-metadata", Files.readString(metadata));
            }
        }
    }

    @Test
    @DisplayName(
            "existing URL and External sending configurations do not enable resource installation")
    void existingUrlAndExternalSendingConfigurationsDoNotEnableResourceInstallation()
            throws Exception {
        UUID id = UUID.randomUUID();
        MenuResourcePack url =
                MenuResourcePack.parse(
                        MenuConfigParser.yaml(
                                "Provider: URL\nURL: https://example.com/pack.zip\nUUID: "
                                        + id
                                        + "\n",
                                "config"));
        MenuResourcePack external =
                MenuResourcePack.parse(
                        MenuConfigParser.yaml("Provider: External\nUUID: " + id + "\n", "config"));
        for (MenuResourcePack config : List.of(url, external)) {
            ResourcePackInstallResult result =
                    install(
                            archive(pair(font, "font")),
                            List.of(provider(ResourcePackProvider.CRAFT_ENGINE)),
                            config);
            assertNull(result.provider());
            assertEquals(id, config.uuid());
            assertTrue(config.requireLoaded());
        }
        assertFalse(Files.exists(directory.resolve("CraftEngine")));
        assertEquals("https://example.com/pack.zip", url.url());
    }

    @Test
    @DisplayName("CE explicit mode does not fall back to another plugin and opt-out exports only")
    void ceExplicitModeDoesNotFallBackToAnotherPluginAndOptOutExportsOnly() throws Exception {
        AvailableResourceProvider ia = provider(ResourcePackProvider.ITEMS_ADDER);
        MenuResourcePack explicit = automatic.withProvider("craftengine");
        assertNull(install(archive(pair(font, "font")), List.of(ia), explicit).provider());
        assertNull(
                install(archive(pair(font, "font")), List.of(ia), automatic.withAutoInstall(false))
                        .provider());
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        assertEquals(
                ce, BundledResourcePack.detect(automatic.withAutoInstall(false), List.of(ia, ce)));
    }

    @Test
    @DisplayName("archive traversal is rejected before exporting or installing files")
    void archiveTraversalIsRejectedBeforeExportingOrInstallingFiles() {
        for (String name :
                List.of("../escape", "assets/../../escape", "assets\\escape", "C:/escape")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            install(
                                    archive(pair(font, "font"), pair(name, "unsafe")),
                                    List.of(provider(ResourcePackProvider.CRAFT_ENGINE))));
        }
        assertFalse(
                Files.exists(
                        directory.resolve("DialogMenu/resourcepack/DialogMenu-resourcepack.zip")));
    }

    @Test
    @DisplayName(
            "file occupying a required directory preserves user contents and other assets install")
    void fileOccupyingARequiredDirectoryPreservesUserContentsAndOtherAssetsInstall()
            throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        Path blocked =
                ce.directory()
                        .resolve("resources/dialogmenu/resourcepack/assets/toraka_settings/font");
        write(blocked, "user-file");
        ResourcePackInstallResult result =
                install(archive(pair(font, "font"), pair(shader, "shader")), List.of(ce));
        assertEquals("user-file", Files.readString(blocked));
        assertTrue(anyContains(result.conflicts(), font));
        assertTrue(
                Files.exists(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
    }

    @Test
    @DisplayName(
            "Nexo ZIP conflicts and corrupt archives preserve shaders without blocking namespaced"
                    + " assets")
    void nexoZipConflictsAndCorruptArchivesPreserveShadersWithoutBlockingNamespacedAssets()
            throws Exception {
        AvailableResourceProvider nexo = provider(ResourcePackProvider.NEXO);
        Path otherZip = nexo.directory().resolve("pack/external_packs/other.zip");
        Files.createDirectories(otherZip.getParent());
        Files.write(otherZip, archive(pair(shader, "other-shader")));
        ResourcePackInstallResult result =
                install(archive(pair(font, "font"), pair(shader, "menu-shader")), List.of(nexo));
        assertTrue(anyContains(result.conflicts(), shader));
        assertFalse(
                Files.exists(nexo.directory().resolve("pack/external_packs/dialogmenu/" + shader)));
        write(otherZip, "invalid-zip");
        ResourcePackInstallResult broken =
                install(
                        archive(pair(font, "new-font"), pair(shader, "menu-shader")),
                        List.of(nexo));
        assertEquals(
                "new-font",
                Files.readString(
                        nexo.directory().resolve("pack/external_packs/dialogmenu/" + font)));
        assertTrue(anyContains(broken.conflicts(), "other.zip"));
    }

    @Test
    @DisplayName(
            "Oraxen uploaded folders including dialogmenu are checked before importing shaders")
    void oraxenUploadedFoldersIncludingDialogmenuAreCheckedBeforeImportingShaders()
            throws Exception {
        AvailableResourceProvider oraxen = provider(ResourcePackProvider.ORAXEN);
        write(oraxen.directory().resolve("pack/uploads/dialogmenu/" + shader), "uploaded-shader");
        ResourcePackInstallResult result =
                install(archive(pair(font, "font"), pair(shader, "menu-shader")), List.of(oraxen));
        assertTrue(anyContains(result.conflicts(), shader));
        assertFalse(Files.exists(oraxen.directory().resolve("pack/" + shader)));
    }

    @Test
    @DisplayName("CE external sources use plugins as base and shader filtering is reported")
    void ceExternalSourcesUsePluginsAsBaseAndShaderFilteringIsReported() throws Exception {
        AvailableResourceProvider ce = provider(ResourcePackProvider.CRAFT_ENGINE);
        write(
                ce.directory().resolve("config.yml"),
                "resource-pack:\n  merge-external-folders: [BetterHud/build]\n  exclude-core-shaders: true\n");
        write(directory.resolve("BetterHud/build/" + shader), "hud-shader");
        AvailableResourceProvider configured =
                AvailableResourceProvider.read(ce.provider(), ce.directory());
        ResourcePackInstallResult result =
                install(
                        archive(pair(font, "font"), pair(shader, "menu-shader")),
                        List.of(configured));
        assertTrue(anyContains(result.conflicts(), "exclude-core-shaders"));
        assertTrue(anyContains(result.conflicts(), "BetterHud"));
        assertFalse(
                Files.exists(
                        ce.directory().resolve("resources/dialogmenu/resourcepack/" + shader)));
    }
}
