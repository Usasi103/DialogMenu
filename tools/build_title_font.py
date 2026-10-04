"""Keep small Latin atlases; reference client CJK once, with server/GPU layout metadata."""
import json
from PIL import Image
from native_cjk import ROOT, RANGES, glyphs, ranges

assets = ROOT / 'resourcepack/assets'


def write(path, value):
    text = json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n'
    if not path.exists() or path.read_text('utf-8') != text:
        path.write_text(text, encoding='utf-8', newline='\n')


providers = []
metrics = {32: (0, 0)}
for source in ['dialogmenu_dialogue/font/labels.json', 'dialogmenu_settings/font/labels.json']:
    for original in json.loads((assets / source).read_text('utf-8'))['providers']:
        if original['type'] != 'bitmap':
            continue
        providers.append(original)
        namespace, texture = original['file'].split(':')
        with Image.open(assets / namespace / 'textures' / texture) as atlas:
            alpha = atlas.convert('RGBA').getchannel('A')
            cw, ch = atlas.width // len(original['chars'][0]), atlas.height // len(original['chars'])
            for y, row in enumerate(original['chars']):
                for x, char in enumerate(row):
                    if ord(char) == 0 or ord(char) in metrics:
                        continue
                    bounds = alpha.crop((x*cw, y*ch, (x+1)*cw, (y+1)*ch)).getbbox()
                    metrics[ord(char)] = (bounds[2] if bounds else 0, ch)

native = {'type': 'reference', 'id': 'dialogmenu_settings:button_cjk'}
for size in range(6, 25):
    if size == 8:
        continue
    for button in [False, True] if size <= 12 else [False]:
        ascent = 7 - (18-size)//2 if button else min(7, size)
        scaled = [{'type': 'space', 'advances': {' ': (size+1)//2}}]
        scaled += [p | {'height': size, 'ascent': ascent} for p in providers]
        scaled.append(native)
        suffix = '_button' if button else ''
        write(assets / f'dialogmenu_dialogue/font/text_{size}{suffix}.json', {'providers': scaled})
write(assets / 'dialogmenu_dialogue/font/title.json', {'providers': [{'type': 'reference', 'id': 'dialogmenu_dialogue:text_16'}]})
write(assets / 'dialogmenu_settings/font/button_cjk.json', {'providers': [{
    'type': 'unihex', 'hex_file': 'minecraft:font/unifont.zip',
    # The 26.3 codec requires from < to. The adjacent coin is never selected by NativeMenuFont.
    'size_overrides': [{'from': chr(a), 'to': chr(max(a+1,b)), 'left': 0, 'right': 15} for a,b in RANGES]
    + [{'from': chr(a), 'to': chr(b), 'left': l, 'right': 15} for a,b,l in
       [(0x1100,0x11FF,0),(0xA960,0xA97F,0),(0xD7B0,0xD7FF,0),(0xAC00,0xD7AF,1)]]}]})
# Reserved negative-X bands, one pair per size/baseline and italic state; no glyph tables.
advances = {}
for index in range(76):
    band = (200+index)*16384
    advances[chr(0xF000+index*2)] = -band
    advances[chr(0xF001+index*2)] = band
write(assets / 'dialogmenu_settings/font/native_position.json', {'providers': [{'type': 'space', 'advances': advances}]})
# An invisible on-screen quad keeps vanilla GUI batching aware of shader-positioned text.
# Its alpha is nonzero for glyph bounds, below the vanilla fragment discard threshold.
Image.new('RGBA', (1, 1), (255, 255, 255, 1)).save(assets / 'dialogmenu_settings/textures/ui/native_bounds.png')
bounds = []
for size in range(6, 25):
    for raised in [False, True]:
        index = (size-6)*2 + int(raised)
        ascent = 11 if size == 8 else 7-(18-size)//2 if raised and size <= 12 else min(7,size)
        bounds.append({'type': 'bitmap', 'file': 'dialogmenu_settings:ui/native_bounds.png',
                       'height': (size*3+1)//2, 'ascent': ascent, 'chars': [chr(0xF100+index)]})
write(assets / 'dialogmenu_settings/font/native_bounds.json', {'providers': bounds})
(ROOT / 'src/main/resources/title-metrics.properties').write_text(''.join(f'{cp}={a},{b}\n' for cp,(a,b) in sorted(metrics.items())), encoding='utf-8', newline='\n')
points = glyphs()
(ROOT / 'src/main/resources/native-cjk-ranges.txt').write_text(''.join(f'{a} {b}\n' for a,b in ranges(points)), encoding='utf-8', newline='\n')
# Retire only generator-owned CJK pages, after every replacement has been written.
for old in (assets / 'dialogmenu_settings/textures/ui').glob('button_cjk_*.png'):
    old.unlink()
print(f'Client CJK: {len(points)} glyphs in {len(list(ranges(points)))} ranges; small bitmap metrics: {len(metrics)}')
