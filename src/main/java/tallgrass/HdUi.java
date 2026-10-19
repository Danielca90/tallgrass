package tallgrass;

import java.awt.*;

/** Modern HD UI pieces: translucent rounded panels and crisp 2x text with a soft shadow. */
public final class HdUi {
    private HdUi() {}

    public static final Color INK = new Color(0xf4f6fb), INK_SHADOW = new Color(0x10141e), ACCENT = new Color(0xf0a040);

    public static void panel(Graphics2D g, int x, int y, int w, int h, int r) {
        Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(x + 2, y + 4, w, h, r * 2, r * 2);
        g.setColor(new Color(22, 30, 52, 228));
        g.fillRoundRect(x, y, w, h, r * 2, r * 2);
        Stroke s = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 200));
        g.drawRoundRect(x + 1, y + 1, w - 2, h - 2, r * 2 - 2, r * 2 - 2);
        g.setStroke(s);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }

    public static void text(Graphics2D g, String s, int x, int y) { text(g, s, x, y, INK); }

    public static void text(Graphics2D g, String s, int x, int y, Color c) {
        Font.raw(g, s, x + 1, y + 1, INK_SHADOW, 2);
        Font.raw(g, s, x, y, c, 2);
    }

    public static int width(String s) { return Font.width(s) * 2; }

    public static void tag(Graphics2D g, String s, int x, int y) {
        Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ACCENT);
        g.fillRoundRect(x, y, width(s) + 20, 20, 10, 10);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
        Font.raw(g, s, x + 10, y + 3, Color.WHITE, 2);
    }

    public static void cursor(Graphics2D g, int x, int y) {
        g.setColor(ACCENT);
        g.fillPolygon(new int[] {x, x + 8, x}, new int[] {y, y + 6, y + 12}, 3);
    }

    public static void moreArrow(Graphics2D g, int x, int y, int tick) {
        if ((tick / 16) % 2 == 0) y += 2;
        g.setColor(ACCENT);
        g.fillPolygon(new int[] {x, x + 12, x + 6}, new int[] {y, y, y + 7}, 3);
    }
}
