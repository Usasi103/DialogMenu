# 快速开始

以下按已经安装 DialogMenu、客户端能够打开现有菜单的服务器说明。当前开发版以 Paper 1.21.11 API 为编译基线，使用 JDK 25 构建、输出 Java 21 字节码；配套资源包声明格式 75–88（1.21.11–26.2）。首次安装及资源包自动接入见 [资源包指南](resource-pack.md)，测试环境与限制见项目 README。

## 1. 找到菜单目录

打开 `plugins/DialogMenu/menus/`。当前全局配置应为 `config.yml` 的 `Version: 3`。旧版先看 [迁移](migration.md)，不要只改版本号。

新建 `hello.yml`，保存为 UTF-8，粘贴以下完整内容：

<!-- example: hello.yml -->
```yaml
Version: 1
Type: settings
Title: 我的第一个菜单
DefaultPage: main
MainMenu: [close]
Pages:
  main:
    Title: 欢迎
    Layout: [问候, 帮助, 关闭]
    Icons:
      问候:
        Type: text
        Name: "你好，{player}！"
        Description: "这是用 DialogMenu 制作的菜单。"
      帮助:
        Name: 查看帮助
        Actions: ["page: help"]
      关闭:
        Actions: [close]
  help:
    Title: 使用帮助
    Layout: [说明, 返回]
    Icons:
      说明:
        Type: text
        Name: 修改文字后检查并重载即可。
      返回:
        Actions: ["page: main"]
```

这里 hello 是菜单 ID，main、help 是页面 ID；问候、帮助是控件名。不同菜单可以使用相同页面 ID。

## 2. 检查、应用、打开

管理员执行：

```text
/dmenu check
/dmenu reload
/dmenu open hello
```

check 只校验。reload 才应用，且所有菜单必须一起通过。打开操作需要由玩家执行，控制台可执行 `dmenu check`、`dmenu reload`。

无需在全局文件里注册 hello。若只想添加菜单，保持原来的 DefaultMenu；若希望 `/dmenu` 默认打开它，才把 `config.yml` 的 `DefaultMenu` 改为 hello。

## 3. 试着改三处

1. 改 Name：调整显示文字。
2. 改 `Layout: [帮助, 问候, 关闭]`：调整控件顺序。
3. 改 `DefaultPage: help`：让这个菜单默认显示帮助页。

每次改完重复 check → reload。Title 是标题，文件名才是命令使用的菜单 ID。

## 4. 需要自由布局时

复制 [NPC 对话完整示例](examples.md) 中的 guide.yml，它使用 `Type: canvas`，通过 Position 调整位置。不要把 Elements 放进上面的 settings 页面。

## 避免第一次就踩坑

- 缩进用空格，不用 Tab；字段名区分大小写。
- Actions 是字符串列表，写 `Actions: [close]`，不要写成 `Actions: close`。
- `"off"`、`"on"`、`"yes"`、`"no"` 等用作字符串时加引号。
- `Color: "#e7deed"` 中的颜色必须加引号，否则 `#` 开始 YAML 注释。
- 多行文字用列表，不在单条文字中嵌入换行。
- 菜单打不开且提示资源包时，先处理 [资源包加载](resource-pack.md)。

下一步：[完整文件结构](structure.md) · [控件参考](controls.md)
