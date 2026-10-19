import tallgrass.Font;
import tallgrass.HdArt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/** Renders design/world.txt into a world map overview and a detailed map of the starting zone. */
public class WorldPreview {
    static class Rect { String name, extra; int x, y, w, h; }
    static class Zone {
        String id, name, biome, levels, gym;
        int gx, gy;
        List<Rect> towns = new ArrayList<>(), spawns = new ArrayList<>(), pois = new ArrayList<>();
    }

    static final Map<String, Integer> BIOME = Map.of(
            "meadow", 0x86c25a, "forest", 0x4f9446, "beach", 0xe2d08e, "city", 0x9aa0a8,
            "desert", 0xe0bc78, "mountain", 0x9aa08a, "swamp", 0x6a8458, "snow", 0xe4ecf4);

    static List<Zone> parse(File f) throws Exception {
        List<Zone> zones = new ArrayList<>();
        Zone z = null;
        for (String raw : Files.readAllLines(f.toPath())) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int c = line.indexOf(':');
            if (c < 0) continue;
            String k = line.substring(0, c).strip(), v = line.substring(c + 1).strip();
            switch (k) {
                case "zone" -> { z = new Zone(); z.id = v; zones.add(z); }
                case "name" -> z.name = v;
                case "grid" -> { String[] p = v.split(","); z.gx = Integer.parseInt(p[0].strip()); z.gy = Integer.parseInt(p[1].strip()); }
                case "levels" -> z.levels = v;
                case "biome" -> z.biome = v;
                case "gym" -> z.gym = v;
                case "town", "spawn", "poi" -> {
                    String[] p = v.split("\\|");
                    Rect r = new Rect();
                    r.name = p[0].strip();
                    String coords = k.equals("town") ? p[2] : p[1];
                    String[] n = coords.strip().split(",");
                    r.x = Integer.parseInt(n[0].strip()); r.y = Integer.parseInt(n[1].strip());
                    if (n.length > 2) { r.w = Integer.parseInt(n[2].strip()); r.h = Integer.parseInt(n[3].strip()); }
                    r.extra = k.equals("town") ? p[1].strip() : k.equals("spawn") ? p[2].strip() + "|" + p[3].strip() : (p.length > 2 ? p[2].strip() : "");
                    (k.equals("town") ? z.towns : k.equals("spawn") ? z.spawns : z.pois).add(r);
                }
                default -> {}
            }
        }
        return zones;
    }

    static void text(Graphics2D g, String s, int x, int y, Color c, int scale) {
        Font.raw(g, s, x + scale, y + scale, new Color(0, 0, 0, 170), scale);
        Font.raw(g, s, x, y, c, scale);
    }

    static void ground(Graphics2D g, Zone z, int ox, int oy, int px, long seed) {
        int base = BIOME.getOrDefault(z.biome, 0x888888);
        for (int y = 0; y < 48 * px; y++)
            for (int x = 0; x < 64 * px; x++) {
                double n = HdArt.fbm(x * .02, y * .02, seed);
                int c = HdArt.mix(0xff000000 | base, n > .5 ? 0xffffffff : 0xff000000, Math.abs(n - .5) * .35);
                g.setColor(new Color(c));
                g.fillRect(ox + x, oy + y, 1, 1);
            }
    }

    static List<Zone> ALL;

    static void drawZone(Graphics2D g, Zone z, int ox, int oy, int px, boolean detail) {
        ground(g, z, ox, oy, px, z.id.hashCode());
        if (!z.towns.isEmpty()) {
            Rect t = z.towns.get(0);
            int cx = ox + (t.x + t.w / 2) * px, cy = oy + (t.y + t.h / 2) * px;
            g.setColor(new Color(0xc9a46a));
            g.setStroke(new BasicStroke(px * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Zone o : ALL) {
                int dx = o.gx - z.gx, dy = o.gy - z.gy;
                if (Math.abs(dx) + Math.abs(dy) != 1) continue;
                int ex = dx > 0 ? ox + 64 * px : dx < 0 ? ox : ox + 32 * px, ey = dy > 0 ? oy + 48 * px : dy < 0 ? oy : oy + 24 * px;
                g.drawLine(cx, cy, dx != 0 ? cx : ex, dx != 0 ? ey : cy);
                g.drawLine(dx != 0 ? cx : ex, dx != 0 ? ey : cy, ex, ey);
            }
            g.setStroke(new BasicStroke(1f));
        }
        Stroke dashed = new BasicStroke(Math.max(1, px / 3f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {px, px}, 0);
        for (Rect s : z.spawns) {
            g.setColor(new Color(40, 110, 40, 90));
            g.fillRect(ox + s.x * px, oy + s.y * px, s.w * px, s.h * px);
            g.setColor(new Color(20, 60, 20, 200));
            Stroke o = g.getStroke();
            g.setStroke(dashed);
            g.drawRect(ox + s.x * px, oy + s.y * px, s.w * px, s.h * px);
            g.setStroke(o);
            String[] e = s.extra.split("\\|");
            int sc = detail ? 2 : 1;
            text(g, s.name + "  Lv " + e[0], ox + s.x * px + 3, oy + s.y * px + 3, new Color(0xfff6b0), sc);
            if (detail) {
                int ly = oy + s.y * px + 22;
                for (String m : e[1].split(",")) { text(g, "- " + m.strip(), ox + s.x * px + 6, ly, Color.WHITE, 1); ly += 10; }
            }
        }
        for (Rect t : z.towns) {
            g.setColor(new Color(0xd8d2c4));
            g.fillRoundRect(ox + t.x * px, oy + t.y * px, t.w * px, t.h * px, px * 2, px * 2);
            // little rooftops so towns of different styles read differently
            Random r = new Random(t.name.hashCode());
            int n = t.extra.equals("metropolis") ? 14 : t.extra.equals("suburbs") ? 10 : 6;
            for (int i = 0; i < n; i++) {
                int bw = t.extra.equals("metropolis") ? 3 + r.nextInt(3) : 2 + r.nextInt(2), bh = t.extra.equals("metropolis") ? 3 + r.nextInt(5) : 2;
                int bx = t.x + 1 + r.nextInt(Math.max(1, t.w - bw - 1)), by = t.y + 1 + r.nextInt(Math.max(1, t.h - bh - 1));
                int col = t.extra.equals("metropolis") ? 0x5a6a80 : t.extra.contains("desert") ? 0xd8a060 : t.extra.contains("stilt") ? 0x7a5a3a : t.extra.contains("research") ? 0xe0e8f0 : 0xc85a48;
                g.setColor(new Color(col));
                g.fillRect(ox + bx * px, oy + by * px, bw * px, bh * px);
                g.setColor(new Color(0, 0, 0, 90));
                g.drawRect(ox + bx * px, oy + by * px, bw * px, bh * px);
            }
            g.setColor(new Color(60, 50, 40));
            g.drawRoundRect(ox + t.x * px, oy + t.y * px, t.w * px, t.h * px, px * 2, px * 2);
            text(g, t.name, ox + t.x * px + 3, oy + (t.y + t.h) * px - (detail ? 20 : 10), Color.WHITE, detail ? 2 : 1);
            text(g, "(" + t.extra + ")", ox + t.x * px + 3, oy + (t.y + t.h) * px + 2, new Color(0xe0e0e0), 1);
        }
        for (Rect p : z.pois) {
            int cx = ox + p.x * px, cy = oy + p.y * px;
            g.setColor(new Color(0x202020));
            g.fillPolygon(new int[] {cx, cx + 6, cx, cx - 6}, new int[] {cy - 7, cy, cy + 7, cy}, 4);
            g.setColor(new Color(0xffd21a));
            g.fillPolygon(new int[] {cx, cx + 4, cx, cx - 4}, new int[] {cy - 5, cy, cy + 5, cy}, 4);
            if (detail) {
                text(g, p.name, cx + 9, cy - 4, new Color(0xffd21a), 1);
                if (!p.extra.isEmpty()) text(g, p.extra, cx + 9, cy + 6, new Color(0xe8e8e8), 1);
            } else text(g, p.name, cx + 7, cy - 3, new Color(0xffe680), 1);
        }
    }

    public static void main(String[] args) throws Exception {
        List<Zone> zones = parse(new File(args[0]));
        ALL = zones;
        File out = new File(args[1]);
        out.mkdirs();
        // ---------- world overview
        int px = 4, zw = 64 * px, zh = 48 * px, m = 30, top = 70;
        BufferedImage im = new BufferedImage(3 * zw + 4 * m, 3 * zh + 4 * m + top, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(new Color(0x2a4a6a));
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        for (int i = 0; i < 400; i++) { // sea sparkle
            g.setColor(new Color(0x3a5a7a));
            g.fillRect((i * 97) % im.getWidth(), (i * 53) % im.getHeight(), 6, 1);
        }
        text(g, "TALLGRASS v2 - WORLD MAP (level cap 60)", m, 16, Color.WHITE, 3);
        text(g, "Open world: every zone connects to its neighbours along the whole border. Yellow diamonds = points of interest, dashed = creature areas.", m, 48, new Color(0xc8d8e8), 1);
        Map<String, int[]> centers = new HashMap<>();
        g.setColor(new Color(0xf0e8c0));   // open borders between neighbours (drawn first, under the headers)
        for (Zone z : zones) {
            int ox = m + z.gx * (zw + m), oy = top + m + z.gy * (zh + m);
            for (Zone o : zones) {
                if (o.gx == z.gx + 1 && o.gy == z.gy) g.fillRect(ox + zw, oy + zh / 2 - 30, m, 60);
                if (o.gy == z.gy + 1 && o.gx == z.gx) g.fillRect(ox + zw / 2 - 40, oy + zh, 80, m - 24);
            }
        }
        for (Zone z : zones) {
            int ox = m + z.gx * (zw + m), oy = top + m + z.gy * (zh + m);
            drawZone(g, z, ox, oy, px, false);
            g.setColor(new Color(0x101820));
            g.fillRect(ox, oy - 22, zw, 20);
            text(g, z.name, ox + 4, oy - 18, Color.WHITE, 2);
            String lv = "Lv " + z.levels;
            int lw = Font.width(lv) * 2 + 10;
            g.setColor(new Color(0x101820));
            g.fillRoundRect(ox + zw - lw - 4, oy + zh - 24, lw, 20, 8, 8);
            text(g, lv, ox + zw - lw + 1, oy + zh - 20, new Color(0xffd21a), 2);
            if (z.gym != null) {
                String gy = "Gym: " + z.gym.split("\\|")[0].strip() + " (" + z.gym.split("\\|")[1].strip() + ")";
                text(g, gy, ox + 4, oy + zh - 12, new Color(0xffc0c0), 1);
            }
            centers.put(z.id, new int[] {ox + zw / 2, oy + zh / 2});
        }
        // locked expansion
        int ex = m + 2 * (zw + m), ey = top + m;
        g.setColor(new Color(0x40303a));
        g.fillRect(ex, ey, zw, zh);
        g.setColor(new Color(0x80606a));
        for (int i = -zh; i < zw; i += 14) g.drawLine(ex + Math.max(0, i), ey + Math.max(0, -i), ex + Math.min(zw, i + zh), ey + Math.min(zh, zw - i + 0));
        text(g, "EXPANSION (locked)", ex + 30, ey + zh / 2 - 26, new Color(0xffb0a0), 2);
        text(g, "The Ember Isles  Lv 60-70", ex + 30, ey + zh / 2, Color.WHITE, 2);
        // levelling route
        String[] order = {"meadow", "forest", "coast", "metro", "desert", "peaks", "swamp", "tundra"};
        g.setStroke(new BasicStroke(3f));
        for (int i = 0; i < order.length; i++) {
            int[] c = centers.get(order[i]);
            if (c == null) continue;
            if (i > 0) {
                int[] p = centers.get(order[i - 1]);
                g.setColor(new Color(255, 210, 26, 150));
                g.drawLine(p[0], p[1], c[0], c[1]);
            }
        }
        for (int i = 0; i < order.length; i++) {
            int[] c = centers.get(order[i]);
            if (c == null) continue;
            g.setColor(new Color(0x101820));
            g.fillOval(c[0] - 13, c[1] - 13, 26, 26);
            g.setColor(new Color(0xffd21a));
            g.fillOval(c[0] - 11, c[1] - 11, 22, 22);
            String s = String.valueOf(i + 1);
            Font.raw(g, s, c[0] - Font.width(s), c[1] - 7, new Color(0x101820), 2);
        }
        g.dispose();
        ImageIO.write(im, "png", new File(out, "world_map.png"));
        // ---------- starting zone, detailed
        Zone start = zones.get(0);
        int dp = 12;
        BufferedImage d = new BufferedImage(64 * dp + 40, 48 * dp + 80, BufferedImage.TYPE_INT_RGB);
        g = d.createGraphics();
        g.setColor(new Color(0x101820));
        g.fillRect(0, 0, d.getWidth(), d.getHeight());
        text(g, start.name + "  -  Lv " + start.levels + "  -  starting zone", 20, 16, Color.WHITE, 3);
        text(g, "Creatures roam inside the dashed areas (no random grass encounters). Borders are open on every side that has a neighbour.", 20, 50, new Color(0xc8d8e8), 1);
        drawZone(g, start, 20, 64, dp, true);
        g.dispose();
        ImageIO.write(d, "png", new File(out, "zone_meadow.png"));
        System.out.println("zones: " + zones.size());
    }
}
