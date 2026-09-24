# 物品源配置（0.1.16）

支持内置 [ItemBridge 1.0.32](https://github.com/jhqwqmc/ItemBridge) 的全部 **39 种插件适配器**，以及原版物品。
这些插件均为软依赖，无需另外安装 ItemBridge；只安装实际使用的物品插件。
未来 ItemBridge 增加的适配器需要随 DialogMenu 更新库版本和清单，不会从网上自动下载未知适配器。

统一语法：`source:插件ID:物品ID`。插件 ID 不分大小写，后面的完整物品 ID 原样传给对应适配器。
例如 `source:Nexo:my_sword`、`source:MythicMobs:SkeletonKingSword`、`source:MMOItems:SWORD:my_sword`、`source:HeadDatabase:123`。
MMOItems 等来源的组合 ID 格式由该版本 ItemBridge 适配器规定；请使用对应来源的真实 ID。
CE、IA、NI、SI/SX-Item 旧写法继续可用；另有 MM=MythicMobs、MI=MMOItems、HDB=HeadDatabase、CF=CustomFishing、EI=ExecutableItems、EB=ExecutableBlocks。
原版可写 `DIAMOND`、`minecraft:diamond`、`source:VANILLA:diamond`。

| 插件 | 插件 ID |
| --- | --- |
| AdvancedItems | `advanceditems` |
| AzureFlow | `azureflow` |
| Baikiruto | `baikiruto` |
| BreweryX | `breweryx` |
| CraftEngine | `craftengine` |
| CrazyVouchers | `crazyvouchers` |
| CustomCrafting | `customcrafting` |
| CustomFishing | `customfishing` |
| DragonArmourers | `dragonarmourers` |
| EcoArmor | `ecoarmor` |
| EcoCrates | `ecocrates` |
| EcoItems | `ecoitems` |
| EcoMobs | `ecomobs` |
| EcoPets | `ecopets` |
| EcoScrolls | `ecoscrolls` |
| EmakiItem | `emakiitem` |
| ExecutableBlocks | `executableblocks` |
| ExecutableItems | `executableitems` |
| HeadDatabase | `headdatabase` |
| HMCCosmetics | `hmccosmetics` |
| ItemEdit | `itemedit` |
| ItemsAdder | `itemsadder` |
| ItemsXL | `itemsxl` |
| MagicGem | `magicgem` |
| MMOItems | `mmoitems` |
| MythicMobs | `mythicmobs` |
| NeigeItems | `neigeitems` |
| Nexo | `nexo` |
| Nova | `nova` |
| Oraxen | `oraxen` |
| PxRpg | `pxrpg` |
| Ratziel | `ratziel` |
| Reforges | `reforges` |
| Sertraline | `sertraline` |
| Slimefun | `slimefun` |
| StatTrackers | `stattrackers` |
| SX-Item | `sxitem` |
| Talismans | `talismans` |
| Zaphkiel | `zaphkiel` |

每个菜单一个文件。下面保存为 `menus/items-demo.yml` 后执行 `/dmenu check`、`/dmenu reload`，再 `/dmenu open items-demo`：

```yaml
Version: 1
Type: settings
Title: 物品演示
DefaultPage: main
MainMenu: [close]
Pages:
  main:
    Title: 我的物品
    Layout: [武器]
    Icons:
      武器:
        Display:
          Material: "source:Nexo:my_sword"
          Name: 预览武器
          Fallback: BARRIER
        Actions: [close]
```

示例 ID 需要在对应插件里实际存在；未安装 Nexo 或找不到物品时显示屏障并停用按钮。
若想加到已有菜单，复制 main 页的内容到该菜单 `Pages.物品页ID`，无需新增全局注册。
`examples/items.yml` 是用于玩家设置菜单的单页片段，应放到 `menus/settings.yml` 的 `Pages.items` 下；其 profile 跳转要求同菜单存在 profile 页。

检查/重载权限 `playersettings.admin`（默认 OP）；打开权限 `playersettings.use`，仍需已加载服务器资源包。
CE / ItemsAdder ID 要求小写命名空间；其他来源保留大小写、中文及内部冒号。Material 不展开 PAPI，不额外解析 TrMenu 的 JS、JSON、动画或头颅表达式。

`Display` 支持 `Material`、`Name`、`Lore`、`Amount`（1–99）、`Fallback`（可选原版物品）。`Name` 与外层 `Name` 二选一，`Lore` 与外层 `Description` 二选一；说明最多 6 行，均支持行内中英双语。它们是菜单文字，不改写源物品的名称、Lore、模型或其他数据。每次展示取新物品并克隆，仅修改展示副本数量，不给玩家物品。

配置 `Display.Material` 后，该页自动进入原生物品布局，也可写 `Renderer: items`。支持 `button`（默认）、`item`（仅展示）、`text`、`heading`，按 `Layout` 顺序排列。只有 `button` 接受 `Actions`、`Permission`、`RequiresPlugin`。保留已有权限检查、动作顺序和会话校验。

原生 Dialog 的物品图标本身没有点击动作；点击旁边的名称/说明或下方同名按钮操作，悬停物品查看原物品提示。原生页面保留 Minecraft 按钮样式、焦点提示及自动布局，不使用原有字体画布皮肤/主题，也不支持自由坐标或缩放。不能在同一页混用原有开关、滑条、下拉框；请通过 `page: 页面ID` 连接不同页面。既有七页画布布局保持兼容。

未安装对应插件、ID 不存在或物品暂未加载时，无 `Fallback` 的配置检查/重载失败，继续使用旧菜单；有 `Fallback` 时报告警告，展示回退物品并禁用动作。运行过程中物品插件失效或物品构建失败也停用动作，点击时再次检查。CE 重载后自动刷新打开的菜单；其他物品插件重载后可执行 /dialogmenu reload 刷新已打开页面。每次展示和点击都会重新查找物品，不缓存物品定义；构建时传入当前查看菜单的玩家。插件启用/停用后重新检测接入状态，check/reload 也会重新检测。API 不兼容会报告错误并停用对应动作。启动期间先读配置，下一 tick 和 CE 重载完成后核验物品，避免提前访问尚未加载的 CE 注册表。

物品模型仍由对应物品插件的资源包提供。本插件附带的资源包只包含 DialogMenu 界面资产，不包含整个服务器的自定义物品资源。
