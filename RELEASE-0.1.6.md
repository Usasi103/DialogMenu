PlayerSettings 0.1.6 adds personal menu language and theme choices under Appearance (界面与语言). Players can select Simplified Chinese or English and a dark or light palette; changes redraw immediately and persist in their player data across reconnects and server restarts. New players retain the Chinese dark defaults.

Translations cover all seven pages, status values, search and footer controls. Language and theme are scoped to this player menu. Native Minecraft input and footer styling, client language and third-party plugin messages are not changed.

The light palette shares the existing panel dimensions, glyph advances and hit regions. The Chinese baseline fix and scoped outer-focus-outline suppression remain in place. The matching resource pack is required; generate and deliver it with the existing CraftEngine workflow.

Validation: native TabooLib build, nine JUnit tests, bitmap font metrics and live Minecraft 26.2 OpenGL interactions. GitHub source synchronization, tag and Release remain pending a repository checkout and usable write credentials.
