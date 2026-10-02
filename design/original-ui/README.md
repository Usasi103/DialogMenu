# Original stone UI

Approved neutral gray controls and original 16×16 icons, authored in Blockbench 5.2.1 with Texture / Painter.edit and saved in original-ui.bbmodel. GUI panels retain their layout sizes. Icon ink matches the accepted 16×16 review exactly; no game item textures or third-party server images are copied into these sources.

Open original-ui.bbmodel in Blockbench to edit. Export each texture using the relative path in manifest.json. The Java compilers consume the exported PNGs; they only slice glyphs and calculate advances, and do not paint or import an external sprite sheet. Template button interiors use a repeated column to preserve arbitrary-width buttons. Legacy amethyst/parchment glyph allocations are retained for compatibility; stone resolves to amethyst.

Sources and generated artwork follow the repository LICENSE. Unifont and client font references retain their separate attribution.
