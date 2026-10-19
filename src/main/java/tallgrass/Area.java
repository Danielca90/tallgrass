package tallgrass;

import java.awt.Color;
import java.util.*;

/** One map: a route, town, cave or building interior. */
public final class Area {
    public record Warp(String area, int x, int y, int facing) {}
    public record Building(TileArt.Kind kind, int x, int y, int w, int h, int doorX, Color roof) {}

    public final String key;
    public String name;
    public Biome biome;
    public int w, h;
    public Tile[][] t;
    public final List<Npc> npcs = new ArrayList<>();
    public final List<Building> buildings = new ArrayList<>();
    public final Map<Long, Warp> warps = new HashMap<>();
    public final Map<Long, String> signs = new HashMap<>();
    public final boolean[] exits = new boolean[4];
    public final int[] gap = new int[4];
    public boolean interior, town, cave;
    public int ax, ay, level;
    public long seed;

    public Area(String key, int w, int h) {
        this.key = key;
        this.w = w;
        this.h = h;
        t = new Tile[h][w];
        for (Tile[] row : t) Arrays.fill(row, Tile.GRASS);
    }

    public static long k(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h; }
    public Tile at(int x, int y) { return inBounds(x, y) ? t[y][x] : Tile.VOID; }
    public void set(int x, int y, Tile tile) { if (inBounds(x, y)) t[y][x] = tile; }

    public Npc npcAt(int x, int y) {
        for (Npc n : npcs) {
            if (n.x == x && n.y == y) return n;
            if (n.moving() && n.x + Actor.dx(n.moveDir) == x && n.y + Actor.dy(n.moveDir) == y) return n;
        }
        return null;
    }

    public int variant(int x, int y) { return (int) (Rng.hash(seed, x, y) & 3); }
}
