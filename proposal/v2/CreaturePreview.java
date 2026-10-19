import tallgrass.Font;
import tallgrass.HdArt;
import tallgrass.PixelCanvas;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Part-based creature designer. Reads design/creatures.txt and draws every species from its body plan,
 * type parts and colors as a 64x64 front sprite (3/4 view, lit from the top-left), then lays them out on
 * a sheet. The game's sprite generator will be this code.
 */
public class CreaturePreview {
    // ------------------------------------------------------------ data
    static class Spec {
        int id; String name; String[] types; String evo; String plan; Set<String> parts = new HashSet<>(); int c1, c2, c3; int stage;
    }

    static final Map<String, Integer> TYPE = new LinkedHashMap<>();
    static {
        String[][] t = {{"Normal", "a8a878"}, {"Fire", "f08030"}, {"Water", "6890f0"}, {"Grass", "78c850"}, {"Electric", "f8d030"}, {"Ice", "98d8d8"},
                {"Fighting", "c03028"}, {"Poison", "a040a0"}, {"Ground", "e0c068"}, {"Flying", "a890f0"}, {"Psychic", "f85888"}, {"Bug", "a8b820"},
                {"Rock", "b8a038"}, {"Ghost", "705898"}, {"Dragon", "7038f8"}, {"Dark", "705848"}, {"Steel", "b8b8d0"}, {"Fairy", "ee99ac"}};
        for (String[] e : t) TYPE.put(e[0], Integer.parseInt(e[1], 16));
    }

    static List<Spec> parse(File f) throws Exception {
        List<Spec> out = new ArrayList<>();
        for (String raw : Files.readAllLines(f.toPath())) {
            String l = raw.strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\|");
            if (p.length < 7) continue;
            Spec s = new Spec();
            s.id = Integer.parseInt(p[0].strip());
            s.name = p[1].strip();
            s.types = p[2].strip().split("/");
            s.evo = p[3].strip();
            s.plan = p[4].strip();
            s.parts.addAll(Arrays.asList(p[5].strip().split("\\s+")));
            String[] c = p[6].strip().split("\\s+");
            s.c1 = Integer.parseInt(c[0], 16); s.c2 = Integer.parseInt(c[1], 16); s.c3 = Integer.parseInt(c[2], 16);
            out.add(s);
        }
        // stage = how many species evolve into this one, following the chain
        Map<Integer, Integer> from = new HashMap<>();
        for (Spec s : out) if (s.evo.contains(">")) from.put(Integer.parseInt(s.evo.split(">")[1]), s.id);
        for (Spec s : out) { int st = 0, id = s.id; while (from.containsKey(id)) { st++; id = from.get(id); } s.stage = st; }
        return out;
    }

    // ------------------------------------------------------------ painter
    static final int N = 64;
    static final double LX = -0.5, LY = -0.68, LZ = 0.54;

    final int[] zbuf = new int[N * N];
    final double[] nzv = new double[N * N], nxv = new double[N * N], nyv = new double[N * N];
    final int[] mat = new int[N * N];
    final List<int[]> ramps = new ArrayList<>();
    final BufferedImage back = img(), front = img(), aura = img();   // aura = glows/gas, never outlined
    double k = 1;                         // size of this stage
    Random rnd;

    static BufferedImage img() { return new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB); }

    CreaturePreview() { Arrays.fill(zbuf, Integer.MIN_VALUE); }

    int material(int rgb) { ramps.add(HdArt.ramp(rgb, 7)); return ramps.size(); }

    double X(double x) { return 32 + (x - 32) * k; }
    double Y(double y) { return 60 - (60 - y) * k; }
    double R(double r) { return r * k; }

    /** Ellipsoid; superellipse when box > 2 (blocky golems). */
    void blob(double x, double y, double r, int m, double sx, double sy, double zb, double box) {
        double cx = X(x), cy = Y(y), rx = R(r) * sx, ry = R(r) * sy;
        for (int py = (int) (cy - ry - 1); py <= cy + ry + 1; py++)
            for (int px = (int) (cx - rx - 1); px <= cx + rx + 1; px++) {
                if (px < 0 || py < 0 || px >= N || py >= N) continue;
                double dx = (px + .5 - cx) / rx, dy = (py + .5 - cy) / ry;
                double d = box > 2 ? Math.pow(Math.abs(dx), box) + Math.pow(Math.abs(dy), box) : dx * dx + dy * dy;
                if (d >= 1) continue;
                double nz = Math.sqrt(1 - Math.min(1, dx * dx + dy * dy) * (box > 2 ? .35 : 1));
                if (box > 2) { double e = Math.pow(1 - d, .3); nz = .55 + .45 * e; }
                int z = (int) ((nz * Math.min(rx, ry) + zb) * 100);
                int i = py * N + px;
                if (z > zbuf[i]) {
                    zbuf[i] = z; mat[i] = m;
                    nxv[i] = box > 2 ? Math.signum(dx) * Math.pow(Math.abs(dx), box - 1) * .6 : dx;
                    nyv[i] = box > 2 ? Math.signum(dy) * Math.pow(Math.abs(dy), box - 1) * .6 : dy;
                    nzv[i] = nz;
                }
            }
    }

    void blob(double x, double y, double r, int m) { blob(x, y, r, m, 1, 1, 0, 0); }
    void blob(double x, double y, double r, int m, double zb) { blob(x, y, r, m, 1, 1, zb, 0); }

    /** Flat shape in the back or front layer, shaded lighter toward the top-left. */
    void poly(BufferedImage layer, double[] xs, double[] ys, int rgb, double shadeBias) {
        int[] r = HdArt.ramp(rgb, 7);
        Polygon p = new Polygon();
        for (int i = 0; i < xs.length; i++) p.addPoint((int) Math.round(X(xs[i])), (int) Math.round(Y(ys[i])));
        Rectangle b = p.getBounds();
        for (int y = b.y; y <= b.y + b.height; y++)
            for (int x = b.x; x <= b.x + b.width; x++) {
                if (x < 0 || y < 0 || x >= N || y >= N || !p.contains(x + .5, y + .5)) continue;
                double t = ((x - b.x) / (double) Math.max(1, b.width) * -LX + (y - b.y) / (double) Math.max(1, b.height) * -LY);
                double v = 4.2 - t * 2.2 + shadeBias;
                layer.setRGB(x, y, r[(int) Math.max(0, Math.min(6, Math.round(v)))]);
            }
    }

    void dot(BufferedImage layer, double x, double y, double r, int argb) {
        double cx = X(x), cy = Y(y), rr = R(r);
        for (int py = (int) (cy - rr - 1); py <= cy + rr + 1; py++)
            for (int px = (int) (cx - rr - 1); px <= cx + rr + 1; px++)
                if (px >= 0 && py >= 0 && px < N && py < N && Math.hypot(px + .5 - cx, py + .5 - cy) <= rr) layer.setRGB(px, py, argb);
    }

    void line(BufferedImage layer, double x0, double y0, double x1, double y1, double w, int argb) {
        double ax = X(x0), ay = Y(y0), bx = X(x1), by = Y(y1);
        int steps = (int) Math.max(1, Math.hypot(bx - ax, by - ay) * 2);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, cx = ax + (bx - ax) * t, cy = ay + (by - ay) * t;
            for (int py = (int) (cy - w); py <= cy + w; py++)
                for (int px = (int) (cx - w); px <= cx + w; px++)
                    if (px >= 0 && py >= 0 && px < N && py < N && Math.hypot(px + .5 - cx, py + .5 - cy) <= w * .5 + .3) layer.setRGB(px, py, argb);
        }
    }

    /** Layered tear-drop flame, tip pointing up. */
    void flame(BufferedImage layer, double x, double y, double s) {
        int[] cols = {0xffe8401c, 0xfff08a20, 0xffffd648, 0xfffff4b0};
        for (int i = 0; i < cols.length; i++) {
            double sc = s * (1 - i * .22);
            double cx = X(x), cy = Y(y + i * s * .12);
            for (int py = (int) (cy - sc * 2.2); py <= cy + sc; py++)
                for (int px = (int) (cx - sc); px <= cx + sc; px++) {
                    if (px < 0 || py < 0 || px >= N || py >= N) continue;
                    double dy = (py + .5 - cy) / sc, dx = (px + .5 - cx) / sc;
                    double w = dy > 0 ? Math.sqrt(Math.max(0, 1 - dy * dy)) : 1 + dy / 2.2;   // round bottom, pointed top
                    if (Math.abs(dx) < w + Math.sin(py * .9 + i) * .12) layer.setRGB(px, py, cols[i]);
                }
        }
    }

    BufferedImage shade() {
        BufferedImage im = img();
        Graphics2D g = im.createGraphics();
        g.drawImage(back, 0, 0, null);
        g.dispose();
        for (int i = 0; i < N * N; i++) {
            if (mat[i] == 0) continue;
            int[] r = ramps.get(mat[i] - 1);
            double lit = nxv[i] * LX + nyv[i] * LY + nzv[i] * LZ;
            double v = .5 + lit * .58;
            int x = i % N, y = i / N;
            int below = y + 1 < N ? mat[i + N] : 0, right = x + 1 < N ? mat[i + 1] : 0;
            if ((below != 0 && below != mat[i] && zbuf[i + N] > zbuf[i]) || (right != 0 && right != mat[i] && zbuf[i + 1] > zbuf[i])) v -= .22; // contact shadow
            im.setRGB(x, y, r[(int) Math.max(1, Math.min(6, Math.round(v * 6)))]);
        }
        g = im.createGraphics();
        g.drawImage(front, 0, 0, null);
        g.dispose();
        PixelCanvas.selOut(im, .62);
        BufferedImage out = img();
        g = out.createGraphics();
        g.drawImage(aura, 0, 0, null);
        g.drawImage(im, 0, 0, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------ the designer

    static BufferedImage draw(Spec s) {
        CreaturePreview c = new CreaturePreview();
        c.rnd = new Random(s.id * 7919L);
        c.k = s.stage == 0 ? .82 : s.stage == 1 ? .96 : 1.08;
        if (s.plan.equals("golem") || s.plan.equals("serpent")) c.k *= .95;
        return c.design(s);
    }

    boolean has(Spec s, String p) { return s.parts.contains(p); }

    BufferedImage design(Spec s) {
        int body = material(s.c1), belly = material(s.c2), accent = material(s.c3), dark = material(HdArt.mix(0xff000000 | s.c1, 0xff000000, .35) & 0xffffff);
        int white = material(0xf4f4f0), metal = material(0x9aa4b4), leaf = material(0x4fae46), shellM = material(has(s, "shell_spikes") ? 0x6a7a90 : s.plan.equals("crab") ? s.c1 : 0xa8784a);
        // head anchor for faces and head parts
        double hx, hy, hr;
        boolean side = false; // 3/4 view facing left
        switch (s.plan) {
            case "quad" -> {
                if (has(s, "fur") || has(s, "tail_fluffy")) { for (int i = 0; i < 5; i++) blob(52 + i * 1.5, 40 - i * 3, 6 - i * .5, has(s, "fur") ? belly : body, 1, 1, -4, 0); }
                blob(38, 43, 15, body, 1.15, .78, 0, 0);
                blob(36, 48, 10, belly, 1.25, .55, 4, 0);
                for (double[] l : new double[][] {{24, 54}, {46, 54}, {29, 56}, {43, 56}}) blob(l[0], l[1], 5, l[0] < 30 ? body : dark, 1, 1.15, l[1] > 55 ? 2 : 1, 0);
                hx = 22; hy = 30; hr = has(s, "shell") || has(s, "shell_spikes") ? 10 : 11.5;
                blob(hx, hy, hr, body, 1.05, .95, 8, 0);
                blob(hx - 4, hy + 5, hr * .45, belly, 1.2, .8, 13, 0);   // muzzle
                side = true;
            }
            case "biped" -> {
                blob(27, 55, 5.5, dark, 1, 1.1, 0, 0);
                blob(39, 55, 5.5, body, 1, 1.1, 0, 0);
                blob(33, 43, 12.5, body, 1, 1.12, 1, 0);
                blob(32, 45, 8.5, belly, 1, 1.1, 5, 0);
                double armR = has(s, "fists") ? 5.6 : 4.5;
                blob(20, 42, armR, has(s, "fists") ? white : body, 1, 1, 6, 0);
                blob(46, 42, armR, has(s, "fists") ? white : dark, 1, 1, 2, 0);
                hx = 32; hy = 24; hr = 12;
                blob(hx, hy, hr, body, 1, .95, 7, 0);
            }
            case "bird" -> {
                double wingSpread = s.stage >= 1 ? 1.25 : 1;
                poly(back, new double[] {38, 60 * wingSpread / 1.1, 56, 44}, new double[] {34, 18, 46, 46}, s.c1, -.6);
                poly(back, new double[] {26, 6, 10, 22}, new double[] {34, 20, 46, 46}, s.c1, .4);
                for (int i = 0; i < 3; i++) line(back, 44 + i * 4, 28 + i * 5, 54 + i * 2, 22 + i * 7, 1, 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .35));
                if (has(s, "tail_feathers")) poly(back, new double[] {36, 52, 56, 48}, new double[] {48, 50, 60, 58}, s.c3, 0);
                blob(32, 42, 12.5, body, 1.05, 1.05, 0, 0);
                blob(30, 46, 8.5, belly, 1, 1, 4, 0);
                line(front, 27, 54, 26, 60, 1.2, 0xffe8a030);
                line(front, 35, 54, 36, 60, 1.2, 0xffe8a030);
                hx = 30; hy = 24; hr = 10.5;
                blob(hx, hy, hr, body, 1, 1, 6, 0);
                int beak = 0xfff0b030;
                if (has(s, "beak_hook")) poly(front, new double[] {hx - 9, hx - 16, hx - 15, hx - 8}, new double[] {hy, hy + 2, hy + 7, hy + 5}, beak, 0);
                else poly(front, new double[] {hx - 8, hx - 15, hx - 8}, new double[] {hy + 1, hy + 3, hy + 6}, beak, 0);
                side = true;
            }
            case "insect" -> {
                if (has(s, "caterpillar")) {
                    double[][] seg = {{52, 52, 6}, {45, 49, 7}, {37, 48, 7.5}, {29, 49, 7.5}};
                    for (double[] q : seg) { blob(q[0], q[1], q[2], body, 1, 1, 0, 0); blob(q[0], q[1] + 4, q[2] * .55, belly, 1, .6, 3, 0); }
                    for (double[] q : seg) dot(front, q[0], q[1] - 3, 1.4, 0xff000000 | s.c3);
                    hx = 20; hy = 40; hr = 10;
                    blob(hx, hy, hr, body, 1, 1, 6, 0);
                    side = true;
                } else if (has(s, "scorpion")) {
                    blob(36, 50, 13, body, 1.25, .6, 0, 0);
                    double[][] tail = {{50, 46, 4.5}, {55, 40, 4.3}, {56, 33, 4}, {53, 26, 3.8}, {47, 22, 3.6}};
                    for (double[] q : tail) blob(q[0], q[1], q[2], body, 1, 1, 2, 0);
                    poly(front, new double[] {44, 40, 45}, new double[] {20, 25, 25}, s.c3, 0);
                    for (int i = 0; i < 3; i++) { line(back, 30 + i * 6, 54, 24 + i * 6, 59, 1.1, 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .4)); line(back, 38 + i * 6, 54, 44 + i * 6, 59, 1.1, 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .4)); }
                    hx = 22; hy = 44; hr = 7.5;
                    blob(hx, hy, hr, body, 1.1, .9, 5, 0);
                    blob(10, 40, 6, body, 1, .8, 4, 0);
                    blob(34, 38, 5.5, body, 1, .8, 3, 0);
                    side = true;
                } else if (has(s, "beetle")) {
                    for (int i = 0; i < 3; i++) { line(back, 22, 46 + i * 5, 14, 52 + i * 4, 1.2, 0xff2a2a30); line(back, 42, 46 + i * 5, 50, 52 + i * 4, 1.2, 0xff2a2a30); }
                    blob(32, 46, 14, accent, 1.05, .85, 0, 0);
                    line(front, 32, 36, 32, 58, .8, 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .5));
                    hx = 32; hy = 30; hr = 8;
                    blob(hx, hy, hr, body, 1.1, .9, 4, 0);
                } else {
                    if (has(s, "wings_insect")) {
                        poly(back, new double[] {32, 6, 4, 26}, new double[] {30, 8, 30, 38}, s.c1, 0);
                        poly(back, new double[] {32, 58, 60, 38}, new double[] {30, 8, 30, 38}, s.c1, -.5);
                        poly(back, new double[] {32, 10, 16, 30}, new double[] {38, 52, 56, 42}, s.c3, 0);
                        poly(back, new double[] {32, 54, 48, 34}, new double[] {38, 52, 56, 42}, s.c3, -.5);
                        dot(back, 14, 22, 3, 0xff000000 | s.c3); dot(back, 50, 22, 3, 0xff000000 | s.c3);
                    }
                    blob(32, 40, 6, dark, 1, 1.2, 0, 0);
                    blob(32, 50, 5, dark, 1, 1.3, 0, 0);
                    hx = 32; hy = 28; hr = 7.5;
                    blob(hx, hy, hr, body, 1, 1, 3, 0);
                }
            }
            case "round" -> {
                blob(24, 57, 4.5, dark, 1, .8, 0, 0);
                blob(40, 57, 4.5, dark, 1, .8, 0, 0);
                hx = 32; hy = 40; hr = 17;
                blob(hx, hy, hr, body, 1, .95, 0, 0);
                if (!has(s, "shell")) blob(32, 47, 9, belly, 1.2, .7, 6, 0);
                if (has(s, "shell")) { blob(36, 38, 15, shellM, 1, .95, 4, 0); for (int i = 0; i < 3; i++) line(front, 26 + i * 7, 30, 30 + i * 7, 48, .6, 0x50000000); }
                hy = 36;
            }
            case "floater" -> {
                if (has(s, "glow")) for (int r = 26; r > 16; r -= 2) dot(aura, 32, 30, r, ((int) (30 - (r - 16) * 2.5) << 24) | s.c3);
                double[][] tail = {{33, 42, 11}, {35, 49, 7}, {38, 54, 4.5}, {42, 57, 2.5}};
                if (!has(s, "tentacles")) for (double[] q : tail) blob(q[0], q[1], q[2], body, 1, 1, -1, 0);
                hx = 32; hy = 30; hr = 15;
                blob(hx, hy, hr, body, 1, .95, 0, 0);
                if (has(s, "tentacles")) for (int i = 0; i < 4; i++) {
                    double bx = 22 + i * 6.5;
                    for (int y = 0; y < 18; y++) dot(back, bx + Math.sin(y * .45 + i) * 2, 40 + y, 1.5, 0xff000000 | (y > 12 ? s.c3 : s.c1));
                }
            }
            case "golem" -> {
                boolean round = has(s, "round_rock");
                double bx = round ? 0 : 4;
                blob(25, 56, 5.5, dark, 1, .8, 0, bx);
                blob(39, 56, 5.5, dark, 1, .8, 0, bx);
                blob(32, 41, 14, body, 1, 1, 1, bx);
                blob(15, 42, 6, body, .8, 1.4, 4, bx);
                blob(49, 42, 6, dark, .8, 1.4, 0, bx);
                hx = 32; hy = round ? 34 : 22; hr = round ? 14 : 9;
                if (!round) blob(hx, hy, hr, body, 1.15, .9, 5, bx);
                if (has(s, "armor_plates")) for (int i = 0; i < 3; i++) line(front, 22, 36 + i * 6, 42, 36 + i * 6, .6, 0x60000000);
            }
            case "fish" -> {
                poly(back, new double[] {46, 60, 60, 50}, new double[] {36, 24, 50, 38}, s.c3, 0);
                poly(back, new double[] {26, 34, 42}, new double[] {28, 16, 28}, s.c3, 0);
                blob(32, 38, 15, body, 1.25, .78, 0, 0);
                blob(30, 43, 9, belly, 1.4, .5, 4, 0);
                hx = 22; hy = 35; hr = 9;
                side = true;
            }
            case "serpent" -> {
                blob(36, 52, 13, body, 1.4, .6, 0, 0);
                blob(44, 44, 9, body, 1.1, .8, 2, 0);
                blob(36, 36, 7.5, body, 1, 1, 3, 0);
                blob(30, 30, 7, belly, 1, 1, 4, 0);
                hx = 26; hy = 21; hr = 10;
                blob(hx, hy, hr, body, 1.15, .9, 6, 0);
                side = true;
            }
            case "crab" -> {
                for (int i = 0; i < 3; i++) { line(back, 22 - i * 3, 48, 12 - i * 3, 58, 1.2, 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .3)); line(back, 42 + i * 3, 48, 52 + i * 3, 58, 1.2, 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .3)); }
                blob(32, 44, 15, shellM, 1.2, .7, 0, 0);
                blob(32, 49, 9, belly, 1.4, .4, 3, 0);
                double cs = has(s, "claws_big") ? 8.5 : 6.5;
                blob(9, 36, cs, body, 1.1, .9, 4, 0);
                blob(55, 36, cs, body, 1.1, .9, 4, 0);
                poly(front, new double[] {4, 12, 4}, new double[] {30, 34, 38}, 0xf8f0e0, 0);
                poly(front, new double[] {60, 52, 60}, new double[] {30, 34, 38}, 0xf8f0e0, 0);
                line(back, 27, 36, 26, 28, 1, 0xff303030);
                line(back, 37, 36, 38, 28, 1, 0xff303030);
                hx = 32; hy = 28; hr = 6;
            }
            default -> { hx = 32; hy = 30; hr = 12; blob(hx, hy, hr, body); }
        }
        decorate(s, hx, hy, hr, side, body, belly, accent, dark, white, metal, leaf, shellM);
        return shade();
    }

    void decorate(Spec s, double hx, double hy, double hr, boolean side, int body, int belly, int accent, int dark, int white, int metal, int leaf, int shellM) {
        int c3 = 0xff000000 | s.c3, c1d = 0xff000000 | HdArt.mix(0xff000000 | s.c1, 0xff000000, .35);
        double top = hy - hr;
        double fx = side ? hx - hr * .35 : hx;          // where the face points
        // ---------- back / tail parts
        if (has(s, "flame_tail")) {
            double[][] t = {{44, 50}, {50, 48}, {54, 43}, {56, 37}};
            for (int i = 0; i < t.length; i++) blob(t[i][0], t[i][1], 3.6 - i * .5, body, 1, 1, -2, 0);
            flame(front, 56, 33, 5 + s.stage);
        }
        if (has(s, "tail_long")) for (int i = 0; i < 14; i++) dot(back, 50 + i * 0.9, 46 - Math.sin(i * .35) * 10 - i * .6, 2 - i * .08, c1d);
        if (has(s, "tail_curl")) for (int i = 0; i < 10; i++) dot(back, 52 + Math.cos(i * .6) * 4, 44 - Math.sin(i * .6) * 4, 2, 0xff000000 | s.c1);
        if (has(s, "zigzag_tail")) poly(back, new double[] {44, 56, 50, 60, 52, 46, 50, 42}, new double[] {48, 38, 34, 22, 32, 36, 42, 50}, s.c1, 0);
        if (has(s, "shell") && s.plan.equals("quad")) {
            blob(40, 37, 13.5, shellM, 1.1, .78, 6, 0);
            for (int i = 0; i < 3; i++) line(front, 32 + i * 6, 30, 32 + i * 6, 44, .5, 0x55000000);
            line(front, 28, 37, 52, 37, .5, 0x55000000);
        }
        if (has(s, "shell_spikes")) {
            blob(40, 36, 14, shellM, 1.1, .8, 6, 0);
            for (int i = 0; i < 3; i++) poly(front, new double[] {32 + i * 7, 35 + i * 7, 38 + i * 7}, new double[] {28 - i, 18 - i * 2, 28 - i}, 0xd8dce4, 0);
            dot(front, 50, 34, 2.5, 0xff303848);
        }
        if (has(s, "leaf_back")) for (int i = 0; i < 2; i++) poly(back, new double[] {38 + i * 8, 44 + i * 8, 52 + i * 8, 46 + i * 8}, new double[] {34, 18, 22, 34}, 0x4fae46, 0);
        if (has(s, "tree_back")) {
            line(back, 42, 34, 44, 22, 2, 0xff7a5230);
            blob(44, 18, 7, leaf, 1, 1, 12, 0); blob(38, 20, 5.5, leaf, 1, 1, 11, 0); blob(50, 21, 5.5, leaf, 1, 1, 11, 0);
        }
        if (has(s, "fins")) {
            if (s.plan.equals("quad")) poly(back, new double[] {38, 44, 50}, new double[] {32, 20, 32}, s.c3, 0);
            poly(back, new double[] {hx + hr - 2, hx + hr + 6, hx + hr}, new double[] {hy - 4, hy - 10, hy + 2}, s.c3, 0);
        }
        if (has(s, "wings_bat") || has(s, "wings_small") || has(s, "wings_big")) {
            double sc = has(s, "wings_big") ? 1.3 : has(s, "wings_small") ? .7 : 1;
            int wc = has(s, "wings_small") ? s.c3 : s.c1;
            poly(back, new double[] {40, 40 + 20 * sc, 40 + 22 * sc, 40 + 12 * sc, 44}, new double[] {36, 36 - 22 * sc, 36 - 6 * sc, 36 + 4 * sc, 44}, wc, -.4);
            poly(back, new double[] {26, 26 - 20 * sc, 26 - 22 * sc, 26 - 12 * sc, 22}, new double[] {36, 36 - 22 * sc, 36 - 6 * sc, 36 + 4 * sc, 44}, wc, .4);
            for (int i = 1; i <= 2; i++) { line(back, 41, 37, 40 + (20 - i * 6) * sc, 36 - (22 - i * 8) * sc, .6, c1d); line(back, 25, 37, 26 - (20 - i * 6) * sc, 36 - (22 - i * 8) * sc, .6, c1d); }
        }
        if (has(s, "ribbon_wings")) {
            poly(back, new double[] {32, 8, 6, 20}, new double[] {36, 22, 36, 44}, s.c3, .2);
            poly(back, new double[] {32, 56, 58, 44}, new double[] {36, 22, 36, 44}, s.c3, -.4);
        }
        if (has(s, "tail_fluffy") && !s.plan.equals("quad")) for (int i = 0; i < 4; i++) blob(50 + i, 46 - i * 3, 6, body, 1, 1, -3, 0);
        if (has(s, "crystal_tail")) for (int i = 0; i < 3; i++) poly(back, new double[] {50 + i * 3, 56 + i * 3, 52 + i * 3}, new double[] {40 - i * 4, 22 - i * 3, 42 - i * 4}, 0x9ad8f8, 0);
        if (has(s, "stinger")) {}
        if (has(s, "glow") && !s.plan.equals("floater")) for (int r = 30; r > 20; r -= 2) dot(aura, 32, 36, r, ((int) (26 - (r - 20) * 2.4) << 24) | s.c3);
        if (has(s, "rings")) for (int a = 0; a < 360; a += 3) {
            double t = Math.toRadians(a);
            dot(Math.sin(t) < 0 ? aura : front, 32 + Math.cos(t) * 24, 32 + Math.sin(t) * 7, 1.2, c3);
        }
        if (has(s, "gas")) for (int i = 0; i < 7; i++) dot(aura, 32 + Math.cos(i) * 20, 32 + Math.sin(i * 1.7) * 16, 5 + i % 3, 0x70000000 | (s.c2 & 0xffffff));
        // ---------- head parts (behind the head)
        if (has(s, "ears_big")) { blob(hx - hr * .7, top + 1, hr * .55, body, 1, 1, 2, 0); blob(hx + hr * .5, top - 1, hr * .55, dark, 1, 1, 1, 0); }
        if (has(s, "ears_pointy")) {
            poly(back, new double[] {hx - hr * .9, hx - hr * .7, hx - hr * .1}, new double[] {top + 6, top - 10, top + 3}, s.c1, .3);
            poly(back, new double[] {hx + hr * .1, hx + hr * .7, hx + hr * .9}, new double[] {top + 3, top - 10, top + 6}, s.c1, -.5);
            poly(back, new double[] {hx - hr * .75, hx - hr * .68, hx - hr * .3}, new double[] {top + 4, top - 5, top + 3}, s.c3, 0);
        }
        if (has(s, "ears_tuft")) {
            poly(back, new double[] {hx - hr, hx - hr * .9, hx - hr * .3}, new double[] {top + 4, top - 8, top + 2}, s.c1, 0);
            poly(back, new double[] {hx + hr * .3, hx + hr * .9, hx + hr}, new double[] {top + 2, top - 8, top + 4}, s.c1, -.5);
        }
        if (has(s, "horns") || has(s, "horn")) {
            int hc = 0xf0e8d0;
            if (has(s, "horn")) poly(front, new double[] {hx - 3, hx - 1, hx + 3}, new double[] {top + 2, top - 14, top + 2}, s.c3, 0);
            else {
                poly(back, new double[] {hx - hr * .6, hx - hr * .9, hx - hr * .2}, new double[] {top + 3, top - 9 - s.stage * 3, top + 2}, hc, 0);
                poly(back, new double[] {hx + hr * .2, hx + hr * .7, hx + hr * .5}, new double[] {top + 2, top - 9 - s.stage * 3, top + 4}, hc, -.6);
            }
        }
        if (has(s, "antlers")) for (int sd = -1; sd <= 1; sd += 2) {
            line(back, hx + sd * 4, top + 2, hx + sd * 10, top - 12, 1.4, c3);
            line(back, hx + sd * 8, top - 8, hx + sd * 15, top - 10, 1.2, c3);
            line(back, hx + sd * 9, top - 10, hx + sd * 8, top - 18, 1.2, c3);
        }
        if (has(s, "crest") || has(s, "crest_big")) {
            double h = has(s, "crest_big") ? 14 : 9;
            poly(back, new double[] {hx - 2, hx + 2, hx + 8, hx + 5}, new double[] {top + 3, top - h, top - h + 4, top + 4}, s.c3, 0);
        }
        if (has(s, "mane")) for (int a = 0; a < 360; a += 40) blob(hx + Math.cos(Math.toRadians(a)) * hr * .95, hy + 3 + Math.sin(Math.toRadians(a)) * hr * .85, hr * .45, accent, 1, 1, 1, 0);
        if (has(s, "flame_mane")) for (int i = 0; i < 5; i++) flame(back, hx + 4 + i * 4, hy - 4 + Math.abs(i - 2) * 3, 3.5);
        if (has(s, "coral_horns")) for (int sd = -1; sd <= 1; sd += 2) { line(back, hx + sd * 6, top + 4, hx + sd * 10, top - 8, 1.6, c3); line(back, hx + sd * 9, top - 4, hx + sd * 14, top - 6, 1.3, c3); }
        if (has(s, "headdress")) poly(back, new double[] {hx - hr - 3, hx + hr + 3, hx + hr + 6, hx - hr - 6}, new double[] {top + 2, top + 2, hy + hr, hy + hr}, 0xe8c040, 0);
        if (has(s, "mushroom_cap")) {
            blob(hx, top + 1, hr * 1.25, accent, 1.15, .62, 14, 0);
            for (int i = 0; i < 4; i++) dot(front, hx - hr * .8 + i * hr * .55, top - 1 + (i % 2) * 3, 1.8, 0xfff8f4f0);
        }
        if (has(s, "spikes") || has(s, "rock_spikes") || has(s, "crystal_spikes")) {
            int sc = has(s, "crystal_spikes") ? 0x9ad8f8 : has(s, "rock_spikes") ? 0x9a948a : s.c3;
            for (int i = 0; i < 4; i++) poly(back, new double[] {34 + i * 5, 37 + i * 5, 40 + i * 5}, new double[] {38 - i * 2, 22 - i * 3 - s.stage * 2, 38 - i * 2}, sc, 0);
        }
        if (has(s, "spines")) for (int i = 0; i < 10; i++) dot(front, 22 + rnd.nextInt(22), 30 + rnd.nextInt(24), .6, 0xfff8f4e0);
        // ---------- head parts in front
        if (has(s, "leaf_head")) {
            line(front, hx + 1, top + 2, hx + 3, top - 4, .8, 0xff3a7a30);
            poly(front, new double[] {hx + 3, hx + 6, hx + 14, hx + 10}, new double[] {top - 4, top - 12, top - 12, top - 4}, 0x5abf4a, 0);
        }
        if (has(s, "flower")) { for (int a = 0; a < 360; a += 72) dot(front, hx + 3 + Math.cos(Math.toRadians(a)) * 3, top + Math.sin(Math.toRadians(a)) * 3, 2.1, c3); dot(front, hx + 3, top, 1.4, 0xfff8e070); }
        if (has(s, "flame_head")) flame(front, hx, top - 2, 6);
        if (has(s, "crown")) poly(front, new double[] {hx - 6, hx - 6, hx - 3, hx, hx + 3, hx + 6, hx + 6}, new double[] {top + 2, top - 6, top - 2, top - 7, top - 2, top - 6, top + 2}, 0xf0c040, 0);
        if (has(s, "headband")) { line(front, hx - hr, hy - 3, hx + hr, hy - 4, 1.3, c3); line(front, hx + hr, hy - 4, hx + hr + 6, hy + 2, 1, c3); }
        if (has(s, "gem")) poly(front, new double[] {fx, fx + 3, fx, fx - 3}, new double[] {top + 2, top + 5, top + 8, top + 5}, s.c3, 0);
        if (has(s, "mask")) for (int y = 0; y < 4; y++) line(front, fx - hr * .8, hy - 2 + y, fx + hr * .7, hy - 2 + y, .5, 0xd0201820);
        // ---------- body details
        if (has(s, "spots")) for (int i = 0; i < 5; i++) dot(front, 30 + rnd.nextInt(18), 36 + rnd.nextInt(14), 1.5, c3);
        if (has(s, "scales")) for (int i = 0; i < 6; i++) for (int j = 0; j < 2; j++) dot(front, 32 + i * 4, 40 + j * 5, .8, c1d);
        if (has(s, "wraps")) for (int i = 0; i < 4; i++) line(front, hx - hr, hy - 6 + i * 6, hx + hr, hy - 2 + i * 6, .7, 0xc0f4ecd8);
        if (has(s, "bark_armor")) for (int i = 0; i < 4; i++) line(front, 26 + i * 4, 34, 25 + i * 4, 52, .5, 0x90402a18);
        if (has(s, "armor_plates") && !s.plan.equals("golem")) for (int i = 0; i < 3; i++) line(front, 26, 40 + i * 5, 46, 40 + i * 5, .5, 0x60000000);
        if (has(s, "rivets")) for (int i = 0; i < 4; i++) dot(front, 22 + i * 7, 48, 1, 0xffe8ecf2);
        if (has(s, "gear")) {
            for (int a = 0; a < 360; a += 45) dot(front, 32 + Math.cos(Math.toRadians(a)) * 6, 42 + Math.sin(Math.toRadians(a)) * 6, 1.6, c3);
            dot(front, 32, 42, 4.6, c3);
            dot(front, 32, 42, 1.8, 0xff303848);
        }
        if (has(s, "belt")) { line(front, 22, 48, 44, 48, 1.4, 0xff4a3020); dot(front, 33, 48, 1.8, 0xfff0c040); }
        if (has(s, "collar")) { line(front, hx - hr * .6, hy + hr * .8, hx + hr * .6, hy + hr * .8, 1.2, 0xffd84848); dot(front, hx, hy + hr * .8 + 2, 1.4, 0xfff0c040); }
        if (has(s, "throat_sac")) dot(front, fx, hy + hr * .8, 4, 0xfff0d860);
        if (has(s, "fur")) for (int i = 0; i < 8; i++) dot(front, 28 + i * 3, 32 + (i % 2), .8, 0xffffffff);
        if (has(s, "claws")) for (int i = 0; i < 3; i++) { dot(front, 22 + i * 1.6, 59, .7, 0xfff4f0e8); dot(front, 44 + i * 1.6, 59, .7, 0xfff4f0e8); }
        if (has(s, "antennae")) { line(front, hx - 3, top + 2, hx - 7, top - 8, .7, 0xff2a2a30); line(front, hx + 3, top + 2, hx + 7, top - 8, .7, 0xff2a2a30); dot(front, hx - 7, top - 9, 1.6, c3); dot(front, hx + 7, top - 9, 1.6, c3); }
        if (has(s, "lantern")) { for (int i = -1; i <= 1; i++) line(front, hx + i * 7, hy - 8, hx + i * 7, hy + 10, .6, 0xff2a2a3a); dot(front, hx, hy + 2, 5, c3); }
        if (has(s, "nose")) dot(front, fx - hr * .7, hy + 3, 2, 0xfff08aa0);
        // ---------- face
        boolean one = has(s, "single_eye");
        double es = has(s, "big_eyes") ? 1.5 : s.plan.equals("golem") ? .9 : 1;
        if (!has(s, "lantern")) {
            if (one) eye(fx, hy - 1, 2.2 * es, s.c3, true);
            else {
                double sp = s.plan.equals("round") || s.plan.equals("floater") ? hr * .42 : hr * .36;
                eye(fx - sp, hy - 1, es, s.c3, false);
                eye(fx + sp * (side ? .7 : 1), hy - 1, es * (side ? .85 : 1), s.c3, false);
            }
        }
        if (has(s, "cheeks")) { dot(front, fx - hr * .65, hy + 4, 2.2, 0xffe84848); dot(front, fx + hr * .55, hy + 4, 2.2, 0xffe84848); }
        if (has(s, "whiskers")) for (int i = -1; i <= 1; i++) { line(front, fx - hr * .3, hy + 4, fx - hr * 1.1, hy + 3 + i * 2, .4, 0xff3a3030); }
        double my = hy + hr * .45;
        if (!s.plan.equals("bird") && !one) {
            line(front, fx - 2, my, fx + 2, my, .5, 0xff3a2020);
            if (has(s, "fangs")) { poly(front, new double[] {fx - 2, fx - 1, fx}, new double[] {my, my + 3, my}, 0xffffff, 0); poly(front, new double[] {fx + 1, fx + 2, fx + 3}, new double[] {my, my + 3, my}, 0xffffff, 0); }
        }
    }

    void eye(double x, double y, double s, int iris, boolean big) {
        double w = 2.2 * s, h = 2.8 * s;
        for (double dy = -h; dy <= h; dy += .5) for (double dx = -w; dx <= w; dx += .5)
            if ((dx * dx) / (w * w) + (dy * dy) / (h * h) <= 1) setF(x + dx, y + dy, 0xfff8f8f8);
        double pw = w * .62, ph = h * .7;
        for (double dy = -ph; dy <= ph; dy += .5) for (double dx = -pw; dx <= pw; dx += .5)
            if ((dx * dx) / (pw * pw) + (dy * dy) / (ph * ph) <= 1) setF(x + dx - .4, y + dy + .5, big ? 0xff000000 | iris : 0xff1a1a24);
        if (big) dot(front, x - .4, y + .5, s * .8, 0xff1a1a24);
        setF(x - w * .3, y - h * .35, 0xffffffff);
    }

    void setF(double x, double y, int c) {
        int px = (int) Math.round(X(x)), py = (int) Math.round(Y(y));
        if (px >= 0 && py >= 0 && px < N && py < N) front.setRGB(px, py, c);
    }

    // ------------------------------------------------------------ sheet

    public static void main(String[] args) throws Exception {
        List<Spec> all = parse(new File(args[0]));
        File out = new File(args[1]);
        out.mkdirs();
        int cols = 10, cw = 144, ch = 176, top = 60;
        int rows = (all.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cw + 20, rows * ch + top + 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0xeef0e6));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        Font.raw(g, "TALLGRASS v2 - CREATURE ROSTER (" + all.size() + " species, 18 types)", 12, 12, new Color(0x303040), 3);
        Font.raw(g, "Generated from design/creatures.txt: body plan + type parts + colors. Battle sprites are these at 64x64; evolved forms are drawn larger.", 12, 40, new Color(0x606070), 1);
        for (int i = 0; i < all.size(); i++) {
            Spec s = all.get(i);
            int x = 10 + (i % cols) * cw, y = top + (i / cols) * ch;
            int tc = TYPE.getOrDefault(s.types[0], 0x888888);
            g.setColor(new Color(HdArt.mix(0xff000000 | tc, 0xffffffff, .78)));
            g.fillRoundRect(x + 2, y, cw - 6, ch - 8, 12, 12);
            g.setColor(new Color(HdArt.mix(0xff000000 | tc, 0xffffffff, .55)));
            g.fillOval(x + 22, y + 118, 96, 16);
            BufferedImage im = draw(s);
            g.drawImage(im, x + 6, y + 6, 128, 128, null);
            Font.raw(g, String.format("%02d", s.id), x + 8, y + 6, new Color(0x707070), 1);
            Font.raw(g, s.name, x + 8, y + 136, new Color(0x202028), 2);
            int tx = x + 8;
            for (String t : s.types) {
                int c = TYPE.getOrDefault(t, 0x888888);
                int w = Font.width(t.toUpperCase()) + 8;
                g.setColor(new Color(c));
                g.fillRoundRect(tx, y + 154, w, 11, 6, 6);
                Font.raw(g, t.toUpperCase(), tx + 4, y + 156, Color.WHITE, 1);
                tx += w + 3;
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(out, "creatures_sheet.png"));
        System.out.println("species: " + all.size());
    }
}
