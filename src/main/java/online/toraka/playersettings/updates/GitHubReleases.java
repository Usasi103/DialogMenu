package online.toraka.playersettings.updates;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/** One bounded, conditional request to the repository's latest published stable Release. */
final class GitHubReleases {
    record Release(String tag, String url) {}

    record Result(Release release, String error, long retryAfterSeconds) {
        static Result failed(String message, long retryAfterSeconds) {
            return new Result(null, message, retryAfterSeconds);
        }
    }

    record Response(int status, String body, String etag, long retryAfterSeconds) {}

    @FunctionalInterface
    interface Transport {
        Response get(URI uri, String token, String etag) throws IOException;
    }

    private final String repository;
    private final URI endpoint;
    private final Transport transport;
    private final boolean includePrereleases;
    private String etag;
    private Release cached;

    GitHubReleases(String repository) {
        this(repository, false, GitHubReleases::httpGet);
    }

    GitHubReleases(String repository, Transport transport) {
        this(repository, false, transport);
    }

    GitHubReleases(String repository, boolean includePrereleases) {
        this(repository, includePrereleases, GitHubReleases::httpGet);
    }

    GitHubReleases(String repository, boolean includePrereleases, Transport transport) {
        if (!repository.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("repository 必须为 GitHub 所有者/仓库名");
        }
        this.repository = repository;
        this.includePrereleases = includePrereleases;
        this.endpoint =
                URI.create(
                        "https://api.github.com/repos/"
                                + repository
                                + (includePrereleases
                                        ? "/releases?per_page=100"
                                        : "/releases/latest"));
        this.transport = Objects.requireNonNull(transport);
    }

    Result check(String token) {
        try {
            Response response = transport.get(endpoint, token, etag);
            if (response.status() == 304 && cached != null) {
                return new Result(cached, null, 0);
            }
            if (response.status() != 200) {
                String error =
                        switch (response.status()) {
                            case 401 -> "GitHub 凭据无效或已过期（HTTP 401）";
                            case 403, 429 ->
                                    "GitHub 拒绝访问或请求限流（HTTP " + response.status() + "），请检查读取权限或稍后重试";
                            case 404 -> "仓库没有正式 Release，或私有仓库的凭据缺少 Contents 读取权限（HTTP 404）";
                            default -> "GitHub 返回 HTTP " + response.status();
                        };
                return Result.failed(error, response.retryAfterSeconds());
            }
            var document = JsonParser.parseString(response.body());
            JsonObject json = null;
            if (includePrereleases) {
                for (var element : document.getAsJsonArray()) {
                    var candidate = element.getAsJsonObject();
                    if (!candidate.get("draft").getAsBoolean()
                            && ReleaseVersion.parse(candidate.get("tag_name").getAsString())
                                    .isPresent()) {
                        json = candidate;
                        break;
                    }
                }
            } else {
                json = document.getAsJsonObject();
            }
            if (json == null
                    || !json.has("draft")
                    || !json.has("prerelease")
                    || json.get("draft").getAsBoolean()
                    || (!includePrereleases && json.get("prerelease").getAsBoolean())) {
                return Result.failed("GitHub 未返回正式发布版本", 0);
            }
            String tag = json.get("tag_name").getAsString();
            if (ReleaseVersion.parse(tag).isEmpty()) {
                return Result.failed("Release 标签不符合可比较的版本格式", 0);
            }
            String url = json.get("html_url").getAsString();
            URI parsed = URI.create(url);
            if (!"https".equals(parsed.getScheme())
                    || !"github.com".equalsIgnoreCase(parsed.getHost())
                    || parsed.getRawUserInfo() != null
                    || parsed.getPort() != -1
                    || !parsed.getPath().startsWith("/" + repository + "/releases/tag/")
                    || url.chars().anyMatch(c -> c < 32 || c == 127 || c == 167)) {
                return Result.failed("GitHub 返回的 Release 地址无效", 0);
            }
            cached = new Release(tag, url);
            etag = response.etag();
            return new Result(cached, null, 0);
        } catch (IOException error) {
            // HTTP response bodies and exception text can contain secrets; do not log them.
            return Result.failed("无法连接 GitHub 或读取响应超时", 0);
        } catch (RuntimeException error) {
            return Result.failed("无法解析 GitHub Release 响应", 0);
        }
    }

    static Response httpGet(URI endpoint, String token, String etag) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Toraka-Plugin-Update-Checker");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            if (token != null && !token.isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }
            if (etag != null) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            int status = connection.getResponseCode();
            long retry = seconds(connection.getHeaderField("Retry-After"));
            if ("0".equals(connection.getHeaderField("X-RateLimit-Remaining"))) {
                retry =
                        Math.max(
                                retry,
                                seconds(connection.getHeaderField("X-RateLimit-Reset"))
                                        - Instant.now().getEpochSecond());
            }
            String body = "";
            if (status == 200) {
                try (var stream = connection.getInputStream()) {
                    byte[] bytes = stream.readNBytes(1_048_577);
                    if (bytes.length > 1_048_576) {
                        throw new IOException("Release response exceeds size limit");
                    }
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
            }
            return new Response(
                    status,
                    body,
                    connection.getHeaderField("ETag"),
                    Math.max(0, Math.min(86400, retry)));
        } finally {
            connection.disconnect();
        }
    }

    private static long seconds(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
