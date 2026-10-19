package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-renders one area for the HD overworld: a ground image with blended terrain edges, baked shadows and
 * flowers, wave overlays for water, and the list of static sprites (trees, rocks, buildings, furniture)
 * that get depth-sorted with characters every frame.
 */
public final class AreaRenderer {
    public static final int T = HdArt.T;

    public record Sprite(BufferedImage img, int x, int y, int baseY) {}

    public final Area area;
    public final BufferedImage ground;
    public final BufferedImage[] waves;
    public final List<Sprite> statics = new ArrayList<>();

    private static final Map<String, BufferedImage> ROOFS = new HashMap<>();

    public AreaRenderer(Area a) {
        this.area = a;
        ground = new BufferedImage(a.w * T, a.h * T, BufferedImage.TYPE_INT_ARGB);
        boolean hasWater = false;
        for (int y = 0; y < a.h; y++) for (int x = 0; x < a.w; x++) if (a.at(x, y) == Tile.WATER) hasWater = true;
        waves = hasWater ? new BufferedImage[4] : null;
        if (a.interior) buildInterior();
        else buildOutdoor();
        buildStatics();
    }

    // ---------------------------------------------------------------- terrain categories

    private static final int G = 0, P = 1, W = 2, S = 3, C = 4;

    private int cat(int tx, int ty) {
        tx = Math.max(0, Math.min(area.w - 1, tx));
        ty = Math.max(0, Math.min(area.h - 1, ty));
        return switch (area.at(tx, ty)) {
            case PATH, DOOR -> area.cave ? G : P;
            case WATER -> W;
            case SAND -> S;
            case CAVE_WALL -> C;
            default -> G;
        };
    }

    private double field(int x, int y, int c) {
        double u = x / (double) T - .5, v = y / (double) T - .5;
        int i0 = (int) Math.floor(u), j0 = (int) Math.floor(v);
        double fx = u - i0, fy = v - j0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = cat(i0, j0) == c ? 1 : 0, b = cat(i0 + 1, j0) == c ? 1 : 0, cc = cat(i0, j0 + 1) == c ? 1 : 0, d = cat(i0 + 1, j0 + 1) == c ? 1 : 0;
        double f = (a + (b - a) * fx) * (1 - fy) + (cc + (d - cc) * fx) * fy;
        return f + (HdArt.fbm(x * .09, y * .09, area.seed + c) - .5) * .32;
    }

    private static int tex(BufferedImage im, int x, int y) { return im.getRGB(Math.floorMod(x, im.getWidth()), Math.floorMod(y, im.getHeight())); }

    private void buildOutdoor() {
        Area a = area;
        BufferedImage gTex = Textures.get(Textures.ground(a.biome)), pTex = Textures.get(Textures.path(a.biome)), sTex = Textures.get("sand"),
                wTex = Textures.get("water"), cTex = Textures.get("cave_wall");
        int W2 = ground.getWidth(), H2 = ground.getHeight();
        for (int ty = 0; ty < a.h; ty++)
            for (int tx = 0; tx < a.w; tx++) {
                int c0 = cat(tx, ty);
                boolean uniform = true;
                for (int dy = -1; dy <= 1 && uniform; dy++) for (int dx = -1; dx <= 1; dx++) if (cat(tx + dx, ty + dy) != c0) { uniform = false; break; }
                for (int py = ty * T; py < ty * T + T; py++)
                    for (int px = tx * T; px < tx * T + T; px++) {
                        int col;
                        if (c0 == C) col = tex(cTex, px, py);
                        else if (uniform) col = c0 == P ? tex(pTex, px, py) : c0 == W ? water(wTex, px, py, 1) : c0 == S ? tex(sTex, px, py) : tex(gTex, px, py);
                        else col = blended(px, py, gTex, pTex, sTex, wTex);
                        double mod = .95 + HdArt.fbm(px * .015, py * .015, a.seed + 9) * .1;   // broad light/dark patches
                        ground.setRGB(px, py, shade(col, mod));
                    }
            }
        // cave walls: lit top edge, shadow on the floor below
        if (a.cave)
            for (int ty = 0; ty < a.h; ty++) for (int tx = 0; tx < a.w; tx++) {
                if (cat(tx, ty) != C) continue;
                if (cat(tx, ty - 1) != C) for (int x = 0; x < T; x++) { put(tx * T + x, ty * T, 0xffa89884); put(tx * T + x, ty * T + 1, 0xff8a7a68); }
                if (ty + 1 < a.h && cat(tx, ty + 1) != C) for (int k = 0; k < 8; k++) for (int x = 0; x < T; x++) darken(tx * T + x, (ty + 1) * T + k, .35 * (1 - k / 8.0));
            }
        // flowers and baked shadows
        for (int ty = 0; ty < a.h; ty++)
            for (int tx = 0; tx < a.w; tx++) {
                Tile t = a.at(tx, ty);
                if (t == Tile.FLOWER) paste(Textures.get("flowers_" + (a.variant(tx, ty))), tx * T, ty * T);
                if (t == Tile.TREE) shadow(tx * T + 18, ty * T + 29, 17, 6, .38);
                if (t == Tile.ROCK) shadow(tx * T + 17, ty * T + 29, 12, 4, .35);
                if (t == Tile.SIGN || t == Tile.FENCE) shadow(tx * T + 17, ty * T + 30, 12, 3, .25);
            }
        for (Area.Building b : a.buildings) shadow(b.x() * T + b.w() * T / 2.0 + 4, (b.y() + b.h()) * T + 2, b.w() * T / 2.0 + 4, 7, .4);
        if (waves != null)
            for (int f = 0; f < 4; f++) {
                waves[f] = new BufferedImage(W2, H2, BufferedImage.TYPE_INT_ARGB);
                BufferedImage wv = Textures.get("waves_" + f);
                for (int ty = 0; ty < a.h; ty++)
                    for (int tx = 0; tx < a.w; tx++) {
                        if (a.at(tx, ty) != Tile.WATER) continue;
                        for (int py = ty * T; py < ty * T + T; py++)
                            for (int px = tx * T; px < tx * T + T; px++)
                                if (field(px, py, W) > .58) waves[f].setRGB(px, py, tex(wv, px, py));
                    }
            }
    }

    private int water(BufferedImage wTex, int x, int y, double depth) {
        return HdArt.mix(tex(wTex, x, y), 0xff245aa4, Math.min(1, depth) * .55);
    }

    private int blended(int x, int y, BufferedImage g, BufferedImage p, BufferedImage s, BufferedImage w) {
        double fw = field(x, y, W);
        if (fw > .5) {
            int c = water(w, x, y, (fw - .5) * 2.6);
            if (fw < .56) c = HdArt.mix(0xffeaf7ff, c, (fw - .5) / .06 * .7);   // foam line
            return c;
        }
        if (fw > .42) {
            int c = tex(s, x, y);
            return fw > .48 ? HdArt.mix(c, 0xff8f7f60, .35) : c;              // wet sand rim
        }
        double fp = field(x, y, P);
        if (fp > .5) {
            int c = tex(p, x, y);
            return fp < .56 ? HdArt.mix(c, 0xff000000, .16) : c;             // worn edge
        }
        if (field(x, y, S) > .5) return tex(s, x, y);
        int c = tex(g, x, y);
        if (fp > .43) c = HdArt.mix(c, 0xff1a2a10, Math.min(.22, (fp - .43) * 3)); // grass darkens at the path edge
        return c;
    }

    private void buildInterior() {
        Area a = area;
        for (int ty = 0; ty < a.h; ty++)
            for (int tx = 0; tx < a.w; tx++) {
                Tile t = a.at(tx, ty);
                String base = switch (t) {
                    case WALL, SHELF -> ty < 2 ? "wall" : "floor";
                    case COUNTER -> "counter";
                    case MAT -> "mat";
                    case RUG -> "rug";
                    case VOID -> null;
                    default -> "floor";
                };
                if (base != null) paste(Textures.get(base), tx * T, ty * T);
                if (t == Tile.WALL && ty == 1) for (int k = 0; k < 6; k++) for (int x = 0; x < T; x++) darken(tx * T + x, 2 * T + k, .3 * (1 - k / 6.0));
            }
    }

    private void buildStatics() {
        Area a = area;
        for (int ty = 0; ty < a.h; ty++)
            for (int tx = 0; tx < a.w; tx++) {
                Tile t = a.at(tx, ty);
                int base = ty * T + T;
                switch (t) {
                    case TREE -> {
                        BufferedImage im = Textures.get(Textures.tree(a.biome.tree, (int) Math.floorMod(Rng.hash(a.seed, tx, ty, 3), 3)));
                        statics.add(new Sprite(im, tx * T + T / 2 - im.getWidth() / 2, base - im.getHeight() + 3, base));
                    }
                    case ROCK -> statics.add(new Sprite(Textures.get("rock_" + (a.variant(tx, ty) & 1)), tx * T, ty * T, base));
                    case SIGN -> statics.add(new Sprite(Textures.get("sign"), tx * T, ty * T, base));
                    case FENCE -> statics.add(new Sprite(Textures.get("fence"), tx * T, ty * T, base));
                    case TABLE -> statics.add(new Sprite(Textures.get("table"), tx * T, ty * T, base));
                    case PLANT -> statics.add(new Sprite(Textures.get("plant"), tx * T, ty * T, base));
                    case SHELF -> statics.add(new Sprite(Textures.get("shelf"), tx * T, ty * T, base));
                    case BED -> statics.add(new Sprite(Textures.get("bed"), tx * T, ty * T, base));
                    default -> {}
                }
            }
        for (Area.Building b : a.buildings) {
            BufferedImage im = Textures.get(Textures.building(b.kind(), b.w(), b.h(), b.doorX()));
            if (b.kind() == TileArt.Kind.HOUSE) {
                int rgb = b.roof().getRGB() & 0xffffff;
                String key = Textures.building(b.kind(), b.w(), b.h(), b.doorX()) + "#" + rgb;
                BufferedImage src = im;
                im = ROOFS.computeIfAbsent(key, k -> HdArt.recolorRoof(src, rgb));
            }
            statics.add(new Sprite(im, b.x() * T, b.y() * T - HdArt.OVERHANG, (b.y() + b.h()) * T));
        }
    }

    // ---------------------------------------------------------------- pixel helpers

    private static int shade(int c, double m) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * m), g = (int) Math.min(255, ((c >> 8) & 255) * m), b = (int) Math.min(255, (c & 255) * m);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private void put(int x, int y, int c) { if (x >= 0 && y >= 0 && x < ground.getWidth() && y < ground.getHeight()) ground.setRGB(x, y, c); }

    private void darken(int x, int y, double a) {
        if (x < 0 || y < 0 || x >= ground.getWidth() || y >= ground.getHeight()) return;
        ground.setRGB(x, y, HdArt.mix(ground.getRGB(x, y), 0xff10182a, a));
    }

    private void shadow(double cx, double cy, double rx, double ry, double a) {
        for (int y = (int) (cy - ry); y <= cy + ry; y++)
            for (int x = (int) (cx - rx); x <= cx + rx; x++) {
                double dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry, d = Math.sqrt(dx * dx + dy * dy);
                if (d < 1) darken(x, y, a * Math.min(1, (1 - d) * 3));
            }
    }

    private void paste(BufferedImage im, int x, int y) {
        Graphics2D g = ground.createGraphics();
        g.drawImage(im, x, y, null);
        g.dispose();
    }

    /** Soft contact shadow for something that moves (drawn every frame). */
    public static void dropShadow(Graphics2D g, int cx, int cy, int rx, int ry) {
        g.setColor(new Color(16, 24, 42, 80));
        g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
    }
}
