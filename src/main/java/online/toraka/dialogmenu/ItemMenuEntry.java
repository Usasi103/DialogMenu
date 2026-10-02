package online.toraka.dialogmenu;

import java.util.List;

public record ItemMenuEntry(
        String kind, String name, List<String> description, ItemDisplay display, String action) {}
