# 文档维护

## 交付内容

`index.html` 是可离线阅读的单文件网页。章节、搜索数据、样式与脚本都在其中；不使用 CDN，也不需要 GitBook 账户或本地 Web 服务。

同目录 Markdown 是源文件，`SUMMARY.md` 提供章节顺序，可用于后续文档站导入。`examples/` 包含从文档中的完整代码块生成的 YAML。

这是可离线阅读的 Wiki，随源码提供 Markdown 与 index.html；尚未部署为 GitBook、GitHub Pages 或独立公网文档站。

## 更新网页

需要 Python 3 和 Python-Markdown 3.8.2。依赖及缓存放在服务器目录之外，不要放进 test_server。

从 DialogMenu 源码根目录执行：

```text
python -B tools/build_wiki.py --dependency-path <安装Markdown的目录>
```

如果当前 Python 环境已经安装 Markdown，可省略 dependency-path。可用 `python -B -m pip install --target <服务器外的依赖目录> Markdown==3.8.2` 准备构建依赖；生成后的网页不再依赖 Python。

构建器会：

1. 按 SUMMARY 读取 Markdown，生成目录和全文搜索数据。
2. 把带 example 标记的完整 YAML 原样提取到 examples。
3. 检查站内文件链接，拒绝断链。
4. 把全部内容嵌入 index.html。

正文片段必须写清放置位置，只有完整菜单才能加 example 标记。

## 示例列表

| 文件 | 内容 |
| --- | --- |
| [hello.yml](examples/hello.yml) | 入门双页 settings |
| [preferences.yml](examples/preferences.yml) | 内置语言与主题 |
| [guide.yml](examples/guide.yml) | 双页 NPC 对话 |
| [trial.yml](examples/trial.yml) | 枚举难度、条件文字、确认页 |
| [item-preview.yml](examples/item-preview.yml) | 原版物品预览 |
| [migrated-help.yml](examples/migrated-help.yml) | 旧单页迁移后的完整结构 |

生成器不修改服务器配置，也不把示例自动部署进 menus。

## 核验范围

字段说明依据当前本地源码。完整 YAML 示例使用 DialogMenu 的 MenuCatalogParser 检查格式、默认页、跳转和布局约束；这不等于启动游戏验证资源包渲染，也不验证文中示意的外部业务命令。

2026-09-24 校验结果：35 段 YAML 语法通过、6 段完整菜单及对应 6 份下载文件解析通过、3 段 ResourcePack 解析通过。网页检查覆盖 17 个页面（含目录）、全文搜索、无结果提示、同章节返回、配色切换、页内导航和复制按钮；桌面与窄屏显示已检查。

2026-09-24 图标文档补充后复核：37 段 YAML、6 段完整菜单及对应 6 份下载文件、3 段 ResourcePack 通过当前插件解析器校验，1 段 bitmap 字体 JSON 语法通过。网页更新为 18 个页面（含目录），已检查新图标章节、Oraxen 全文搜索、复制控件及 500 像素窄屏；10 份默认 / 部署 YAML 修改前后解析值一致，仅增加注释。未新增或实际加载示例 PNG，字体图形与 Advance 仍需使用者按实际图片验证。

示例中出现的 ExamplePlugin、example 命令、my_pack 物品、example.com 资源地址都是明确的集成占位，需要替换。随附六份完整菜单只使用内置动作、内置偏好或原版物品。

每次插件改动后至少核对：字段白名单与默认值、动作顺序、变量生命周期、物品源清单、资源包门槛，以及文件 / 页面数量限制。
