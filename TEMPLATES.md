# 对话和首领菜单模板

0.1.15 增加独立的 templates/*.yml，无需放进玩家设置的 config.yml.Pages。

| 命令 | 用途 |
| --- | --- |
| /dmenu template npc-dialogue | 木色对话：姓名、正文、继续和离开 |
| /dmenu template boss-intro | 紫色首领介绍：立绘位、故事、建议、奖励占位和开始按钮 |
| /dmenu template boss-confirm | 普通/困难/过量选择、对应说明、确认和取消 |
| /dmenu check | 同时检查设置菜单和模板，不应用 |
| /dmenu reload | 全部校验通过后一起应用，刷新已打开的菜单 |

首次升级导出三个模板；已有 templates 目录不会被重置。复制文件并改文件名可以增加模板，
例如 templates/shop-talk.yml 对应 /dmenu template shop-talk。模板之间用 template: 文件名 跳转。
文件损坏、目标不存在或坐标越界时，重载保留上一次有效的全部配置。

## 修改位置与外观

    Version: 1
    Title: "我的对话"
    Skin: amethyst # 紫色；parchment 为木色
    Canvas:
      Width: 552
      Rows: 20
      Background: panel # none 不绘制背景；也可用 sprite 元素放自己的背景
      HideFocusOutline: true
    Elements:
      text:
        Type: text
        Position: [30, 3] # X=30 像素，Y=3 行=27 像素
        Width: 480
        Rows: 4
        Text: ["你好，{player}。", "这是一段会按宽度自动折行的文字。"]
        Color: "#e7deed"
      done:
        Type: button
        Position: [210, 16]
        Sprite: wide-button
        Text: "结束对话"
        Actions: ["close"]

X 可以逐像素调整；纵向使用 9 像素文字行，原点在画布左上角。
文字超过 Rows 时截断并显示省略号。按钮标签居中，点边缘和文字均有效；
相互重叠的按钮会拒绝加载。Skin 选择整页配色。
Sprite 可选 panel、button、selected、wide-button、close、divider、emblem、reward。
button / selected 为 108×18，wide-button 为 144×18，close 为 18×18，reward 为 27×27。
贴图大小来自字体，不会因为填写 Width 自动拉伸；text 的 Width/Rows 控制排版。

默认画布 552×180，不随窗口自动铺满全屏。客户端 GUI 太窄时需调低“GUI 缩放”。
这是普通 Minecraft Dialog，保留底部原生关闭按钮及 ESC 操作。
默认焦点轮廓为 576×188；与旧 /settings 的 474×269 一起由 gui 着色器按几何尺寸筛选。
修改画布尺寸时先将 HideFocusOutline 设为 false。过滤并不识别命令，同位置同尺寸白线也可能匹配。

## 选择状态和动作

    Variables:
      difficulty: [normal, hard, overload] # 第一项为默认值
    Elements:
      hard:
        Type: button
        Position: [144, 11]
        Text: "困难"
        SelectedWhen: difficulty=hard
        SelectedSprite: selected
        Actions: ["set: difficulty=hard", "refresh"]

同一次对话的模板跳转保留合法的同名变量；重新用命令打开从默认值开始。
不同玩家互不影响，不写入玩家语言/主题 PDC。VisibleWhen 控制某元素是否显示。
变量值必须为配置声明的字母/数字/-/_枚举，不接受任意玩家输入。

动作按顺序执行：set: 键=值、message: 文字、command: 玩家指令、
console: 控制台指令、template: 模板、refresh、close。
跳转、刷新或关闭放在最后；指令失败停止后续动作，已执行动作不回滚。
按钮可以加 Permission: "your.permission"，点击时再次检查。
指令支持 {player}、{uuid} 和声明的 {difficulty} 等变量，不支持 PAPI 替换。

附带确认按钮只提示当前选择，不扣钥匙、不召唤首领、不发奖励。
接入实际副本时，用对应插件的正式命令替换 Actions，由副本插件验证门票、次数和奖励。

## 替换立绘与奖励

左侧徽记和奖励方格都是占位图，并非截图中的怪物模型或真实物品。
可将立绘/物品图导入资源包字体，然后配置 sprite：

    portrait:
      Type: sprite
      Position: [21, 3]
      Font: "my_pack:portraits"
      Glyph: "\uE001"
      Width: 108
      Rows: 12
      Advance: 109

字体图片的实际显示尺寸应为 108×108，ascent=7，Advance 填实际字宽（包括末尾 1 像素）。
使用 CraftEngine images 时填它实际生成的字体 ID 和字符，避免字体冲突。
自定义字体内容仅凭服务端 YAML 无法验证，需要在客户端检查尺寸。
ItemBridge 的 source:CE/IA/NI 等获取 ItemStack，不会自动变为这个自由画布里的字体立绘；
真实物品展示仍使用已有 menus/ 原生物品页面。

## 资源放置

当前服务器用 CraftEngine 合并并发送最终资源包。复制新增 assets/toraka_dialogue，
保留 assets/toraka_settings，同时合并更新两个自有 assets/minecraft/shaders/core/gui.* 文件。
本机源目录：

    test_server/plugins/CraftEngine/resources/toranca_pack/resourcepack/

不要把 ZIP 当配置扔进 BetterHud。这些 Dialog 的打开、变量、动作和重载由 DialogMenu 处理。
若另一插件也改了 GUI shader，需要合并，不能互相覆盖。
服务器运行后执行 /ce workflow default，确认成功生成/上传并让客户端接收新包。

## 使用本地 UI_Sprite.png

JDK 25 下，在项目目录运行：

    java tools/BuildTemplateSkin.java . C:/Users/rabbi/Downloads/UI_Sprite.png

它裁切、九宫格伸展和编译已有图集，输出 toraka_dialogue 字体贴图及 template-skins.properties。
不传图片路径时生成基础几何皮肤。模板不依赖 BetterHud。素材说明见 TEMPLATE-ASSETS.md。
