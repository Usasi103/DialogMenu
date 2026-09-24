"""Generate readable operator defaults from the maintained bilingual text catalog."""
from pathlib import Path
import json

resources = Path(__file__).resolve().parents[1] / 'src/main/resources'
translations = {}
for locale in ('zh_cn', 'en_us'):
    values = {}
    for line in (resources / f'languages/{locale}.yml').read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        key, value = line.split(':', 1)
        values[key.strip('"')] = json.loads(value.strip())
    translations[locale] = values

def label(key):
    values = {locale: messages[key] for locale, messages in translations.items()}
    return values['zh_cn'] if len(set(values.values())) == 1 else values

def item(kind, key, **fields): return dict(Type=kind, Name=label(key), **fields)
def text(key): return item('text', key)
def heading(key): return item('heading', key)
def toggle(key, bind, description=None):
    result = item('toggle', key, Bind=bind)
    if description: result['Description'] = label(description)
    return result

pages = {}
def page(id, icon, keywords, icons):
    pages[id] = dict(Title=label('tab.' + id), Icon=icon, Keywords=keywords, Layout=list(icons), Icons=icons)

page('profile', 'profile-icon', ['玩家', '信息', '等级', '金币', '余额', '图鉴', 'player', 'level', 'coin'], {
    '玩家': text('profile.player'), '等级': text('profile.level'), '资产': heading('profile.assets'),
    '金币': text('profile.coins'), '余额': text('profile.balance'), '鱼币': text('profile.fishcoins'),
    '图鉴': text('profile.collection'), '世界': text('profile.world')})
page('sound', 'sound-icon', ['声音', '音效', '鸟鸣', '风声', 'sound', 'ambience', 'audio'], {
    '音效': toggle('sound.label', 'sounds', 'sound.scope'), '说明': heading('sound.about'),
    '保存': text('sound.saved'), '个人': text('sound.others'), '音量': text('sound.volume')})
page('particles', 'particles-icon', ['粒子', '萤火虫', '落叶', '密度', 'particle', 'firefly', 'leaves', 'density'], {
    '总开关': toggle('particles.show', 'particles', 'particles.scope'), '分类': heading('particles.categories'),
    '落叶': toggle('particles.leaves', 'leaves'), '萤火虫': toggle('particles.firefly', 'firefly'),
    '群系': toggle('particles.biome', 'biome'),
    '密度': item('slider', 'particles.density', Bind='particle-density', Options={v: label('off' if v == 'off' else 'density.' + v) for v in ('off', 'low', 'medium', 'high')}),
    '说明': text('particles.disabled')})
page('notices', 'notices-icon', ['拾取', '提示', '通知', 'pickup', 'notice', 'notification'], {
    '拾取提示': toggle('pickup.show', 'pickup', 'pickup.scope'), '说明': heading('pickup.about'),
    '拾取': text('pickup.disabled'), '资源包': text('pickup.requires'), '保存': text('pickup.saved')})
page('loot', 'loot-icon', ['掉落', '光柱', '掉落音效', 'loot', 'beam', 'drop'], {
    '光柱': toggle('loot.show', 'loot-beams', 'loot.scope'), '音效设置': heading('loot.sounds'),
    '音效': toggle('loot.play', 'loot-sounds'), '分别设置': text('loot.separate'), '个人设置': text('loot.personal')})
page('appearance', 'appearance-icon', ['界面', '语言', '主题', '亮色', '暗色', 'language', 'theme', 'light', 'dark'], {
    '语言': item('dropdown', 'appearance.language', Bind='language', Description=label('appearance.language_scope'),
               Options={'zh_cn': label('appearance.chinese'), 'en_us': label('appearance.english')}),
    '主题设置': heading('appearance.theme'),
    '主题': item('dropdown', 'appearance.theme', Bind='theme',
               Options={'dark': label('appearance.dark'), 'light': label('appearance.light')}),
    '保存说明': text('appearance.saved'), '个人设置': text('appearance.personal')})
page('help', 'loot-icon', ['帮助', '资源包', '缩放', 'help', 'resource', 'scale'], {
    '导航': text('help.navigation'), '高亮': text('help.selected'), '资源包': heading('help.pack'),
    '加载': text('help.requires'), '缩放': text('help.scale'), '关闭': text('help.close')})

def scalar(value):
    if isinstance(value, dict) and set(value) == {'zh_cn', 'en_us'}:
        return '{' + ', '.join(k + ': ' + scalar(v) for k, v in value.items()) + '}'
    return json.dumps(value, ensure_ascii=False)

FIELD_COMMENTS = {
    'Version': '配置格式版本，保持当前值',
    'DefaultPage': '打开菜单时首先显示的页面 ID，必须存在于 Pages 中',
    'HideFocusOutline': 'true 隐藏该菜单的焦点白框；需要配套资源包着色器，false 保留',
    'Bold': 'true 加粗；false 常规字体',
    'Position': '[X 像素, Y 行号]，从 0 开始；Y 每行 9 像素',
    'Actions': '点击后按顺序执行的动作列表；命令不带 /',
    'Permission': '点击所需权限节点；省略表示不额外限制',
    'Language': '新玩家默认语言：zh_cn / en_us；不覆盖玩家已保存的选择',
    'Theme': '新玩家默认主题：dark 暗色 / light 亮色；不覆盖已保存的选择',
    'ShowFooter': 'true 显示底部“返回游戏”；false 隐藏，仍可按 Esc 关闭',
    'MainMenu': '左侧“返回主菜单”的动作；此处关闭后以玩家身份执行 /menu',
    'Navigation': '左侧分类的排版样式',
    'Step': '相邻分类的起始行间隔；至少 2 行，每行 9 像素',
    'TitleStyle': '右侧页面标题样式；省略字段沿用默认值',
    'Icon': '左侧分类图标的内置贴图 ID',
    'Keywords': '搜索此分类时匹配的关键词列表',
    'Layout': '显示顺序；名称必须与下方 Icons 的项目名称一致',
    'Icons': '本页的文字、按钮和选项定义；每个项目名称需唯一',
    'FontSize': '字号默认 8；文字/标题 6–24，固定高度控件 6–12',
    'Width': '文字最大宽度（像素），超出截断；仅用于 text / heading，不改变背景大小',
    'Color': "文字颜色：text / muted / heading 或 '#RRGGBB'；不支持 &a / §a",
    'LabelPosition': '左侧 Name 的独立位置：[X 像素, Y 行号]；仅开关、滑条、下拉框',
    'Style': 'toggle 的外观：switch 为 On/Off 小开关，button 为宽按钮',
    'Name': '显示文字，可用单个字符串或 zh_cn / en_us 双语配置',
    'Bind': '内置偏好绑定 ID，自动读取与保存；不要改为显示文字',
    'Description': '控件下方的说明文字，可用字符串、双语文本或最多 3 项的列表',
    'Options': '选项值: 显示文字；按顺序排列，2–8 项；"off" 必须加引号',
    'Renderer': 'items 使用原生物品布局，展示真实物品模型与悬浮提示',
    'Display': '物品展示定义；本页将采用原生物品布局',
    'Material': '原版材质或 source:物品源:物品ID；命名空间需完整',
    'Fallback': '物品源不可用时的原版替代材质，同时停用关联按钮',
    'Lore': '物品旁的说明列表，不覆盖物品本身的 Lore',
    'Amount': '展示数量；只影响显示，不发放或扣除物品',
    'RequiresPlugin': '所需插件名，缺少时该控件不可用',
    'State': '无 Bind 时读取的完整 %PAPI变量%；需自行配置相应动作',
    'Title': '菜单或分类显示名称，可用中文或 zh_cn / en_us 双语文本',
    'Type': '控件类型：text / heading / button / toggle / slider / dropdown',
    'Pages': '旧版格式的页面 ID 列表，按此顺序显示左侧分类',
}

LAYOUT_NOTES = """# 排版字段说明（可省略；省略时使用默认样式和自动排版）：
# Position: [X, Y]：相对菜单画布的位置；X 是像素，Y 是从 0 开始的行号，每行 9 像素。
# 例如 [123, 3] 表示向右 123 像素、从第 3 行开始；不是屏幕坐标，也不是 Y=3 像素。
# text / heading 移动文字；button / toggle / slider / dropdown 移动整个控件。
# LabelPosition: [X, Y]：仅用于 toggle / slider / dropdown，单独定位左侧 Name；省略时跟随控件。
# FontSize: 8：文字字号，默认 8；text / heading 为 6–24，固定高度控件为 6–12 的整数。
# Bold: false：true 加粗，false 常规；加粗和放大都会增加文字占用宽度。
# Width: 316：text / heading 的文字最大横向宽度，单位像素；超出截断，不自动换行。
# Width 不改变字号，不拉伸贴图，也不修改按钮尺寸；其他控件不要填写 Width。
# Color: muted：text / heading 的文字颜色；支持 text（正文）、muted（次要文字）、heading（标题）。
# 这三个颜色名称随 Theme 切换；也可写固定六位十六进制颜色，如 Color: '#55FF55'。
# Color 不接受 &a、§a、&f 或 &l；加粗请用 Bold，颜色不会改变控件背景。
# TitleStyle：右侧页面标题样式，可填 Position / FontSize / Bold / Width / Color，规则同 heading。
# Navigation：左侧分类样式，可填 Position / Step / FontSize / Bold；Step 是行距，至少 2 行。
# Style: switch：toggle 显示 On/Off 小开关；button 显示宽按钮，省略 Style 默认 button。
# Layout：按名称引用 Icons，顺序决定自动排版；未列入 Layout 的项目不显示。
# Name / Description 可直接写中文，也可写 {zh_cn: "中文", en_us: "English"}；语言键不能改名。
# Bind：绑定内置偏好并自动保存；不要同时填写 State / Actions，选项值须与绑定支持的值一致。
# Options：slider 的横向档位或 dropdown 的下拉选项，按书写顺序排列，支持 2–8 项。
# 下拉框要为展开后的列表留出空间；Position / FontSize 修改后运行 /dmenu check 检查边界。
# Renderer: items 或 Display.Material 会使用原生物品页；该页不支持上述画布坐标与字体样式。
# 原版 16 色对照（Color 填右侧的 '#RRGGBB'，不能直接填 &a 或 §a）：
# &0 黑 '#000000'；&1 深蓝 '#0000AA'；&2 深绿 '#00AA00'；&3 湖蓝 '#00AAAA'。
# &4 深红 '#AA0000'；&5 紫 '#AA00AA'；&6 金 '#FFAA00'；&7 灰 '#AAAAAA'。
# &8 深灰 '#555555'；&9 蓝 '#5555FF'；&a 绿 '#55FF55'；&b 青 '#55FFFF'。
# &c 红 '#FF5555'；&d 粉紫 '#FF55FF'；&e 黄 '#FFFF55'；&f 白 '#FFFFFF'。
# YAML 中 # 会开始注释，因此十六进制颜色必须加引号，例如 Color: '#55FF55'。
"""


def dump(value, indent=0):
    lines = []
    for key, item_value in value.items():
        key_text = json.dumps(key, ensure_ascii=False) if key.lower() in {'on', 'off', 'true', 'false', 'yes', 'no', 'null'} else key
        prefix = ' ' * indent + key_text + ':'
        note = ' # ' + FIELD_COMMENTS[key] if key in FIELD_COMMENTS else ''
        if isinstance(item_value, dict) and set(item_value) != {'zh_cn', 'en_us'}:
            lines.extend([prefix + note, dump(item_value, indent + 2)])
        else:
            lines.append(prefix + ' ' + scalar(item_value) + note)
    return '\n'.join(lines)

folder = resources / 'simple'
(folder / 'menus').mkdir(parents=True, exist_ok=True)
config = dict(Version=2, Title=label('menu.title'), DefaultPage='profile', Language='zh_cn', Theme='dark',
              HideFocusOutline=True, Pages=list(pages), MainMenu=['close', 'command: menu'])
(folder / 'config.yml').write_text(
    '# DialogMenu 简化配置：日常修改各页请打开 menus/ 对应文件。\n'
    '# Pages 是左侧页面顺序；新增页面后在此加入它的文件名（不含 .yml）。\n'
    '# Language / Theme 只设置新玩家默认偏好，不覆盖已保存的选择。\n'
    '# MainMenu 是左侧“返回主菜单”的动作；command 以玩家身份执行，不带 /。\n'
    '# 修改后 /dialogmenu check 检查，/dialogmenu reload 应用（OP 或 playersettings.admin）。\n'
    + dump(config) + '\n', encoding='utf-8', newline='\n')
for id, value in pages.items():
    header = LAYOUT_NOTES + '\n# ' + translations['zh_cn']['tab.' + id] + '：Layout 决定显示顺序，Icons 定义每一项。\n'
    header += '# Name / Description 可直接写中文；{zh_cn: 中文, en_us: English} 用于双语。\n'
    header += '# 默认位置自动计算，可用 Position: [X像素, Y行号] 覆盖；每行9像素；FontSize / Bold 控制文字。\n'
    header += '# Bind 自动读取和保存设置；修改后 /dialogmenu reload。\n'
    (folder / f'menus/{id}.yml').write_text(header + dump(value) + '\n', encoding='utf-8', newline='\n')
print('Generated config.yml and seven simple menu files.')
