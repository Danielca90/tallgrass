package tallgrass;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-made (PixelLab) creature art, looked up by species name like {@link Textures}:
 * <ol>
 *   <li>{@code ~/.tallgrass/resourcepack/creatures/sproutle.png} (your own edits),</li>
 *   <li>{@code /creatures/sproutle.png} inside the jar (copied from {@code art/creatures/001_sproutle.png} at build time),</li>
 *   <li>none: the species falls back to {@link SpriteGen}.</li>
 * </ol>
 * Art is high resolution: it is drawn 1:1 on the 480x320 framebuffer (see {@link HdSprite}), and a
 * 64x64 reduction is used where a classic sprite is expected.
 */
public final class CreatureArt {
    private CreatureArt() {}

    private static final Map<String, Optional<BufferedImage>> CACHE = new HashMap<>();
    private static final File USER_PACK = new File(System.getProperty("user.home"), ".tallgrass/resourcepack/creatures");

    /** The art cropped to its visible pixels, or null when this species has none. */
    public static synchronized BufferedImage get(String speciesName) {
        if (speciesName == null) return null;
        String key = speciesName.toLowerCase();
        return CACHE.computeIfAbsent(key, k -> Optional.ofNullable(load(k))).orElse(null);
    }

    private static BufferedImage load(String key) {
        BufferedImage im = null;
        try {
            File f = new File(USER_PACK, key + ".png");
            if (f.isFile()) im = ImageIO.read(f);
        } catch (Exception ignored) {}
        if (im == null) {
            try (InputStream in = CreatureArt.class.getResourceAsStream("/creatures/" + key + ".png")) {
                if (in != null) im = ImageIO.read(in);
            } catch (Exception ignored) {}
        }
        return im == null ? null : crop(im);
    }

    private static BufferedImage crop(BufferedImage src) {
        int x0 = src.getWidth(), y0 = src.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                if ((src.getRGB(x, y) >>> 24) > 16) {
                    x0 = Math.min(x0, x); y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                }
        if (x1 < 0) return null;
        Rectangle r = new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        BufferedImage out = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, -r.x, -r.y, null);
        g.dispose();
        return out;
    }

    /** A 64x64 classic-size version, bottom-centred, for screens that still draw at 240x160. */
    public static BufferedImage classic(BufferedImage art, boolean silhouette) {
        double s = Math.min(1, Math.min(62.0 / art.getWidth(), 62.0 / art.getHeight()));
        int w = Math.max(1, (int) Math.round(art.getWidth() * s)), h = Math.max(1, (int) Math.round(art.getHeight() * s));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scaled.createGraphics();
        sg.drawImage(art.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        sg.dispose();
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int ox = (64 - w) / 2, oy = 63 - h;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = scaled.getRGB(x, y);
                if ((p >>> 24) < 128) continue;   // keep hard pixel-art edges
                out.setRGB(ox + x, oy + y, silhouette ? 0xff383848 : p | 0xff000000);
            }
        return out;
    }
}
