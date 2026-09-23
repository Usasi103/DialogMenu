PlayerSettings 0.1.5 provides a Simplified Chinese settings interface, including navigation, page descriptions, status labels, search and footer controls. Chinese keywords work alongside the existing English search aliases.

The settings canvas uses a reserved layout that opts into focus-outline suppression. The resource-pack GUI shader filters matching white edge pixels, while ordinary Dialog bodies and native input/button focus indicators remain visible. The selector is based on geometry and position, not the command name; other matching line geometry can also be affected. Narrower-than-layout GUIs retain vanilla rendering. Validation targets Minecraft 26.2 OpenGL.

The search dialog now explicitly disables pause when using `after_action: none`, fixing its previous opening error. Validation includes six JUnit tests, the real client text widget, bitmap metrics, and live settings/ordinary-dialog/input-focus comparisons with Chinese search submission.

Deploy the matching JAR and resource pack together and deliver the regenerated pack. The GUI shaders require a compatibility merge if another pack replaces the same files.

GitHub source synchronization, tag and Release remain pending usable GitHub credentials.
