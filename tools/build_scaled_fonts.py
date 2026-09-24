"""Compile menu-scale font variants and exact advances; no shader scaling.

Usage: python -B tools/build_scaled_fonts.py /path/to/vanilla-client.jar
Reuses existing textures. Unihex glyphs are converted to shared bitmap atlases
because the vanilla Unihex provider has no size/ascent settings.
"""
from pathlib import Path
from functools import cache
import io
import hashlib
import json
import math
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'resourcepack/assets'
SCALES = (50, 75)
TOP = 3


def rounded(value):
    return math.floor(value + 0.5)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, separators=(',', ':'), ensure_ascii=True) + '\n', encoding='utf-8')


def generate(client_path):
    client = zipfile.ZipFile(client_path)
    fonts = {}
    # Only source definitions: generated scale variants must never be fed back in.
    for namespace in ('dialogmenu_settings', 'dialogmenu_dialogue'):
        for path in sorted((ASSETS / namespace / 'font').glob('*.json')):
            fonts[f'{namespace}:{path.stem}'] = json.loads(path.read_text(encoding='utf-8'))['providers']

    @cache
    def texture(key):
        namespace, name = key.split(':')
        path = ASSETS / namespace / 'textures' / name
        data = path.read_bytes() if path.is_file() else client.read(f'assets/{namespace}/textures/{name}')
        return Image.open(io.BytesIO(data)).convert('RGBA')

    # Match the bundled Unihex provider's inclusive crop bounds, including overrides.
    unicode_providers = []
    unifont = fonts['dialogmenu_settings:unifont'][0]
    overrides = unifont['size_overrides']
    ns, filename = unifont['hex_file'].split(':')
    glyphs = []
    with zipfile.ZipFile(ASSETS / ns / filename) as archive:
        for name in sorted(archive.namelist()):
            if not name.endswith('.hex'):
                continue
            for line in archive.read(name).decode().splitlines():
                code, bits = line.split(':')
                cp = int(code, 16)
                digits = len(bits) // 16
                values = [int(bits[i:i + digits], 16) for i in range(0, len(bits), digits)]
                mask = 0
                for value in values:
                    mask |= value
                left = digits * 4 - mask.bit_length() if mask else 0
                right = digits * 4 - ((mask & -mask).bit_length() - 1) - 1 if mask else digits * 4
                for override in overrides:
                    if ord(override['from']) <= cp <= ord(override['to']):
                        left, right = override['left'], override['right']
                        break
                glyphs.append((cp, values, digits * 4, left, right))
    # One shared raster set serves both scales and every vertical offset.
    for index in range(0, len(glyphs), 2048):
        chunk = glyphs[index:index + 2048]
        atlas = Image.new('RGBA', (64 * 32, 16 * math.ceil(len(chunk) / 32)))
        chars = []
        for position, (cp, values, bits, left, right) in enumerate(chunk):
            x, y = position % 32 * 64, position // 32 * 16
            for row, value in enumerate(values):
                for column in range(left, min(right + 1, bits)):
                    if value & (1 << (bits - column - 1)):
                        atlas.putpixel((x + column - left, y + row), (255, 255, 255, 255))
            # Pin the advance to the declared inclusive bounds, even for blank glyphs.
            # Alpha 1 is discarded by the text shader but counted by BitmapProvider.
            anchor = (x + right - left, y + 15)
            if atlas.getpixel(anchor)[3] == 0:
                atlas.putpixel(anchor, (255, 255, 255, 1))
            chars.append(chr(cp))
        chars.extend('\0' for _ in range((-len(chars)) % 32))
        file = f'dialogmenu_settings:scaled/unicode_{index // 2048}.png'
        dest = ASSETS / 'dialogmenu_settings/textures/scaled' / f'unicode_{index // 2048}.png'
        dest.parent.mkdir(parents=True, exist_ok=True)
        atlas.save(dest, optimize=True)
        unicode_providers.append({'type': 'bitmap', 'file': file, 'height': 8, 'ascent': 7,
                                  'chars': [''.join(chars[i:i + 32]) for i in range(0, len(chars), 32)]})
    fonts['dialogmenu_settings:unifont'] = unicode_providers

    @cache
    def bitmap_metrics(serialized):
        entry = json.loads(serialized)
        img = texture(entry['file'])
        rows = entry['chars']
        cw, ch = img.width // len(rows[0]), img.height // len(rows)
        alpha = img.getchannel('A')
        result = {}
        for y, row in enumerate(rows):
            for x, char in enumerate(row):
                if char == '\0':
                    continue
                bounds = alpha.crop((x * cw, y * ch, (x + 1) * cw, (y + 1) * ch)).getbbox()
                result[ord(char)] = (bounds[2] if bounds else 0, ch)
        return result

    metric_lines = ['# Generated by tools/build_scaled_fonts.py: font start end advance']
    shared_providers = {}

    @cache
    def rasterize(file, display_height, characters):
        # Pre-rasterize at the displayed resolution. BitmapProvider otherwise
        # retains a full RGBA source atlas for EVERY size/ascent combination.
        # Preserve rounded ink advances with shader-discarded alpha anchors.
        source = texture(file)
        chars = characters
        cols, rows = len(chars[0]), len(chars)
        cw, ch = source.width // cols, source.height // rows
        # Keep two texture samples per displayed pixel for small text. A 6px
        # Chinese glyph rasterized at 1x loses strokes before GUI scaling.
        samples = 2 if display_height <= 12 or cols * rows < 256 else 1
        h = min(ch, display_height * samples)
        raw_width = max(1, math.ceil(cw * h / ch))
        boxes = {}
        alpha = source.getchannel('A')
        for row, line in enumerate(chars):
            for column, character in enumerate(line):
                if character != '\0':
                    boxes[row, column] = alpha.crop((column * cw, row * ch, (column + 1) * cw, (row + 1) * ch)).getbbox()
        w = max(1, max((math.ceil(box[2] * h / ch) for box in boxes.values() if box), default=1))
        stamp = hashlib.sha256(('v3' + file + str((cols, rows, display_height, h, w))).encode()).hexdigest()[:20]
        dest = ASSETS / 'dialogmenu_settings/textures/scaled' / f'compact_{stamp}.png'
        if not dest.exists():
            compact = Image.new('RGBA', (w * cols, h * rows))
            for row, line in enumerate(chars):
                for column, character in enumerate(line):
                    if character == '\0':
                        continue
                    cell = source.crop((column * cw, row * ch, (column + 1) * cw, (row + 1) * ch))
                    bounds = boxes[row, column]
                    advance = rounded((bounds[2] if bounds else 0) * display_height / ch)
                    ink = min(w, rounded(advance * h / display_height))
                    raster = cell.resize((raw_width, h), Image.Resampling.NEAREST).crop((0, 0, w, h))
                    # Rounding and nearest sampling may differ by one column.
                    if ink < w:
                        raster.paste((0, 0, 0, 0), (ink, 0, w, h))
                    if ink > 0 and raster.getpixel((ink - 1, h - 1))[3] == 0:
                        raster.putpixel((ink - 1, h - 1), (255, 255, 255, 1))
                    compact.paste(raster, (column * w, row * h))
            compact.save(dest, optimize=True)
        return f'dialogmenu_settings:scaled/compact_{stamp}.png'

    def compact_bitmap(entry, compact):
        if compact:
            entry['file'] = rasterize(entry['file'], entry['height'], tuple(entry['chars']))
        key = json.dumps(entry, sort_keys=True)
        tag = hashlib.sha256(key.encode()).hexdigest()[:20]
        shared_providers[tag] = entry
        return {'type': 'reference', 'id': f'dialogmenu_settings:scaled/providers/{tag}'}

    for percent in SCALES:
        scale = percent / 100
        offsets = sorted({(rounded(row * 9 * scale) + TOP) % 9 for row in range(30)})
        measured = {}

        def variants(key):
            if key in measured:
                return measured[key]
            result = {}
            ns, name = key.split(':')
            for offset in offsets:
                providers = []
                for index, original in enumerate(fonts[key]):
                    entry = dict(original)
                    if entry['type'] == 'reference':
                        child = entry['id']
                        child_ns, child_name = child.split(':')
                        entry['id'] = f'{child_ns}:scaled/{percent}/{offset}/{child_name}'
                        child_metrics = variants(child)
                        for cp, advance in child_metrics.items():
                            result.setdefault(cp, advance)
                    elif entry['type'] == 'space':
                        entry['advances'] = {char: width * scale for char, width in entry['advances'].items()}
                        for char, advance in entry['advances'].items():
                            result.setdefault(ord(char), advance)
                    elif entry['type'] == 'bitmap':
                        height = original.get('height', 8)
                        entry['height'] = max(1, rounded(height * scale))
                        entry['ascent'] = 7 - offset - rounded((7 - original['ascent']) * scale)
                        metrics = bitmap_metrics(json.dumps(original, sort_keys=True))
                        for cp, (ink, cell_height) in metrics.items():
                            result.setdefault(cp, rounded(ink * entry['height'] / cell_height) + 1)
                        if entry['ascent'] > entry['height']:
                            # Minecraft requires ascent <= declared height. Extra transparent
                            # cell padding preserves the ink scale and position for small fonts.
                            image = texture(original['file'])
                            row_count = len(original['chars'])
                            cell_height = image.height // row_count
                            padded = Image.new('RGBA', (image.width, image.height * 2))
                            for atlas_row in range(row_count):
                                padded.paste(image.crop((0, atlas_row * cell_height, image.width,
                                                        (atlas_row + 1) * cell_height)),
                                             (0, atlas_row * cell_height * 2))
                            tag = hashlib.sha256((original['file'] + str(row_count)).encode()).hexdigest()[:16]
                            dest = ASSETS / 'dialogmenu_settings/textures/scaled' / f'padded_{tag}.png'
                            if not dest.exists():
                                padded.save(dest, optimize=True)
                            entry['file'] = f'dialogmenu_settings:scaled/padded_{tag}.png'
                            entry['height'] *= 2
                            assert entry['ascent'] <= entry['height']
                    else:
                        raise ValueError(f'Unsupported provider: {key}: {entry["type"]}')
                    providers.append(compact_bitmap(entry, name not in {'ui', 'switches', 'quest_ui', 'quest_items', 'rewards'}) if entry['type'] == 'bitmap' else entry)
                write_json(ASSETS / ns / 'font/scaled' / str(percent) / str(offset) / f'{name}.json', {'providers': providers})
            measured[key] = result
            return result

        for key in sorted(fonts):
            entries = variants(key)
            metric_key = f'{key}@{percent}'
            metric_lines.append(f'@ {metric_key}')
            start = end = -1
            previous = None
            for cp, advance in sorted(entries.items()):
                if cp == end + 1 and advance == previous:
                    end = cp
                    continue
                if previous is not None:
                    metric_lines.append(f'{metric_key} {start} {end} {previous:g}')
                start = end = cp
                previous = advance
            if previous is not None:
                metric_lines.append(f'{metric_key} {start} {end} {previous:g}')

        # Transparent bitmap rectangles have real, scaled client hit geometry.
        # Their advance is 1px; repeat horizontally to form an arbitrary-width hit.
        for row in range(29):
            top = rounded(row * 9 * scale) + TOP
            offset = top % 9
            for span in range(1, 30 - row):
                height = rounded((row + span) * 9 * scale) + TOP - top
                assert 7 - offset <= height
                write_json(ASSETS / 'dialogmenu_settings/font/scaled/hit' / str(height) / f'{offset}.json', {
                    'providers': [{'type': 'bitmap', 'file': 'dialogmenu_settings:scaled/hit.png',
                                   'height': height, 'ascent': 7 - offset, 'chars': ['\ue000']}]})
    hit = ASSETS / 'dialogmenu_settings/textures/scaled/hit.png'
    Image.new('RGBA', (1, 1)).save(hit)
    for tag, entry in shared_providers.items():
        write_json(ASSETS / 'dialogmenu_settings/font/scaled/providers' / f'{tag}.json', {'providers': [entry]})
    generated_fonts = (ASSETS / 'dialogmenu_settings/font/scaled/providers').resolve()
    for candidate in generated_fonts.glob('*.json'):
        assert candidate.resolve().parent == generated_fonts
        if candidate.stem not in shared_providers:
            candidate.unlink()
    retained = {entry['file'].split('/')[-1] for entry in shared_providers.values() if ':scaled/' in entry['file']} | {'hit.png'}
    generated = (ASSETS / 'dialogmenu_settings/textures/scaled').resolve()
    for candidate in generated.glob('*.png'):
        assert candidate.resolve().parent == generated
        if candidate.name not in retained and candidate.name.startswith(('unicode_', 'padded_', 'compact_')):
            candidate.unlink()
    (ROOT / 'src/main/resources/scaled-font-metrics.txt').write_text('\n'.join(metric_lines) + '\n', encoding='utf-8')
    client.close()
    print(f'Generated {len(fonts)} font families at 50%/75%, {len(glyphs)} Unicode glyphs, {len(metric_lines)} metric ranges.')


if __name__ == '__main__':
    generate(sys.argv[1])
