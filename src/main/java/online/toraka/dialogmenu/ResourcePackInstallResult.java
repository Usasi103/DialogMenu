package online.toraka.dialogmenu;

import java.nio.file.Path;
import java.util.List;

public record ResourcePackInstallResult(
        Path exportedZip,
        AvailableResourceProvider provider,
        int changed,
        List<String> conflicts) {}
