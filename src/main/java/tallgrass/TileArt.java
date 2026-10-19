package tallgrass;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Draws every 16x16 map tile procedurally, tinted by biome, with a few animation frames. */
public final class TileArt {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private TileArt() {}

    public static BufferedImage get(Tile t, Biome b, int variant, int frame) {
        int f = switch (t) {
            case WATER -> frame % 4;
            case TALL, FLOWER -> frame % 2;
            default -> 0;
        };
        String key = t.ordinal() + ":" + b.ordinal() + ":" + (variant & 3) + ":" + f;
        BufferedImage im = CACHE.get(key);
        if (im == null) {
            im = draw(t, b, variant & 3, f);
            CACHE.put(key, im);
        }
        return im;
    }

    private static BufferedImage draw(Tile t, Biome b, int v, int f) {
        BufferedImage im = Gfx.image(16, 16);
        Rng r = new Rng(Rng.hash(t.ordinal(), b.ordinal(), v));
        switch (t) {
            case GRASS, BLD, DOOR -> ground(im, b, r);
            case TALL -> tall(im, b, r, f);
            case PATH -> path(im, b, r);
            case SAND -> sand(im, r);
            case FLOWER -> { ground(im, b, r); flowers(im, r, f); }
            case TREE -> { ground(im, b, r); tree(im, b); }
            case ROCK -> { ground(im, b, r); boulder(im, 8, 9, 6, 5); }
            case WATER -> water(im, f, v);
            case FENCE -> { ground(im, b, r); fence(im); }
            case SIGN -> { ground(im, b, r); sign(im); }
            case FLOOR -> floor(im, v);
            case RUG -> rug(im);
            case MAT -> mat(im);
            case WALL -> wall(im);
            case COUNTER -> counter(im);
            case TABLE -> { floor(im, v); table(im); }
            case PLANT -> { floor(im, v); plant(im); }
            case SHELF -> shelf(im, r);
            case BED -> { floor(im, v); bed(im); }
            case CAVE_WALL -> caveWall(im, r);
            default -> Gfx.fill(im, 0, 0, 16, 16, Color.BLACK);
        }
        Palette.quantize(im);
        return im;
    }

    private static void ground(BufferedImage im, Biome b, Rng r) {
        Palette.Ramp g = Palette.ramp(b.ground.getRGB() & 0xffffff);
        Gfx.fill(im, 0, 0, 16, 16, g.color(2));
        boolean grassy = !(b == Biome.BEACH || b == Biome.DESERT || b == Biome.SNOW || b == Biome.CAVE || b == Biome.CANYON);
        for (int i = 0; i < 5; i++) {
            int x = r.nextInt(15), y = r.nextInt(14);
            if (!grassy) {
                Gfx.px(im, x, y, g.color(1));
                if (r.chance(.6)) Gfx.px(im, x + 1, y, g.color(3));
            } else { // little "v" grass blades: dark base, light tip
                Gfx.px(im, x, y + 1, g.color(1));
                Gfx.px(im, x + 1, y + 1, g.color(1));
                Gfx.px(im, x + 1, y, g.color(3));
                if (r.chance(.4)) Gfx.px(im, x - 1, y, g.color(3));
            }
        }
    }

    private static void tall(BufferedImage im, Biome b, Rng r, int f) {
        ground(im, b, r);
        Palette.Ramp t = Palette.ramp(b.tall.getRGB() & 0xffffff);
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 2; col++) {
                int ox = col * 8, oy = row * 8 + 1;
                int sway = (f == 1 && (row + col) % 2 == 0) ? 1 : 0;
                for (int i = 0; i < 6; i++) {          // a "W" tuft, shaded from dark roots to light tips
                    int hgt = (i % 2 == 0) ? 6 : 4;
                    for (int y = 0; y < hgt; y++) {
                        int x = ox + 1 + i + (y < 2 ? sway : 0);
                        int shade = y == 0 ? 4 : y < 2 ? 3 : y < hgt - 1 ? 2 : 1;
                        if (i == 5 && y > 0) shade = Math.max(1, shade - 1); // right edge in shadow
                        Gfx.px(im, x, oy + 7 - hgt + y, t.color(shade));
                    }
                }
                Gfx.fill(im, ox + 1, oy + 7, 6, 1, t.color(0));
                Gfx.px(im, ox, oy + 6, t.color(0));
                Gfx.px(im, ox + 7, oy + 6, t.color(0));
            }
    }

    private static void path(BufferedImage im, Biome b, Rng r) {
        Palette.Ramp p = Palette.ramp(b.path.getRGB() & 0xffffff);
        Gfx.fill(im, 0, 0, 16, 16, p.color(2));
        for (int i = 0; i < 3; i++) { // a few pebbles: lit top, shaded bottom
            int x = r.nextInt(14), y = r.nextInt(14);
            Gfx.px(im, x, y, p.color(3));
            Gfx.px(im, x, y + 1, p.color(1));
        }
        for (int i = 0; i < 3; i++) Gfx.px(im, r.nextInt(16), r.nextInt(16), p.color(1));
    }

    private static void sand(BufferedImage im, Rng r) {
        Color s = new Color(0xe8d898);
        Gfx.fill(im, 0, 0, 16, 16, s);
        for (int i = 0; i < 8; i++) Gfx.px(im, r.nextInt(16), r.nextInt(16), Gfx.darker(s, .1));
    }

    private static void flowers(BufferedImage im, Rng r, int f) {
        Color[] cols = {new Color(0xf85868), new Color(0xf8f8f8), new Color(0xf8d038), new Color(0xb878f8)};
        Color c = cols[r.nextInt(cols.length)];
        int[][] at = {{3, 3}, {11, 9}};
        for (int[] p : at) {
            int x = p[0], y = p[1] + (f == 1 ? 1 : 0);
            Gfx.px(im, x, y - 1, c);
            Gfx.px(im, x - 1, y, c);
            Gfx.px(im, x + 1, y, c);
            Gfx.px(im, x, y + 1, c);
            Gfx.px(im, x, y, new Color(0xf8e870));
            Gfx.px(im, x, y + 2, new Color(0x388830));
        }
    }

    private static void circle(BufferedImage im, double cx, double cy, double rad, Color c) {
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double dx = x + .5 - cx, dy = y + .5 - cy;
                if (dx * dx + dy * dy <= rad * rad) Gfx.px(im, x, y, c);
            }
    }

    private static void tree(BufferedImage im, Biome b) {
        if (b.tree == Biome.Tree.PALM) { palm(im); return; }
        PixelCanvas c = new PixelCanvas(16, 16);
        int wood = c.material(Palette.WOOD);
        switch (b.tree) {
            case PINE, SNOW_PINE -> {
                int leaf = c.material(Palette.PINE);
                int snow = c.material(Palette.SNOW);
                c.rect(7, 12, 2, 3, wood);
                for (int layer = 0; layer < 3; layer++) {
                    int top = 1 + layer * 4, half = 2 + layer * 2;
                    for (int y = 0; y < 5; y++) {
                        int hw = Math.min(half, 1 + y * half / 4);
                        c.rect(8 - hw, top + y, hw * 2, 1, leaf);
                    }
                    if (b.tree == Biome.Tree.SNOW_PINE) c.rect(8 - half / 2 - 1, top + 1, half / 2 + 2, 1, snow);
                }
            }
            case CACTUS -> {
                int g = c.material(Palette.CACTUS);
                c.rect(6, 2, 4, 13, g);
                c.rect(2, 6, 2, 5, g);
                c.rect(2, 10, 4, 2, g);
                c.rect(12, 4, 2, 5, g);
                c.rect(10, 8, 4, 2, g);
            }
            case BOULDER -> {
                int st = c.material(Palette.STONE);
                c.ellipse(8, 9, 6.6, 5.6, st);
                c.ellipse(6, 7, 4, 3.5, st);
            }
            default -> {
                int g = c.material(b.tree == Biome.Tree.ROUND_DARK ? Palette.LEAF_DARK
                        : b.tree == Biome.Tree.WILLOW ? Palette.ramp(0x5a7a42) : Palette.LEAF);
                c.rect(6, 11, 4, 4, wood);
                // canopy as a cluster of blobs, so the shading reads as leafy lumps
                c.ellipse(8, 7, 6.6, 5.6, g);
                c.ellipse(5, 5, 3.6, 3.4, g);
                c.ellipse(11, 5, 3.4, 3.2, g);
                c.ellipse(8, 3.4, 3.4, 2.8, g);
                if (b.tree == Biome.Tree.WILLOW) for (int x = 3; x < 14; x += 3) c.rect(x, 10, 1, 3, g);
            }
        }
        PixelCanvas.Style style = PixelCanvas.Style.sprite();
        style.dither = false;
        style.light = -0.2;
        style.innerLines = false;
        java.awt.Graphics2D gg = im.createGraphics();
        gg.drawImage(c.render(style), 0, 0, null);
        gg.dispose();
    }

    private static void palm(BufferedImage im) {
        Color trunk = Palette.WOOD.color(2), trunkD = Palette.WOOD.color(1);
        for (int y = 6; y < 16; y++) Gfx.fill(im, 7 + (y < 10 ? 1 : 0), y, 2, 1, y % 2 == 0 ? trunk : trunkD);
        Palette.Ramp g = Palette.LEAF;
        for (int i = 0; i < 7; i++) {
            Gfx.px(im, 8 - i, 4 + i / 2, g.color(2)); Gfx.px(im, 8 + i, 4 + i / 2, g.color(1));
            Gfx.px(im, 8 - i, 3 + i / 2, g.color(4)); Gfx.px(im, 8 + i, 3 + i / 2, g.color(3));
        }
        circle(im, 8.5, 4.5, 2.2, Palette.WOOD.color(1));
    }

    private static void boulder(BufferedImage im, int cx, int cy, int rx, int ry) {
        PixelCanvas c = new PixelCanvas(16, 16);
        int st = c.material(Palette.STONE);
        c.ellipse(cx, cy, rx, ry, st);
        c.ellipse(cx - rx * .3, cy - ry * .3, rx * .6, ry * .6, st);
        java.awt.Graphics2D g = im.createGraphics();
        g.drawImage(c.render(PixelCanvas.Style.sprite()), 0, 0, null);
        g.dispose();
    }

    private static void water(BufferedImage im, int f, int v) {
        Palette.Ramp w = Palette.WATER;
        Gfx.fill(im, 0, 0, 16, 16, w.color(2));
        for (int y = 0; y < 16; y++) // gentle dithered depth band
            for (int x = 0; x < 16; x++)
                if (((x + y + v) & 3) == 0 && ((y + f) % 8) < 3) Gfx.px(im, x, y, w.color(1));
        for (int i = 0; i < 3; i++) {
            int y = (i * 5 + v * 2) % 16, x = (i * 6 + f * 2 + v) % 16;
            Gfx.fill(im, x, y, 4, 1, w.color(4));
            Gfx.fill(im, x + 1, y + 1, 2, 1, w.color(3));
        }
    }

    private static void fence(BufferedImage im) {
        Color w = new Color(0xd8b078), d = new Color(0x806040);
        Gfx.fill(im, 1, 4, 3, 10, w); Gfx.fill(im, 12, 4, 3, 10, w);
        Gfx.fill(im, 0, 6, 16, 2, w); Gfx.fill(im, 0, 10, 16, 2, w);
        Gfx.fill(im, 0, 8, 16, 1, d); Gfx.fill(im, 0, 12, 16, 1, d);
        Gfx.fill(im, 3, 4, 1, 10, d); Gfx.fill(im, 14, 4, 1, 10, d);
    }

    private static void sign(BufferedImage im) {
        Color w = new Color(0xc89858), d = new Color(0x705028);
        Gfx.fill(im, 7, 9, 2, 7, d);
        Gfx.fill(im, 1, 2, 14, 8, d);
        Gfx.fill(im, 2, 3, 12, 6, w);
        Gfx.fill(im, 4, 5, 8, 1, d);
        Gfx.fill(im, 4, 7, 6, 1, d);
    }

    private static void floor(BufferedImage im, int v) {
        Color a = new Color(0xe0c898), b = new Color(0xd0b080);
        Gfx.fill(im, 0, 0, 16, 16, a);
        Gfx.fill(im, 0, 7, 16, 1, b);
        Gfx.fill(im, 0, 15, 16, 1, b);
        Gfx.fill(im, (v % 2) * 8 + 3, 0, 1, 7, b);
        Gfx.fill(im, ((v + 1) % 2) * 8 + 3, 8, 1, 7, b);
    }

    private static void rug(BufferedImage im) {
        Gfx.fill(im, 0, 0, 16, 16, new Color(0xc85050));
        Gfx.fill(im, 2, 2, 12, 12, new Color(0xe07060));
        Gfx.fill(im, 4, 4, 8, 8, new Color(0xc85050));
    }

    private static void mat(BufferedImage im) {
        floor(im, 0);
        Gfx.fill(im, 1, 3, 14, 10, new Color(0x8b3a3a));
        Gfx.fill(im, 2, 4, 12, 8, new Color(0xc05050));
        Gfx.fill(im, 3, 7, 10, 1, new Color(0x8b3a3a));
    }

    private static void wall(BufferedImage im) {
        Gfx.fill(im, 0, 0, 16, 16, new Color(0xf0e8d0));
        for (int x = 0; x < 16; x += 4) Gfx.fill(im, x, 0, 1, 13, new Color(0xe0d4b8));
        Gfx.fill(im, 0, 12, 16, 4, new Color(0xa88860));
        Gfx.fill(im, 0, 12, 16, 1, new Color(0x786040));
    }

    private static void counter(BufferedImage im) {
        Gfx.fill(im, 0, 0, 16, 16, new Color(0xb07848));
        Gfx.fill(im, 0, 0, 16, 5, new Color(0xe8d0a8));
        Gfx.fill(im, 0, 5, 16, 1, new Color(0x704828));
        Gfx.fill(im, 0, 15, 16, 1, new Color(0x704828));
    }

    private static void table(BufferedImage im) {
        Gfx.fill(im, 1, 3, 14, 8, new Color(0xa06838));
        Gfx.fill(im, 2, 4, 12, 5, new Color(0xc88850));
        Gfx.fill(im, 2, 11, 2, 4, new Color(0x704828));
        Gfx.fill(im, 12, 11, 2, 4, new Color(0x704828));
    }

    private static void plant(BufferedImage im) {
        Gfx.fill(im, 4, 10, 8, 6, new Color(0xb85838));
        Gfx.fill(im, 4, 10, 8, 1, new Color(0x883828));
        circle(im, 8, 6, 5, new Color(0x388838));
        circle(im, 7, 5, 2.5, new Color(0x58b058));
    }

    private static void shelf(BufferedImage im, Rng r) {
        Gfx.fill(im, 0, 0, 16, 16, new Color(0x805030));
        for (int row = 0; row < 3; row++) {
            Gfx.fill(im, 1, 1 + row * 5, 14, 4, new Color(0x583018));
            for (int x = 2; x < 14; x += 2) Gfx.fill(im, x, 2 + row * 5, 1, 3, Color.getHSBColor((float) r.nextDouble(), .5f, .8f));
        }
    }

    private static void bed(BufferedImage im) {
        Gfx.fill(im, 1, 1, 14, 15, new Color(0x704828));
        Gfx.fill(im, 2, 2, 12, 13, new Color(0x5878d0));
        Gfx.fill(im, 3, 2, 10, 4, new Color(0xf8f8f8));
        Gfx.fill(im, 2, 8, 12, 1, new Color(0x4060b0));
    }

    private static void caveWall(BufferedImage im, Rng r) {
        Color c = new Color(0x584838), l = new Color(0x786858), d = new Color(0x382818);
        Gfx.fill(im, 0, 0, 16, 16, c);
        for (int i = 0; i < 5; i++) {
            int x = r.nextInt(12), y = r.nextInt(12);
            Gfx.fill(im, x, y, 4, 1, l);
            Gfx.fill(im, x, y + 3, 4, 1, d);
        }
    }

    // ---------- buildings ----------
    public enum Kind { CENTER, HOUSE, GYM, LAB }

    private static final Map<String, BufferedImage> BUILDINGS = new HashMap<>();

    public static BufferedImage building(Kind k, int w, int h, int doorX, Color roof) {
        String key = k + ":" + w + ":" + h + ":" + doorX + ":" + roof.getRGB();
        BufferedImage cached = BUILDINGS.get(key);
        if (cached != null) return cached;
        int W = w * 16, H = h * 16;
        BufferedImage im = Gfx.image(W, H);
        int roofH = (int) (H * (k == Kind.GYM ? .42 : .5));
        Color wallC = k == Kind.GYM ? new Color(0xc8c0b0) : new Color(0xf0e8d8);
        Color wallD = Gfx.darker(wallC, .25);
        // walls
        Gfx.fill(im, 1, roofH - 2, W - 2, H - roofH + 2, wallC);
        Gfx.fill(im, 1, H - 3, W - 2, 3, wallD);
        Gfx.fill(im, 0, roofH - 2, 1, H - roofH + 2, Gfx.darker(wallC, .6));
        Gfx.fill(im, W - 1, roofH - 2, 1, H - roofH + 2, Gfx.darker(wallC, .6));
        // roof with stripes and overhang
        Color rd = Gfx.darker(roof, .3), rl = Gfx.lighter(roof, .2);
        Gfx.fill(im, 0, 0, W, roofH, Gfx.darker(roof, .55));
        Gfx.fill(im, 1, 1, W - 2, roofH - 2, roof);
        for (int y = 3; y < roofH - 2; y += 3) Gfx.fill(im, 1, y, W - 2, 1, rd);
        Gfx.fill(im, 1, 1, W - 2, 1, rl);
        Gfx.fill(im, 0, roofH - 2, W, 2, Gfx.darker(roof, .55));
        // windows
        int wy = roofH + 3;
        for (int tx = 0; tx < w; tx++) {
            if (tx == doorX) continue;
            if (k == Kind.CENTER && Math.abs(tx - doorX) > 1 && tx > 0 && tx < w - 1 || k != Kind.CENTER && tx % 2 == 1 && tx != w - 1) {
                int x = tx * 16 + 3;
                Gfx.fill(im, x, wy, 10, 7, new Color(0x404858));
                Gfx.fill(im, x + 1, wy + 1, 8, 5, new Color(0x88c0e8));
                Gfx.fill(im, x + 1, wy + 1, 3, 2, new Color(0xd0f0f8));
            }
        }
        // door
        int dx = doorX * 16 + 2;
        Gfx.fill(im, dx, H - 14, 12, 14, new Color(0x404040));
        Gfx.fill(im, dx + 1, H - 13, 10, 13, k == Kind.CENTER ? new Color(0x90c8f0) : new Color(0x8a5a30));
        if (k == Kind.CENTER) Gfx.fill(im, dx + 5, H - 13, 1, 13, new Color(0x606060));
        else Gfx.px(im, dx + 9, H - 7, new Color(0xf8d860));
        // emblem
        if (k == Kind.CENTER) {
            int cx = W / 2, cy = roofH / 2;
            Gfx.fill(im, cx - 7, cy - 6, 14, 12, new Color(0xf8f8f8));
            Gfx.fill(im, cx - 2, cy - 5, 4, 10, new Color(0xe04848));
            Gfx.fill(im, cx - 6, cy - 2, 12, 4, new Color(0xe04848));
        } else if (k == Kind.GYM) {
            BufferedImage txt = Gfx.image(40, 10);
            java.awt.Graphics2D g = txt.createGraphics();
            Font.draw(g, "GYM", 0, 1, new Color(0xf8f8f8), new Color(0x303030));
            g.dispose();
            int tw = Font.width("GYM") + 2;
            for (int y = 0; y < 10; y++) for (int x = 0; x < tw; x++) {
                int p = txt.getRGB(x, y);
                if ((p >>> 24) != 0) im.setRGB(W / 2 - tw / 2 + x, roofH / 2 - 4 + y, p);
            }
        } else if (k == Kind.LAB) {
            Gfx.fill(im, W / 2 - 10, roofH / 2 - 3, 20, 6, new Color(0xf8f8f8));
            Gfx.fill(im, W / 2 - 9, roofH / 2 - 2, 18, 4, new Color(0x5878c8));
        }
        BUILDINGS.put(key, im);
        return im;
    }
}
