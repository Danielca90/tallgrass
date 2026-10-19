package tallgrass;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** 16x20 overworld character sprites in four directions with a two-step walk cycle. */
public final class CharArt {
    public record Look(int hat, int hair, int skin, int shirt, int pants, boolean cap, boolean longHair) implements java.io.Serializable {}

    public static final Look PLAYER = new Look(0xe03838, 0x483020, 0xf8c8a0, 0x3868c8, 0x404858, true, false);
    public static final Look NURSE = new Look(0xf8f8f8, 0xf090b0, 0xf8d0b0, 0xf8b0c8, 0xf8f8f8, true, true);
    public static final Look CLERK = new Look(0x3878c8, 0x403028, 0xf0c098, 0x48a8e0, 0x384050, true, false);
    public static final Look PROF = new Look(0x909090, 0xb8b8b8, 0xf0c8a0, 0xf0f0f0, 0x605848, false, false);
    public static final Look MOM = new Look(0, 0xa05830, 0xf8d0b0, 0xe07858, 0x5070b0, false, true);

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private CharArt() {}

    public static Look random(Rng r) {
        int[] hair = {0x302018, 0x704020, 0xd8a040, 0x202838, 0xb04828, 0x989898};
        int[] skin = {0xf8d0b0, 0xe8b088, 0xc08860, 0x8c5a3a};
        float h = (float) r.nextDouble();
        int shirt = Color.HSBtoRGB(h, .55f, .85f) & 0xffffff;
        int pants = Color.HSBtoRGB((float) r.nextDouble(), .3f, .45f) & 0xffffff;
        int hat = Color.HSBtoRGB(h + .5f, .6f, .8f) & 0xffffff;
        return new Look(hat, hair[r.nextInt(hair.length)], skin[r.nextInt(skin.length)], shirt, pants, r.chance(.4), r.chance(.4));
    }

    public static BufferedImage get(Look l, int facing, int frame) {
        String key = l.hashCode() + ":" + facing + ":" + frame;
        BufferedImage im = CACHE.get(key);
        if (im == null) {
            im = draw(l, facing, frame);
            CACHE.put(key, im);
        }
        return im;
    }

    private static BufferedImage draw(Look l, int facing, int frame) {
        BufferedImage im = Gfx.image(16, 20);
        Color hair = new Color(l.hair()), skin = new Color(l.skin()), shirt = new Color(l.shirt()), pants = new Color(l.pants());
        Color hat = new Color(l.hat()), shoe = new Color(0x383030);
        int bob = frame == 0 ? 0 : 1;
        int t = bob; // body shifts down 1px mid-stride
        boolean side = facing == Actor.LEFT || facing == Actor.RIGHT;
        // legs / feet
        int lf = 0, rf = 0;
        if (frame == 1) { lf = -1; rf = 1; }
        if (frame == 2) { lf = 1; rf = -1; }
        if (side) {
            Gfx.fill(im, 6, 15 + t, 4, 3, pants);
            Gfx.fill(im, 5 + (frame == 1 ? -1 : frame == 2 ? 1 : 0), 17, 3, 2, pants);
            Gfx.fill(im, 8 + (frame == 1 ? 1 : frame == 2 ? -1 : 0), 17, 3, 2, Gfx.darker(pants, .2));
            Gfx.fill(im, 5 + (frame == 1 ? -1 : frame == 2 ? 1 : 0), 19, 3, 1, shoe);
            Gfx.fill(im, 8 + (frame == 1 ? 1 : frame == 2 ? -1 : 0), 19, 3, 1, shoe);
        } else {
            Gfx.fill(im, 5, 15 + t, 6, 2, pants);
            Gfx.fill(im, 5, 17 + Math.min(0, lf), 2, 2 - Math.min(0, lf) - (lf > 0 ? 1 : 0), pants);
            Gfx.fill(im, 9, 17 + Math.min(0, rf), 2, 2 - Math.min(0, rf) - (rf > 0 ? 1 : 0), pants);
            Gfx.fill(im, 4, 19 + Math.min(0, lf), 3, 1, shoe);
            Gfx.fill(im, 9, 19 + Math.min(0, rf), 3, 1, shoe);
        }
        // torso + arms
        Gfx.fill(im, 4, 10 + t, 8, 6, shirt);
        Gfx.fill(im, 4, 15 + t, 8, 1, Gfx.darker(shirt, .25));
        if (side) {
            int sw = frame == 1 ? -1 : frame == 2 ? 1 : 0;
            int ax = facing == Actor.LEFT ? 7 + sw : 7 - sw;
            Gfx.fill(im, ax, 11 + t, 2, 4, Gfx.darker(shirt, .2));
            Gfx.fill(im, ax, 15 + t, 2, 1, skin);
        } else {
            Gfx.fill(im, 3, 11 + t + (frame == 1 ? 1 : 0), 2, 4, Gfx.darker(shirt, .15));
            Gfx.fill(im, 11, 11 + t + (frame == 2 ? 1 : 0), 2, 4, Gfx.darker(shirt, .15));
            Gfx.fill(im, 3, 15 + t + (frame == 1 ? 1 : 0), 2, 1, skin);
            Gfx.fill(im, 11, 15 + t + (frame == 2 ? 1 : 0), 2, 1, skin);
        }
        // head
        int hy = 2 + t;
        Gfx.fill(im, 4, hy + 2, 8, 7, skin);
        Gfx.fill(im, 3, hy + 3, 10, 5, skin);
        if (facing == Actor.UP) {
            Gfx.fill(im, 3, hy + 1, 10, 7, hair);
            if (l.longHair()) Gfx.fill(im, 4, hy + 8, 8, 3, hair);
        } else {
            Gfx.fill(im, 3, hy + 1, 10, 3, hair);
            if (side) {
                if (facing == Actor.LEFT) Gfx.fill(im, 9, hy + 1, 4, 6, hair); else Gfx.fill(im, 3, hy + 1, 4, 6, hair);
                int ex = facing == Actor.LEFT ? 5 : 10;
                Gfx.fill(im, ex, hy + 5, 1, 2, new Color(0x202020));
            } else {
                Gfx.fill(im, 3, hy + 3, 1, 4, hair);
                Gfx.fill(im, 12, hy + 3, 1, 4, hair);
                Gfx.fill(im, 5, hy + 5, 1, 2, new Color(0x202020));
                Gfx.fill(im, 10, hy + 5, 1, 2, new Color(0x202020));
                Gfx.px(im, 7, hy + 8, Gfx.darker(skin, .2));
                Gfx.px(im, 8, hy + 8, Gfx.darker(skin, .2));
            }
            if (l.longHair()) {
                Gfx.fill(im, 2, hy + 3, 2, 7, hair);
                Gfx.fill(im, 12, hy + 3, 2, 7, hair);
            }
        }
        if (l.cap()) {
            Gfx.fill(im, 3, hy, 10, 3, hat);
            Gfx.fill(im, 4, hy - 1, 8, 1, hat);
            Gfx.fill(im, 6, hy, 4, 1, Gfx.lighter(hat, .5));
            if (facing == Actor.DOWN) Gfx.fill(im, 3, hy + 3, 10, 1, Gfx.darker(hat, .3));
            if (facing == Actor.LEFT) Gfx.fill(im, 1, hy + 2, 4, 2, Gfx.darker(hat, .2));
            if (facing == Actor.RIGHT) Gfx.fill(im, 11, hy + 2, 4, 2, Gfx.darker(hat, .2));
        }
        PixelCanvas.selOut(im, .5);
        Palette.quantize(im);
        return im;
    }

    private static void outline(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        boolean[][] solid = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) solid[y][x] = (im.getRGB(x, y) >>> 24) != 0;
        int ol = 0xff282830;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (solid[y][x]) continue;
                boolean edge = (x > 0 && solid[y][x - 1]) || (x < w - 1 && solid[y][x + 1]) || (y > 0 && solid[y - 1][x]) || (y < h - 1 && solid[y + 1][x]);
                if (edge) im.setRGB(x, y, ol);
            }
    }
}
