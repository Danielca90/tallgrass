package tallgrass;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * An indexed pixel-art canvas. Each pixel holds a material (a {@link Palette.Ramp}) rather than a color;
 * colors are only decided when the canvas is rendered, by these passes:
 *
 *  - cleanup: removes orphan pixels;
 *  - volume shading: a distance field turns any silhouette into a lit, rounded shape;
 *  - selective outline: each edge is outlined with a dark shade of its own material, lighter on the lit side;
 *  - inner lines: a darker seam where two materials meet;
 *  - optional ordered dithering between shades.
 */
public final class PixelCanvas {
    public final int w, h;
    private final int[] mat;                 // 0 = empty, otherwise index into ramps (1-based)
    private final int[] forced;              // -1 = shade automatically, otherwise fixed shade index
    private final List<Palette.Ramp> ramps = new ArrayList<>();

    // light from the top-left, slightly toward the viewer
    private static final double LX = -0.55, LY = -0.72, LZ = 0.62;

    public PixelCanvas(int w, int h) {
        this.w = w;
        this.h = h;
        mat = new int[w * h];
        forced = new int[w * h];
        java.util.Arrays.fill(forced, -1);
    }

    /** Registers a material and returns its id for painting. */
    public int material(Palette.Ramp r) {
        ramps.add(r);
        return ramps.size();
    }

    public boolean in(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h; }
    public int get(int x, int y) { return in(x, y) ? mat[y * w + x] : 0; }

    public void set(int x, int y, int m) { if (in(x, y)) { mat[y * w + x] = m; forced[y * w + x] = -1; } }

    /** Paints with a fixed shade (e.g. a highlight glint) that the shading pass won't change. */
    public void setShade(int x, int y, int m, int shade) { if (in(x, y)) { mat[y * w + x] = m; forced[y * w + x] = shade; } }

    public void rect(int x, int y, int rw, int rh, int m) {
        for (int yy = y; yy < y + rh; yy++) for (int xx = x; xx < x + rw; xx++) set(xx, yy, m);
    }

    public void ellipse(double cx, double cy, double rx, double ry, int m) {
        for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
            for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
                double dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry;
                if (dx * dx + dy * dy <= 1) set(x, y, m);
            }
    }

    /** Only repaints pixels that currently hold {@code onlyOn}. */
    public void ellipseOn(double cx, double cy, double rx, double ry, int m, int onlyOn) {
        for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
            for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
                double dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry;
                if (dx * dx + dy * dy <= 1 && get(x, y) == onlyOn) set(x, y, m);
            }
    }

    public void line(int x0, int y0, int x1, int y1, int m) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            set(x0, y0, m);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public void mirrorX() {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w / 2; x++) {
                mat[y * w + (w - 1 - x)] = mat[y * w + x];
                forced[y * w + (w - 1 - x)] = forced[y * w + x];
            }
    }

    /** Removes single pixels with no filled 4-neighbours (noise left over from primitives). */
    public void cleanup() {
        int[] copy = mat.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (copy[y * w + x] == 0) continue;
                int n = 0;
                if (x > 0 && copy[y * w + x - 1] != 0) n++;
                if (x < w - 1 && copy[y * w + x + 1] != 0) n++;
                if (y > 0 && copy[(y - 1) * w + x] != 0) n++;
                if (y < h - 1 && copy[(y + 1) * w + x] != 0) n++;
                if (n == 0) mat[y * w + x] = 0;
            }
    }

    // ---------------------------------------------------------------- rendering

    public static final class Style {
        public boolean shade = true, outline = true, innerLines = true, dither = false;
        public double roundness = 1.0;  // how domed shapes look (1 = sphere-like)
        public double outlineDark = .45;
        public double light = 0;        // shifts every lit value: negative = darker overall
        public static Style sprite() { return new Style(); }
        public static Style flat() { Style s = new Style(); s.shade = false; return s; }
    }

    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    /** Renders at 1x into a new w x h ARGB image (leave a 1px margin around shapes for the outline). */
    public BufferedImage render(Style st) {
        int pad = 0;
        int W = w, H = h;
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        double[] height = st.shade ? heightField(st.roundness) : null;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int m = mat[y * w + x];
                if (m == 0) continue;
                Palette.Ramp r = ramps.get(m - 1);
                int shade;
                if (forced[y * w + x] >= 0) shade = forced[y * w + x];
                else if (r.flat || !st.shade) shade = r.size() / 2;
                else {
                    double nx = (hAt(height, x - 1, y) - hAt(height, x + 1, y)) * .5;
                    double ny = (hAt(height, x, y - 1) - hAt(height, x, y + 1)) * .5;
                    double nz = 0.42;
                    double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    double lit = (nx * LX + ny * LY + nz * LZ) / len;           // -1..1
                    double v = 0.5 + lit * 0.62 + st.light;                         // 0..1 mostly
                    if (st.dither) v += (BAYER[y & 3][x & 3] / 16.0 - .47) * (0.9 / r.size());
                    shade = (int) Math.round(Math.max(0, Math.min(1, v)) * (r.size() - 1));
                    shade = Math.max(1, shade); // keep the darkest shade for outlines
                }
                // seam where two materials meet: darken this pixel if the neighbour below/right is a different material
                if (st.innerLines && !r.flat) {
                    int below = get(x, y + 1), right = get(x + 1, y);
                    if ((below != 0 && below != m && !ramps.get(below - 1).flat) || (right != 0 && right != m && !ramps.get(right - 1).flat))
                        shade = Math.max(0, Math.min(shade, 1));
                }
                im.setRGB(x + pad, y + pad, r.get(shade));
            }
        if (st.outline) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if (get(x, y) != 0) continue;
                    // which neighbour are we outlining, and is it on the shadow side?
                    int src = 0;
                    boolean shadowSide = false;
                    if (get(x - 1, y) != 0) { src = get(x - 1, y); shadowSide = true; }
                    else if (get(x, y - 1) != 0) { src = get(x, y - 1); shadowSide = true; }
                    else if (get(x + 1, y) != 0) src = get(x + 1, y);
                    else if (get(x, y + 1) != 0) src = get(x, y + 1);
                    if (src == 0) continue;
                    Palette.Ramp r = ramps.get(src - 1);
                    int base = r.flat ? 0xff202028 : r.darkest();
                    int c = Palette.shadow(base, shadowSide ? st.outlineDark + .2 : st.outlineDark);
                    im.setRGB(x + pad, y + pad, c);
                }
        }
        return im;
    }

    private double hAt(double[] hf, int x, int y) { return in(x, y) ? hf[y * w + x] : 0; }

    /** Height of each filled pixel from its distance to the silhouette edge, shaped like a dome. */
    private double[] heightField(double roundness) {
        int[] d = new int[w * h];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (mat[i] == 0) { d[i] = 0; continue; }
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1
                        || mat[i - 1] == 0 || mat[i + 1] == 0 || mat[i - w] == 0 || mat[i + w] == 0;
                if (edge) { d[i] = 1; q.add(new int[] {x, y}); } else d[i] = Integer.MAX_VALUE;
            }
        int max = 1;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int cd = d[p[1] * w + p[0]];
            max = Math.max(max, cd);
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : nb) {
                int x = p[0] + o[0], y = p[1] + o[1];
                if (!in(x, y)) continue;
                int i = y * w + x;
                if (mat[i] != 0 && d[i] > cd + 1) { d[i] = cd + 1; q.add(new int[] {x, y}); }
            }
        }
        double R = Math.max(2, max * 0.9);
        double[] hf = new double[w * h];
        for (int i = 0; i < hf.length; i++) {
            if (mat[i] == 0) continue;
            double t = Math.min(1, d[i] / R);
            hf[i] = Math.sqrt(1 - (1 - t) * (1 - t)) * R * 0.85 * roundness;
        }
        return hf;
    }

    /** Scales an image by an integer factor with nearest-neighbour. */
    public static BufferedImage scale(BufferedImage src, int s) {
        if (s == 1) return src;
        BufferedImage out = new BufferedImage(src.getWidth() * s, src.getHeight() * s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, src.getRGB(x / s, y / s));
        return out;
    }

    /**
     * Selective outline for an already-colored ARGB image: every transparent pixel next to a colored one
     * becomes a dark, cool version of that neighbour. Used for characters drawn with plain fills.
     */
    public static void selOut(BufferedImage im, double dark) {
        int W = im.getWidth(), H = im.getHeight();
        int[] src = im.getRGB(0, 0, W, H, null, 0, W);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                if ((src[y * W + x] >>> 24) != 0) continue;
                int n = 0;
                boolean shadowSide = false;
                if (x > 0 && (src[y * W + x - 1] >>> 24) != 0) { n = src[y * W + x - 1]; shadowSide = true; }
                else if (y > 0 && (src[(y - 1) * W + x] >>> 24) != 0) { n = src[(y - 1) * W + x]; shadowSide = true; }
                else if (x < W - 1 && (src[y * W + x + 1] >>> 24) != 0) n = src[y * W + x + 1];
                else if (y < H - 1 && (src[(y + 1) * W + x] >>> 24) != 0) n = src[(y + 1) * W + x];
                if (n == 0) continue;
                im.setRGB(x, y, Palette.shadow(n, shadowSide ? dark + .15 : dark));
            }
    }
}
