# 常见问题

排查时先执行 `/dmenu check`，读报错中的文件 / 页面 / 字段路径。check 通过后才执行 reload。新菜单文件即使尚未打开，也会参与整体验证。

## 配置报错

| 报错 / 现象 | 原因与处理 |
| --- | --- |
| Version 必须为 3 / 1 | 全局 config.yml 使用 3，单个 menus 文件使用 1 |
| 缺少 Pages | 把旧版单页文件放到了新版 menus；给它补完整菜单外层 |
| 未知字段 | 拼写、大小写或层级不对；或混用了两类菜单 / TrMenu 语法 |
| 无效菜单文件名 / 页面 ID | 使用小写英文开头、数字、下划线或短横线，总长不超过 48 |
| 页面不存在 | DefaultPage / page 指向不存在的页；页名只在当前菜单内查找 |
| menu 目标需要为 canvas | canvas 的 menu 动作只能直达 canvas 菜单 |
| 需要字符串列表 | Actions / Layout 写成了单条字符串，或 on/off 被 YAML 解析成布尔值 |
| 需要非空单行文字 | 使用了换行、Tab、空字符串或多行块文本 |
| close 位置错误 | settings 放首项；canvas 放末项 |
| 两个面板放不下 | 减少 Description，减少控件，调整 heading 或拆页 |
| 下拉展开超出画布 | 把 dropdown 提前，减少选项；选项最多 8 个 |
| 元素超出画布 | 检查 Position 加元素尺寸；Row 不是像素 |
| 按钮点击区域重叠 | 调整位置；只有同变量不同条件的互斥按钮允许重叠 |
| 选中贴图尺寸必须相同 | 标准 button 配 selected；wide-button 不能配 selected |
| 隐藏焦点框只支持默认尺寸 | Canvas 不是 552 × 20 行时设 HideFocusOutline: false |
| 双语文字需要 zh_cn 和 en_us | 翻译映射缺语言；或改成单一纯文本 |
| Bind 插件依赖不能替换 | 保留绑定原依赖；完全自定义时改 State + Actions |

新版 settings 会先编译成内部格式；个别报错路径仍可能出现旧的 menus/页ID.yml 或 config.yml 前缀。以外层完整菜单路径和具体字段名定位。

## 修改没生效

1. 确认编辑的是部署目标的 `plugins/DialogMenu/menus`，不是源码默认资源或归档服务器。
2. 确认文件扩展名是小写 .yml，不是 .yaml / .txt，也不在子目录里。
3. check 只检查，不应用；执行 reload 并确认成功消息。
4. 整体校验失败时上一份配置继续使用，先修复报错。
5. 改 Language / Theme 不会重置已有玩家偏好，在界面中重新选择。
6. 新版不会同时读取旧的 templates / languages 来覆盖新菜单，检查实际配置模式。

## 无法打开

| 提示 / 现象 | 检查 |
| --- | --- |
| 要求加载资源包 | ResourcePack 指定来源是否正确，客户端是否成功加载指定 UUID |
| pack 命令没有发包 | 只有 Provider: URL 由 DialogMenu 发包，其他模式由外部来源发送 |
| 控制台 open 无效 | open 是玩家命令，没有目标玩家参数 |
| 旧 template 名打不开 | demo 菜单被改名 / 删除，使用实际菜单和页面 ID |
| 无权限 | playersettings.use；管理命令另需 playersettings.admin |
| 重新启动后变回默认样式 | 启动配置失败时会暂用内置默认值；查看启动错误 |

## 界面显示问题

- 方块字、图标缺失：资源包缺少相应 dialogmenu_settings / dialogmenu_dialogue 字体，或客户端仍是旧包。旧版 toraka_settings / toraka_dialogue 引用与当前开发版不能混用；更换方式见 [图标与材质](icons.md)。
- 自定义物品没有模型：物品来源插件的资源包未包含 / 未加载，不是加 Name 就能解决。
- 文字被截断：settings 缩短文字 / 分页；canvas 增加 Width / Rows，并检查边界。
- 画面被窗口裁切：降低客户端 GUI 缩放；大画布不会自动缩放适配窗口。
- 改 Width 没让按钮变宽：内置按钮尺寸取决于 Sprite；改用 wide-button。
- 物品页不跟随主题：原生 items 页面本来就使用 Minecraft 原生布局。
- Title 没出现在 canvas 面板里：Title 是外部标题，添加 text 元素显示可见标题。
- 不显示底部原生关闭按钮：画布页面已移除该按钮，使用自己的 × 或 ESC。

## 点击与状态问题

- 按钮可见但不能执行：检查 Permission、RequiresPlugin、依赖插件自身权限和物品源是否失效。
- 回退物品显示正常却不能点：Fallback 只用于显示，来源失败时动作会停用。
- 自定义开关显示未接入：确认 PlaceholderAPI 能返回正确 token，且返回值属于支持的真 / 假值。
- 选择值又变回去：外部命令未保存状态，或 State 返回值与 Options 键不一致。
- 切页后变量丢失：目标页没声明同名变量，或不接受该值；注意 Variables 整段覆盖。
- 放置很久后按钮无响应：当前会话点击凭据有效期为 10 分钟，重新打开。
- canvas 想用 command 打开其他界面却被覆盖：动作完成后会重绘；canvas 内使用 page / menu。
- 想限制每日领取：本插件没有内置领取次数 / 冷却 / 经济事务，交给业务插件校验。

## 需要保留的诊断信息

记录插件版本、命令、完整报错、相关菜单及全局 ResourcePack 段。若是显示问题，再记录客户端版本、GUI 缩放以及是否成功加载资源包。

纯菜单 YAML 修改通常只需 check / reload，不必重启服务器。本机如果另有任务需要重启，仍使用 test_server/启动.bat 并保留可见、可输入的 CMD 控制台。
