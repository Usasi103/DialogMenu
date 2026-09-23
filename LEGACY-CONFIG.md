# 玩家菜单手动配置

文件保存在 `plugins/DialogMenu/`：

- `menu.yml`：页面、顺序、布局、按钮、滑条、状态来源、点击动作。
- `languages/zh_cn.yml`：中文文案。
- `languages/en_us.yml`：英文文案。

文件使用 UTF-8 编码和空格缩进。只在首次缺少文件时生成默认配置；重启不会覆盖你的修改。

## 修改后生效

在游戏中执行 `/dialogmenu check` 检查文件，再执行 `/dialogmenu reload` 应用。别名 `/settings check`、`/settings reload` 同样可用。控制台输入时不用 `/`。

管理员权限是 `playersettings.admin`，默认 OP。普通玩家打开菜单只需要 `playersettings.use`，默认允许。

`check` 不改变当前菜单。`reload` 会同时校验菜单和两份语言文件，全部通过才替换；失败时会显示出错文件或字段，继续使用上一份有效配置。成功后刷新已经打开的菜单；玩家所在页面被删除时回到 `default-page`，原点击会话失效。首次启动配置错误时暂用内置默认菜单，错误文件保留供你修正。

菜单中的“刷新”按钮只刷新玩家状态，不能代替管理员重载。

## 改文字

在 `languages/zh_cn.yml` 中修改：

```yaml
menu.title: "个人设置"
particles.density: "效果强度"
```

`menu.yml` 中 `text: "$particles.density"` 会读取当前菜单语言的对应键。增加新键时，中英文文件都要添加。也可以直接写 `text: "回城"`，这种写法不随语言切换。

文字支持 `{player}`、`{uuid}`、`{ping}`、`{world}` 和 `%PlaceholderAPI变量%`。文案是单行纯文本，不使用 MiniMessage、颜色代码或换行；超出分配宽度会裁切。颜色通过控件的 `color` 设置。

## 加一个按钮

在现有 `actions:` 下增加动作，不要再写第二个 `actions:`：

```yaml
  spawn:
    type: player-command
    command: "spawn"
    close: true
```

在某个页面的 `widgets:` 列表末尾增加：

```yaml
      - type: button
        x: 330
        row: 25
        text: "回城"
        action: spawn
```

`player-command` 以点击玩家身份执行，保留原指令权限。`close: true` 会先关闭 Dialog，适用于传送或打开其他菜单；默认 false，执行后刷新当前页。

控制台动作示例：

```yaml
  reward:
    type: console-command
    command: "give {player} minecraft:stone 1"
    permission: "example.reward"
```

控制台动作具有服务器权限；用 `permission` 限定可使用的玩家。插件不会给玩家临时 OP。命令不带 `/`，仅支持 `{player}`、`{uuid}` 两种替换；不接受 PAPI 或搜索输入作为命令参数。可通过 `requires-plugin: Ambience` 限定联动插件必须已启用。

## 增加或删除页面

页面位于 `pages:`，书写顺序就是左侧导航顺序。下面增加第八页，默认导航位置仍有空间：

```yaml
  travel:
    label: "传送"
    icon: profile-icon
    keywords: ["传送", "回城", "travel"]
    widgets:
      - type: heading
        row: 1
        text: "传送服务"
      - type: button
        row: 4
        text: "回城"
        action: spawn
```

`action: spawn` 引用上一节的动作。也可以通过 `type: page`、`value: travel` 的动作跳到该页。

删除页面时同时调整指向它的 `default-page` 或 page 动作。搜索根据各页的 `keywords` 和中文、英文页名匹配，较长关键词优先。

## 布局和贴图

画布固定宽 450 像素、高 29 行。`x` 是从左起的像素坐标；`row` 是从上起的行号，每行 9 像素，从 0 开始。

| 控件 type | 用途 | 默认位置和尺寸 |
|---|---|---|
| heading | 标题 | x=122，width=316，占 1 行 |
| text | 说明文字 | x=123，width=316，占 1 行 |
| button | 按钮 | x=330，control 贴图为 114×18 |
| toggle | 显示状态的开关 | x=330，114×18 |
| slider | 点击档位滑条 | x=280，164×18；左侧另留 60 像素显示值 |
| dropdown | 下拉选择框 | x=330，114×18；展开后每项再占 2 行 |
| sprite | 背景或图标 | x=114，尺寸由贴图决定 |

`row` 必填。button / toggle / slider / dropdown 可用 `label` 显示左侧说明，`label-x` 默认 123。button 的 `text` 是按钮内文字。

`text` / `heading` 可以设置 `width` 与 `color: text`、`muted`、`heading` 或 `"#RRGGBB"`。其他控件尺寸固定；修改其 width 会提示错误，避免误以为贴图可以直接缩放。

按钮 `skin` 支持 `control`（114×18）、`nav` 或 `search`（102×18）。用 `state` 加 `selected: "值"` 可以按状态高亮。

sprite 支持 `panel-top`（336×81）、`panel-bottom`（336×126）、`search-icon`、`refresh-icon`、`profile-icon`、`sound-icon`、`particles-icon`、`notices-icon`、`loot-icon`、`appearance-icon`，也可显示按钮底图。页面 `icon` 使用宽 9 像素的图标名称。

控件不能超出画布；两个独立点击区域不能重叠。增加更多导航页时，检查 `navigation.row` / `navigation.step` 与 `common` 中返回、刷新按钮是否冲突。

`hide-focus-outline: true` 沿用此菜单的白框隐藏；false 显示普通焦点框。整体画布尺寸与资源包着色器配套，不能通过 YAML 扩大。新增 PNG、改变控件尺寸、修改字体或着色器仍需要开发和更新资源包；已有控件的文案、位置和动作只需重载。

## 状态、开关和滑条

`states:` 定义数据来源。右侧可以是完整的 `%PAPI变量%`，或内置的 `pickup`、`loot-beams`、`loot-sounds`、`language`、`theme`。

toggle 将 `true/on/enabled/1/开/开启` 视为开启，将 `false/off/disabled/0/关/关闭` 视为关闭，未知值显示“未接入”。`toggle-command` 动作根据开启状态执行 `when-true`，关闭状态执行 `when-false`；未知状态不执行。例如默认拾取提示和掉落光柱。

滑条支持 2–8 个有序档位，位置由列表顺序决定。每档单独配置 `value`、`label` 和 `action`。箭头切换相邻档，边界禁用；点击轨道直接选择。此文字画布不支持连续拖拽。

将密度改成三档的示例（保留现有 density 状态和三个动作）：

```yaml
      - type: slider
        x: 280
        row: 19
        label: "$particles.density"
        state: density
        options:
          - {value: "low", label: "$density.low", action: density_low}
          - {value: "medium", label: "$density.medium", action: density_medium}
          - {value: "high", label: "$density.high", action: density_high}
```

若玩家保存的值不在列表中，会显示“未接入”且不绘制选中滑块，仍可点击轨道选择有效档。YAML 中 off/on/true/false 等用作文字或 ID 时必须加引号。

## 默认偏好与已有玩家

菜单语言使用类似例图 Chat Flag 的下拉框，配置示例：

```yaml
      - type: dropdown
        x: 330
        row: 3
        label: "$appearance.language"
        state: language
        options:
          - {value: "zh_cn", label: "$appearance.chinese", action: language_zh_cn}
          - {value: "en_us", label: "$appearance.english", action: language_en_us}
```

点击当前值或箭头展开，再次点击收起。列表中当前项绿色高亮；选中语言后立即切换并收起。切页、重载也会收起。每次只展开一个下拉框。支持 2–8 项，展开区域必须在画布内；文字会为箭头预留空间。列表会覆盖其下方的文字，建议为它预留空白区域。下拉控件也能绑定其他状态和动作；语言本身目前支持简体中文和 English 两种。

主题也使用下拉框，默认放在 `row: 13`：

```yaml
      - type: dropdown
        row: 13
        label: "$appearance.theme"
        state: theme
        options:
          - {value: "dark", label: "$appearance.dark", action: theme_dark}
          - {value: "light", label: "$appearance.light", action: theme_light}
```

`defaults.language` 支持 `zh_cn` / `en_us`；`defaults.theme` 支持 `dark` / `light`。它们只用于没有保存有效偏好的玩家，不覆盖已有玩家选择。原有 PDC 键和其他插件的玩家数据保持兼容。

动作 `builtin` 支持：`close`、`refresh`、`search`、`language:zh_cn`、`language:en_us`、`theme:dark`、`theme:light`。
