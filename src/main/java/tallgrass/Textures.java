package tallgrass;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The texture pack, Minecraft style. A texture named {@code tree_pine_0} is looked up in order:
 * <ol>
 *   <li>{@code ~/.tallgrass/resourcepack/textures/tree_pine_0.png} (your own edits),</li>
 *   <li>{@code /textures/tree_pine_0.png} inside the jar (baked at build time by {@link TextureBaker}),</li>
 *   <li>generated on the spot by {@link HdArt} (so running from source never needs a bake step).</li>
 * </ol>
 */
public final class Textures {
    private Textures() {}

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final File USER_PACK = new File(System.getProperty("user.home"), ".tallgrass/resourcepack/textures");

    public static synchronized BufferedImage get(String name) {
        BufferedImage im = CACHE.get(name);
        if (im != null) return im;
        im = load(name);
        CACHE.put(name, im);
        return im;
    }

    public static synchronized void reload() { CACHE.clear(); }

    private static BufferedImage load(String name) {
        try {
            File f = new File(USER_PACK, name + ".png");
            if (f.isFile()) return argb(ImageIO.read(f));
        } catch (Exception ignored) {}
        try (InputStream in = Textures.class.getResourceAsStream("/textures/" + name + ".png")) {
            if (in != null) return argb(ImageIO.read(in));
        } catch (Exception ignored) {}
        return make(name);
    }

    private static BufferedImage argb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    // ---------------------------------------------------------------- names

    public static String ground(Biome b) { return "ground_" + b.name().toLowerCase(); }
    public static String path(Biome b) { return "path_" + b.name().toLowerCase(); }
    public static String tall(Biome b, int f) { return "tall_" + b.name().toLowerCase() + "_" + f; }
    public static String tree(Biome.Tree t, int v) { return "tree_" + t.name().toLowerCase() + "_" + v; }
    public static String building(TileArt.Kind k, int w, int h, int door) { return "building_" + k.name().toLowerCase() + "_" + w + "x" + h + "_" + door; }

    /** The buildings the world generator uses: kind, width, height, door column. */
    static final int[][] BUILDINGS = {
            {TileArt.Kind.CENTER.ordinal(), 5, 4, 2}, {TileArt.Kind.HOUSE.ordinal(), 4, 3, 1},
            {TileArt.Kind.GYM.ordinal(), 6, 5, 2}, {TileArt.Kind.LAB.ordinal(), 5, 4, 2}};

    public static List<String> all() {
        List<String> n = new ArrayList<>();
        for (Biome b : Biome.values()) { n.add(ground(b)); n.add(path(b)); n.add(tall(b, 0)); n.add(tall(b, 1)); }
        for (Biome.Tree t : Biome.Tree.values()) for (int v = 0; v < 3; v++) n.add(tree(t, v));
        n.addAll(List.of("sand", "water", "waves_0", "waves_1", "waves_2", "waves_3", "cave_wall", "flowers_0", "flowers_1", "flowers_2", "flowers_3",
                "rock_0", "rock_1", "fence", "sign", "orb", "floor", "wall", "counter", "mat", "rug", "table", "plant", "shelf", "bed"));
        for (int[] b : BUILDINGS) n.add(building(TileArt.Kind.values()[b[0]], b[1], b[2], b[3]));
        return n;
    }

    static BufferedImage make(String name) {
        String[] p = name.split("_");
        switch (p[0]) {
            case "ground": return HdArt.ground(Biome.valueOf(p[1].toUpperCase()));
            case "path": return HdArt.dirt(Biome.valueOf(p[1].toUpperCase()));
            case "tall": return HdArt.tallGrass(Biome.valueOf(p[1].toUpperCase()), Integer.parseInt(p[2]));
            case "tree": {
                String type = name.substring(5, name.lastIndexOf('_')).toUpperCase();
                return HdArt.tree(Biome.Tree.valueOf(type), Integer.parseInt(p[p.length - 1]));
            }
            case "waves": return HdArt.waves(Integer.parseInt(p[1]));
            case "flowers": return HdArt.flowers(Integer.parseInt(p[1]));
            case "rock": return HdArt.rock(Integer.parseInt(p[1]));
            case "building": {
                String[] wh = p[2].split("x");
                return HdArt.building(TileArt.Kind.valueOf(p[1].toUpperCase()), Integer.parseInt(wh[0]), Integer.parseInt(wh[1]), Integer.parseInt(p[3]));
            }
            default:
        }
        return switch (name) {
            case "sand" -> HdArt.sand();
            case "water" -> HdArt.water();
            case "cave" -> HdArt.caveWall();
            case "fence" -> HdArt.fence();
            case "sign" -> HdArt.sign();
            case "orb" -> HdArt.orb();
            case "floor" -> HdArt.floor();
            case "wall" -> HdArt.wall();
            case "counter" -> HdArt.counter();
            case "mat" -> HdArt.mat();
            case "rug" -> HdArt.rug();
            case "table" -> HdArt.table();
            case "plant" -> HdArt.plant();
            case "shelf" -> HdArt.shelf();
            case "bed" -> HdArt.bed();
            default -> name.equals("cave_wall") ? HdArt.caveWall() : missing();
        };
    }

    /** Magenta/black checkerboard, like Minecraft's missing texture. */
    private static BufferedImage missing() {
        BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) im.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xfff800f8 : 0xff000000);
        return im;
    }
}
