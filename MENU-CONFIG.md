# 一个菜单一个文件（0.1.17）

`menus/settings.yml` 是完整的玩家设置菜单，个人信息、声音、粒子、拾取、掉落、语言等都在它的 `Pages` 内。
`menus/demo-dialogue.yml` 是对话演示菜单；`menus/demo-boss.yml` 是首领演示菜单，包含介绍和确认两个子页。
两个 demo 也是普通菜单，可复制、改名、改动作。确认只显示演示消息，不召唤首领、不扣物品。

`config.yml` 保存默认入口和资源包要求：

```yaml
Version: 3
DefaultMenu: settings
```

`menus` 下所有 `.yml` 自动加载，文件名就是菜单 ID，不用在另一个文件重复注册。
新增菜单复制一个完整文件；新增子页面只在该文件 `Pages` 下添加。
`settings` 类型最多 8 个子页，顺序也是左侧导航顺序；`canvas` 类型最多 64 页。
不要把旧版单页 YAML 直接放到新版 `menus` 根目录。

## 打开与重载

| 命令 | 用途 |
| --- | --- |
| `/dmenu`、`/settings` | 打开 DefaultMenu |
| `/dmenu open settings` | 玩家设置默认页 |
| `/dmenu open settings appearance` | 玩家设置的语言/主题页 |
| `/dmenu open demo-dialogue` | 对话演示 |
| `/dmenu open demo-boss` | 首领介绍 |
| `/dmenu open demo-boss confirm` | 首领确认 |
| `/dmenu check` | 检查全部菜单，不应用 |
| `/dmenu reload` | 全部通过才应用，并刷新打开的菜单 |

打开仍需 `playersettings.use` 和已成功加载资源包；检查/重载需 `playersettings.admin`（默认 OP）。
旧 `/dmenu open particles` 和 `/dmenu template npc-dialogue/boss-intro/boss-confirm` 入口保留兼容。
文件有错时保留上一份有效菜单；删除正在打开的菜单后重载会关闭该界面。
保存 UTF-8，使用空格缩进。

## 玩家设置样式：子页写在 Pages 里

```yaml
Version: 1
Type: settings
Title: 我的菜单
DefaultPage: appearance
Language: zh_cn
Theme: dark
HideFocusOutline: true
ShowFooter: false
MainMenu: [close]
Pages:
  appearance:
    Title: 界面设置
    Layout: [语言, 主题, 下一页]
    Icons:
      语言:
        Type: dropdown
        Name: 菜单语言
        Bind: language
        Options: {zh_cn: 简体中文, en_us: English}
      主题:
        Type: dropdown
        Name: 界面主题
        Bind: theme
        Options: {dark: 暗色, light: 亮色}
      下一页:
        Actions: ["page: help"]
  help:
    Title: 帮助
    Layout: [返回]
    Icons:
      返回:
        Actions: ["page: appearance"]
```

`ShowFooter` 控制 settings 底部原生“返回游戏”按钮，省略或 false 时隐藏；true 时显示。settings 的画布页和原生物品页均可用 ESC 退出，demo 的 × 保持不变。旧 v2 简化配置同名；旧 v1 `menu.yml` 使用 `footer.enabled`。

`Layout` 排列当前页的控件。`Name` 可省略，默认取控件名。
名称/说明可写纯中文或 `{zh_cn: 中文, en_us: English}`，不需要 `$文本键`。
`Type` 支持 text、heading、button、toggle、slider、dropdown；`Bind`、`Description`、`State`、`Permission` 和 `Actions` 用法延续原有简化格式。
已有位置、颜色、贴图等自由布局需求请用下方 canvas 类型。
更多控件字段见 `SIMPLE-CONFIG.md`；该文档里的旧单页内容放到当前 `Pages.<页面ID>` 下即可。

`page: 页面名` 只在当前菜单内跳转。`command: 指令` 以玩家身份执行，`console: 指令` 以控制台身份执行；不写开头的 `/`。
settings 的 `close` 放在首项，可接命令；页面跳转、search、refresh 放最后。指令支持 `{player}`、`{uuid}`。
例如跨菜单可写 `["close", "command: dmenu open demo-boss"]`。
需要实际物品时在子页内写 `Display.Material`，详见 `ITEM-SOURCES.md`。
玩家已有语言/主题偏好继续使用原 PDC；文件里的 Language/Theme 仅作默认值。

## 自由画布：对话和首领 demo

```yaml
Version: 1
Type: canvas
DefaultPage: main
Skin: parchment
Canvas: {Width: 552, Rows: 20, Background: panel, HideFocusOutline: true}
Pages:
  main:
    Title: 我的对话
    Elements:
      text:
        Type: text
        Position: [30, 3]
        Width: 480
        Rows: 4
        Text: ["你好，{player}。", "正文、按钮和位置都在这一份文件里改。"]
        Color: "#e7deed"
      done:
        Type: button
        Position: [210, 16]
        Sprite: wide-button
        Text: 结束对话
        Actions: [close]
```

Skin、Canvas、Variables 可以写在菜单根部作为公共默认值，也可在某个 Pages 子页里覆盖对应的整个配置段。
`page: confirm` 跳到本文件的 confirm 页；`menu: demo-boss` 跳到另一个 canvas 菜单的默认页。
不同菜单允许使用相同子页名，互不冲突。
同次跳转保留合法同名变量，重新用命令打开从默认值开始。
canvas 动作支持 set、message、command、console、page、menu、refresh、close；跳转/刷新/关闭放最后。
首领 demo 的难度选项使用 `Variables`、`SelectedWhen`、`VisibleWhen`，可直接照文件中的中文注释改。

画布默认 552×180：`Position: [X, Row]` 横向逐像素、纵向每行 9 像素。
text 的 Width/Rows 控制排版，超长截断；按钮大小由 Sprite 决定。
Skin 支持 amethyst、parchment；Sprite 支持 panel、button、selected、wide-button、close、divider、emblem、reward。
button/selected 为 108×18，wide-button 为 144×18，close 为 18×18，reward 为 27×27。
字体立绘使用 sprite 的 Font、Glyph、Width、Rows、Advance，示例见 demo-dialogue.yml。
占位徽记/奖励图不是真实物品，ItemBridge 物品仍放在 settings 类型的原生物品子页内。

底部原生“关闭对话”已移除，模板右上角 × 和 ESC 保留。这是插件不创建原生按钮，未新增着色器隐藏规则。
焦点框隐藏仍按 576×188 几何区域匹配；改画布尺寸时将 HideFocusOutline 设为 false。
它不识别菜单 ID，同位置同尺寸白线也可能匹配。GUI 太窄时需调低客户端 GUI 缩放。

## 资源包与升级

当前使用 CraftEngine 合并资源：`test_server/plugins/CraftEngine/resources/toranca_pack/resourcepack/`。
保留 toraka_settings、toraka_dialogue 和配套 gui 着色器；无需放到 BetterHud。
0.1.17 新增首领奖励字体，需合入新资源，执行 `/ce workflow default` 并加载新包。其余贴图和焦点着色器保持原有版本。
本机 UI_Sprite.png 皮肤与可分发基础皮肤具有相同尺寸；许可和本地编译方式见 `TEMPLATE-ASSETS.md`。

旧 `config.yml Version: 2 + menus 单页文件 + templates` 及旧 `menu.yml` 继续读取，不会自动覆盖现有文件。
当前 test_server 已按原内容合并；旧文件备份在服务器目录外。新版全新安装直接导出上述三个菜单。
不要只把旧 config.yml 的 Version 改成 3；需要同时把旧页面移入完整菜单的 Pages。

## 明确指定菜单资源包

全局 `config.yml.ResourcePack` 可修改名称、来源及具体目标：

```yaml
ResourcePack:
  Name: "DialogMenu 菜单资源（CraftEngine 合并包）"
  Provider: CraftEngine
  Pack: default
  RequireLoaded: true
  URL: ""
  UUID: ""
  SHA1: ""
```

`CraftEngine` 模式：`Pack` 对应 CraftEngine 配置中 `resource-pack.packs` 的包 ID；当前为 `default`。
DialogMenu 从 CraftEngine 当前托管信息读取实际 UUID，不用复制易变的下载地址；包仍由 CraftEngine 合并和发送。
菜单资源源文件仍放在 `plugins/CraftEngine/resources/toranca_pack/resourcepack/`，这个源文件目录由 CraftEngine 管理。

`URL` 模式：填写 ZIP 直链，可填 SHA1；UUID 留空按 URL 生成，也可指定固定 UUID。
玩家使用 `/dmenu pack` 下载这个包。插件以追加方式发送，不移除已有包，不因拒绝下载踢人。
更新同一 URL 的内容时建议更新 SHA1，然后让玩家重新 `/dmenu pack`。

`External` 模式：由 BetterHud、其他插件或 server.properties 发送时，填写发送方实际使用的 `UUID`。
DialogMenu 只核对加载状态；不能凭 ZIP 文件名或 pack.mcmeta 描述代替发送时的 UUID。
`/dmenu pack` 会提示当前包的名称和来源。只有 URL 模式会由本插件发送下载请求。

保存后 `/dmenu check`、`/dmenu reload`。配置中的 UUID、URL、SHA1 格式不正确时拒绝重载，保留旧配置。
`RequireLoaded: true` 按指定 UUID 的成功响应放行，加载其他包不算；CraftEngine 包组要求全部成员成功。
修改 ResourcePack 后会关闭已打开菜单；已加载且未变的包可直接再次打开。URL 模式改变同 UUID 的地址或 SHA1 后需重新 /dmenu pack；更换为尚未加载的 CraftEngine 包时，让玩家重进或使用其发送资源包命令。
`RequireLoaded: false` 关闭加载检查，适合自行确保本地客户端资源已准备好的情况。
本地手动启用的资源包不会向服务器报告 UUID；服务器也不能检查客户端是否真的渲染了其中每张图片。
这些设置选择要加载的资源包，不会自动转换字体 ID；替换包需包含菜单所用 toraka_settings、toraka_dialogue 字体及配套资源。

## 首领奖励贴图

`demo-boss.yml` 的 `Pages.intro.Elements.shard-icon`、`mark-icon`、`gear-icon` 分别显示碎片、印记和装备。
字体 `toraka_dialogue:rewards` 的 E000 / E001 / E002 对应原版紫水晶碎片、下界之星、钻石胸甲；只引用客户端纹理，不附带原版 PNG。
在 YAML 的 Position 修改位置，在资源包 `assets/toraka_dialogue/font/rewards.json` 修改贴图引用。更换贴图或高度时需重新测量 Advance，避免同行画布漂移。当前 16 像素高，Advance 分别为 15、16、16。
这三个是画布展示图标，不会发放物品；ItemBridge 实际物品使用 Display.Material。
