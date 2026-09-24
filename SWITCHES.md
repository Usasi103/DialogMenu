# Settings 的 On/Off 小开关

从 0.1.20 开始，`Type: toggle` 可以逐项指定 `Style`：

```yaml
总开关:
  Type: toggle
  Style: switch # switch：状态文字 + 小开关；button：原有宽按钮
  Name: "显示环境粒子"
  Bind: particles
```

编辑 `plugins/DialogMenu/menus/settings.yml` 对应的 `Pages.<页面>.Icons.<控件>`，
执行 `/dmenu check`、`/dmenu reload`。省略 `Style` 默认使用 `button`，旧菜单无需迁移。
`Style` 只用于 toggle；拼错或放在其他控件上会校验失败，当前生效配置保持不变。

默认 settings 中的环境音效、环境粒子总开关、落叶、萤火虫、群系粒子、拾取提示、
掉落光柱和掉落音效共八项使用小开关。语言与主题下拉框、粒子密度滑条保持原样。

开启时左半块为绿色，关闭时右半块为灰色；左侧文字随菜单语言显示“开启／关闭”
或“On／Off”。无法读取状态时显示“不可用／Unavailable”和双横线，避免显示成关闭。
点击状态文字或开关任一侧都执行原先的动作，权限、插件依赖和偏好保存逻辑不变。
缺少联动插件时，沿用原有不可用提示。

自定义 PlaceholderAPI 状态和动作也可以使用这个样式：

```yaml
提示:
  Type: toggle
  Style: switch
  Name: "自定义提示"
  State: "%example_notice%"
  Actions: ["command: notice toggle"]
```

旧版高级 `menu.yml` 的 toggle 控件使用 `toggle-style: switch`。
其 `x`、`row` 仍表示原来 114 × 18 控件的起点；36 × 18 开关靠右显示，左边留给状态文字。
暗色、亮色各有开启、关闭、不可用三种皮肤；不需要修改焦点着色器。

插件与 `DialogMenu-resourcepack-0.1.20.zip` 一起更新。合入 CraftEngine 的新文件为：

- `assets/dialogmenu_settings/font/switches.json`
- `assets/dialogmenu_settings/textures/ui/switch_*.png`（六张）

合并到已有资源包后执行 `/ce workflow default`，等待生成、上传完成并让客户端加载新包。
仅修改 YAML 样式时只需重载 DialogMenu。图元可通过
`java tools/BuildSwitchSkin.java <DialogMenu项目路径>` 重建，不会重写旧字体或其他皮肤。
