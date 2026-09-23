# 物品源配置

0.1.13 通过随插件打包并隔离包名的 [ItemBridge 1.0.32](https://github.com/jhqwqmc/ItemBridge) 支持 Oraxen、ItemsAdder、SX-Item、NeigeItems、CraftEngine 五种可选物品源，同时保留原版物品。无需额外安装 ItemBridge 插件；只安装自己使用的物品插件即可。语法参考 [TrMenu 物品源](https://hhhhhy.gitbook.io/trmenu-v3/menu/icon/display/material#wu-pin-yuan)，五种来源均使用 ItemBridge 的适配器，不会自动接入库内其他物品源。不安装这些插件仍可使用原有菜单和原版物品。

```yaml
Title: 我的物品菜单
Layout: [开始战斗]
Icons:
  开始战斗:
    Display:
      Material: "source:CE:boss:night_watcher"
      Name: "开始战斗"
      Lore: ["点击名称或下方按钮进入挑战"]
      Fallback: BARRIER
    Permission: "boss.challenge"
    Actions: ["close", "command: boss challenge"]
```

保存到 `menus/battle.yml`，在 `config.yml` 的 `Pages` 中加入 `battle`（最多 8 页）。该示例需要自行创建对应 CE 物品和挑战指令。现成的彩虹鱼示例见插件导出的 `examples/items.yml`；复制到 `menus/items.yml` 并启用即可使用。

1. `/dialogmenu check` 检查所有启用页面和物品 ID。
2. `/dialogmenu reload` 应用修改并刷新打开的菜单。
3. `/dialogmenu open battle` 打开指定页面；`/settings open battle` 同样有效。

前两条需要 `playersettings.admin`（默认 OP），打开需要 `playersettings.use`（默认允许），并沿用资源包已加载检查。

| Material 写法 | 含义 |
| --- | --- |
| `source:ORAXEN:my_sword` | Oraxen |
| `source:IA:weapons:sword` / `source:ITEMSADDER:weapons:sword` | ItemsAdder |
| `source:SX-Item:我的武器` / `source:SXITEM:我的武器` / `source:SI:我的武器` | SX-Item |
| `source:NI:我的武器` / `source:NEIGEITEMS:我的武器` | NeigeItems |
| `DIAMOND` / `minecraft:diamond` | 原版物品 |
| `source:MINECRAFT:diamond` / `source:VANILLA:diamond` | 原版物品源 |
| `source:CE:customfishing:rainbow_fish` | CraftEngine 物品 |
| `source:CRAFTENGINE:customfishing:rainbow_fish` | 同上 |

物品源别名不区分大小写；CE / ItemsAdder ID 必须使用小写并包含命名空间；Oraxen / SX-Item / NeigeItems 的 ID 保留大小写和中文。`Material` 不展开 PAPI，仅支持表中的物品源，不支持其他 TrMenu 物品源、JS、JSON、动画或头颅语法。未知字段、未知源和错误 ID 会明确报错。

`Display` 支持 `Material`、`Name`、`Lore`、`Amount`（1–99）、`Fallback`（可选原版物品）。`Name` 与外层 `Name` 二选一，`Lore` 与外层 `Description` 二选一；说明最多 6 行，均支持行内中英双语。它们是菜单文字，不改写源物品的名称、Lore、模型或其他数据。每次展示取新物品并克隆，仅修改展示副本数量，不给玩家物品。

配置 `Display.Material` 后，该页自动进入原生物品布局，也可写 `Renderer: items`。支持 `button`（默认）、`item`（仅展示）、`text`、`heading`，按 `Layout` 顺序排列。只有 `button` 接受 `Actions`、`Permission`、`RequiresPlugin`。保留已有权限检查、动作顺序和会话校验。

原生 Dialog 的物品图标本身没有点击动作；点击旁边的名称/说明或下方同名按钮操作，悬停物品查看原物品提示。原生页面保留 Minecraft 按钮样式、焦点提示及自动布局，不使用原有字体画布皮肤/主题，也不支持自由坐标或缩放。不能在同一页混用原有开关、滑条、下拉框；请通过 `page: 页面ID` 连接不同页面。既有七页画布布局保持兼容。

未安装对应插件、ID 不存在或物品暂未加载时，无 `Fallback` 的配置检查/重载失败，继续使用旧菜单；有 `Fallback` 时报告警告，展示回退物品并禁用动作。运行过程中物品插件失效或物品构建失败也停用动作，点击时再次检查。CE 重载后自动刷新打开的菜单；其他物品插件重载后可执行 /dialogmenu reload 刷新已打开页面。每次展示和点击都会重新查找物品，不缓存物品定义；构建时传入当前查看菜单的玩家。插件启用/停用后重新检测接入状态，check/reload 也会重新检测。API 不兼容会报告错误并停用对应动作。启动期间先读配置，下一 tick 和 CE 重载完成后核验物品，避免提前访问尚未加载的 CE 注册表。

物品模型仍由对应物品插件的资源包提供。本插件附带的资源包只包含 DialogMenu 界面资产，不包含整个服务器的自定义物品资源。
