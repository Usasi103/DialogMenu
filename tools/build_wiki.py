"""Build the offline Chinese wiki without touching the deployed server."""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dependency-path", type=Path)
    args = parser.parse_args()
    if args.dependency_path:
        sys.path.insert(0, str(args.dependency_path.resolve()))
    try:
        import markdown
        from markdown.extensions.toc import slugify_unicode
    except ImportError:
        raise SystemExit("Install Markdown==3.8.2 outside test_server, then use --dependency-path.")

    root = Path(__file__).resolve().parents[1] / "docs" / "wiki"
    summary = (root / "SUMMARY.md").read_text(encoding="utf-8")
    entries = re.findall(r"^\* \[([^\]]+)\]\(([^)]+\.md)\)", summary, re.M)
    if not entries:
        raise SystemExit("SUMMARY.md has no chapters")
    entries.append(("目录", "SUMMARY.md"))
    ids = {name: Path(name).stem.lower() for _, name in entries}
    pages = []
    examples = set()
    link_count = 0
    for title, name in entries:
        source = (root / name).read_text(encoding="utf-8")
        for filename, code in re.findall(
            r"<!-- example: ([a-z][a-z0-9_-]*\.yml) -->\s*```yaml\n(.*?)\n```",
            source,
            re.S,
        ):
            if filename in examples:
                raise SystemExit(f"Duplicate example: {filename}")
            examples.add(filename)
            target = root / "examples" / filename
            target.parent.mkdir(exist_ok=True)
            target.write_text(code + "\n", encoding="utf-8", newline="\n")
        engine = markdown.Markdown(
            extensions=["tables", "fenced_code", "toc"],
            extension_configs={"toc": {"slugify": slugify_unicode}},
        )
        rendered = engine.convert(source)

        def link(match: re.Match[str]) -> str:
            nonlocal link_count
            href = html.unescape(match.group(1))
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", href) or href.startswith("#"):
                return match.group(0)
            path, _, anchor = href.partition("#")
            if not (root / unquote(path)).is_file():
                # Examples can be produced by a later chapter.
                if not path.startswith("examples/") or not path.endswith(".yml"):
                    raise SystemExit(f"{name}: missing link {href}")
            link_count += 1
            if path in ids:
                route = "#" + ids[path] + ("/" + anchor if anchor else "")
                return 'href="' + html.escape(route, quote=True) + '"'
            return match.group(0)

        rendered = re.sub(r'href="([^"]+)"', link, rendered)
        plain = html.unescape(re.sub(r"<[^>]+>", " ", rendered))
        pages.append({
            "id": ids[name], "title": title, "file": name,
            "html": rendered, "text": re.sub(r"\s+", " ", plain).strip(),
        })

    # Validate generated examples and all relative links after all chapters are read.
    for _, name in entries:
        source = (root / name).read_text(encoding="utf-8")
        for href in re.findall(r"\[[^\]]*\]\(([^)]+)\)", source):
            if "://" in href or href.startswith("#"):
                continue
            target = root / unquote(href.partition("#")[0])
            if not target.is_file():
                raise SystemExit(f"{name}: missing link {href}")
    payload = json.dumps(pages, ensure_ascii=False).replace("<", "\\u003c")
    template = (root / "site-template.html").read_text(encoding="utf-8")
    if template.count("__WIKI_PAGES__") != 1:
        raise SystemExit("Expected exactly one page data slot")
    output = template.replace("__WIKI_PAGES__", payload)
    (root / "index.html").write_text(output, encoding="utf-8", newline="\n")
    print(f"Built {len(pages)} pages, {len(examples)} complete YAML examples, {link_count} local links.")
    print(root / "index.html")


if __name__ == "__main__":
    main()
