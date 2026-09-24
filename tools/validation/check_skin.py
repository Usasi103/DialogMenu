"""Validate actual bitmap metrics and render the emitted component for inspection.

Requires Pillow. All output is written to the caller's external report directory.
"""
from pathlib import Path
import json
import math
import sys
from PIL import Image

project, scene, report = map(Path, sys.argv[1:])
root = project / "resourcepack/assets/dialogmenu_settings"
metrics = dict(line.split("=", 1) for line in (project / "src/main/resources/ui-metrics.properties").read_text().splitlines())
fonts = {}
for name in ("button_cjk", "ui", "labels", "button_labels"):
    glyphs = {}
    for p in json.loads((root / f"font/{name}.json").read_text())["providers"]:
        if p["type"] == "space":
            for c, width in p["advances"].items():
                glyphs[c] = (None, width, 0)
        elif p["type"] == "reference" and p['id'].startswith('dialogmenu_settings:'):
            for c, glyph in fonts[p['id']].items():
                glyphs.setdefault(c, glyph)
        elif p["type"] == "bitmap":
            image = Image.open(root / "textures" / p["file"].split(":", 1)[1]).convert("RGBA")
            columns, rows = len(p["chars"][0]), len(p["chars"])
            cellw, cellh = image.width // columns, image.height // rows
            height = p.get("height", 8)
            assert p["ascent"] <= height, (name, p)
            assert cellw <= 256 and cellh <= 256, "Minecraft glyph atlas limit"
            for y, line in enumerate(p["chars"]):
                for x, c in enumerate(line):
                    if c == "\0" or c in glyphs:
                        continue
                    tile = image.crop((x * cellw, y * cellh, (x + 1) * cellw, (y + 1) * cellh))
                    bounds = tile.getbbox()
                    width = math.floor((bounds[2] if bounds else 0) * height / cellh + 0.5) + 1
                    key = ("glyph." if name == "ui" else "label.") + str(ord(c))
                    expected = int(metrics.get(key, '9'))
                    assert expected == width, (name, c, width, expected)
                    tile = tile.resize((round(cellw * height / cellh), height), Image.Resampling.NEAREST)
                    glyphs[c] = (tile, width, 7 - p["ascent"])
    fonts["dialogmenu_settings:" + name] = glyphs

# Body labels use vanilla Unihex at its normal baseline. The padded CJK bitmap
# contains the same source ink; crop the transparent padding for preview only.
for c, (tile, width, offset) in fonts['dialogmenu_settings:button_cjk'].items():
    fonts['dialogmenu_settings:labels'].setdefault(c, (tile.crop((0, 0, tile.width, 8)), width, 0))

button_font = fonts['dialogmenu_settings:button_labels']
for c in '开关高中低':
    assert button_font[c][1] == 9, ('CJK advance', c, button_font[c][1])
    assert button_font[c][2] == button_font['T'][2] == -4, ('Mixed-label baseline', c)

canvas = Image.new("RGBA", (452, 29 * 9), (20, 23, 28, 255))
x, row = 0, 0
for part in json.loads(scene.read_text(encoding="utf-8"))["extra"]:
    if isinstance(part, str):
        assert part == "\n" and x == 452, (row, x)
        row += 1
        x = 0
        continue
    font = fonts[part["font"]]
    for c in part["text"]:
        tile, width, offset = font[c]
        if tile:
            if part.get("color", "white") not in ("white", "#ffffff"):
                color = part["color"].lstrip("#")
                tint = Image.new("RGBA", tile.size, tuple(bytes.fromhex(color)) + (255,))
                tint.putalpha(tile.getchannel("A"))
                tile = tint
            canvas.alpha_composite(tile, (x, row * 9 + offset))
        x += width
        assert 0 <= x <= 452, (row, x)
assert row == 28 and x == 452
report.mkdir(parents=True, exist_ok=True)
canvas.resize((904, 522), Image.Resampling.NEAREST).save(report / "canvas-preview.png")
print("PASS: all shipped glyph advances match bitmap alpha bounds; all font ascents valid; 29 rows aligned.")
print("Offline component preview:", report / "canvas-preview.png")
