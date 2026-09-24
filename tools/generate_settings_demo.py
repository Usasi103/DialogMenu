"""Copy the maintained settings layout into a standalone, plugin-free demo."""
from pathlib import Path
import re

menus = Path(__file__).resolve().parents[1] / "src/main/resources/catalog/menus"
source = (menus / "settings.yml").read_text(encoding="utf-8")
source = source.replace("Type: settings #", "Type: settings-demo #")
source = source.replace('zh_cn: "玩家设置", en_us: "Player Settings"',
                        'zh_cn: "设置演示", en_us: "Settings Demo"')
source = re.sub(r'^MainMenu:.*$', 'MainMenu: ["close"] # 演示菜单无需其他菜单插件', source, flags=re.M)
samples = {
    "%playerlevel_level%": "24", "%excellenteconomy_balance_coin%": "1,280",
    "%excellenteconomy_balance_money%": "360", "%excellenteconomy_balance_fishcoin%": "48",
    "%handbook_total%": "12", "%handbook_max%": "80",
    "环境音效偏好会自动保存。": "可点击开关预览，本次演示内保留选择。",
    "Your ambience preference is saved.": "Try the switch; this preview keeps your choice.",
    "你的提示偏好会自动保存。": "重新打开演示后，选项恢复初始状态。",
    "Your notice preference is saved.": "Reopening the demo resets its choices.",
    "选择会自动保存，下次打开继续使用。": "语言、主题与缩放仅应用于本次演示。",
    "Your choices are saved for your next visit.": "Language, theme and scale apply to this preview.",
    "仅影响你自己的玩家菜单。": "演示选项不会改变你的真实玩家设置。",
    "Applies only to your own player menu.": "The demo leaves your real settings unchanged.",
    "所在世界：{world}": "演示数据 · 世界：{world}",
    "World: {world}": "Sample data · World: {world}",
    "仅调整你自己听到的环境音效。": "预览音效开关，不改变实际环境音效。",
    "Changes only the ambience you hear.": "Preview the switch without changing real sounds.",
    "仅影响你自己看到的环境粒子。": "预览粒子选项，不改变实际环境粒子。",
    "Changes only the particles you see.": "Preview controls without changing real particles.",
    "在屏幕上显示刚刚拾取的物品。": "预览拾取提示开关，不改变实际提示。",
    "Shows the items you just picked up.": "Preview the pickup notice switch.",
    "帮助你发现地面上的稀有掉落物。": "预览光柱开关，不改变实际掉落效果。",
    "Helps you spot rare items on the ground.": "Preview the beam switch without changing effects.",
    "需要加载服务器资源包和界面组件。": "加载菜单资源包即可体验全部演示控件。",
    "Requires the server resource pack and HUD.": "All demo controls work with the menu resource pack.",
}
for before, after in samples.items():
    source = source.replace(before, after)
source = source.replace("/dmenu open settings", "/dmenu open demo-settings")
source = source.replace("Bind 自动读取和保存设置", "Bind 在本演示内模拟状态")
source = source.replace("绑定内置偏好并自动保存", "在本演示内模拟内置偏好")
source = source.replace("内置偏好绑定 ID，自动读取与保存", "演示绑定 ID，仅改变本次演示状态")
source = source.replace("按玩家自动保存，设置、任务与对话画布共用", "仅缩放本次演示，不改变其他菜单")
source = ("# 独立设置模板；真实服务器 settings.yml 继续使用 Type: settings。\n"
          "# Type: settings-demo 让开关/滑条/下拉框独立运行，无需 Ambience 或 PAPI。\n"
          "# 示例资产为静态演示数据；重新打开会重置，不写玩家偏好、不执行业务命令。\n" + source)
(menus / "demo-settings.yml").write_text(source, encoding="utf-8", newline="\n")
