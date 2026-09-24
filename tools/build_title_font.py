"""Reuse the bundled font atlases at title size and measure every glyph advance."""
from pathlib import Path
import json
import math
from PIL import Image

project = Path(__file__).resolve().parents[1]
assets = project / 'resourcepack/assets'
load = lambda name: json.loads((assets / name).read_text(encoding='utf-8'))['providers']
providers = []
metrics = {32: (0, 0, 0)}
for source, height in [
    ('toraka_dialogue/font/labels.json', 16),
    ('toraka_settings/font/labels.json', 16),
    ('toraka_settings/font/button_cjk.json', 24),
]:
    for original in load(source):
        if original['type'] != 'bitmap':
            continue
        provider = original | {'height': height, 'ascent': 7}
        providers.append((provider, height == 24))
        namespace, texture = provider['file'].split(':')
        with Image.open(assets / namespace / 'textures' / texture) as atlas:
            alpha = atlas.convert('RGBA').getchannel('A')
            cell_width = atlas.width // len(provider['chars'][0])
            cell_height = atlas.height // len(provider['chars'])
            for row, characters in enumerate(provider['chars']):
                for column, character in enumerate(characters):
                    if ord(character) == 0 or ord(character) in metrics:
                        continue
                    cell = alpha.crop((column * cell_width, row * cell_height,
                                       (column + 1) * cell_width, (row + 1) * cell_height))
                    bounds = cell.getbbox()
                    metrics[ord(character)] = (bounds[2] if bounds else 0, cell_height, int(height == 24))
for size in range(6, 25):
    if size == 8:
        continue
    scaled = [{'type': 'space', 'advances': {' ': (size + 1) // 2}}]
    scaled += [provider | {'height': (size * 3 + 1) // 2 if padded else size} for provider, padded in providers]
    (assets / f'toraka_dialogue/font/text_{size}.json').write_text(json.dumps({'providers': scaled}, ensure_ascii=True, separators=(',', ':')) + '\n', encoding='utf-8')
(assets / 'toraka_dialogue/font/title.json').write_text('{"providers":[{"type":"reference","id":"toraka_dialogue:text_16"}]}\n', encoding='utf-8')
(project / 'src/main/resources/title-metrics.properties').write_text(''.join(f'{code}={values[0]},{values[1]},{values[2]}\n' for code, values in sorted(metrics.items())), encoding='utf-8')
print('Configurable font sizes 6-24:', len(metrics), 'measured characters; reuses existing raster atlases.')
