PlayerSettings 0.1.7 replaces the particle density dropdown with the HallowPrison Background Opacity reference style: blue arrow buttons, a dark ticked rail and a light rectangular thumb. The localized current value appears beside the control.

Click a rail position for off / low / medium / high, or use the arrows to move one step. End arrows disable. This uses the existing Ambience commands and saved player settings, with both menu languages and themes. Continuous dragging is not supported by the custom Dialog text canvas.

Deploy the JAR and matching resource pack together. Quote the Ambience YAML key `'off'` under `particles.density`, because unquoted `off` may be parsed as boolean `false` and reject the off command. The test_server deployment includes this configuration correction, with an external backup.

Validation: native TabooLib build, nine JUnit tests, actual Minecraft 26.2 text-widget geometry and OpenGL client interaction checks. See VALIDATION.md for the completed verification record.
