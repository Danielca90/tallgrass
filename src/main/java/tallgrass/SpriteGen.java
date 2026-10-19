package tallgrass;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Procedural creature sprites. Each species is built on a 32x32 grid from body primitives
 * (head, torso, limbs, wings, coils...) plus seeded features (ears, horns, spots), then shaded
 * and outlined. Front sprites are 64x64 (2x), like GBA battle sprites.
 */
public final class SpriteGen {
    public enum View { FRONT, BACK, ICON, SILHOUETTE }

    private static final int N = 32;
    private static final int BODY = 1, BELLY = 2, ACCENT = 3, EYE = 4, PUPIL = 5, DARK = 6;

    private SpriteGen() {}

    private static final class Grid {
        final int[][] g = new int[N][N];
        void ell(double cx, double cy, double rx, double ry, int lab, boolean over) {
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    double dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry;
                    if (dx * dx + dy * dy <= 1) put(x, y, lab, over);
                }
        }
        void ellOnly(double cx, double cy, double rx, double ry, int lab, int onlyOn) {
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    double dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry;
                    if (dx * dx + dy * dy <= 1 && g[y][x] == onlyOn) g[y][x] = lab;
                }
        }
        void rect(int x, int y, int w, int h, int lab, boolean over) {
            for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) put(xx, yy, lab, over);
        }
        void tri(double ax, double ay, double bx, double by, double cx, double cy, int lab, boolean over) {
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    double px = x + .5, py = y + .5;
                    double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
                    double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
                    double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
                    boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                    if (!(neg && pos)) put(x, y, lab, over);
                }
        }
        void put(int x, int y, int lab, boolean over) {
            if (x < 0 || y < 0 || x >= N || y >= N) return;
            if (over || g[y][x] == 0) g[y][x] = lab;
        }
        int at(int x, int y) { return (x < 0 || y < 0 || x >= N || y >= N) ? 0 : g[y][x]; }
        /** Mirror the left half onto the right so every creature is symmetric. */
        void mirror() { for (int y = 0; y < N; y++) for (int x = 0; x < N / 2; x++) g[y][N - 1 - x] = g[y][x]; }
    }

    private static Grid build(Species sp) {
        Rng r = new Rng(sp.spriteSeed);
        Grid G = new Grid();
        double s = 0.82 + sp.stage * 0.12;
        double c = 16;
        double headY, eyeDx, headR;
        switch (sp.shape) {
            case BIPED -> {
                headY = 11 - sp.stage; headR = 6.2 * s;
                G.ell(c - 4 * s, 28.2, 2.6, 1.8, BODY, false);
                G.ell(c + 4 * s, 28.2, 2.6, 1.8, BODY, false);
                G.ell(c, 20.5, 6.3 * s, 7.2 * s, BODY, false);
                G.ell(c - 7.6 * s, 18.5, 2.2, 3.6, BODY, false);
                G.ell(c + 7.6 * s, 18.5, 2.2, 3.6, BODY, false);
                G.ellOnly(c, 21.5, 4 * s, 5 * s, BELLY, BODY);
                G.ell(c, headY, headR, 5.8 * s, BODY, true);
                eyeDx = 2.8 * s;
            }
            case QUAD -> {
                headY = 13 - sp.stage * .5; headR = 6.6 * s;
                for (double lx : new double[] {-8 * s, -3.2 * s, 3.2 * s, 8 * s}) G.rect((int) Math.round(c + lx - 1.5), 24, 3, 6, BODY, false);
                G.ell(c, 21.5, 10.5 * s, 6 * s, BODY, false);
                G.ellOnly(c, 23.5, 6 * s, 3 * s, BELLY, BODY);
                G.ell(c, headY, headR, 6 * s, BODY, true);
                G.ellOnly(c, headY + 3, 3.4 * s, 2.2 * s, BELLY, BODY);
                eyeDx = 3.2 * s;
            }
            case BLOB -> {
                headY = 15; headR = 10 * s;
                G.ell(c - 5 * s, 28, 3, 1.8, BODY, false);
                G.ell(c + 5 * s, 28, 3, 1.8, BODY, false);
                G.ell(c, 18.5, 10.5 * s, 10 * s, BODY, true);
                G.ellOnly(c, 22, 6.5 * s, 5.5 * s, BELLY, BODY);
                eyeDx = 3.6 * s;
            }
            case SERPENT -> {
                headY = 9.5; headR = 5.6 * s;
                G.ell(c, 23, 11 * s, 6.5 * s, BODY, false);
                G.ellOnly(c, 23, 5.2 * s, 2.6 * s, 0, BODY);
                G.ellOnly(c, 25.5, 8 * s, 2 * s, BELLY, BODY);
                G.rect((int) (c - 2.5 * s), 12, (int) Math.ceil(5 * s), 9, BODY, false);
                G.ellOnly(c, 16, 1.6 * s, 4, BELLY, BODY);
                G.ell(c, headY, headR, 5 * s, BODY, true);
                eyeDx = 2.6 * s;
            }
            case BIRD -> {
                headY = 10.5; headR = 5.2 * s;
                G.tri(c - 4, 13, c - 15.5, 7 - sp.stage * 2, c - 12, 23, ACCENT, false);
                G.tri(c + 4, 13, c + 15.5, 7 - sp.stage * 2, c + 12, 23, ACCENT, false);
                G.tri(c - 4, 14, c - 13.5, 9 - sp.stage * 2, c - 10.5, 21, BODY, true);
                G.tri(c + 4, 14, c + 13.5, 9 - sp.stage * 2, c + 10.5, 21, BODY, true);
                G.rect((int) c - 4, 26, 2, 4, DARK, false);
                G.rect((int) c + 2, 26, 2, 4, DARK, false);
                G.ell(c, 19.5, 6 * s, 7 * s, BODY, true);
                G.ellOnly(c, 21, 3.8 * s, 4.6 * s, BELLY, BODY);
                G.ell(c, headY, headR, 5 * s, BODY, true);
                G.tri(c - 1.6, headY + 1.5, c + 1.6, headY + 1.5, c, headY + 4.5, ACCENT, true);
                eyeDx = 2.6 * s;
            }
            default -> { // FLOATER
                headY = 12.5; headR = 8.6 * s;
                for (double k : new double[] {-6 * s, -2 * s, 2 * s, 6 * s})
                    for (int y = 19; y < 30; y++) {
                        int x = (int) Math.round(c + k + Math.sin(y * .7 + k) * 1.1 - 1);
                        G.rect(x, y, 2, 1, ACCENT, false);
                    }
                G.ell(c, headY, headR, 8 * s, BODY, true);
                G.ellOnly(c, headY + 4, 5 * s, 3 * s, BELLY, BODY);
                eyeDx = 3.4 * s;
            }
        }

        // Seeded features: ears, horns, crest, antennae, spots, tail tufts.
        int top = (int) Math.round(headY - (sp.shape == Species.Shape.BLOB ? 10 * s : 5.6 * s));
        int feature = r.nextInt(5);
        if (sp.stage >= 1 && feature == 4) feature = r.nextInt(4);
        switch (feature) {
            case 0 -> { // pointy ears
                double ex = headR * .62;
                G.tri(c - ex - 2.5, top + 3, c - ex + 1.5, top + 2, c - ex - 1.5, top - 4 - sp.stage * 2, BODY, false);
                G.tri(c + ex + 2.5, top + 3, c + ex - 1.5, top + 2, c + ex + 1.5, top - 4 - sp.stage * 2, BODY, false);
                G.tri(c - ex - 1.2, top + 2, c - ex + .4, top + 2, c - ex - 1.2, top - 2 - sp.stage * 2, ACCENT, true);
                G.tri(c + ex + 1.2, top + 2, c + ex - .4, top + 2, c + ex + 1.2, top - 2 - sp.stage * 2, ACCENT, true);
            }
            case 1 -> { // horns
                double ex = headR * .45;
                G.tri(c - ex - 1.5, top + 2, c - ex + 1.5, top + 2, c - ex - 2.5, top - 4 - sp.stage * 3, ACCENT, false);
                G.tri(c + ex + 1.5, top + 2, c + ex - 1.5, top + 2, c + ex + 2.5, top - 4 - sp.stage * 3, ACCENT, false);
            }
            case 2 -> { // crest
                G.tri(c - 2.5, top + 2, c + 2.5, top + 2, c, top - 5 - sp.stage * 2, ACCENT, false);
                G.tri(c - 5, top + 3, c - 1, top + 2, c - 4, top - 2 - sp.stage, ACCENT, false);
                G.tri(c + 5, top + 3, c + 1, top + 2, c + 4, top - 2 - sp.stage, ACCENT, false);
            }
            case 3 -> { // round ears
                double ex = headR * .7;
                G.ell(c - ex, top + 1, 2.8, 2.8, BODY, false);
                G.ell(c + ex, top + 1, 2.8, 2.8, BODY, false);
                G.ellOnly(c - ex, top + 1, 1.4, 1.4, ACCENT, BODY);
                G.ellOnly(c + ex, top + 1, 1.4, 1.4, ACCENT, BODY);
            }
            default -> { // antennae
                G.rect((int) (c - 3), top - 4, 1, 5, DARK, false);
                G.rect((int) (c + 2), top - 4, 1, 5, DARK, false);
                G.ell(c - 2.5, top - 5, 1.6, 1.6, ACCENT, false);
                G.ell(c + 2.5, top - 5, 1.6, 1.6, ACCENT, false);
            }
        }
        if (r.chance(.5) || sp.stage == 2) { // spots / stripes
            int n = 1 + r.nextInt(2) + sp.stage;
            for (int i = 0; i < n; i++) {
                double sx = c - 2 - r.nextDouble() * 6, sy = headY + 6 + r.nextDouble() * 12;
                G.ellOnly(sx, sy, 1.3, 1.3, ACCENT, BODY);
                G.ellOnly(2 * c - sx, sy, 1.3, 1.3, ACCENT, BODY);
            }
        }
        // Eyes
        int ey = (int) Math.round(headY - .5);
        int ex = (int) Math.round(c - eyeDx) - 1;
        boolean fierce = sp.stage > 0 && r.chance(.6);
        G.rect(ex - 1, ey - 1, 3, 4, EYE, true);
        G.rect(ex, ey, 2, 3, PUPIL, true);
        G.put(ex, ey, EYE, true);
        if (fierce) G.rect(ex - 1, ey - 2, 3, 1, DARK, true);
        // mouth
        if (sp.shape != Species.Shape.BIRD) G.rect((int) c - 2, ey + 4, 2, 1, DARK, true);
        G.mirror();
        return G;
    }

    public static BufferedImage render(Species sp, View view) {
        Grid G = build(sp);
        PixelCanvas c = new PixelCanvas(N, N);
        boolean sil = view == View.SILHOUETTE, back = view == View.BACK;
        int body, belly, accent, eye, pupil, dark;
        if (sil) {
            body = belly = accent = eye = pupil = dark = c.material(Palette.flat(0x383848));
        } else {
            body = c.material(Palette.ramp(sp.c1));
            belly = c.material(Palette.ramp(sp.c2));
            accent = c.material(Palette.ramp(sp.c3));
            eye = c.material(Palette.flat(0xf8f8f8));
            pupil = c.material(Palette.flat(0x181820));
            dark = c.material(Palette.ramp(Gfx.darker(new java.awt.Color(sp.c1), .5).getRGB() & 0xffffff));
        }
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                int lab = G.g[y][x];
                if (lab == 0) continue;
                if (back && (lab == BELLY || lab == EYE || lab == PUPIL || lab == DARK)) lab = BODY;
                c.set(x, y, switch (lab) {
                    case BODY -> body;
                    case BELLY -> belly;
                    case ACCENT -> accent;
                    case EYE -> eye;
                    case PUPIL -> pupil;
                    default -> dark;
                });
            }
        c.cleanup();
        PixelCanvas.Style style = sil ? PixelCanvas.Style.flat() : PixelCanvas.Style.sprite();
        style.outlineDark = back ? .5 : .45;
        style.innerLines = !sil;
        BufferedImage im = c.render(style);
        return view == View.ICON ? im : PixelCanvas.scale(im, 2);
    }
}
