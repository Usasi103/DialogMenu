# DialogMenu · Minecraft Dialog Menus

[Simplified Chinese](README.md) · **English**

DialogMenu lets you build menus with Minecraft's Dialog interface and YAML. Create player settings, NPC conversations, boss introductions, and confirmation screens with text, icons, and interactive controls.

Each file defines a complete menu, with related pages grouped under `Pages`. Arrange settings controls in a simple list, or use canvas coordinates to design your own layout. Bundled examples give you a starting point to copy and customize.

[Download the plugin and resource pack](https://github.com/Usasi103/DialogMenu/releases/latest) · [Configuration guide (Chinese)](docs/guides/MENU-CONFIG.md) · [Changelog (Chinese)](CHANGELOG.md)

## Screenshots

In-game screenshots with Chinese menu text. Click an image to view the original.

| NPC conversation | Boss introduction | Quest list |
| :---: | :---: | :---: |
| [![DialogMenu NPC conversation with the gatekeeper and trial dialogue choices](docs/images/npc-dialogue.jpg)](https://raw.githubusercontent.com/Usasi103/DialogMenu/main/docs/images/npc-dialogue.jpg) | [![DialogMenu boss introduction with background story, combat tips, and rewards](docs/images/boss-menu.jpg)](https://raw.githubusercontent.com/Usasi103/DialogMenu/main/docs/images/boss-menu.jpg) | [![DialogMenu quest list with category filters, quest details, progress, and rewards](docs/images/quest-menu.jpg)](https://raw.githubusercontent.com/Usasi103/DialogMenu/main/docs/images/quest-menu.jpg) |
| Character icon and dialogue choices | Background story, combat tips, and rewards | Categories, pagination, and quest progress |

## What you can build

| Use case | Features |
| --- | --- |
| Player settings | Toggles, buttons, stepped sliders, dropdowns, search, and category navigation |
| Conversations and confirmations | Page navigation, conditional content, option state, and player or console commands |
| Custom canvases | Element positions, text widths, colors, and sprites; text supports font sizes from 6 to 24 and bold styling |
| Item previews | Display vanilla or custom items with their models and hover tooltips |
| Quest list demo | Up to five entries per page, categories, details, progress, reward icons, and a completed category |

Settings menus support English and Simplified Chinese, with dark and light themes. Each player's language and theme choices are saved. Boolean options can use compact green/gray On/Off switches or full-width buttons. Stepped sliders accept clicks on the track and arrow buttons.

Menus use their configured canvas dimensions. If a menu does not fit, lower GUI Scale in Minecraft's video settings.

## Included menus

| Menu | Command | Purpose |
| --- | --- | --- |
| Settings demo | `/dmenu open demo-settings` | The player settings layout with independent toggles, sliders, and dropdowns; no gameplay plugins required |
| Conversation demo | `/dmenu open demo-dialogue` | NPC dialogue and follow-up choices |
| Boss demo | `/dmenu open demo-boss` | Boss introduction, difficulty selection, and entry confirmation |
| Quest demo | `/dmenu open demo-quests` | Categories, pagination, details, and simulated reward claims |

You can copy, rename, and edit every example. Boss confirmation does not spawn a boss automatically. The quest demo does not track live quest progress or award items or currency. Connect your own gameplay plugins through button actions.

Settings demo choices last for the current preview and reset when reopened. Balances are sample data. Existing server `settings` menus retain their real integrations, preferences, and default entry point.

## Installation

The current development build targets the **Paper 1.21.11** API and has been tested on **Paper 1.21.11 / 26.2 with Java 25**. The 1.21.11 checks cover menu protocol interactions and shader compilation; a full visual review in the game client remains outstanding. See the [compatibility test record](docs/development/PAPER-1.21.11.md). Menu skins require the matching resource pack; players can use the vanilla client.

1. Place the plugin JAR in your server's `plugins` directory. The current development build includes its resources and exports them to `plugins/DialogMenu/resourcepack/DialogMenu-resourcepack.zip` on startup.
2. Fresh installations use `ResourcePack.Provider: Auto`. DialogMenu selects an enabled provider in this order: **CraftEngine → ItemsAdder → Nexo → Oraxen**, then installs its resources into that provider's source directory. Later starts update managed files while preserving administrator edits; conflicts are reported in the console.
3. Follow the console instructions to rebuild and send the selected provider's pack. Without these plugins, enable the exported ZIP manually or configure URL / External delivery. Copying resources does not build, host, or load a pack for players. When using BetterHud, keep its original pack and merge the menu resources into it.
4. Load the pack in the client, then open the default menu with `/dmenu` or `/settings`. Fresh installations disable load-response gating by default. Existing CraftEngine / URL / External settings and their gating preference are preserved.

Release 0.1.20 and earlier still require the separate matching resource pack and a manual merge. See the [resource-pack guide](docs/wiki/resource-pack.md) for installation paths, conflict handling, and delivery configuration.

A fresh installation creates the default configuration and four example menus. Existing configurations are preserved. After editing, run `/dmenu check`, then `/dmenu reload` to apply the changes. Invalid configuration leaves the previous working menu active.

## Create a menu

Save this example as `plugins/DialogMenu/menus/my-menu.yml`. Check and reload the configuration, then run `/dmenu open my-menu`. The filename is the menu ID; no separate registration is needed.

```yaml
Version: 1
Type: settings
Title: "My Menu"
DefaultPage: appearance
Language: en_us
Theme: dark
MainMenu: [close]

Pages:
  appearance:
    Title: "Appearance"
    Layout: [theme]
    Icons:
      theme:
        Type: dropdown
        Name: "Menu theme"
        Bind: theme
        Options:
          dark: "Dark"
          light: "Light"
```

`Layout` sets the control order, and `Icons` defines content and actions. Names and descriptions accept plain text or an inline translation map keyed by `zh_cn` and `en_us`. For individual element positions and canvas skins, use `Type: canvas`; see the configuration guide for examples.

## Optional integrations

Install only the integrations your menus use.

| Integration | Purpose |
| --- | --- |
| PlaceholderAPI | Display placeholders in text or read custom toggle and option states |
| Ambience | Connect the bundled sound, particle, loot, and pickup settings; the legacy standalone LootBeam and PickupNotifier plugins remain supported |
| CraftEngine | Supply custom items and optionally build and deliver the resource pack |
| ItemBridge item sources | All 39 plugin adapters in the bundled ItemBridge version, including Oraxen, ItemsAdder, SX-Item, NeigeItems, CraftEngine, MMOItems, and Nexo; no separate ItemBridge installation is needed |

Configure item sources with `Display.Material: "source:PluginID:ItemID"`. Real item previews use native Dialog item pages, and custom models need their provider's resource pack. Icons on custom canvases use font sprites. The item-source guide explains both page types and their limits.

## Commands and permissions

| Command | Description |
| --- | --- |
| `/dmenu help` | Show help, including validation and reload commands for administrators |
| `/dmenu`, `/settings` | Open the configured default menu |
| `/dmenu open <menu> [page]` | Open a specific menu or page |
| `/dmenu check` | Validate configuration without applying it |
| `/dmenu reload` | Apply valid configuration and refresh open menus |
| `/dmenu pack` | Send the configured URL pack or identify its external sender |

The main command is `/dialogmenu`, with `/dmenu` as an alias. `playersettings.use` is granted to players by default. `playersettings.admin` controls validation and reload, and defaults to operators. Permission names are retained from earlier versions so existing assignments continue to work.

## Further reading

The [documentation index (Chinese)](docs/README.md) groups configuration guides, legacy references, and development notes.

- [Settings coordinates and typography (Chinese)](docs/guides/SETTINGS-LAYOUT.md)

The detailed configuration guides below are currently written in Chinese. This English introduction includes the installation steps, a complete starter menu, integrations, and commands.

- [Menu configuration and canvas layouts](docs/guides/MENU-CONFIG.md)
- [On/Off switch styles](docs/guides/SWITCHES.md)
- [Conversation and boss templates, font settings](docs/guides/TEMPLATES.md)
- [Quest list demo](docs/guides/QUEST-DEMO.md)
- [Item sources and item pages](docs/guides/ITEM-SOURCES.md)
- [Migration from PlayerSettings](docs/guides/MIGRATION.md)
- [Resource artwork and usage scope](docs/guides/TEMPLATE-ASSETS.md)

[Validation notes](docs/development/VALIDATION.md) and [publication notes](docs/development/PUBLICATION.md) are available in English. See the [changelog](CHANGELOG.md) for version history.
