# 原生 Dialog 图标动画

`/dmenu open demo-animation` 打开淡入演示。可在界面里选择上一个／下一个效果、重播，或直接指定预设，例如 `/dmenu open demo-animation spin`。`main` 保留为淡入的别名。关闭按钮和 ESC 都会停止播放。

| 预设 ID | 效果 |
| --- | --- |
| `fade_in` / `fade_out` | 淡入／淡出 |
| `fly_in` / `fly_out` | 从左侧短距离进入／向右侧退出 |
| `zoom_in` / `zoom_out` | 放大进入／缩小退出 |
| `bounce_in` / `bounce_out` | 缩放回弹进入／退出 |
| `pulse` | 放大再恢复 |
| `shake` | 左右抖动后恢复 |
| `swing` | 摇摆后恢复 |
| `spin` | 顺时针旋转一周 |
| `float_in` / `float_out` | 上浮并淡入／淡出 |

默认每个图标播放 1 秒，六个图标相隔 0.1 秒启动，总长 1.5 秒（30 tick）；可修改下列配置。播放结束停止刷新，等待启动和量化进度不变时不重复发包。强调效果播放一轮；退出效果结束后图标保持隐藏，可按重播重新观看。图标名称保留。图标使用固定点击区域，完全隐藏时不接受点击；点击只显示反馈，不发放物品。切换效果和重播会更换会话令牌，旧按钮回执无效。切换菜单、外来 Dialog、打开物品界面、死亡、离线、成功重载和停用都会取消播放；失败重载保持旧会话。

## 自定义启动、结束与速率

编辑插件目录的 `animations.yml`，运行 `/dmenu check` 检查，再用 `/dmenu reload` 应用。两版配置格式相同。启动时间以打开或重播时为零点；时间单位为秒，按服务器 20 tick/秒换算。

```yaml
defaults:
  start: 0.0
  end: 1.0
  speed: 1.0
demo-stagger: 0.1
presets:
  fade_in: {start: 0.5, end: 2.5, speed: 1.0}
  spin: {start: 1.0, end: 3.0, speed: 2.0}
```

每种预设都能独立设置这三个字段；省略的字段继承 `defaults`。`start` 固定，`end` 是 1 倍速下的基准结束时间，**实际结束 = start + (end - start) / speed**。上例旋转在第 1 秒开始、第 2 秒结束；改为 0.5 倍速时在第 5 秒结束。

`demo-stagger` 只控制六个演示图标之间的启动间隔，0 为同时开始，不随速率缩放。第 n 个图标的启动和实际结束分别再加 `n × demo-stagger`（n 从 0 开始）。启动前保持首帧，播完保持末帧；强调效果只播放一轮。

`start >= 0`、`end > start`、`speed > 0`，均须为有限数值；基准结束和包含错峰间隔的实际总长不得超过 3600 秒。动画按 tick 更新，不足一 tick 的时长会在下一个 tick 完成。未知字段、未知预设、空值或非法取值会使整次重载取消，保留旧配置与播放会话；成功重载取消现有演示，新开使用新值。

## 资源与适用范围

本功能使用原生 Dialog 中的 bitmap 字形。六个演示图标只在字体 JSON 中引用客户端已有的 `minecraft:item/*.png`；不复制 PNG，不制作动画帧图。每张图标一个 36 像素显示尺寸的字体定义，14 个效果共用一组顶点／片段着色器。源贴图依然是 16×16，36 是菜单显示大小。

淡出使用真实 alpha 混合；只对原贴图的空白像素执行裁弃，再乘动画透明度，避免淡出末段突然消失。图标使用专用坐标带和颜色编码，普通 GUI 和世界文字不参加动画。CPU 裁切边界复用现有透明边界字形，不新增图片。播放按服务器 tick 更新，网络延迟及 TPS 会影响流畅度。

CE 的单张物品贴图也能引用：在同一合并资源包里提供字体 JSON，`file` 指向 CE 已有的贴图路径即可。**这是平面图标，不是 ItemStack 渲染**：完整立体模型、附魔光效、数据驱动的模型切换和物品属性不会自动变成图标动画；不改 CE 原物品。本文不包含裁剪类效果。

字体原版引用若被更高优先级资源包替换，图标也随之变化。动画需要匹配版本的字体和两个文字 shader 入口；只合并 JSON 不会得到动画。第三方核心 shader 必须由整包所有者组合，不能直接覆盖发送方的 shader。

## 在代码中复用

`AnimationPreset` 是公共预设枚举，`id()` / `parse()` 提供稳定名称；`frame(progress)` 返回与 shader 使用同一量化进度的变换。`DialogCanvas.animatedSprite(x, row, skin, preset, progress, action)` 把该预设应用到一张字形。`progress` 范围为 0–1，有限越界值会夹取，NaN／Infinity 会拒绝。

`AnimationTiming(start, end, speed)` 使用与配置相同的时间语义，`progress(elapsedSeconds)` 计算进度；`MenuRuntime.animationTiming(preset)` 返回已成功加载的该预设时间快照。调用方可以取配置，也可为单个图标独立覆盖：

```java
AnimationTiming timing = new AnimationTiming(1.0, 3.0, 2.0);
canvas.animatedSprite(x, row, skin, AnimationPreset.SPIN,
        timing, elapsedTicks / 20.0, action);
```

调用方负责调度、分组、循环和会话取消；`timing.actualEnd()` 可用于确定完成时刻。循环通过调用方重置已过时间实现。`animations.yml` 配置预设时间，但不会自动为已有 YAML 菜单添加动画；现有菜单没有通用 `Animation:` 字段。

自定义 `Skin` 必须是单个 36×36 bitmap 字形，`width=36`、`columns=1`、无切片，`advances` 使用实际字宽。它的字体需定义两个控制间距，示例只引用已经存在的图片：

```json
{"providers":[
  {"type":"space","advances":{"\ue000":-3145728,"\ue001":3145728}},
  {"type":"bitmap","file":"example:item/gem.png","height":36,"ascent":7,"chars":["\ue100"]}
]}
```

这里 `example:item/gem.png` 是示例路径，需要替换为自己的已有资源；不要把 CE 的物品 ID 直接当作贴图路径。字宽随透明边缘变化，不能统一假定为 37。

内置原版图标定义及度量可重新生成：

```powershell
py -3 -B tools/build_animation_icons.py --client-jar <匹配版本的原版客户端JAR>
```

修改后重新构建插件与资源包，再让客户端加载新包。生成器只读取 PNG 字节计算度量，不向工程导出图片。
