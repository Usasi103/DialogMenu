package online.toraka.dialogmenu;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/** {@code config.yml.ResourcePack}: which pack gates the menus and who sends it. */
public record MenuResourcePack(
        String name,
        String provider,
        String pack,
        String url,
        UUID uuid,
        String sha1,
        boolean requireLoaded,
        boolean autoInstall) {

    private static final Set<String> AUTO = Kt.setOf("auto", "craftengine");
    private static final Pattern PACK_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private static final Pattern UUID_TEXT =
            Pattern.compile(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final MenuResourcePack LEGACY =
            new MenuResourcePack("服务器资源包", "legacy", "", "", null, "", true);

    /** Kotlin default: Auto and CraftEngine install the bundled pack automatically. */
    public MenuResourcePack(
            String name,
            String provider,
            String pack,
            String url,
            UUID uuid,
            String sha1,
            boolean requireLoaded) {
        this(name, provider, pack, url, uuid, sha1, requireLoaded, AUTO.contains(provider));
    }

    /** Old configurations retain their previous gate until explicitly configured. */
    public static MenuResourcePack legacy() {
        return LEGACY;
    }

    /** {@code copy(provider = provider)}: the install flag is kept, as Kotlin's copy did. */
    public MenuResourcePack withProvider(String value) {
        return new MenuResourcePack(name, value, pack, url, uuid, sha1, requireLoaded, autoInstall);
    }

    /** {@code copy(autoInstall = value)}. */
    public MenuResourcePack withAutoInstall(boolean value) {
        return new MenuResourcePack(name, provider, pack, url, uuid, sha1, requireLoaded, value);
    }

    public static MenuResourcePack parse(ConfigurationSection section) {
        if (section == null) {
            return LEGACY;
        }
        String path = "config.yml.ResourcePack";
        Set<String> allowed =
                Kt.setOf(
                        "Name",
                        "Provider",
                        "Pack",
                        "URL",
                        "UUID",
                        "SHA1",
                        "RequireLoaded",
                        "AutoInstall");
        Kt.require(
                Kt.minus(section.getKeys(false), allowed).isEmpty(),
                () -> path + ": 未知字段 " + Kt.minus(section.getKeys(false), allowed));
        String provider = Kt.lower(text(section, path, "Provider", "CraftEngine"));
        Kt.require(
                Kt.setOf("auto", "craftengine", "url", "external").contains(provider),
                () -> path + ".Provider: Auto / CraftEngine / URL / External");
        String name = text(section, path, "Name", "DialogMenu 菜单资源");
        Kt.require(Kt.isNotBlank(name), () -> path + ".Name: 不能为空");
        String pack = text(section, path, "Pack", "default");
        Kt.require(
                !AUTO.contains(provider) || PACK_ID.matcher(pack).matches(),
                () -> path + ".Pack: 填写 CraftEngine 的包 ID");
        String url = text(section, path, "URL", "");
        if (!url.isEmpty()) {
            URI uri;
            try {
                uri = new URI(url);
            } catch (Exception error) {
                uri = null;
            }
            URI parsed = uri;
            Kt.require(
                    parsed != null
                            && Kt.setOf("http", "https").contains(parsed.getScheme())
                            && parsed.getHost() != null
                            && !parsed.getHost().isEmpty()
                            && parsed.getUserInfo() == null,
                    () -> path + ".URL: 需要 http/https 资源包直链");
        }
        Kt.require(!provider.equals("url") || !url.isEmpty(), () -> path + ".URL: URL 模式必须指定资源包直链");
        String rawId = text(section, path, "UUID", "");
        UUID id;
        if (!rawId.isEmpty()) {
            Kt.require(UUID_TEXT.matcher(rawId).matches(), () -> path + ".UUID: 无效 UUID");
            id = UUID.fromString(rawId);
        } else if (provider.equals("url")) {
            id = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        } else {
            id = null;
        }
        Kt.require(
                !provider.equals("external") || id != null,
                () -> path + ".UUID: External 模式必须填写发送方使用的包 UUID");
        String hash = Kt.lower(text(section, path, "SHA1", ""));
        Kt.require(
                hash.isEmpty() || SHA1.matcher(hash).matches(),
                () -> path + ".SHA1: 留空或填写 40 位 SHA-1");
        Kt.require(
                !section.contains("RequireLoaded") || section.isBoolean("RequireLoaded"),
                () -> path + ".RequireLoaded: true/false");
        Kt.require(
                !section.contains("AutoInstall") || section.isBoolean("AutoInstall"),
                () -> path + ".AutoInstall: true/false");
        return new MenuResourcePack(
                name,
                provider,
                pack,
                url,
                id,
                hash,
                section.getBoolean("RequireLoaded", true),
                section.getBoolean("AutoInstall", AUTO.contains(provider)));
    }

    private static String text(
            ConfigurationSection section, String path, String key, String defaultValue) {
        Object value = section.get(key);
        if (value == null) {
            value = defaultValue;
        }
        Kt.require(
                value instanceof String text && Kt.noControl(text) && text.equals(Kt.trim(text)),
                () -> path + "." + key + ": 需要单行字符串");
        return (String) value;
    }
}
