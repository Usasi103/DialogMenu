# 资源包

DialogMenu 的字体画布需要配套资源包。ResourcePack 配置只决定检查 / 发送哪个包，不会自动把其他图片转换成菜单字体，也不会自动替换字体 ID。

CraftEngine 不是必需依赖。使用 IA / Nexo / Oraxen 等来源的物品，和由哪个插件发送资源包，是两项独立设置；物品源写在 Display.Material，字体图标写 Font/Glyph，发送方式写这里的 Provider。换图标及自定义 PNG 的完整步骤见 [图标与材质](icons.md)。

## 当前服务器的 CraftEngine 方式

以下是全局 config.yml 的完整结构：

```yaml
Version: 3
DefaultMenu: settings
ResourcePack:
  Name: "DialogMenu 菜单资源（CraftEngine 合并包）"
  Provider: CraftEngine
  Pack: default
  RequireLoaded: true
  URL: ""
  UUID: ""
  SHA1: ""
```

当前资源源文件在 `plugins/CraftEngine/resources/toranca_pack/resourcepack/`。Pack: default 对应 CraftEngine 配置 `resource-pack.packs` 的包 ID，不是源文件目录名。

DialogMenu 从 CraftEngine 托管信息读取实际 UUID，资源的合并与发送仍由 CraftEngine 完成。更新源资源后，按服务器现有流程执行 `/ce workflow default`，并让玩家成功加载。

## ResourcePack 全字段

| 字段 | 默认 | 规则 |
| --- | --- | --- |
| Name | DialogMenu 菜单资源 | 给玩家看的非空名称 |
| Provider | CraftEngine | CraftEngine / URL / External，大小写不敏感 |
| Pack | default | CraftEngine 包 ID；1–64 位字母、数字、短横线、下划线 |
| RequireLoaded | true | 是否需要指定资源包成功加载 |
| URL | 空字符串 | URL 模式必填 http / https ZIP 直链 |
| UUID | 空字符串 | URL 可省略；External 必填发送方的真实包 UUID |
| SHA1 | 空字符串 | 可留空，否则必须是 40 位十六进制 SHA-1 |

整段 ResourcePack 省略时，为兼容旧配置使用旧加载检查；建议显式指定来源，避免把其他包的加载成功误认为菜单包已经加载。

## URL：DialogMenu 发送直链包

替换全局配置中的 ResourcePack 段：

```yaml
ResourcePack:
  Name: 我的菜单资源
  Provider: URL
  URL: "https://example.com/dialogmenu.zip"
  UUID: ""
  SHA1: ""
  RequireLoaded: true
```

example.com 是示意地址，必须改成真实可下载的资源包 ZIP。UUID 留空时按 URL 生成，也可指定固定 UUID。

玩家执行 `/dmenu pack` 发送下载请求。采用追加方式，不移除其他包，不因为拒绝加载而踢人。

同一 URL 的文件内容变更后，更新 SHA1，再 check / reload，让玩家重新执行 pack。同 UUID 的 URL 或 SHA1 发生变更时，之前记录的加载成功状态会失效。

## External：其他插件发送

适用于已经由 ItemsAdder、Nexo、Oraxen 等插件或 server.properties 发送资源包的服务器。先将 DialogMenu 配套资源合入该发送方实际使用的资源包，保留 assets 目录结构，再填写发送时实际使用的 UUID；DialogMenu 不会自动执行资源合并。

```yaml
ResourcePack:
  Name: 外部合并资源包
  Provider: External
  UUID: "12345678-1234-1234-1234-123456789abc"
  RequireLoaded: true
```

这里 UUID 是格式示例，必须替换成其他插件或 server.properties 发送时实际使用的 UUID。不要根据 ZIP 文件名或 pack.mcmeta 的 description 猜测 UUID。

External 模式中 pack 命令只显示来源提示，由外部系统负责发送。

## 什么算加载成功

RequireLoaded: true 时，需要指定 UUID 的成功加载响应，加载其他包不算。CraftEngine 包组要求所有成员成功。

客户端本地手动启用的包不会给服务器发送对应 UUID 的加载响应；服务器也不能逐张检查玩家是否实际渲染了图片。

RequireLoaded: false 可以关闭这个门槛，但不会自动补齐缺失的字体或模型。仅在已经自行保证客户端资源到位时使用。

资源包设置发生修改会关闭已打开菜单。未变且已加载的包可再次打开；更换到未加载的包时需让玩家通过来源插件重新接收，或重进服务器。

## 资源内容

字体画布需要对应资产：

- `dialogmenu_settings`：设置界面皮肤、标签和按钮字体。
- `dialogmenu_dialogue`：对话画布、任务图标和文字字体。
- 配套 `assets/minecraft/shaders/core/gui.vsh` 与 `gui.fsh`：已有焦点 / 边框处理。

已有 GUI 着色器的整合包需要合并兼容，不能把其中一方直接覆盖。物品模型则由物品来源插件自己的资源包提供。

当前开发版 0.1.21-SNAPSHOT 已将旧 toraka_settings / toraka_dialogue 命名空间改为 dialogmenu_settings / dialogmenu_dialogue；升级时同步匹配版本的资源包，并修改自定义菜单中的旧字体引用。不要直接使用旧版资源包搭配新字体 ID。仅修改菜单文字、顺序和动作通常无需重新生成资源包；新增字体立绘或替换贴图需要更新资源。

本机 UI_Sprite 素材版与可分发基础皮肤的许可边界见源码仓库 `docs/guides/TEMPLATE-ASSETS.md`。发布资源包前沿用已有许可说明。
