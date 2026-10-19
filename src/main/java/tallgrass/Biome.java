package tallgrass;

import java.awt.Color;

/** Regions of the world. Which one you walk into depends on the direction you head from home. */
public enum Biome {
    MEADOW("Meadow", 0x78c850, 0x68b040, 0x48a038, 0xc8a870, Tree.ROUND, new Type[] {Type.NORMAL, Type.BLOOM, Type.GALE, Type.VOLT}, 0x98d8f8),
    FOREST("Forest", 0x58a848, 0x489838, 0x307828, 0xa88858, Tree.ROUND_DARK, new Type[] {Type.BLOOM, Type.SHADE, Type.GALE, Type.NORMAL}, 0x88c8a0),
    SWAMP("Swamp", 0x6a8a50, 0x5a7a44, 0x40683a, 0x806848, Tree.WILLOW, new Type[] {Type.TIDE, Type.SHADE, Type.BLOOM, Type.SPIRIT}, 0x90a8a0),
    MOUNTAIN("Highlands", 0x98a868, 0x889858, 0x688848, 0xa89880, Tree.PINE, new Type[] {Type.STONE, Type.GALE, Type.IRON, Type.NORMAL}, 0xa8c8e8),
    SNOW("Snowfield", 0xe8f0f8, 0xd0e0f0, 0xa8c8d8, 0xc8d0e0, Tree.SNOW_PINE, new Type[] {Type.FROST, Type.IRON, Type.SPIRIT, Type.GALE}, 0xc8d8f0),
    BEACH("Coast", 0xe8d898, 0xd8c880, 0x88b850, 0xd8c078, Tree.PALM, new Type[] {Type.TIDE, Type.GALE, Type.STONE, Type.VOLT}, 0x88d0f8),
    DESERT("Dunes", 0xe8c880, 0xd8b870, 0xb8a050, 0xc8a060, Tree.CACTUS, new Type[] {Type.STONE, Type.EMBER, Type.SHADE, Type.IRON}, 0xf8d8a0),
    CANYON("Canyon", 0xd09060, 0xc08050, 0xa87040, 0xb87850, Tree.BOULDER, new Type[] {Type.EMBER, Type.STONE, Type.IRON, Type.VOLT}, 0xf0b888),
    CAVE("Cave", 0x786858, 0x685848, 0x585040, 0x887868, Tree.BOULDER, new Type[] {Type.STONE, Type.SHADE, Type.IRON, Type.FROST}, 0x403830);

    public enum Tree { ROUND, ROUND_DARK, WILLOW, PINE, SNOW_PINE, PALM, CACTUS, BOULDER }

    public final String label;
    public final Color ground, ground2, tall, path, sky;
    public final Tree tree;
    public final Type[] types;

    Biome(String label, int ground, int ground2, int tall, int path, Tree tree, Type[] types, int sky) {
        this.label = label;
        this.ground = new Color(ground);
        this.ground2 = new Color(ground2);
        this.tall = new Color(tall);
        this.path = new Color(path);
        this.tree = tree;
        this.types = types;
        this.sky = new Color(sky);
    }

    /** The world grows outward from home (0,0): north gets colder, south reaches the sea, east dries out, west grows wild. */
    public static Biome at(int ax, int ay, long seed) {
        int d = Math.abs(ax) + Math.abs(ay);
        if (d <= 1) return MEADOW;
        boolean far = d >= 4;
        Biome b;
        if (Math.abs(ay) >= Math.abs(ax)) b = ay < 0 ? (far ? SNOW : MOUNTAIN) : BEACH;
        else b = ax > 0 ? (far ? CANYON : DESERT) : (far ? SWAMP : FOREST);
        if ((b == MOUNTAIN || b == SNOW || b == CANYON) && Math.floorMod(Rng.hash(seed, ax, ay, 77), 3) == 0) return CAVE;
        return b;
    }

    public Type mainType() { return types[0]; }
}
