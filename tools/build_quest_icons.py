"""Reference original client items as glyphs; never copy their PNGs into the pack."""
from pathlib import Path
from PIL import Image
import io, json, math, sys, zipfile

project = Path(__file__).resolve().parents[1]
names = ['iron_sword', 'fishing_rod', 'nether_star', 'book', 'iron_ingot',
         'gold_ingot', 'experience_bottle', 'amethyst_shard', 'cod',
         'diamond_chestplate', 'bread', 'oak_sapling', 'compass']
providers, metrics = [], []
with zipfile.ZipFile(sys.argv[1]) as client:
    for i, name in enumerate(names):
        texture = {'oak_sapling': 'block/oak_sapling', 'compass': 'item/compass_16'}.get(name, f'item/{name}')
        im = Image.open(io.BytesIO(client.read(f'assets/minecraft/textures/{texture}.png'))).convert('RGBA')
        right = im.getchannel('A').getbbox()[2]
        advance = math.floor(right * 12 / im.height + 0.5) + 1
        providers.append({'type': 'bitmap', 'file': f'minecraft:{texture}.png',
                          'height': 12, 'ascent': 4, 'chars': [chr(0xE000 + i)]})
        metrics.append(f'{name}={0xE000+i},{advance}\n')
# List icons center in an 18px button. Reward labels sit on the following 9px row;
# move only reward icons down 4px so their 12px cells share the 8px text center.
for font, ascent in [('quest_items', 4), ('quest_rewards', 0)]:
    (project / f'resourcepack/assets/dialogmenu_dialogue/font/{font}.json').write_text(
        json.dumps({'providers': [p | {'ascent': ascent} for p in providers]}, indent=2) + '\n', encoding='utf-8')
(project / 'src/main/resources/quest-icons.properties').write_text(''.join(metrics), encoding='utf-8')
