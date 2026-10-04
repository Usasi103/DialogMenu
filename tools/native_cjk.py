"""Client-owned Unihex glyphs used by the menu's sized/raised CJK text."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RANGES = [(0x20A0, 0x20CF), (0x26C2, 0x26C2), (0x3001, 0x9FFF),
          (0xF900, 0xFAFF), (0xFF01, 0xFF5E)]


def glyphs():
    path = ROOT / 'design/minecraft-font/unifont.zip'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == 'aea3e9918b0d31de6f94623080f04c31c8c16a5b1a2e8d99ab39f1acdddd30f7'
    result = set()
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith('.hex'):
                for line in archive.read(name).decode().splitlines():
                    cp = int(line.split(':', 1)[0], 16)
                    if any(a <= cp <= b for a, b in RANGES):
                        result.add(cp)
    return result


def ranges(points):
    start = end = None
    for cp in sorted(points):
        if end is not None and cp == end + 1:
            end = cp
        else:
            if end is not None:
                yield start, end
            start = end = cp
    if end is not None:
        yield start, end
