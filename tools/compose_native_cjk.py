"""Add the public menu font hook to an existing generated text vertex shader (e.g. BetterHud).

Writes a separate reviewed merge candidate; leaves the provider's input and fragment shader intact.
Fullscreen shader composition is independent and must be retained by the pack owner.
"""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def compose(source):
    if 'dm_native_cjk_vertex' in source:
        raise ValueError('Native CJK hook already exists; do not inject it twice')
    if re.search(r'^\s*#(?:Create|Generate)\w+', source, re.M):
        raise ValueError('Use the generated provider shader, after its template directives are expanded')
    for name in ('Position', 'ProjMat', 'ModelViewMat'):
        if not re.search(r'\b' + name + r'\b', source):
            raise ValueError('Unsupported text shader: missing ' + name)
    source, count = re.subn(r'void\s+main\s*\(\s*\)', 'void dm_native_provider_main()', source)
    if count != 1:
        raise ValueError('Expected exactly one text vertex main function')
    return source + '\n' + (ROOT / 'design/font/native_cjk.vsh').read_text('utf-8') + '\nvoid main() { dm_native_provider_main(); dm_native_cjk_vertex(); }\n'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error('Output must be a separate merge candidate')
    candidate = compose(args.input.read_text('utf-8-sig'))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(candidate, encoding='utf-8', newline='\n')
    print('Composed native font vertex hook:', args.output)
