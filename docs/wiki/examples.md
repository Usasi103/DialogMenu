# 完整示例库

本章每个代码块都是完整菜单，可保存到对应文件。无需在全局配置中注册，随后依次执行 `/dmenu check`、`/dmenu reload`，再用给出的 open 命令打开。

离线网页的代码块支持复制；[示例目录说明](examples/README.md) 列出了随 Wiki 附带的 YAML 文件。只复制你要使用的菜单，不必覆盖现有文件。

## 偏好设置：preferences.yml

保存到 `menus/preferences.yml`，打开 `/dmenu open preferences`。只使用内置语言 / 主题偏好，不依赖其他业务插件。

<!-- example: preferences.yml -->
```yaml
Version: 1
Type: settings
Title: 我的偏好
DefaultPage: appearance
Language: zh_cn
Theme: dark
MainMenu: [close]
Pages:
  appearance:
    Title: 界面与语言
    Icon: appearance-icon
    Keywords: [语言, 主题, appearance]
    Layout: [语言, 主题, 关闭]
    Icons:
      语言:
        Type: dropdown
        Name: {zh_cn: 菜单语言, en_us: Menu language}
        Bind: language
        Options:
          zh_cn: 简体中文
          en_us: English
      主题:
        Type: dropdown
        Name: {zh_cn: 界面主题, en_us: Menu theme}
        Bind: theme
        Options:
          dark: {zh_cn: 暗色, en_us: Dark}
          light: {zh_cn: 亮色, en_us: Light}
      关闭:
        Name: {zh_cn: 关闭, en_us: Close}
        Actions: [close]
```

## 两页 NPC 对话：guide.yml

保存到 `menus/guide.yml`，打开 `/dmenu open guide`。message 只发送演示文本，不会创建任务。

<!-- example: guide.yml -->
```yaml
Version: 1
Type: canvas
DefaultPage: main
Skin: parchment
Canvas:
  Width: 552
  Rows: 20
  Background: panel
  HideFocusOutline: true
Pages:
  main:
    Title: 守门人的委托
    Elements:
      portrait:
        Type: sprite
        Position: [24, 3]
        Sprite: emblem
      name:
        Position: [156, 3]
        Width: 330
        Text: 守门人 · 艾琳
        Color: "#f0d8b9"
      dialogue:
        Position: [156, 7]
        Width: 348
        Rows: 5
        Text:
          - "你好，{player}。"
          - 古堡里的灯又亮了。你愿意听听那里的故事吗？
      continue:
        Type: button
        Position: [156, 16]
        Sprite: wide-button
        Text: 继续
        Actions: ["page: story"]
      leave:
        Type: button
        Position: [312, 16]
        Sprite: wide-button
        Text: 稍后再来
        Actions: [close]
      close:
        Type: button
        Position: [516, 2]
        Sprite: close
        Text: "×"
        Actions: [close]
  story:
    Title: 古堡的故事
    Elements:
      title:
        Position: [30, 3]
        Width: 480
        Text: 消失的巡夜人
        Color: "#f0d8b9"
      story:
        Position: [30, 6]
        Width: 480
        Rows: 7
        Text:
          - 很久以前，巡夜人每晚都会点亮高塔。
          - 如果你在古堡遇到他，请替我问一句好。
      accept:
        Type: button
        Position: [156, 16]
        Sprite: wide-button
        Text: 我记住了
        Actions: ["message: 艾琳：祝你旅途平安。", close]
      back:
        Type: button
        Position: [312, 16]
        Sprite: wide-button
        Text: 返回
        Actions: ["page: main"]
```

## 难度选择与确认：trial.yml

保存到 `menus/trial.yml`，打开 `/dmenu open trial`。变量放在根部，两个页面共同使用；确认只发消息。

<!-- example: trial.yml -->
```yaml
Version: 1
Type: canvas
DefaultPage: choose
Skin: amethyst
Variables:
  difficulty: [normal, hard]
Pages:
  choose:
    Title: 选择试炼
    Elements:
      title:
        Position: [30, 3]
        Width: 480
        Text: 请选择难度
      normal:
        Type: button
        Position: [30, 7]
        Text: 普通
        SelectedWhen: difficulty=normal
        SelectedSprite: selected
        Actions: ["set: difficulty=normal", refresh]
      hard:
        Type: button
        Position: [150, 7]
        Text: 困难
        SelectedWhen: difficulty=hard
        SelectedSprite: selected
        Actions: ["set: difficulty=hard", refresh]
      normal-info:
        Position: [30, 11]
        Width: 480
        Rows: 3
        VisibleWhen: difficulty=normal
        Text: 普通模式，适合初次挑战。
      hard-info:
        Position: [30, 11]
        Width: 480
        Rows: 3
        VisibleWhen: difficulty=hard
        Text: 困难模式，需要熟悉机制。
      next:
        Type: button
        Position: [210, 16]
        Sprite: wide-button
        Text: 下一步
        Actions: ["page: confirm"]
  confirm:
    Title: 确认试炼
    Elements:
      summary:
        Position: [30, 4]
        Width: 480
        Rows: 4
        Text:
          - "玩家：{player}"
          - "选择的难度：{difficulty}"
          - 这是演示，不消耗物品，也不召唤首领。
      confirm:
        Type: button
        Position: [156, 16]
        Text: 确认
        Actions: ["message: 你选择了 {difficulty} 难度。", close]
      back:
        Type: button
        Position: [282, 16]
        Text: 返回选择
        Actions: ["page: choose"]
```

按钮中间的临时变量不会成为永久存档。接入真实副本时，把确认动作改为业务插件实际命令，并让它检查准入和消耗。

## 原生物品展示：item-preview.yml

保存到 `menus/item-preview.yml`，打开 `/dmenu open item-preview`。不依赖自定义物品插件，也不发放物品。

<!-- example: item-preview.yml -->
```yaml
Version: 1
Type: settings
Title: 物品预览
DefaultPage: main
MainMenu: [close]
Pages:
  main:
    Title: 展示柜
    Renderer: items
    Layout: [说明, 钻石, 返回]
    Icons:
      说明:
        Type: text
        Name: 将鼠标移到物品上查看原版提示。
      钻石:
        Type: item
        Display:
          Material: minecraft:diamond
          Amount: 3
          Name: 钻石展示
          Lore:
            - 这里只展示物品副本。
            - 不会把钻石放入背包。
      返回:
        Name: 关闭预览
        Actions: [close]
```

要显示 CE 物品，把 Material 改为真实的 `source:CE:namespace:id`，并视需要加 `Fallback: BARRIER`。完整规则见 [物品页面](items.md)。

## 现有三个菜单如何修改

| 文件 | 常见改动位置 |
| --- | --- |
| settings.yml | `Pages.<页>.Layout` 改排列；`Icons.<控件>.Name / Description` 改文案；Bind 连接内置设置 |
| demo-dialogue.yml | `Pages.main.Elements.dialogue.Text` 改正文；portrait 改立绘；continue 改后续跳转 |
| demo-boss.yml | `Pages.intro` 改背景与建议；`Pages.confirm` 改难度说明与确认动作 |

demo-boss 现有 Variables 在各页中分别声明；要扩展难度时保证各页变量允许值一致，或移到根部统一继承。不同条件说明的 VisibleWhen 与按钮 set 值也要同步。
