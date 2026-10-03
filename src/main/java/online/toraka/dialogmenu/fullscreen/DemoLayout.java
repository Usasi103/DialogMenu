package online.toraka.dialogmenu.fullscreen;

import java.util.List;

/** Logical coordinates: centre (0,0), positive y up, 320 by 180 canvas. */
final class DemoLayout {
    static final int TILE_COLUMNS = 8;
    static final int TILE_ROWS = 5;
    static final int TILE_WIDTH = 240;
    static final int TILE_HEIGHT = 216;
    static final int TILE_BORDER = 2;

    static String canvasGlyphs() {
        var text = new StringBuilder(TILE_COLUMNS * TILE_ROWS);
        for (int i = 0; i < TILE_COLUMNS * TILE_ROWS; i++) text.append((char) (0xe000 + i));
        return text.toString();
    }

    record Button(String id, String label, double x, double y, double width, double height) {
        boolean contains(PointerState.Point point) {
            return Math.abs(point.x() - x) <= width / 2 && Math.abs(point.y() - y) <= height / 2;
        }
    }

    static final List<Button> BUTTONS =
            List.of(
                    new Button("top-left", "左上", -127, 77, 66, 26),
                    new Button("top-right", "右上", 127, 77, 66, 26),
                    new Button("centre", "中央点击", 0, 0, 84, 30),
                    new Button("bottom-left", "左下", -127, -77, 66, 26),
                    new Button("bottom-right", "右下", 127, -77, 66, 26),
                    new Button("settings", "原有菜单", -48, -45, 80, 23),
                    new Button("close", "退出", 48, -45, 64, 23));

    static Button hit(PointerState.Point point) {
        for (Button button : BUTTONS) if (button.contains(point)) return button;
        return null;
    }

    private DemoLayout() {}
}
