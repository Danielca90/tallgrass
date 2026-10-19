package tallgrass;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.Map;

/**
 * Color ramps and GBA color depth.
 *
 * A ramp is a short run of shades for one material, darkest first. Shades are hue-shifted the way
 * pixel artists do it by hand: shadows drift toward blue and gain saturation, highlights drift toward
 * warm yellow and lose a little saturation. Everything is snapped to the GBA's 15-bit color.
 */
public final class Palette {
    private Palette() {}

    // ---------------------------------------------------------------- GBA 15-bit color

    /** Snaps one RGB value to 5 bits per channel (what the GBA can show), keeping alpha. */
    public static int gba(int argb) {
        int a = argb & 0xff000000;
        int r = q((argb >> 16) & 0xff), g = q((argb >> 8) & 0xff), b = q(argb & 0xff);
        return a | r << 16 | g << 8 | b;
    }

    private static int q(int c) {
        int v = c >> 3;              // 0..31
        return (v << 3) | (v >> 2);  // spread back to 0..255 so white stays white
    }

    public static Color gba(Color c) { return new Color(gba(c.getRGB()), true); }

    /** Quantizes a whole image in place. Works on INT_RGB/INT_ARGB images (fast path) and others (slow path). */
    public static void quantize(BufferedImage im) {
        if (im.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            int[] px = db.getData();
            for (int i = 0; i < px.length; i++) px[i] = gba(px[i]);
            return;
        }
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) im.setRGB(x, y, gba(im.getRGB(x, y)));
    }

    // ---------------------------------------------------------------- ramps

    public static final class Ramp {
        public final int[] shade; // ARGB, darkest first
        public final boolean flat;

        Ramp(int[] shade, boolean flat) {
            this.shade = shade;
            this.flat = flat;
        }

        public int size() { return shade.length; }
        public int get(int i) { return shade[Math.max(0, Math.min(shade.length - 1, i))]; }
        public int darkest() { return shade[0]; }
        public int lightest() { return shade[shade.length - 1]; }
        public int mid() { return shade[shade.length / 2]; }
        public Color color(int i) { return new Color(get(i), true); }
    }

    private static final Map<Long, Ramp> CACHE = new HashMap<>();

    /** A 5-shade hue-shifted ramp whose middle shade is {@code rgb}. */
    public static Ramp ramp(int rgb) { return ramp(rgb, 5); }

    public static Ramp ramp(int rgb, int n) {
        long key = ((long) (rgb & 0xffffff) << 8) | n;
        Ramp cached = CACHE.get(key);
        if (cached != null) return cached;
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, null);
        int[] out = new int[n];
        int mid = n / 2;
        for (int i = 0; i < n; i++) {
            double k = (i - mid) / (double) Math.max(1, mid);     // -1 (darkest) .. +1 (lightest)
            double h = hsb[0], s = hsb[1], b = hsb[2];
            if (k < 0) {
                h = toward(h, 0.66, -k * 0.07);                    // shadows lean blue
                s = Math.min(1, s + (-k) * 0.12 + (s < .08 ? 0 : .02));
                b = b * (1 + k * 0.42);
            } else if (k > 0) {
                h = toward(h, 0.15, k * 0.05);                     // highlights lean warm
                s = Math.max(0, s - k * 0.18);
                b = Math.min(1, b + (1 - b) * k * 0.5 + k * 0.05);
            }
            out[i] = gba(0xff000000 | (Color.HSBtoRGB((float) h, (float) s, (float) Math.max(0, b)) & 0xffffff));
        }
        Ramp r = new Ramp(out, false);
        CACHE.put(key, r);
        return r;
    }

    /** A single fixed color that shading leaves alone (eyes, pupils, glints). */
    public static Ramp flat(int rgb) { return new Ramp(new int[] {gba(0xff000000 | rgb)}, true); }

    private static double toward(double h, double target, double amount) {
        double d = ((target - h + 1.5) % 1.0) - 0.5; // shortest way round the color wheel
        double r = h + d * Math.min(1, amount * 4);
        return ((r % 1.0) + 1.0) % 1.0;
    }

    /** A darker, cooler version of a color — for outlines next to it (selective outlining). */
    public static int shadow(int argb, double amount) {
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, null);
        double h = toward(hsb[0], 0.66, amount * 0.08);
        double s = Math.min(1, hsb[1] + amount * 0.15);
        double b = hsb[2] * (1 - amount);
        return gba(0xff000000 | (Color.HSBtoRGB((float) h, (float) s, (float) b) & 0xffffff));
    }

    // ---------------------------------------------------------------- shared material ramps

    public static final Ramp WOOD = ramp(0x9a6a3c);
    public static final Ramp STONE = ramp(0x98948c);
    public static final Ramp WATER = ramp(0x4f88d8);
    public static final Ramp LEAF = ramp(0x358a3a);
    public static final Ramp LEAF_DARK = ramp(0x286a34);
    public static final Ramp PINE = ramp(0x3a7650);
    public static final Ramp SNOW = ramp(0xe0e8f4);
    public static final Ramp CACTUS = ramp(0x58a050);
    public static final Ramp ROOF_RED = ramp(0xd85048);
    public static final Ramp ORB_RED = ramp(0xe04848);
    public static final Ramp ORB_WHITE = ramp(0xe8e8e8);
}
