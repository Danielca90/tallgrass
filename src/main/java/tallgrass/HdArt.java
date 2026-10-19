package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Generators for the HD (32px tile) art. Everything here is pure code; {@link TextureBaker} writes the
 * results out as PNGs so they ship as a texture pack and can be replaced, like Minecraft block textures.
 */
public final class HdArt {
    private HdArt() {}

    public static final int T = 32;
    static final double LX = -0.5, LY = -0.68, LZ = 0.54;

    // ---------------------------------------------------------------- color

    public static int[] ramp(int rgb, int n) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        int[] out = new int[n];
        int mid = n / 2;
        for (int i = 0; i < n; i++) {
            double k = (i - mid) / (double) mid;
            double h = hsb[0], s = hsb[1], b = hsb[2];
            if (k < 0) { h = toward(h, .66, -k * .08); s = Math.min(1, s - k * .14); b *= 1 + k * .5; }
            else if (k > 0) { h = toward(h, .14, k * .06); s = Math.max(0, s - k * .2); b = Math.min(1, b + (1 - b) * k * .55 + k * .05); }
            out[i] = 0xff000000 | (Color.HSBtoRGB((float) h, (float) s, (float) b) & 0xffffff);
        }
        return out;
    }

    static double toward(double h, double t, double a) {
        double d = ((t - h + 1.5) % 1.0) - .5;
        return (((h + d * Math.min(1, a * 4)) % 1.0) + 1.0) % 1.0;
    }

    static int sh(int[] r, double v) { return r[(int) Math.max(0, Math.min(r.length - 1, Math.round(v)))]; }

    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (a & 0xff000000) | r << 16 | g << 8 | bl;
    }

    static int rgb(Color c) { return c.getRGB() & 0xffffff; }

    static void px(BufferedImage im, int x, int y, int c) { if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) im.setRGB(x, y, c); }

    static void blend(BufferedImage im, int x, int y, int c, double a) {
        if (x < 0 || y < 0 || x >= im.getWidth() || y >= im.getHeight() || a <= 0) return;
        int base = im.getRGB(x, y);
        if ((base >>> 24) == 0) { im.setRGB(x, y, ((int) (Math.min(1, a) * 255) << 24) | (c & 0xffffff)); return; }
        im.setRGB(x, y, (base & 0xff000000) | (mix(base, c, a) & 0xffffff));
    }

    static void rect(BufferedImage im, int x, int y, int w, int h, int c) { for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) px(im, xx, yy, c); }

    static BufferedImage img(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); }

    // ---------------------------------------------------------------- noise

    public static double hash(int x, int y, long s) {
        long h = s * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x8CB92BA72F3D8DD7L;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return (h >>> 11) / (double) (1L << 53);
    }

    public static double vnoise(double x, double y, long s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    public static double fbm(double x, double y, long s) { return vnoise(x, y, s) * .55 + vnoise(x * 2, y * 2, s + 1) * .3 + vnoise(x * 4, y * 4, s + 2) * .15; }

    /** Value noise whose lattice wraps every {@code period} cells, so textures tile seamlessly. */
    static double tnoise(double x, double y, int period, long s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        int x0 = Math.floorMod(xi, period), x1 = Math.floorMod(xi + 1, period), y0 = Math.floorMod(yi, period), y1 = Math.floorMod(yi + 1, period);
        double a = hash(x0, y0, s), b = hash(x1, y0, s), c = hash(x0, y1, s), d = hash(x1, y1, s);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    /** Tileable fbm over a 32px texture. */
    static double tfbm(int x, int y, long s) {
        return tnoise(x / 8.0, y / 8.0, 4, s) * .55 + tnoise(x / 4.0, y / 4.0, 8, s + 1) * .3 + tnoise(x / 2.0, y / 2.0, 16, s + 2) * .15;
    }

    // ---------------------------------------------------------------- sphere-union shading

    static void blobs(BufferedImage im, List<double[]> bl, int[] ramp, long seed, double grain, double bias, int cell) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) {
                double best = -1e9, second = -1e9, nx = 0, ny = 0, nz = 0;
                for (double[] b : bl) {
                    double dx = x + .5 - b[0], dy = y + .5 - b[1], r = b[2];
                    double d2 = dx * dx + dy * dy;
                    if (d2 >= r * r) continue;
                    double hgt = Math.sqrt(r * r - d2);
                    double z = hgt + b[1] * .35;
                    if (z > best) { second = best; best = z; nx = dx / r; ny = dy / r; nz = hgt / r; }
                    else if (z > second) second = z;
                }
                if (best < -1e8) continue;
                double lit = nx * LX + ny * LY + nz * LZ;
                double v = .5 + lit * .55 + bias + (hash(x / cell, y / cell, seed) - .5) * grain;
                if (best - second < 2.2) v -= .14;
                im.setRGB(x, y, sh(ramp, Math.max(.6, v * (ramp.length - 1))));
            }
    }

    static boolean grassy(Biome b) { return !(b == Biome.BEACH || b == Biome.DESERT || b == Biome.SNOW || b == Biome.CAVE || b == Biome.CANYON); }

    // ---------------------------------------------------------------- terrain textures (tileable 32x32)

    public static BufferedImage ground(Biome b) {
        BufferedImage im = img(T, T);
        int[] g = ramp(rgb(b.ground), 7);
        long s = b.ordinal() * 31L + 1;
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) im.setRGB(x, y, sh(g, 3 + (tfbm(x, y, s) - .5) * 2 + (hash(x / 2, y / 2, s) - .5) * .7));
        for (int i = 0; i < (grassy(b) ? 26 : 12); i++) {
            int x = (int) (hash(i, 1, s) * T), y = (int) (hash(i, 2, s) * T);
            if (grassy(b)) {
                px(im, x, y, g[1]);
                px(im, x, Math.floorMod(y - 1, T), g[4]);
                px(im, Math.floorMod(x + (i % 2 == 0 ? 1 : -1), T), Math.floorMod(y - 2, T), g[5]);
            } else {
                px(im, x, y, g[2]);
                px(im, Math.floorMod(x + 1, T), y, g[4]);
            }
        }
        return im;
    }

    public static BufferedImage dirt(Biome b) {
        BufferedImage im = img(T, T);
        int[] d = ramp(rgb(b.path), 7);
        long s = b.ordinal() * 37L + 3;
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) im.setRGB(x, y, sh(d, 3 + (tfbm(x, y, s) - .5) * 1.8));
        for (int i = 0; i < 3; i++) {
            int x = (int) (hash(i, 1, s) * 30), y = (int) (hash(i, 2, s) * 30);
            px(im, x, y, d[4]); px(im, x, y + 1, d[2]);
        }
        return im;
    }

    public static BufferedImage sand() {
        BufferedImage im = img(T, T);
        int[] d = ramp(0xe4d29b, 5);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) im.setRGB(x, y, sh(d, 2 + (tfbm(x, y, 11) - .5) * 1.4 + (hash(x, y, 12) > .93 ? 1 : 0)));
        return im;
    }

    public static BufferedImage water() {
        BufferedImage im = img(T, T);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) im.setRGB(x, y, mix(0xff5ab0e2, 0xff3a84cc, tfbm(x, y, 21)));
        return im;
    }

    /** Transparent wave highlights; four frames that loop and tile. */
    public static BufferedImage waves(int f) {
        BufferedImage im = img(T, T);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                double w = Math.sin(2 * Math.PI * (x / 32.0 + 2 * y / 32.0) + f * Math.PI / 2 + tnoise(x / 8.0, y / 8.0, 4, 23) * 3);
                if (w > .9) im.setRGB(x, y, 0x90ffffff);
                else if (w > .78) im.setRGB(x, y, 0x40ffffff);
            }
        return im;
    }

    public static BufferedImage caveWall() {
        BufferedImage im = img(T, T);
        int[] r = ramp(0x6a5a4a, 7);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                double n = tfbm(x, y, 31);
                double v = 2.6 + (n - .5) * 3;
                if (tnoise(x / 4.0, y / 4.0, 8, 33) > .72) v += 1.4;
                im.setRGB(x, y, sh(r, v));
            }
        return im;
    }

    public static BufferedImage tallGrass(Biome b, int frame) {
        BufferedImage im = img(T, T);
        int[] t = ramp(rgb(b.tall), 7);
        long seed = b.ordinal() * 41L + 7;
        for (int row = 0; row < 3; row++) {
            int base = 12 + row * 10;
            for (int i = 0; i < 7; i++) {
                double bx = 1 + i * 5 + hash(i, row, seed) * 3 - (row == 1 ? 2 : 0);
                int hgt = 9 + (int) (hash(i, row + 7, seed) * 6);
                double lean = (hash(i, row + 3, seed) - .5) * 4 + (frame == 1 ? (row % 2 == 0 ? 1.2 : -1.2) : 0);
                for (int y = 0; y < hgt; y++) {
                    double tt = y / (double) hgt;
                    double half = 2.2 * tt + .3;
                    double cx = bx + lean * (1 - tt);
                    for (int x = (int) Math.floor(cx - half); x <= Math.ceil(cx + half); x++) {
                        if (Math.abs(x + .5 - cx) > half) continue;
                        double v = 1.5 + (1 - tt) * 3.4 + row * .6 + (x + .5 < cx ? .7 : -.3);
                        px(im, x, base - hgt + y, sh(t, v));
                    }
                }
            }
        }
        PixelCanvas.selOut(im, .35);
        return im;
    }

    public static BufferedImage flowers(int variant) {
        BufferedImage im = img(T, T);
        int[] cols = {0xffe8505a, 0xfff6d04a, 0xfff6f6f6, 0xffb87af0};
        int c = cols[variant % cols.length];
        for (int i = 0; i < 4; i++) {
            int x = 4 + (int) (hash(i, variant, 51) * 24), y = 5 + (int) (hash(i, variant + 9, 51) * 22);
            px(im, x, y + 2, 0xff3f8a3a); px(im, x, y + 3, 0xff2f6a2a);
            px(im, x - 1, y, c); px(im, x + 1, y, c); px(im, x, y - 1, c); px(im, x, y + 1, mix(c, 0xff000000, .3));
            px(im, x, y, 0xfff8e070);
        }
        return im;
    }

    // ---------------------------------------------------------------- objects

    public static final int TREE_W = 44, TREE_H = 60;

    public static BufferedImage tree(Biome.Tree type, int variant) {
        long seed = type.ordinal() * 100L + variant;
        double jit = (hash(variant, 3, seed) - .5) * 3;
        if (type == Biome.Tree.BOULDER) return boulder(variant);
        BufferedImage im = img(TREE_W, TREE_H);
        int[] bark = ramp(0x7a5230, 6);
        int cx = TREE_W / 2;
        switch (type) {
            case PALM -> {
                for (int y = 18; y < TREE_H - 2; y++) {
                    double xo = cx + Math.sin(y * .09) * 3 - 2;
                    for (int x = (int) (xo - 3); x < xo + 3; x++) px(im, x, y, sh(bark, 3 - (x - xo) * .6 + ((y / 3) % 2 == 0 ? .6 : -.4)));
                }
                int[] leaf = ramp(0x4aa848, 7);
                for (int k = 0; k < 7; k++) {
                    double ang = Math.PI + k * Math.PI / 6 + jit * .05;
                    for (int s = 0; s < 18; s++) {
                        double t = s / 18.0;
                        int x = (int) (cx - 2 + Math.cos(ang) * s * 1.1), y = (int) (17 + Math.sin(ang) * s * .6 + t * t * 10);
                        for (int w = -1; w <= 1; w++) px(im, x, y + w, sh(leaf, 3 + (w < 0 ? 1.5 : 0) - t));
                    }
                }
                blobsInto(im, List.of(new double[] {cx - 2, 17, 4}), ramp(0x8a5a30, 5), seed, .2, 0, 1);
            }
            case CACTUS -> {
                int[] g = ramp(0x58a050, 7);
                List<double[]> parts = new ArrayList<>();
                for (int y = 14; y < TREE_H - 3; y += 3) parts.add(new double[] {cx, y, 6.5});
                for (int y = 30; y < 44; y += 3) parts.add(new double[] {cx - 11, y, 3.6});
                for (int x = 0; x < 9; x += 3) parts.add(new double[] {cx - 11 + x, 44, 3.6});
                for (int y = 24; y < 38; y += 3) parts.add(new double[] {cx + 11, y, 3.6});
                for (int x = 0; x < 9; x += 3) parts.add(new double[] {cx + 11 - x, 38, 3.6});
                blobsInto(im, parts, g, seed, .3, .05, 1);
                for (int i = 0; i < 18; i++) px(im, cx - 5 + (int) (hash(i, 1, seed) * 10), 14 + (int) (hash(i, 2, seed) * 40), 0xfff4f0d0);
            }
            case PINE, SNOW_PINE -> {
                rect(im, cx - 3, TREE_H - 14, 6, 13, bark[2]);
                rect(im, cx - 3, TREE_H - 14, 2, 13, bark[4]);
                List<double[]> tiers = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    double ty = TREE_H - 16 - i * 11, rx = 18 - i * 3.8, r = 7 - i * .6;
                    for (double k = -1; k <= 1.01; k += .5) tiers.add(new double[] {cx + k * rx * .7 + jit * .3, ty - Math.abs(k) * 2.5, r + (1 - Math.abs(k)) * 2.5});
                }
                tiers.add(new double[] {cx, 9, 5});
                int[] leaf = type == Biome.Tree.PINE ? ramp(0x2f6f4c, 7) : ramp(0x3a6a5a, 7);
                BufferedImage n = img(TREE_W, TREE_H);
                blobs(n, tiers, leaf, seed, .5, 0, 1);
                if (type == Biome.Tree.SNOW_PINE) {
                    int[] snow = ramp(0xe6eef6, 5);
                    for (int y = 1; y < TREE_H; y++)
                        for (int x = 0; x < TREE_W; x++)
                            if ((n.getRGB(x, y) >>> 24) != 0 && (n.getRGB(x, y - 1) >>> 24) == 0) {
                                for (int k = 0; k < 2; k++) if ((n.getRGB(x, y + k) >>> 24) != 0) n.setRGB(x, y + k, snow[k == 0 ? 4 : 2]);
                            }
                }
                draw(im, n, 0, 0);
            }
            default -> {
                for (int y = 34; y < TREE_H - 1; y++) {
                    double hw = 4 + (y > TREE_H - 8 ? (y - (TREE_H - 8)) * .8 : 0);
                    for (int x = (int) (cx - hw); x < cx + hw; x++) {
                        double t = (x + .5 - cx) / hw;
                        px(im, x, y, sh(bark, 3.2 - t * 1.8 + (vnoise(x * .9, y * .12, seed) - .5) * 1.6));
                    }
                }
                int leafRgb = type == Biome.Tree.ROUND_DARK ? 0x2f7a38 : type == Biome.Tree.WILLOW ? 0x5c8040 : 0x4e9d3d;
                float[] hsb = Color.RGBtoHSB((leafRgb >> 16) & 255, (leafRgb >> 8) & 255, leafRgb & 255, null);
                leafRgb = Color.HSBtoRGB(hsb[0] + (float) (jit * .006), hsb[1], hsb[2]) & 0xffffff;
                List<double[]> crown = new ArrayList<>(List.of(
                        new double[] {cx, 21 + jit * .3, 14.5}, new double[] {cx - 10, 26, 10}, new double[] {cx + 10, 26, 10},
                        new double[] {cx - 6, 13, 9.5}, new double[] {cx + 6, 13, 9.5}, new double[] {cx, 8, 7.5},
                        new double[] {cx, 31, 10.5}, new double[] {cx - 15, 19, 6.5}, new double[] {cx + 15, 19, 6.5}));
                BufferedImage leaves = img(TREE_W, TREE_H);
                blobs(leaves, crown, ramp(leafRgb, 7), seed, .55, -.02, 2);
                if (type == Biome.Tree.WILLOW) {
                    int[] w = ramp(leafRgb, 7);
                    for (int x = 6; x < TREE_W - 6; x += 3) for (int y = 28; y < 42 - (x % 2) * 4; y++) px(leaves, x, y, sh(w, 2 + (y % 3 == 0 ? 1 : 0)));
                }
                draw(im, leaves, 0, 0);
            }
        }
        PixelCanvas.selOut(im, .5);
        return im;
    }

    static void blobsInto(BufferedImage im, List<double[]> bl, int[] ramp, long seed, double grain, double bias, int cell) {
        BufferedImage n = img(im.getWidth(), im.getHeight());
        blobs(n, bl, ramp, seed, grain, bias, cell);
        draw(im, n, 0, 0);
    }

    static void draw(BufferedImage dst, BufferedImage src, int x, int y) {
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, x, y, null);
        g.dispose();
    }

    static BufferedImage boulder(int variant) {
        BufferedImage im = img(TREE_W, TREE_H);
        long seed = 900 + variant;
        double j = (hash(variant, 1, seed) - .5) * 4;
        blobsInto(im, List.of(new double[] {22, 44 + j * .3, 15}, new double[] {11, 49, 9}, new double[] {33, 48, 10}, new double[] {18 + j, 34, 9.5}, new double[] {29, 37, 8}),
                ramp(0x7a5e48, 7), seed, .35, -.22, 1);
        PixelCanvas.selOut(im, .5);
        return im;
    }

    public static BufferedImage rock(int variant) {
        BufferedImage im = img(T, T);
        long seed = 800 + variant;
        blobsInto(im, List.of(new double[] {16, 21, 10}, new double[] {9, 24, 6.5}, new double[] {23, 23, 7}, new double[] {14, 14, 6.5}), ramp(0x9b978e, 7), seed, .35, .05, 1);
        for (int i = 0; i < 6; i++) px(im, 15 + i / 2, 17 + i, ramp(0x9b978e, 7)[1]);
        PixelCanvas.selOut(im, .5);
        return im;
    }

    static final int[] WOOD = ramp(0xc4955c, 6);

    public static BufferedImage fence() {
        BufferedImage im = img(T, T);
        for (int px0 : new int[] {3, 25}) {
            for (int y = 8; y < 31; y++) for (int x = px0; x < px0 + 4; x++) px(im, x, y, sh(WOOD, x == px0 ? 4 : x == px0 + 3 ? 1.5 : 3));
            rect(im, px0, 7, 4, 1, WOOD[5]);
        }
        for (int ry : new int[] {13, 21})
            for (int x = 0; x < T; x++) {
                if ((x >= 3 && x < 7) || (x >= 25 && x < 29)) continue;
                px(im, x, ry, WOOD[5]); px(im, x, ry + 1, WOOD[3]); px(im, x, ry + 2, WOOD[3]); px(im, x, ry + 3, WOOD[1]);
            }
        PixelCanvas.selOut(im, .55);
        return im;
    }

    public static BufferedImage sign() {
        BufferedImage im = img(T, T);
        rect(im, 14, 18, 4, 13, WOOD[2]);
        rect(im, 14, 18, 1, 13, WOOD[4]);
        for (int y = 4; y < 20; y++)
            for (int x = 3; x < 29; x++) {
                int plank = (y - 4) / 5;
                double v = 3 + (y == 4 + plank * 5 ? 1.5 : 0) - (y == 8 + plank * 5 ? 1.5 : 0) + (vnoise(x * .4, y * 2, 3) - .5);
                px(im, x, y, sh(WOOD, v));
            }
        for (int l = 0; l < 2; l++) rect(im, 8, 9 + l * 5, 16 - l * 5, 2, WOOD[0]);
        PixelCanvas.selOut(im, .55);
        return im;
    }

    public static BufferedImage orb() {
        BufferedImage im = img(20, 20);
        int[] red = ramp(0xe04848, 7), white = ramp(0xe8e8ee, 7);
        for (int y = 0; y < 20; y++)
            for (int x = 0; x < 20; x++) {
                double dx = (x + .5 - 10) / 8.5, dy = (y + .5 - 10) / 8.5, d = dx * dx + dy * dy;
                if (d > 1) continue;
                double nz = Math.sqrt(1 - d), v = .5 + (dx * LX + dy * LY + nz * LZ) * .6;
                int c = y < 9 ? sh(red, v * 6) : y > 10 ? sh(white, v * 6) : 0xff303038;
                im.setRGB(x, y, c);
            }
        for (int y = 8; y < 12; y++) for (int x = 8; x < 12; x++) im.setRGB(x, y, (x == 8 || x == 11 || y == 8 || y == 11) ? 0xff303038 : 0xfff4f4f4);
        px(im, 6, 4, 0xffffffff); px(im, 7, 4, 0xffffffff); px(im, 6, 5, 0xffffffff);
        PixelCanvas.selOut(im, .6);
        return im;
    }

    // ---------------------------------------------------------------- buildings

    /** Roof shades are drawn from this key color so roofs can be recolored per town at load time. */
    public static final int ROOF_KEY = 0xc8564a;
    public static final int OVERHANG = 24;

    public static BufferedImage building(TileArt.Kind kind, int wt, int ht, int doorX) {
        int w = wt * T, h = ht * T + OVERHANG;
        long seed = kind.ordinal() * 7L + wt * 13L + ht;
        BufferedImage im = img(w, h);
        int roofTop = 10, roofBot = (int) (h * (kind == TileArt.Kind.GYM ? .4 : .48)), wallBot = h - 6;
        int[] roof = kind == TileArt.Kind.CENTER ? ramp(0xd84848, 7) : kind == TileArt.Kind.GYM ? ramp(0x5a6a8e, 7) : kind == TileArt.Kind.LAB ? ramp(0x8a94a6, 7) : ramp(ROOF_KEY, 7);
        int[] wall = kind == TileArt.Kind.GYM ? ramp(0xc8c4b8, 6) : kind == TileArt.Kind.LAB ? ramp(0xf4f4f0, 6) : ramp(0xf0e4cb, 6);
        int[] stone = ramp(0x9b978e, 7), brick = ramp(0xb06048, 6);
        if (kind == TileArt.Kind.HOUSE) // chimney behind the roof
            for (int y = 0; y < 30; y++) for (int x = w - 34; x < w - 22; x++) {
                boolean mortar = y % 6 == 5 || (x + (y / 6 % 2) * 4) % 8 == 7;
                px(im, x, y, mortar ? brick[1] : sh(brick, 3 + (hash(x / 8, y / 6, seed) - .5)));
            }
        for (int y = roofBot; y < wallBot; y++)
            for (int x = 4; x < w - 4; x++) {
                double v = 3 + (fbm(x * .15, y * .15, seed) - .5) * 1.2;
                if (kind == TileArt.Kind.GYM) v += ((y - roofBot) % 10 == 0 || (x + ((y - roofBot) / 10) * 8) % 16 == 0) ? -1.2 : 0;
                if (y < roofBot + 9) v -= (roofBot + 9 - y) * .22;
                px(im, x, y, sh(wall, v));
            }
        if (kind == TileArt.Kind.HOUSE || kind == TileArt.Kind.CENTER)
            for (int x : new int[] {4, w - 8}) for (int y = roofBot; y < wallBot; y++) for (int k = 0; k < 4; k++) px(im, x + k, y, sh(WOOD, 1.5 + (k == 0 ? 1 : 0)));
        for (int y = wallBot - 10; y < h; y++)
            for (int x = 4; x < w - 4; x++) {
                int yy = y - (wallBot - 10), row = yy / 5;
                boolean gap = yy % 5 == 4 || (x + row * 6) % 12 == 11;
                px(im, x, y, gap ? stone[1] : sh(stone, 3 + (hash((x + row * 6) / 12, row, seed) - .5) * 1.6 + (yy % 5 == 0 ? 1 : 0)));
            }
        // windows in every tile column except around the door
        for (int tx = 0; tx < wt; tx++) {
            if (Math.abs(tx - doorX) <= (kind == TileArt.Kind.CENTER ? 1 : 0)) continue;
            int wx = tx * T + 6, wy = roofBot + 14, ww = 20, wh = Math.min(24, wallBot - 12 - wy);
            if (wh < 10) continue;
            rect(im, wx - 2, wy - 2, ww + 4, wh + 4, WOOD[1]);
            for (int y = 0; y < wh; y++)
                for (int x = 0; x < ww; x++) {
                    int glass = mix(0xffcdeaf7, 0xff4f86b8, y / (double) wh * .9);
                    double diag = x + y * .6;
                    if (diag > 4 && diag < 7) glass = mix(glass, 0xffffffff, .6);
                    px(im, wx + x, wy + y, glass);
                }
            rect(im, wx + ww / 2 - 1, wy, 2, wh, WOOD[3]);
            rect(im, wx, wy + wh / 2 - 1, ww, 2, WOOD[3]);
            rect(im, wx - 3, wy + wh + 1, ww + 6, 3, WOOD[4]);
            if (kind == TileArt.Kind.HOUSE)
                for (int i = 0; i < 6; i++) {
                    int fx = wx + i * 3 + 1, fy = wy + wh - 2;
                    int c = i % 3 == 0 ? 0xffe8505a : i % 3 == 1 ? 0xfff6d04a : 0xfff3f3f3;
                    px(im, fx, fy, c); px(im, fx + 1, fy, c); px(im, fx, fy + 1, 0xff3f8a3a);
                }
        }
        // door
        int dx = doorX * T + 5, dw = 22, dTop = wallBot - 34;
        for (int y = dTop; y < h; y++)
            for (int x = dx; x < dx + dw; x++) {
                boolean frame = x < dx + 2 || x > dx + dw - 3 || y < dTop + 2;
                double v = frame ? 1.5 : 2.6 + ((x - dx - 2) % 5 == 0 ? -1 : 0) + (x < dx + 6 ? .6 : 0);
                int c = sh(WOOD, v);
                if (kind == TileArt.Kind.CENTER && !frame) c = mix(0xffcdeaf7, 0xff6f9fc8, (y - dTop) / 34.0);
                px(im, x, y, c);
            }
        if (kind == TileArt.Kind.CENTER) rect(im, dx + dw / 2 - 1, dTop + 2, 2, h - dTop - 2, 0xff707880);
        else { px(im, dx + dw - 6, dTop + 18, 0xfff8d860); px(im, dx + dw - 5, dTop + 18, 0xffc89a30); }
        // roof
        for (int y = roofTop; y < roofBot; y++) {
            double f = (y - roofTop) / (double) (roofBot - roofTop);
            int inset = (int) ((1 - f) * 8);
            int row = (y - roofTop) / 7, ry = (y - roofTop) % 7;
            for (int x = inset; x < w - inset; x++) {
                int col = (x + (row % 2) * 8) / 16, cx = (x + (row % 2) * 8) % 16;
                double v = 3.4 - f * 1.2 + (hash(col, row, seed) - .5) * 1.2;
                if (ry == 0) v += 1.6;
                if (ry == 6) v -= 2;
                if (cx == 15) v -= 1.4;
                if (x < inset + 2 || x > w - inset - 3) v -= 1.2;
                px(im, x, y, sh(roof, v));
            }
        }
        for (int x = 8; x < w - 8; x++) { px(im, x, roofTop - 2, roof[5]); px(im, x, roofTop - 1, roof[4]); px(im, x, roofTop, roof[2]); }
        for (int x = 0; x < w; x++) { px(im, x, roofBot, WOOD[1]); px(im, x, roofBot + 1, WOOD[0]); }
        // emblems
        int midX = w / 2, midY = (roofTop + roofBot) / 2;
        if (kind == TileArt.Kind.CENTER) {
            for (int y = -11; y <= 11; y++) for (int x = -11; x <= 11; x++) if (x * x + y * y <= 121) px(im, midX + x, midY + y, x * x + y * y > 100 ? 0xffb8b8c0 : 0xfff8f8f8);
            rect(im, midX - 3, midY - 8, 6, 16, 0xffe04848);
            rect(im, midX - 8, midY - 3, 16, 6, 0xffe04848);
        } else if (kind == TileArt.Kind.GYM || kind == TileArt.Kind.LAB) {
            String label = kind == TileArt.Kind.GYM ? "GYM" : "LAB";
            int lw = Font.width(label) * 2 + 12;
            rect(im, midX - lw / 2, midY - 9, lw, 18, 0xff303848);
            rect(im, midX - lw / 2 + 1, midY - 8, lw - 2, 16, kind == TileArt.Kind.GYM ? 0xffd8b040 : 0xff5878c8);
            Graphics2D g = im.createGraphics();
            Font.raw(g, label, midX - lw / 2 + 7, midY - 6, new Color(0x303848), 2);
            Font.raw(g, label, midX - lw / 2 + 6, midY - 7, Color.WHITE, 2);
            g.dispose();
        }
        PixelCanvas.selOut(im, .6);
        return im;
    }

    /** Recolors a house's key-colored roof to another hue. */
    public static BufferedImage recolorRoof(BufferedImage src, int targetRgb) {
        int[] from = ramp(ROOF_KEY, 7), to = ramp(targetRgb, 7);
        BufferedImage out = img(src.getWidth(), src.getHeight());
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getRGB(x, y);
                for (int i = 0; i < from.length; i++) if (c == from[i]) { c = to[i]; break; }
                out.setRGB(x, y, c);
            }
        return out;
    }

    // ---------------------------------------------------------------- interiors

    public static BufferedImage floor() {
        BufferedImage im = img(T, T);
        int[] w = ramp(0xd8b07a, 6);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int plank = y / 16, off = (plank % 2) * 16;
                boolean seam = y % 16 == 15 || (x + off) % 32 == 0;
                int board = (x + off) / 32;
                double tone = (hash(board, plank, 61) - .5) * .7;                  // each board a slightly different tone
                boolean streak = y % 16 == 5 + (int) (hash(board, plank, 62) * 6) && (x + off) % 32 > 4 && (x + off) % 32 < 26;
                double v = seam ? 1.3 : 2.9 + tone + (y % 16 == 0 ? .8 : 0) - (streak ? .6 : 0);
                im.setRGB(x, y, sh(w, v));
            }
        return im;
    }

    public static BufferedImage wall() {
        BufferedImage im = img(T, T);
        int[] p = ramp(0xf0e2c4, 6);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int c;
                if (y < 22) c = sh(p, 3 + (x % 8 < 4 ? .4 : -.4) + (y < 2 ? 1 : 0));
                else if (y == 22) c = WOOD[4];
                else c = sh(WOOD, 2.4 + (x % 8 == 0 ? -1 : 0) + (y == 31 ? -1.2 : 0));
                im.setRGB(x, y, c);
            }
        return im;
    }

    public static BufferedImage counter() {
        BufferedImage im = img(T, T);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int c = y < 10 ? sh(ramp(0xf0e6d6, 5), y == 0 ? 4 : 2.5) : y == 10 ? WOOD[0] : sh(WOOD, 2.5 + (x % 16 == 0 ? -1 : 0) + (y > 28 ? -1 : 0));
                im.setRGB(x, y, c);
            }
        return im;
    }

    public static BufferedImage mat() {
        BufferedImage im = floor();
        int[] r = ramp(0xb84848, 5);
        for (int y = 5; y < 27; y++) for (int x = 2; x < 30; x++) {
            boolean border = x < 4 || x > 27 || y < 7 || y > 24;
            px(im, x, y, sh(r, border ? 1 : 2.5 + ((x + y) % 4 == 0 ? .5 : 0)));
        }
        return im;
    }

    public static BufferedImage rug() {
        BufferedImage im = img(T, T);
        int[] r = ramp(0xc85a50, 6);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int d = Math.min(Math.min(x, y), Math.min(31 - x, 31 - y));
            im.setRGB(x, y, sh(r, d < 2 ? 1 : d < 4 ? 4 : (d / 3) % 2 == 0 ? 2.5 : 3.5));
        }
        return im;
    }

    public static BufferedImage table() {
        BufferedImage im = img(T, T);
        rect(im, 4, 18, 3, 12, WOOD[1]);
        rect(im, 25, 18, 3, 12, WOOD[1]);
        for (int y = 6; y < 20; y++) for (int x = 1; x < 31; x++) px(im, x, y, sh(WOOD, y < 8 ? 5 : y > 16 ? 1.5 : 3.5));
        PixelCanvas.selOut(im, .55);
        return im;
    }

    public static BufferedImage plant() {
        BufferedImage im = img(T, T);
        int[] pot = ramp(0xc06038, 6);
        for (int y = 20; y < 31; y++) for (int x = 9 - (30 - y) / 4; x < 23 + (30 - y) / 4; x++) px(im, x, y, sh(pot, y == 20 ? 4.5 : 2.5 - (x > 18 ? 1 : 0)));
        blobsInto(im, List.of(new double[] {16, 12, 8}, new double[] {9, 15, 5.5}, new double[] {23, 15, 5.5}, new double[] {16, 5, 5}), ramp(0x3f9a46, 7), 71, .5, .02, 2);
        PixelCanvas.selOut(im, .55);
        return im;
    }

    public static BufferedImage shelf() {
        BufferedImage im = img(T, T);
        rect(im, 1, 0, 30, 32, WOOD[1]);
        for (int r = 0; r < 3; r++) {
            rect(im, 3, 2 + r * 10, 26, 8, WOOD[0]);
            for (int x = 4; x < 28; x += 3) {
                int c = Color.HSBtoRGB((float) hash(x, r, 81), .55f, .75f) | 0xff000000;
                int bh = 5 + (int) (hash(x, r + 5, 81) * 3);
                rect(im, x, 2 + r * 10 + 8 - bh, 2, bh, c);
                px(im, x, 2 + r * 10 + 8 - bh, mix(c, 0xffffffff, .4));
            }
            rect(im, 1, 10 + r * 10, 30, 2, WOOD[3]);
        }
        PixelCanvas.selOut(im, .55);
        return im;
    }

    public static BufferedImage bed() {
        BufferedImage im = img(T, T);
        rect(im, 2, 0, 28, 32, WOOD[1]);
        for (int y = 2; y < 30; y++) for (int x = 4; x < 28; x++) px(im, x, y, y < 10 ? sh(ramp(0xf4f4f8, 5), y < 4 ? 4 : 2.5) : sh(ramp(0x5878d0, 6), 3 + (x < 8 ? .6 : 0) + (y % 6 == 0 ? -.8 : 0)));
        PixelCanvas.selOut(im, .55);
        return im;
    }

    // ---------------------------------------------------------------- characters (32x48, 4 directions x 3 frames)

    public static final int CW = 32, CH = 48;

    public static BufferedImage character(CharArt.Look l, int facing, int frame) {
        BufferedImage im = img(CW, CH);
        int[] S = ramp(l.skin(), 5), Hh = ramp(l.hair(), 5), C = ramp(l.cap() ? l.hat() : 0x808080, 5), J = ramp(l.shirt(), 5), P = ramp(l.pants(), 5), SH = ramp(0x3a3a46, 5);
        int[] BAG = ramp(0x8a5a3a, 5);
        int bob = frame == 0 ? 0 : 1;
        boolean side = facing == Actor.LEFT || facing == Actor.RIGHT;
        if (!side) {
            boolean back = facing == Actor.UP;
            // long hair hangs behind the body
            if (l.longHair() && back) for (int y = 12; y < 32; y++) for (int x = 7; x < 25; x++) if (Math.abs(x + .5 - 16) < 9 - (y > 26 ? (y - 26) : 0)) px(im, x, y + bob, sh(Hh, 2 + (x % 3 == 0 ? -.6 : 0)));
            if (l.longHair() && !back) for (int y = 12; y < 30; y++) for (int x = 6; x < 26; x++) if (Math.abs(x + .5 - 16) < 9.5) px(im, x, y + bob, sh(Hh, x < 16 ? 2.5 : 1.5));
            // legs: the forward leg reaches lower in a stride
            int lo = frame == 1 ? 1 : frame == 2 ? -1 : 0, ro = -lo;
            for (int y = 34; y < 44 + Math.max(0, lo); y++) rect(im, 10, y + bob, 5, 1, P[y < 36 ? 3 : 2]);
            for (int y = 34; y < 44 + Math.max(0, ro); y++) rect(im, 17, y + bob, 5, 1, P[1]);
            for (int k = 0; k < 4; k++) {
                int v = k == 0 ? 4 : k == 3 ? 0 : 2;
                rect(im, 9, 43 + Math.max(0, lo) + k + bob - (k == 3 ? 0 : 0), 7, 1, SH[v]);
                rect(im, 17, 43 + Math.max(0, ro) + k + bob, 7, 1, SH[Math.max(0, v - 1)]);
            }
            // torso and arms
            for (int y = 22; y < 36; y++) for (int x = 8; x < 24; x++) {
                if ((y == 22 || y == 35) && (x == 8 || x == 23)) continue;
                px(im, x, y + bob, sh(J, x < 11 ? 4 : x < 20 ? 3 : 2));
            }
            if (!back) { for (int y = 24; y < 35; y++) px(im, 16, y + bob, J[1]); rect(im, 12, 22 + bob, 9, 2, 0xfff4f4f4); }
            else { // backpack
                for (int y = 23; y < 34; y++) for (int x = 10; x < 23; x++) px(im, x, y + bob, sh(BAG, 2.5 + (x < 13 ? 1 : x > 20 ? -1 : 0) + (y == 23 ? 1 : 0)));
                rect(im, 10, 27 + bob, 13, 2, BAG[1]);
                rect(im, 15, 28 + bob, 3, 2, 0xffd8c060);
            }
            int la = frame == 1 ? 1 : frame == 2 ? -1 : 0;
            for (int y = 23; y < 34; y++) { rect(im, 5, y + bob + la, 3, 1, J[y < 25 ? 4 : 3]); rect(im, 24, y + bob - la, 3, 1, J[1]); }
            rect(im, 5, 34 + bob + la, 3, 2, S[3]);
            rect(im, 24, 34 + bob - la, 3, 2, S[1]);
            if (!back) for (int y = 22; y < 30; y++) { px(im, 11, y + bob, BAG[1]); px(im, 21, y + bob, BAG[1]); }
            // head
            for (int y = 6; y < 23; y++) for (int x = 7; x < 26; x++) {
                double dx = (x + .5 - 16.5) / 8.6, dy = (y + .5 - 14.5) / 8.2, d = dx * dx + dy * dy;
                if (d > 1) continue;
                double v = 2 + (dx * LX + dy * LY + Math.sqrt(1 - d) * LZ) * 2.6;
                px(im, x, y + bob, back ? sh(Hh, v - .3) : sh(S, v));
            }
            for (int y = 3; y < 16; y++) for (int x = 6; x < 27; x++) {   // hair
                double dx = (x + .5 - 16.5) / 10.2, dy = (y + .5 - 11) / 7.6;
                int fringe = l.cap() ? 11 : 8 + (x % 3 == 0 ? 1 : 0);
                if (dx * dx + dy * dy > 1 || (!back && y > fringe && x > 9 && x < 24)) continue;
                px(im, x, y + bob, sh(Hh, 2.6 - dx * 1.2 - (y - 8) * .15 + ((x + y) % 4 == 0 ? -1 : 0)));
            }
            if (back) for (int y = 14; y < 22; y++) for (int x = 8; x < 25; x++) if (Math.abs(x + .5 - 16.5) < 8.2 - (y - 14) * .4) px(im, x, y + bob, sh(Hh, 2 + ((x + y) % 4 == 0 ? -1 : 0)));
            if (l.cap()) {
                for (int y = 2; y < 11; y++) for (int x = 7; x < 26; x++) {
                    double dx = (x + .5 - 16.5) / 9.5, dy = (y + .5 - 10) / 8;
                    if (dx * dx + dy * dy > 1) continue;
                    px(im, x, y + bob, sh(C, 3 - dx * 1.4 - dy * .8));
                }
                if (!back) { for (int x = 6; x < 27; x++) { px(im, x, 10 + bob, C[1]); px(im, x, 11 + bob, C[0]); } rect(im, 15, 4 + bob, 3, 3, 0xfff4f4f4); }
                else rect(im, 12, 9 + bob, 9, 2, C[1]);
            }
            if (!back) {
                for (int ex : new int[] {11, 19}) {
                    rect(im, ex, 15 + bob, 3, 4, 0xfff8f8f8);
                    rect(im, ex + 1, 16 + bob, 2, 3, 0xff20202a);
                    px(im, ex + 1, 16 + bob, 0xfff8f8f8);
                }
                rect(im, 15, 20 + bob, 3, 1, mix(S[1], 0xff803030, .4));
                blend(im, 10, 19 + bob, 0xffff7088, .45);
                blend(im, 22, 19 + bob, 0xffff7088, .45);
            }
        } else {
            // drawn facing left, mirrored for right
            int step = frame == 1 ? 3 : frame == 2 ? -3 : 0;
            for (int y = 34; y < 44; y++) { rect(im, 15 - step, y + bob, 5, 1, P[1]); }       // far leg
            for (int k = 0; k < 4; k++) rect(im, 13 - step, 43 + k + bob, 7, 1, SH[k == 3 ? 0 : 1]);
            if (l.longHair()) for (int y = 12; y < 30; y++) for (int x = 15; x < 25; x++) if (x - 15 < 9 - Math.max(0, y - 24)) px(im, x, y + bob, sh(Hh, 2 + (y % 3 == 0 ? -.6 : 0)));
            for (int y = 23; y < 34; y++) for (int x = 19; x < 25; x++) px(im, x, y + bob, sh(BAG, 2.5 + (y == 23 ? 1 : 0) + (x > 22 ? -1 : 0)));   // backpack
            for (int y = 22; y < 36; y++) for (int x = 10; x < 21; x++) px(im, x, y + bob, sh(J, x < 13 ? 4 : x < 18 ? 3 : 2));
            for (int y = 34; y < 44; y++) rect(im, 13 + step, y + bob, 5, 1, P[y < 36 ? 3 : 2]);  // near leg
            for (int k = 0; k < 4; k++) rect(im, 11 + step, 43 + k + bob, 7, 1, SH[k == 0 ? 4 : k == 3 ? 0 : 2]);
            int arm = frame == 1 ? -2 : frame == 2 ? 2 : 0;
            for (int y = 23; y < 33; y++) rect(im, 14 + arm * (y - 23) / 10, y + bob, 3, 1, J[y < 25 ? 4 : 3]);
            rect(im, 14 + arm, 33 + bob, 3, 2, S[3]);
            for (int y = 6; y < 23; y++) for (int x = 6; x < 25; x++) {
                double dx = (x + .5 - 15.5) / 8.4, dy = (y + .5 - 14.5) / 8.2, d = dx * dx + dy * dy;
                if (d > 1) continue;
                double v = 1.9 + (dx * LX + dy * LY + Math.sqrt(1 - d) * LZ) * 1.7;   // profile faces the light: keep it softer
                px(im, x, y + bob, sh(S, v));
            }
            px(im, 6, 16 + bob, S[3]);                                  // nose
            for (int y = 3; y < 18; y++) for (int x = 7; x < 26; x++) {   // hair over the back of the head
                double dx = (x + .5 - 16.5) / 9.6, dy = (y + .5 - 11.5) / 8;
                if (dx * dx + dy * dy > 1) continue;
                if (x < 14 && y > (l.cap() ? 10 : 8)) continue;
                px(im, x, y + bob, sh(Hh, 2.6 - dx * .8 - (y - 8) * .15 + ((x + y) % 4 == 0 ? -1 : 0)));
            }
            if (l.cap()) {
                for (int y = 2; y < 11; y++) for (int x = 7; x < 25; x++) {
                    double dx = (x + .5 - 16) / 9, dy = (y + .5 - 10) / 8;
                    if (dx * dx + dy * dy > 1) continue;
                    px(im, x, y + bob, sh(C, 3 - dx * 1.2 - dy * .8));
                }
                for (int x = 2; x < 12; x++) { px(im, x, 10 + bob, C[1]); px(im, x, 11 + bob, C[0]); }
            }
            rect(im, 8, 15 + bob, 2, 3, 0xff20202a);   // eye in profile: dark with a glint
            px(im, 9, 15 + bob, 0xfff8f8f8);
            px(im, 8, 20 + bob, mix(S[1], 0xff803030, .4));
            px(im, 9, 20 + bob, mix(S[1], 0xff803030, .4));
            blend(im, 11, 19 + bob, 0xffff7088, .45);
            if (facing == Actor.RIGHT) {
                BufferedImage m = img(CW, CH);
                for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) m.setRGB(CW - 1 - x, y, im.getRGB(x, y));
                im = m;
            }
        }
        PixelCanvas.selOut(im, .55);
        return im;
    }
}
