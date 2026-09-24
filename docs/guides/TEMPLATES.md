# 对话与首领演示菜单

从 0.1.16 起，两个 demo 与其他菜单统一放在 `menus`：

- `demo-dialogue.yml`：对话菜单，含 main 页。
- `demo-boss.yml`：首领菜单，含 intro、confirm 页。

使用 `/dmenu open demo-dialogue`、`/dmenu open demo-boss`。
原 `/dmenu template` 三个示例入口保留兼容。

菜单格式、坐标、动作、资源和完整中文例子见 [MENU-CONFIG.md](MENU-CONFIG.md)。
底部原生“关闭对话”已移除，仍可使用右上角 × 或 ESC。

## 字号与加粗

画布文字元素支持 `FontSize: 6` 到 `FontSize: 24` 的整数字号（默认 8），`Bold: true` 加粗（默认 false）。例如：

```yaml
title:
  Position: [156, 1]
  Width: 348
  Text: "夜巡者"
  FontSize: 16
  Bold: true
  Color: "#d9bfef"
```

`TextSize` 是 FontSize 的兼容别名，不必同时设置。按字体实际字宽换行和截断，加粗的额外字宽也会计算。未设置 Rows 时会自动预留一行文字所需的画布行数；每个画布行仍是 9 像素。多行文字请扩大 Rows，16 号每行需要 3 个画布行；移动字号更大的标题时注意相邻元素的位置，插件不会自动移动其他元素。

FontSize 用于文字元素；按钮文字支持 Bold，字号保持 8，点击区域尺寸由按钮贴图决定。自定义字号需要资源包的 `assets/dialogmenu_dialogue/font/text_*.json`，复用已有的字体图片。未收录的字符显示问号。8 号正文、按钮及背景保持原有字体/贴图，焦点着色器不变。
