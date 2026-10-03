package online.toraka.dialogmenu;

import java.io.File;
import dev.keystone.config.ConfigProblems;
import dev.keystone.config.ReloadTransaction;
import dev.keystone.lang.Lang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MenuRuntime {

    private static MenuDefinition fallback;
    private static MenuRepository repository;
    private static TemplateRepository templateRepository;
    private static CatalogRepository catalogRepository;
    private static File directory;
    private static MenuTranslations translations = new MenuTranslations();
    private static FullscreenPackSettings fullscreenSettings;

    public static FullscreenPackSettings fullscreenSettings() {
        return fullscreenSettings;
    }

    private MenuRuntime() {}

    /** The bundled menu, parsed on first use like Kotlin's {@code by lazy}. */
    private static synchronized MenuDefinition fallback() {
        if (fallback == null) {
            fallback = MenuRepository.bundled();
        }
        return fallback;
    }

    public static MenuTranslations translations() {
        return translations;
    }

    private static MenuCatalog catalog() {
        return catalogRepository != null ? catalogRepository.current() : null;
    }

    public static Map<String, DialogTemplate> templates() {
        MenuCatalog catalog = catalog();
        if (catalog != null) {
            return catalog.templates();
        }
        return templateRepository != null ? templateRepository.current() : Collections.emptyMap();
    }

    public static MenuDefinition current() {
        MenuCatalog catalog = catalog();
        if (catalog != null) {
            CatalogMenu preferred = catalog.menus().get(catalog.defaultMenu());
            MenuDefinition settings = preferred != null ? preferred.settings() : null;
            if (settings == null) {
                for (CatalogMenu menu : catalog.menus().values()) {
                    if (menu.settings() != null) {
                        settings = menu.settings();
                        break;
                    }
                }
            }
            if (settings != null) {
                return settings;
            }
        }
        if (repository != null) {
            return repository.current();
        }
        return fallback();
    }

    public static List<MenuDefinition> definitions() {
        MenuCatalog catalog = catalog();
        if (catalog == null) {
            return Kt.listOf(current());
        }
        List<MenuDefinition> result = new ArrayList<>();
        for (CatalogMenu menu : catalog.menus().values()) {
            if (menu.settings() != null) {
                result.add(menu.settings());
            }
        }
        return result;
    }

    public static List<String> menuIds() {
        MenuCatalog catalog = catalog();
        if (catalog != null) {
            return new ArrayList<>(catalog.menus().keySet());
        }
        List<String> result = new ArrayList<>();
        result.add("settings");
        result.addAll(templates().keySet());
        return result;
    }

    public static MenuDefinition settings(String id) {
        MenuCatalog catalog = catalog();
        CatalogMenu menu = catalog != null ? catalog.menus().get(id) : null;
        if (menu != null && menu.settings() != null) {
            return menu.settings();
        }
        return catalog == null && id.equals("settings") ? current() : null;
    }

    public static List<String> pages(String id) {
        MenuCatalog catalog = catalog();
        CatalogMenu menu = catalog != null ? catalog.menus().get(id) : null;
        if (menu != null) {
            return new ArrayList<>(menu.pages());
        }
        return id.equals("settings")
                ? new ArrayList<>(current().pages().keySet())
                : Collections.emptyList();
    }

    public static String resolveTemplate(String id) {
        if (templates().containsKey(id)) {
            return id;
        }
        return switch (id) {
            case "npc-dialogue" -> "demo-dialogue/main";
            case "boss-intro" -> "demo-boss/intro";
            case "boss-confirm" -> "demo-boss/confirm";
            default -> id;
        };
    }

    public static void open(Player player) {
        open(player, null, null);
    }

    public static void open(Player player, String id) {
        open(player, id, null);
    }

    public static void open(Player player, String id, String page) {
        if (!player.hasPermission("playersettings.use")) return;
        MenuCatalog catalog = catalog();
        String requested = id != null ? id : catalog != null ? catalog.defaultMenu() : "settings";
        CatalogMenu menu = catalog != null ? catalog.menus().get(requested) : null;
        if (menu != null && menu.menuType() == MenuType.FULLSCREEN) {
            if (page != null && !menu.pages().contains(page)) {
                player.sendMessage("DialogMenu：全屏菜单仅有 main 页面。");
            } else if (DialogMenu.fullscreen() != null) {
                DialogMenu.fullscreen().request(player);
            } else {
                player.sendMessage("DialogMenu：当前服务器不支持全屏菜单，需要 Paper 26.3。");
            }
            return;
        }
        if (menu != null && menu.settings() != null) {
            MenuDialog.open(player, page != null ? page : menu.defaultPage(), requested);
        } else if (menu != null) {
            TemplateDialog.open(
                    player, requested + "/" + (page != null ? page : menu.defaultPage()));
        } else if (requested.equals("settings") && catalog == null) {
            MenuDialog.open(player, page != null ? page : current().defaultPage());
        } else if (page == null && current().pages().containsKey(requested)) {
            String owner = null;
            if (catalog != null) {
                MenuDefinition active = current();
                for (CatalogMenu candidate : catalog.menus().values()) {
                    if (candidate.settings() == active) {
                        owner = candidate.id();
                        break;
                    }
                }
            }
            MenuDialog.open(player, requested, owner != null ? owner : "settings");
        } else if (page == null && templates().containsKey(resolveTemplate(requested))) {
            TemplateDialog.open(player, requested);
        } else {
            player.sendMessage(
                    "DialogMenu：菜单或页面不存在 " + requested + (page != null ? "/" + page : ""));
        }
    }

    public static void initialize(File folder) {
        Startup startup = prepareStartup(folder);
        applyStartup(startup);
        initialize(startup);
    }

    /** The diagnostic fullscreen return button always selects a Dialog, even if fullscreen is default. */
    public static void openDialog(Player player) {
        MenuCatalog catalog = catalog();
        if (catalog != null) {
            CatalogMenu preferred = catalog.menus().get(catalog.defaultMenu());
            if (preferred != null && preferred.menuType() == MenuType.DIALOG) {
                open(player, preferred.id());
                return;
            }
            for (CatalogMenu menu : catalog.menus().values()) {
                if (menu.menuType() == MenuType.DIALOG) {
                    open(player, menu.id());
                    return;
                }
            }
            player.sendMessage("DialogMenu：当前没有配置 Dialog 类型菜单。");
        } else {
            open(player);
        }
    }

    record Startup(File folder, MenuFiles files, MenuCandidate candidate) {}

    static Startup prepareStartup(File folder) {
        MenuFiles files = null;
        MenuCandidate candidate;
        try {
            files = MenuFiles.collect(folder, true);
            candidate = files.startupCandidate();
        } catch (RuntimeException error) {
            MenuLog.severe("启动候选校验失败，内存使用完整内置菜单，其他原文件保留：" + error.getMessage());
            candidate = MenuFiles.bundled();
        }
        return new Startup(folder, files, candidate);
    }

    static void applyStartup(Startup startup) {
        // Even dependency failures may leave independently broken files to recover/skip.
        // No file is touched until read-only preflight has finished.
        if (startup.files() != null) {
            startup.files().applyStartup();
            startup.files().loadStartupLanguages();
        } else {
            // Preflight could not be completed; never use an ordinary filesystem language load.
            StartupLanguages.install(Map.of());
        }
    }

    static void initialize(Startup startup) {
        File folder = startup.folder();
        MenuCandidate candidate = startup.candidate();
        directory = folder;
        repository = new MenuRepository(folder);
        templateRepository = new TemplateRepository(folder);
        install(candidate, false);
        for (String name : Kt.listOf("配置说明.md", "任务菜单说明.md", "examples/items.yml")) {
            File file = new File(folder, name);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                Kt.writeText(file, MenuRepository.resource(name));
            }
        }
        MenuLog.loaded("已加载外置菜单：" + candidate.count() + "。");
    }

    private static void install(MenuCandidate next, boolean refresh) {
        fullscreenSettings = next.fullscreen();
        if (next.catalog() != null) {
            CatalogRepository store = new CatalogRepository(directory);
            store.install(next.catalog());
            catalogRepository = store;
        } else {
            repository.install(next.definition());
            templateRepository.install(next.templates());
            catalogRepository = null;
        }
        translations = next.translations();
        ItemBridgeSources.reset();
        MenuImages.reset();
        MenuResources.install(
                next.catalog() != null ? next.catalog().resourcePack() : MenuResourcePack.legacy());
        if (refresh) {
            if (DialogMenu.fullscreen() != null) DialogMenu.fullscreen().reloaded();
            MenuDialog.reloaded();
            TemplateDialog.reloaded();
        }
    }

    public static void reload(CommandSender sender, boolean checkOnly) {
        if (!sender.hasPermission("playersettings.admin")) {
            MenuLog.reply(sender, "你没有权限使用此命令：playersettings.admin");
            return;
        }
        Kt.requireNotNull(repository, () -> "菜单尚未初始化");
        try (ReloadTransaction tx = ReloadTransaction.begin()) {
            MenuFiles files = MenuFiles.collect(directory, false);
            Lang.load();
            MenuCandidate candidate = null;
            if (tx.valid()) {
                try {
                    candidate = MenuCandidate.parse(files);
                } catch (RuntimeException error) {
                    tx.problem(files.problem(error));
                }
            }
            if (checkOnly) {
                if (tx.valid()) {
                    MenuLog.reply(sender, "DialogMenu 配置检查通过：" + candidate.count() + "，未应用修改。");
                } else {
                    for (var problem : tx.problems()) {
                        MenuLog.warning(problem.headline());
                    }
                    MenuLog.reply(sender, "DialogMenu 配置检查未通过；文件保持原样，详细原因请查看后台。");
                }
                return;
            }
            if (!tx.commit(sender)) {
                return;
            }
            files.resolveInactive();
            install(Kt.requireNotNull(candidate), true);
            for (String warning : candidate.warnings()) {
                MenuLog.reply(sender, "DialogMenu 警告：" + warning);
            }
            for (String warning : TemplateDialog.warnings(candidate.templates().values())) {
                MenuLog.reply(sender, "DialogMenu 警告：" + warning);
            }
            MenuLog.reply(sender, "DialogMenu 重载成功：" + candidate.count() + "，已刷新打开的菜单。");
        }
    }
}
