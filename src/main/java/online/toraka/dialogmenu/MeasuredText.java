package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/** Immutable measured glyphs keep images intact during wrapping, clipping and popup masking. */
public record MeasuredText(List<MeasuredGlyph> glyphs) {

    public float advance() {
        double sum = 0;
        for (MeasuredGlyph glyph : glyphs) {
            sum += glyph.advance();
        }
        return (float) sum;
    }

    public int width() {
        return (int) Math.ceil(advance());
    }

    public Component component() {
        if (glyphs.isEmpty()) {
            return Component.empty();
        }
        Component nativeText = NativeMenuFont.render(glyphs);
        if (nativeText != null) {
            return nativeText;
        }
        Component first = glyphs.get(0).component();
        boolean plain = true;
        for (MeasuredGlyph glyph : glyphs) {
            Component component = glyph.component();
            if (!(component instanceof TextComponent)
                    || !component.children().isEmpty()
                    || !component.style().equals(first.style())) {
                plain = false;
                break;
            }
        }
        if (plain) {
            StringBuilder content = new StringBuilder();
            for (MeasuredGlyph glyph : glyphs) {
                content.append(((TextComponent) glyph.component()).content());
            }
            return Component.text(content.toString()).style(first.style());
        }
        List<Component> children = new ArrayList<>(glyphs.size());
        for (MeasuredGlyph glyph : glyphs) {
            children.add(glyph.component());
        }
        return Component.empty().children(children).compact();
    }

    public MeasuredText fit(int pixels) {
        float width = 0f;
        List<MeasuredGlyph> kept = new ArrayList<>();
        for (MeasuredGlyph glyph : glyphs) {
            width += glyph.advance();
            if (!(width <= pixels)) {
                break;
            }
            kept.add(glyph);
        }
        return new MeasuredText(kept);
    }

    public MeasuredText plus(MeasuredText other) {
        return new MeasuredText(Kt.plus(glyphs, other.glyphs));
    }

    public List<MeasuredText> wrap(int pixels) {
        List<MeasuredText> lines = new ArrayList<>();
        List<MeasuredGlyph> line = new ArrayList<>();
        float width = 0f;
        for (MeasuredGlyph glyph : glyphs) {
            if (width + glyph.advance() > pixels && !line.isEmpty()) {
                lines.add(new MeasuredText(line));
                line = new ArrayList<>();
                width = 0f;
            }
            // A glyph wider than the entire region cannot be split or painted outside the canvas.
            if (glyph.advance() <= pixels) {
                line.add(glyph);
                width += glyph.advance();
            }
        }
        lines.add(new MeasuredText(line));
        return lines;
    }
}
