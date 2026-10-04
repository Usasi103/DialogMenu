import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;

/** Decode every shipped font provider with the unmodified target client, including large spaces. */
public final class NativeFontCodecProbe {
    public static void main(String[] args) throws Exception {
        int fonts = 0, providers = 0, nativeProviders = 0;
        try (var pack = new ZipFile(Path.of(args[0]).toFile());
                var client = new ZipFile(Path.of(args[1]).toFile())) {
            for (var entry : pack.stream().toList()) {
                String name = entry.getName();
                if (!name.matches("assets/[^/]+/font/.*\\.json")) continue;
                var document = JsonParser.parseString(new String(pack.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var element : document.getAsJsonArray("providers")) {
                    GlyphProviderDefinition.MAP_CODEC.codec().parse(JsonOps.INSTANCE, element).getOrThrow(message -> new IllegalArgumentException(name + ": " + message));
                    var provider = element.getAsJsonObject();
                    String type = provider.get("type").getAsString();
                    if (type.equals("unihex")) {
                        if (!provider.get("hex_file").getAsString().equals("minecraft:font/unifont.zip")) throw new AssertionError("Bundled Unicode archive " + name);
                        nativeProviders++;
                    } else if (type.equals("reference")) {
                        String id = provider.get("id").getAsString();
                        String[] parts = id.split(":", 2);
                        if (!parts[0].equals("minecraft") && pack.getEntry("assets/" + parts[0] + "/font/" + parts[1] + ".json") == null) throw new AssertionError("Missing reference " + id);
                    } else if (type.equals("bitmap")) {
                        String[] parts = provider.get("file").getAsString().split(":", 2);
                        String bitmap = "assets/" + parts[0] + "/textures/" + parts[1];
                        if (pack.getEntry(bitmap) == null && client.getEntry(bitmap) == null) throw new AssertionError("Missing bitmap " + name + ": " + bitmap);
                    }
                    providers++;
                }
                fonts++;
            }
            if (nativeProviders != 2) throw new AssertionError("Expected exactly two client Unihex providers: " + nativeProviders);
            if (pack.stream().anyMatch(e -> e.getName().matches(".*button_cjk_[0-9]+\\.png") || e.getName().endsWith("/unifont.zip"))) throw new AssertionError("Retired CJK asset shipped");
        }
        System.out.println("PASS: original Minecraft 26.3 codec decoded " + providers + " providers in " + fonts + " fonts; references resolve; two client Unihex providers; no CJK atlas/archive.");
    }
}
