package tallgrass;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Small hand-authored sprites written as ASCII templates — still pure code, but drawn pixel by pixel
 * like a pixel artist would. Each letter maps to a shade from a {@link Palette.Ramp}.
 */
public final class Icons {
    private Icons() {}

    /** Builds an image from rows of characters; '.' is transparent. */
    public static BufferedImage template(String[] rows, Map<Character, Integer> colors) {
        int h = rows.length, w = rows[0].length();
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                char c = rows[y].charAt(x);
                if (c == '.') continue;
                Integer col = colors.get(c);
                if (col != null) im.setRGB(x, y, Palette.gba(col));
            }
        return im;
    }

    private static Map<Character, Integer> orbColors() {
        Map<Character, Integer> m = new HashMap<>();
        Palette.Ramp red = Palette.ORB_RED, white = Palette.ORB_WHITE;
        m.put('k', 0xff282830);
        m.put('H', 0xffffffff);
        m.put('R', red.get(3));
        m.put('r', red.get(2));
        m.put('d', red.get(1));
        m.put('W', white.get(4));
        m.put('w', white.get(3));
        m.put('g', white.get(1));
        m.put('b', 0xff383840);
        m.put('c', white.get(4));
        return m;
    }

    public static final BufferedImage ORB = template(new String[] {
            "....kkkk....",
            "..kkRRRrkk..",
            ".kRHRrrrrdk.",
            ".kRRrrrrrdk.",
            "kRrrrrrrrddk",
            "kbbbbkkbbbbk",
            "kbbbkcckbbbk",
            "kwwwwkkwwggk",
            ".kWwwwwwwgk.",
            ".kwwwwwwggk.",
            "..kkwgggkk..",
            "....kkkk...."}, orbColors());

    public static final BufferedImage ORB_SMALL = template(new String[] {
            "..kkkk..",
            ".kRHrrk.",
            "kRrrrrdk",
            "kbbccbbk",
            "kwwccwgk",
            "kwwwwwgk",
            ".kwwwgk.",
            "..kkkk.."}, orbColors());

    public static final BufferedImage EMOTE = template(new String[] {
            ".kkkkkkkkk..",
            "kWWWWWWWWWk.",
            "kWWWWrrWWWk.",
            "kWWWWrrWWWk.",
            "kWWWWrrWWWk.",
            "kWWWWrrWWWk.",
            "kWWWWWWWWWk.",
            "kWWWWrrWWWk.",
            "kWWWWWWWWWk.",
            ".kkkkkWWkk..",
            "......kWk...",
            ".......k...."}, Map.of('k', 0xff303038, 'W', 0xffffffff, 'r', 0xffe04848));

    private static final Map<GameState.Item, BufferedImage> ITEMS = new HashMap<>();

    /** Bag icon for an item, 16x16. Potions are shaded bottles built on a {@link PixelCanvas}. */
    public static BufferedImage item(GameState.Item it) {
        return ITEMS.computeIfAbsent(it, k -> {
            if (k.isOrb()) {
                BufferedImage o = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                o.getGraphics().drawImage(ORB, 2, 2, null);
                return o;
            }
            int liquid = k == GameState.Item.POTION ? 0x9858e0 : k == GameState.Item.SUPER_POTION ? 0xe058a8 : 0xf0c838;
            PixelCanvas c = new PixelCanvas(16, 16);
            int glass = c.material(Palette.ramp(liquid));
            int cap = c.material(Palette.STONE);
            int shine = c.material(Palette.flat(0xffffff));
            if (k == GameState.Item.REVIVE) {
                // a crystal shard
                for (int y = 2; y < 15; y++) {
                    int half = y < 8 ? (y - 1) / 2 + 1 : (15 - y) / 2 + 1;
                    c.rect(8 - half, y, half * 2, 1, glass);
                }
                c.set(6, 6, shine);
                c.set(6, 7, shine);
            } else {
                c.ellipse(8, 10, 5.5, 4.8, glass);
                c.rect(6, 3, 4, 3, glass);
                c.rect(5, 1, 6, 3, cap);
                c.set(5, 8, shine);
                c.set(5, 9, shine);
                c.set(6, 7, shine);
            }
            return c.render(PixelCanvas.Style.sprite());
        });
    }
}
