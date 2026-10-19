package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Shared palette and GBA-style UI pieces: framed windows, HP/EXP bars, cursors. */
public final class Gfx {
    public static final Color TEXT = new Color(0x404048);
    public static final Color SHADOW = new Color(0xd0d0c8);
    public static final Color WHITE_TEXT = new Color(0xf8f8f8);
    public static final Color DARK_SHADOW = new Color(0x585860);
    public static final Color FRAME = new Color(0x4a6aa8);
    public static final Color FRAME_LIGHT = new Color(0x8cb0e8);
    public static final Color PAPER = new Color(0xf8f8f8);
    public static final Color RED = new Color(0xe05038);
    public static final Color BLUE = new Color(0x3870d0);

    private Gfx() {}

    public static Color rgb(int v) { return new Color(v); }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color darker(Color c, double t) { return mix(c, Color.BLACK, t); }
    public static Color lighter(Color c, double t) { return mix(c, Color.WHITE, t); }

    /** The classic dialogue/menu window: white paper, rounded two-tone frame. */
    public static void window(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(darker(FRAME, .35));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(FRAME);
        g.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 7, 7);
        g.setColor(FRAME_LIGHT);
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 6, 6);
        g.setColor(PAPER);
        g.fillRoundRect(x + 4, y + 4, w - 8, h - 8, 4, 4);
    }

    /** Battle-style text panel (dark frame, dark fill, white text goes on it). */
    public static void battleText(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x283848));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0xc8a848));
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 6, 6);
        g.setColor(new Color(0x406878));
        g.fillRoundRect(x + 4, y + 4, w - 8, h - 8, 4, 4);
    }

    public static void hpBar(Graphics2D g, int x, int y, int w, double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        g.setColor(new Color(0x404848));
        g.fillRoundRect(x - 1, y - 1, w + 2, 5, 3, 3);
        g.setColor(new Color(0x506858));
        g.fillRect(x, y, w, 3);
        Color c = ratio > .5 ? new Color(0x70f8a8) : ratio > .2 ? new Color(0xf8e038) : new Color(0xf85838);
        int fw = (int) Math.ceil(w * ratio);
        if (ratio > 0 && fw < 1) fw = 1;
        g.setColor(c);
        g.fillRect(x, y, fw, 3);
        g.setColor(darker(c, .25));
        g.fillRect(x, y + 2, fw, 1);
    }

    public static void expBar(Graphics2D g, int x, int y, int w, double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        g.setColor(new Color(0x404848));
        g.fillRect(x - 1, y - 1, w + 2, 4);
        g.setColor(new Color(0x9098a0));
        g.fillRect(x, y, w, 2);
        g.setColor(new Color(0x40c8f8));
        g.fillRect(x, y, (int) (w * ratio), 2);
    }

    public static void cursor(Graphics2D g, int x, int y) { Font.draw(g, ">", x, y, TEXT, SHADOW); }

    /** Blinking "more text" arrow. */
    public static void moreArrow(Graphics2D g, int x, int y, int tick) {
        if ((tick / 16) % 2 == 0) y += 1;
        g.setColor(RED);
        g.fillRect(x, y, 7, 1);
        g.fillRect(x + 1, y + 1, 5, 1);
        g.fillRect(x + 2, y + 2, 3, 1);
        g.fillRect(x + 3, y + 3, 1, 1);
    }

    public static void typeTag(Graphics2D g, Type t, int x, int y) {
        int w = 32;
        g.setColor(darker(t.color, .35));
        g.fillRoundRect(x, y, w, 11, 4, 4);
        g.setColor(t.color);
        g.fillRoundRect(x + 1, y + 1, w - 2, 9, 3, 3);
        String n = t.label.toUpperCase();
        Font.draw(g, n, x + (w - Font.width(n)) / 2, y + 2, WHITE_TEXT, darker(t.color, .5));
    }

    public static BufferedImage image(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); }

    public static void px(BufferedImage im, int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) im.setRGB(x, y, c.getRGB());
    }

    public static void fill(BufferedImage im, int x, int y, int w, int h, Color c) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) px(im, xx, yy, c);
    }
}
