package online.toraka.dialogmenu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public record AvailableResourceProvider(
        ResourcePackProvider provider,
        Path directory,
        List<Path> externalDirectories,
        List<Path> externalArchives,
        List<String> warnings) {

    public AvailableResourceProvider(ResourcePackProvider provider, Path directory) {
        this(
                provider,
                directory,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public static AvailableResourceProvider read(ResourcePackProvider provider, Path directory) {
        Path file =
                directory.resolve(
                        provider == ResourcePackProvider.CRAFT_ENGINE
                                ? "config.yml"
                                : "settings.yml");
        if (!Files.isRegularFile(file)) {
            return new AvailableResourceProvider(provider, directory);
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            List<Path> directories = new ArrayList<>();
            List<Path> archives = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            if (provider == ResourcePackProvider.CRAFT_ENGINE) {
                // CraftEngine's PackCacheData resolves external inputs relative to plugins/.
                for (String folder : yaml.getStringList("resource-pack.merge-external-folders")) {
                    directories.add(directory.getParent().resolve(folder));
                }
                for (String zip : yaml.getStringList("resource-pack.merge-external-zip-files")) {
                    Path archive = directory.getParent().resolve(zip);
                    if (Files.exists(archive)) {
                        archives.add(archive);
                    }
                }
                if (yaml.getBoolean("resource-pack.exclude-core-shaders")) {
                    warnings.add(
                            file
                                    + " 的 resource-pack.exclude-core-shaders 已启用，合并包会缺少 DialogMenu GUI shader；请由服主检查，未自动修改。");
                }
            }
            if (provider == ResourcePackProvider.ORAXEN
                    && yaml.getBoolean("Pack.import.remove_core_shaders_from_imported_packs")) {
                warnings.add(
                        file
                                + " 的 Pack.import.remove_core_shaders_from_imported_packs 已启用，Oraxen 会过滤 DialogMenu GUI shader；请由服主检查，未自动修改。");
            }
            return new AvailableResourceProvider(
                    provider, directory, directories, archives, warnings);
        } catch (Exception error) {
            // Do not write into a provider whose source configuration could not be inspected.
            throw new IllegalArgumentException(
                    "无法读取 " + provider.pluginName() + " 资源输入配置 " + file + "：" + error.getMessage(),
                    error);
        }
    }
}
