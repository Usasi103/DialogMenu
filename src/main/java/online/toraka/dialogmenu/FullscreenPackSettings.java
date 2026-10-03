package online.toraka.dialogmenu;

import java.net.URI;
import java.util.Set;

/** Local HTTP delivery settings, separate from a menu's rendering type and layout. */
public record FullscreenPackSettings(String bind, int port, String url) {
    public static FullscreenPackSettings parse(String source) {
        var root = MenuConfigParser.yaml(source, "fullscreen.yml");
        Set<String> allowed = Set.of("pack-bind", "pack-port", "pack-url");
        if (!allowed.containsAll(root.getKeys(false))) {
            throw new IllegalArgumentException(
                    "fullscreen.yml: 只允许 pack-bind、pack-port 和 pack-url");
        }
        if (!(root.get("pack-bind") instanceof String bind)
                || bind.isBlank()
                || !root.isInt("pack-port")
                || root.getInt("pack-port") < 1
                || root.getInt("pack-port") > 65535
                || !(root.get("pack-url") instanceof String url)) {
            throw new IllegalArgumentException("fullscreen.yml: 地址必须为字符串，端口必须在 1–65535 之间");
        }
        if (!url.isEmpty()) {
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("fullscreen.yml.pack-url: 无效 URL", error);
            }
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getQuery() != null) {
                throw new IllegalArgumentException(
                        "fullscreen.yml.pack-url: 需要不含认证信息、查询或片段的 HTTP(S) 基础地址");
            }
        }
        return new FullscreenPackSettings(bind, root.getInt("pack-port"), url);
    }
}
