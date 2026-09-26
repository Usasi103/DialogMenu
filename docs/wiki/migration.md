# 迁移与 TrMenu 对照

## 三种格式

| 格式 | 入口 | 页面在哪里 |
| --- | --- | --- |
| 旧版 v1 | 无 config.yml，存在 menu.yml | 单一旧配置及 languages |
| 简化 v2 | config.yml 的 Version: 2 | config.yml 注册 Pages 列表；menus 一文件一页；templates 独立 |
| 当前 v3 | config.yml 的 Version: 3 | menus 一文件一菜单；每个文件自己的 Pages |

旧格式保留兼容，不会在启动时强制覆盖。首次安装才导出新版默认文件。

## 从 v2 迁移 settings

1. 在服务器目录外备份现有配置。
2. 新建一份完整 settings 菜单：Version: 1、Type: settings。
3. 将旧 config.yml 的 Title、DefaultPage、Language、Theme、HideFocusOutline、MainMenu 移到这份菜单根部。
4. 将每份旧单页文件内容放到 `Pages.<原页面ID>` 下，保留原顺序。
5. 全局 config.yml 改为 Version: 3、DefaultMenu: settings，并填写 ResourcePack。
6. 将旧单页文件移出新版 menus 根目录，否则会按“完整菜单”解析并报缺少 Pages。
7. check 全部通过后 reload，逐页检查。

旧单页示意：

```yaml
Title: 帮助
Layout: [关闭]
Icons:
  关闭:
    Actions: [close]
```

移入 Pages 后的完整菜单：

<!-- example: migrated-help.yml -->
```yaml
Version: 1
Type: settings
Title: 玩家设置
DefaultPage: help
MainMenu: [close]
Pages:
  help:
    Title: 帮助
    Layout: [关闭]
    Icons:
      关闭:
        Actions: [close]
```

只把旧 Version 改成 3 不会自动迁移内容。

## 从独立 templates 迁移

给 canvas 模板加菜单外层，把每个模板除 Version 之外的内容放进 Pages 对应页。把同菜单的 `template: boss-confirm` 改为 `page: confirm`；跨 canvas 菜单跳转可用 `menu: 菜单ID`。

新版读取 menus 文件中的画布页，不把旧 templates 自动并入。共同变量可移至根 Variables，但要理解子页是整段覆盖。

## PlayerSettings 旧插件名

DialogMenu 从 0.1.14 更名。不要同时加载旧 PlayerSettings JAR 与新 DialogMenu。新目录没有配置时，启动迁移会尝试导入旧目录并保留原文件；已有新配置优先，冲突不覆盖。

命令别名、playersettings 权限名、玩家语言与主题 PDC 键保留兼容。0.1.21 已把 toraka_settings / toraka_dialogue 字体命名空间改为 dialogmenu_settings / dialogmenu_dialogue；需要同步配套资源包并修改自定义 Font 引用，见 [资源包](resource-pack.md)。

## 对照 TrMenu 理解

本 Wiki 参考 [TrMenu 文档](https://hhhhhy.gitbook.io/trmenu-v3) 的分章节配置手册形式。下表解释 DialogMenu 的实际机制，不承诺兼容 TrMenu 配置。

| 习惯中的概念 | DialogMenu 对应方式 |
| --- | --- |
| 一个菜单配置文件 | menus/文件名.yml，每份包含 Pages |
| 菜单标题 | settings 的 Title；canvas 可见标题用 text 元素 |
| 布局 | settings 的 Layout 是控件名顺序；canvas 用像素 / 行坐标 |
| 图标 | settings 用 Icons；canvas 用 Elements；真实物品用 Display.Material |
| 点击动作 | Actions 字符串列表，动作名及顺序规则见本 Wiki |
| 条件显示 | canvas 只支持枚举变量等值条件 |
| 自定义物品 | source:插件ID:物品ID，交给内置 ItemBridge 适配器 |
| 变量 | settings 文本 / State 用 PAPI；canvas 用声明的枚举变量 |

不要把箱子槽位网格、TrMenu 的嵌套动作组、JS / Kether、动态物品表达式、自动动画或点击类型配置直接贴进 DialogMenu。当前解析器只接受文档中列出的字段。

DialogMenu 当前没有内置经济交易、物品消耗、次数记录或定时刷新字段。需要这些业务时，通过命令接入对应插件。
