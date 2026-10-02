package online.toraka.dialogmenu;

/** Paths follow each provider's documented resource-pack input, never its generated output. */
public enum ResourcePackProvider {
    CRAFT_ENGINE("CraftEngine", "resources/dialogmenu", "resources"),
    ITEMS_ADDER("ItemsAdder", "contents/dialogmenu", "contents"),
    NEXO("Nexo", "pack/external_packs/dialogmenu", "pack/external_packs"),
    ORAXEN("Oraxen", "pack", "pack");

    private final String pluginName;
    private final String sourceDirectory;
    private final String inputDirectory;

    ResourcePackProvider(String pluginName, String sourceDirectory, String inputDirectory) {
        this.pluginName = pluginName;
        this.sourceDirectory = sourceDirectory;
        this.inputDirectory = inputDirectory;
    }

    public String pluginName() {
        return pluginName;
    }

    public String sourceDirectory() {
        return sourceDirectory;
    }

    public String inputDirectory() {
        return inputDirectory;
    }

    public String instructions() {
        return switch (this) {
            case CRAFT_ENGINE ->
                    "执行 /ce reload all 重新读取资源目录，再执行服务器配置的资源包工作流（默认 /ce workflow default），让玩家重新接收合并包。";
            case ITEMS_ADDER -> "执行 /iazip 重新生成资源包，再由 ItemsAdder 发送，让玩家接受并加载。";
            case NEXO -> "执行 /nexo reload pack；确认 Nexo 已启用资源包发送，让玩家接受并加载。";
            case ORAXEN -> "执行 /oraxen reload pack，再使用 /oraxen pack send <玩家> 或重新进服接收。";
        };
    }
}
