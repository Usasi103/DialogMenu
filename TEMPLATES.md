# 对话与首领演示菜单

从 0.1.16 起，两个 demo 与其他菜单统一放在 `menus`：

- `demo-dialogue.yml`：对话菜单，含 main 页。
- `demo-boss.yml`：首领菜单，含 intro、confirm 页。

使用 `/dmenu open demo-dialogue`、`/dmenu open demo-boss`。
原 `/dmenu template` 三个示例入口保留兼容。

菜单格式、坐标、动作、资源和完整中文例子见 [MENU-CONFIG.md](MENU-CONFIG.md)。
底部原生“关闭对话”已移除，仍可使用右上角 × 或 ESC。
