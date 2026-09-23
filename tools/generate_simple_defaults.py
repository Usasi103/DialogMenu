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

def dump(value, indent=0):
    lines = []
    for key, item_value in value.items():
        key_text = json.dumps(key, ensure_ascii=False) if key.lower() in {'on', 'off', 'true', 'false', 'yes', 'no', 'null'} else key
        prefix = ' ' * indent + key_text + ':'
        if isinstance(item_value, dict) and set(item_value) != {'zh_cn', 'en_us'}:
            lines.extend([prefix, dump(item_value, indent + 2)])
        else:
            lines.append(prefix + ' ' + scalar(item_value))
    return '\n'.join(lines)

folder = resources / 'simple'
(folder / 'menus').mkdir(parents=True, exist_ok=True)
config = dict(Version=2, Title=label('menu.title'), DefaultPage='particles', Language='zh_cn', Theme='dark',
              HideFocusOutline=True, Pages=list(pages), MainMenu=['close', 'command: menu'])
(folder / 'config.yml').write_text(
    '# DialogMenu 简化配置：日常修改各页请打开 menus/ 对应文件。\n'
    '# Pages 是左侧页面顺序；新增页面后在此加入它的文件名（不含 .yml）。\n'
    '# Language / Theme 只设置新玩家默认偏好，不覆盖已保存的选择。\n'
    '# MainMenu 是左侧“返回主菜单”的动作；command 以玩家身份执行，不带 /。\n'
    '# 修改后 /dialogmenu check 检查，/dialogmenu reload 应用（OP 或 playersettings.admin）。\n'
    + dump(config) + '\n', encoding='utf-8', newline='\n')
for id, value in pages.items():
    header = '# ' + translations['zh_cn']['tab.' + id] + '：Layout 决定显示顺序，Icons 定义每一项。\n'
    header += '# Name / Description 可直接写中文；{zh_cn: 中文, en_us: English} 用于双语。\n'
    header += '# 位置自动计算。heading 开始下方面板；未列入 Layout 的项目不显示。\n'
    header += '# Bind 自动读取和保存设置；修改后 /dialogmenu reload。\n'
    (folder / f'menus/{id}.yml').write_text(header + dump(value) + '\n', encoding='utf-8', newline='\n')
print('Generated config.yml and seven simple menu files.')
