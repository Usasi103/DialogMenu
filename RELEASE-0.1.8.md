# PlayerSettings 0.1.8

The player menu can now be maintained through external YAML. Startup exports `menu.yml`, `languages/zh_cn.yml`, `languages/en_us.yml` and a Chinese `配置说明.md` guide without replacing existing files.

Both **Menu language** and **Menu theme / Dark / Light** use dropdowns like the supplied Chat Flag example. Click to expand, see the current choice highlighted in green, then select to apply and collapse. Saved preferences remain compatible with earlier versions.

Use `/playersettings check` before `/playersettings reload`; both require `playersettings.admin` (OP by default). Invalid configuration preserves the previous valid menu. Pages, labels, coordinates, actions, toggles, dropdowns and 2–8-step sliders are configurable. Slider interactions remain click/arrow steps, without continuous dragging.

Deploy the JAR with the matching resource pack, run the CraftEngine workflow and load the offered pack. The menu retains its Chinese font alignment and 29-line geometry. Focus-outline suppression retains the existing geometry-based OpenGL scope. See `VALIDATION.md` for checks and `src/main/resources/配置说明.md` for examples.
