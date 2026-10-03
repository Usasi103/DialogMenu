"""Build the public diagnostic GUI pack using only the official vanilla client shaders."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def build(client_path, output, version):
    client = zipfile.ZipFile(client_path)
    files = {}
    layout = (ROOT / 'src/main/java/online/toraka/dialogmenu/fullscreen/DemoLayout.java').read_text('utf-8')
    buttons = re.findall(r'new Button\("([^"]+)", "([^"]+)", ([^)]+)\)', layout)
    constants = {name: int(value) for name, value in re.findall(r'static final int (TILE_\w+) = (\d+);', layout)}
    columns, rows = constants['TILE_COLUMNS'], constants['TILE_ROWS']
    tile_width, tile_height, border = constants['TILE_WIDTH'], constants['TILE_HEIGHT'], constants['TILE_BORDER']
    width, height = columns * tile_width, rows * tile_height
    scale = width / 320
    assert height / 180 == scale
    assert tile_width + border * 2 <= 256 and tile_height + border * 2 <= 256
    assert len(buttons) == 7
    image = Image.new('RGBA', (width, height), '#101722')
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', round(11.25 * scale))
    small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', round(8.75 * scale))

    def label(text, x, y, selected=font, fill='#ffffff'):
        draw.text((width / 2 + x * scale, height / 2 - y * scale), text, font=selected, anchor='mm', fill=fill)

    for _, text, coords in buttons:
        x, y, w, h = map(float, coords.split(','))
        draw.rectangle((width/2+(x-w/2)*scale, height/2-(y+h/2)*scale,
                        width/2+(x+w/2)*scale-1, height/2-(y-h/2)*scale-1), fill='#29415d')
        label(text, x, y)
    label('DialogMenu · 本地光标试验', 0, 44)
    label('黄色光标与悬停在客户端绘制', 0, 27, small, '#8dcde8')
    label('Shift / F 退出 · 点击结果见聊天', 0, -84, small)
    # Vanilla font atlas pages are 256 square. Each glyph carries one high-resolution
    # tile plus neighbour gutters for seamless filtering; only the outer corners carry tags.
    providers = []
    padded = Image.new('RGBA', (width + border * 2, height + border * 2))
    padded.paste(image, (border, border))
    padded.paste(image.crop((0, 0, 1, height)).resize((border, height)), (0, border))
    padded.paste(image.crop((width-1, 0, width, height)).resize((border, height)), (width+border, border))
    padded.paste(padded.crop((0, border, width+2*border, border+1)).resize((width+2*border, border)), (0, 0))
    padded.paste(padded.crop((0, height+border-1, width+2*border, height+border)).resize((width+2*border, border)), (0, height+border))
    for index in range(columns * rows):
        left, top = index % columns * tile_width, index // columns * tile_height
        tile = padded.crop((left, top, left+tile_width+2*border, top+tile_height+2*border))
        tw, th = tile.size
        for (x, y), gb in [((0, 0), (31, 223)), ((0, th-1), (31, 31)),
                            ((tw-1, th-1), (223, 31)), ((tw-1, 0), (223, 223))]:
            tile.putpixel((x, y), (32+index, *gb, 255))
        png = io.BytesIO()
        tile.save(png, format='PNG')
        name = f'gui/tile_{index:02}.png'
        files[f'assets/dialogmenu_fullscreen/textures/{name}'] = png.getvalue()
        providers.append({'type': 'bitmap', 'file': f'dialogmenu_fullscreen:{name}',
                          'height': 16, 'ascent': 8, 'chars': [chr(0xe000+index)]})
    files['assets/dialogmenu_fullscreen/font/canvas.json'] = json.dumps({'providers': providers}).encode()
    files['assets/dialogmenu_fullscreen/layout.json'] = json.dumps({
        'columns': columns, 'rows': rows, 'tile_width': tile_width, 'tile_height': tile_height,
        'border': border, 'width': width, 'height': height}).encode()
    files['pack.mcmeta'] = json.dumps({'pack': {'description': 'DialogMenu local cursor ' + version,
                                             'min_format': [97, 1], 'max_format': [97, 1]}}).encode()
    source_v = client.read('assets/minecraft/shaders/core/text.vsh').decode('utf-8-sig')
    source_f = client.read('assets/minecraft/shaders/core/text.fsh').decode('utf-8-sig')
    assert 'dm_local_' not in source_v and 'dm_local_' not in source_f
    for suffix, source in [('vsh', source_v), ('fsh', source_f)]:
        source = re.sub(r'(#extension[^\n]*\n)', r'\1#include <minecraft:globals.glsl>\n', source, count=1)
        source, count = re.subn(r'void\s+main\s*\(\s*\)', 'void dm_previous_main()', source)
        assert count == 1
        own = (ROOT / 'design/fullscreen' / f'local_cursor.{suffix}').read_text('utf-8')
        own = own.replace('DM_BUTTONS', ', '.join('vec4('+b[2]+')' for b in buttons))
        own = own.replace('DM_TILE_GRID', f'vec2({columns}.0, {rows}.0)')
        own = own.replace('DM_TILE_SIZE', f'vec2({tile_width}.0, {tile_height}.0)')
        own = own.replace('DM_TILE_BORDER', str(border))
        tail = ('void main() { dm_previous_main(); dm_local_vertex(); }' if suffix == 'vsh'
                else 'void main() { if (!dm_local_fragment()) dm_previous_main(); }')
        files[f'assets/minecraft/shaders/core/text.{suffix}'] = (source+'\n'+own+'\n'+tail+'\n').encode()

    # Mask vanilla world-label backgrounds only while this player has a fullscreen menu.
    background = client.read('assets/minecraft/shaders/core/position_color.fsh').decode('utf-8-sig')
    background = re.sub(r'(#extension[^\n]*\n)', r'\1#include <minecraft:globals.glsl>\n#include <minecraft:projection.glsl>\n', background, count=1)
    files['assets/minecraft/shaders/core/position_color.vsh'] = client.read('assets/minecraft/shaders/core/position_color.vsh')
    background, count = re.subn(r'void\s+main\s*\(\s*\)', 'void dm_previous_main()', background)
    assert count == 1
    files['assets/minecraft/shaders/core/position_color.fsh'] = (background + '''
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
void main() {
    // 26.3 world-label backgrounds share position_color; preserve orthographic GUI/pause/chat.
    if (GameTime < -0.2 && GameTime > -0.55 && ProjMat[3][3] == 0.0) discard;
    dm_previous_main();
}
''').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as target:
        for name, data in sorted(files.items()):
            item = zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(item, data)
    print(f'{output}: {output.stat().st_size} bytes; SHA1 {hashlib.sha1(output.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    build(args.client, args.output, args.version)
