package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Menu pieces in the style of Minecraft's front end: dirt backdrop, stone buttons, dark text fields. */
public final class McUi {
    private McUi() {}

    private static BufferedImage dirt;

    private static BufferedImage dirtTile() {
        if (dirt != null) return dirt;
        dirt = Gfx.image(16, 16);
        Rng r = new Rng(42);
        Color[] c = {new Color(0x3b2a1e), new Color(0x46321f), new Color(0x2f2118), new Color(0x553c26), new Color(0x3f2d1f)};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) Gfx.px(dirt, x, y, c[r.nextInt(c.length)]);
        for (int i = 0; i < 6; i++) { int x = r.nextInt(15), y = r.nextInt(15); Gfx.fill(dirt, x, y, 2, 1, new Color(0x6a4a2c)); }
        return dirt;
    }

    public static void dirt(Graphics2D g, int scroll) {
        BufferedImage t = dirtTile();
        int off = (scroll / 4) % 16;
        for (int y = -16; y < Main.H + 16; y += 16)
            for (int x = -16; x < Main.W + 16; x += 16)
                g.drawImage(t, x + off, y + off, null);
    }

    public static void button(Graphics2D g, int x, int y, int w, int h, String label, boolean selected, boolean enabled) {
        g.setColor(selected ? new Color(0xf8f8f8) : new Color(0x101010));
        g.fillRect(x, y, w, h);
        Color face = !enabled ? new Color(0x404040) : selected ? new Color(0x7d8fd4) : new Color(0x6f6f6f);
        g.setColor(face);
        g.fillRect(x + 1, y + 1, w - 2, h - 2);
        g.setColor(Gfx.lighter(face, .3));
        g.fillRect(x + 1, y + 1, w - 2, 1);
        g.fillRect(x + 1, y + 1, 1, h - 2);
        g.setColor(Gfx.darker(face, .35));
        g.fillRect(x + 1, y + h - 2, w - 2, 1);
        g.fillRect(x + w - 2, y + 1, 1, h - 2);
        Color tc = !enabled ? new Color(0xa0a0a0) : selected ? new Color(0xffffa0) : new Color(0xf0f0f0);
        Font.draw(g, label, x + (w - Font.width(label)) / 2, y + (h - 7) / 2, tc, new Color(0x303030));
    }

    public static void field(Graphics2D g, int x, int y, int w, int h, String text, boolean focused, int tick, String placeholder) {
        g.setColor(focused ? new Color(0xf0f0f0) : new Color(0xa0a0a0));
        g.fillRect(x, y, w, h);
        g.setColor(Color.BLACK);
        g.fillRect(x + 1, y + 1, w - 2, h - 2);
        java.awt.Shape clip = g.getClip();
        g.setClip(x + 2, y + 1, w - 4, h - 2);
        int tx = x + 4;
        int tw = Font.width(text);
        if (tw > w - 12) tx -= tw - (w - 12);
        if (text.isEmpty() && !focused && placeholder != null) Font.draw(g, placeholder, tx, y + (h - 7) / 2, new Color(0x707070), null);
        else Font.draw(g, text + (focused && (tick / 15) % 2 == 0 ? "_" : ""), tx, y + (h - 7) / 2, new Color(0xe0e0e0), new Color(0x383838));
        g.setClip(clip);
    }

    public static void title(Graphics2D g, String s, int y) {
        Font.draw(g, s, (Main.W - Font.width(s)) / 2, y, new Color(0xf8f8f8), new Color(0x3a3a3a));
    }

    public static void label(Graphics2D g, String s, int x, int y) { Font.draw(g, s, x, y, new Color(0xc8c8c8), new Color(0x2a2a2a)); }

    /** Edits a string with this frame's typed characters and backspaces. */
    public static String edit(Input in, String s, int max) {
        StringBuilder b = new StringBuilder(s);
        for (char c : in.typed()) if (b.length() < max) b.append(c);
        for (int i = 0; i < in.backspaces() && b.length() > 0; i++) b.setLength(b.length() - 1);
        return b.toString();
    }
}
