# 目录与菜单结构

## 三个层级

```text
plugins/DialogMenu/
├── config.yml                # 全局入口与资源包
└── menus/
    ├── demo-settings.yml     # 独立设置模板；原有 settings.yml 继续用于真实设置
    │   └── Pages             # YAML 配置段，不是磁盘目录
    │       ├── profile
    │       ├── particles
    │       └── appearance
    ├── demo-dialogue.yml     # 一个完整的 canvas 菜单
    │   └── Pages.main
    └── demo-boss.yml
        ├── Pages.intro
        └── Pages.confirm
```

只有 menus **直接下级**、扩展名为小写 `.yml` 的普通文件会被新版加载。子目录不递归加载，`.yaml` 不加载；文件名去掉 `.yml` 后就是菜单 ID。

## 全局 config.yml

| 字段 | 类型 | 默认 / 要求 | 用途 |
| --- | --- | --- | --- |
| Version | 整数 | 必填 3 | 选择一个文件一个菜单的格式 |
| DefaultMenu | 字符串 | 默认 settings | 必须是一个实际加载的菜单 ID |
| ResourcePack | 配置段 | 省略走旧版加载检查 | 指定需要成功加载的资源包，见[资源包](resource-pack.md) |

这里只允许以上三个字段。Title、Pages、Language 等属于具体菜单，不再写进全局配置。

## 每份菜单文件

两类菜单都需要：

| 字段 | 类型 | 要求 |
| --- | --- | --- |
| Version | 整数 | 必填 1 |
| Type | 字符串 | 必填 settings 或 canvas |
| DefaultPage | 字符串 | 不填取 Pages 中第一项；填写时必须存在 |
| Pages | 配置段 | 每个键是一页，值是对应页面配置 |

其余根字段按类型选择：[settings 根字段](settings.md) / [canvas 根字段](canvas.md)。

Pages 使用映射，不是页面名列表。只有旧 Version 2 的全局配置才使用 `Pages: [profile, ...]`。

## ID 与数量限制

| 对象 | 规则 |
| --- | --- |
| 菜单 ID、页面 ID | `[a-z][a-z0-9_-]{0,47}`：小写英文字母开头，总长 1–48 |
| canvas 元素 ID、变量名 | 同上；变量名不能叫 player 或 uuid |
| settings 控件名 | 可用中文；非空，不含点 `.` 或控制字符 |
| 菜单文件数量 | 1–64 |
| 单文件大小 | config.yml 和每份菜单不超过 1 MiB |
| settings 子页 | 1–8 |
| canvas 子页 | 1–64 |
| settings 每页 Layout | 1–30 个不重复控件名，仍受实际布局容量限制 |
| canvas 每页 Elements | 1–64 个元素 |

比如 boss_01.yml 有效，Boss.yml、1boss.yml、首领.yml 无效。菜单显示的中文名称写 Title。

## 加载与错误处理

check 和 reload 会读全部菜单，包括当前没打开的菜单。一个坏文件会使整次重载失败，运行中的上一份有效配置继续使用，磁盘文件不会被改回。

启动时配置无效与热重载失败不同：启动尚无旧快照可用，插件记录错误并暂用内置默认菜单。不要把“还能打开菜单”当成这次编辑已成功加载。

首次安装、没有 config.yml / menu.yml 且没有既存菜单目录时，才导出默认的三个菜单。正常重启不会覆盖已有文件；缺失引用页会报错。

## 新增、复制和移除

- 新增菜单：增加一份完整 .yml，无需注册。
- 新增页面：放进对应文件的 Pages，更新指向它的动作。settings 的声明顺序就是左侧导航顺序。
- 复制菜单：换文件名；page: 仍跳转到复制后菜单内的页面，menu: 的目标不会自动改名。
- 临时停用：将文件移到 menus 外或改为 .yml.disabled，并清理指向它的默认入口和跳转。新版没有 Enabled 字段。
- 移除菜单：检查 DefaultMenu 和其他菜单的链接；重载后已打开的被移除菜单会关闭。

备份放服务器目录之外，不要在 menus 中保留另一个 .yml 副本，否则它也是菜单。
