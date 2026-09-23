PlayerSettings 0.1.3 fixes the menu fragmentation and displaced controls shown in the client screenshot. The dialog's declared width previously ignored eight pixels of internal padding, causing automatic wrapping; the widget also performs a second wrap when calculating its height.

The body now measures 460 pixels, with a 452-pixel line containing the 450-pixel artwork. The existing Hallow-based skin and settings integrations are retained. A current 0.1.2 server resource pack remains compatible.

Validation: four JUnit tests, the native TabooLib build, bitmap metrics checks, the real Minecraft 26.2 Dialog text widget (old height 530 pixels, corrected height 269 pixels), and live rendering in an isolated vanilla client.

GitHub source synchronization, tag and Release remain pending due to unavailable credentials and local Git history.
