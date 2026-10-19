package tallgrass;

import java.awt.Color;
import java.util.*;

/**
 * Builds the world one area at a time as the player walks into it. Areas sit on an infinite grid around
 * home at (0,0); the biome depends on which way you head, and difficulty rises with distance.
 * Everything is derived from the world seed, so revisiting an area rebuilds it identically.
 */
public final class WorldGen {
    public static final int RW = 28, RH = 20;

    private final GameState st;
    private final Map<String, Area> cache = new HashMap<>();

    public WorldGen(GameState st) { this.st = st; }

    public Area get(String key) {
        Area a = cache.get(key);
        if (a == null) {
            a = key.contains(":") ? interior(key) : outdoor(key);
            cache.put(key, a);
        }
        return a;
    }

    public static String key(int ax, int ay) { return ax + "," + ay; }

    public static int[] coords(String key) {
        String k = key.contains(":") ? key.substring(key.indexOf(':') + 1).split(":")[0] : key;
        String[] p = k.split(",");
        return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    // ---------------------------------------------------------------- world layout rules

    private boolean edgeOpen(int ax, int ay, int dir) {
        int bx = ax + Actor.dx(dir), by = ay + Actor.dy(dir);
        if ((ax == 0 && ay == 0) || (bx == 0 && by == 0)) return true;
        boolean horizontal = dir == Actor.LEFT || dir == Actor.RIGHT;
        if (horizontal && ay == 0) return true;   // the east-west highway
        if (!horizontal && ax == 0) return true;  // the north-south highway
        long h = horizontal ? Rng.hash(st.seed, Math.min(ax, bx), ay, 11) : Rng.hash(st.seed, ax, Math.min(ay, by), 13);
        return Math.floorMod(h, 10) < 7;
    }

    private int gapPos(int ax, int ay, int dir) {
        int bx = ax + Actor.dx(dir), by = ay + Actor.dy(dir);
        boolean horizontal = dir == Actor.LEFT || dir == Actor.RIGHT;
        long h = horizontal ? Rng.hash(st.seed, Math.min(ax, bx), ay, 17) : Rng.hash(st.seed, ax, Math.min(ay, by), 19);
        int len = horizontal ? RH : RW;
        return 4 + (int) Math.floorMod(h, len - 10);
    }

    public boolean isTown(int ax, int ay) {
        int d = Math.abs(ax) + Math.abs(ay);
        if (d == 0) return true;
        if (d == 3 && (ax == 0 || ay == 0)) return true;
        if (d == 7 && (ax == 0 || ay == 0)) return true;
        return d >= 4 && Math.floorMod(Rng.hash(st.seed, ax, ay, 23), 7) == 0;
    }

    private boolean hasGym(int ax, int ay) {
        int d = Math.abs(ax) + Math.abs(ay);
        if (d == 0) return false;
        if (ax == 0 || ay == 0) return true;
        return Math.floorMod(Rng.hash(st.seed, ax, ay, 29), 2) == 0;
    }

    private static int levelFor(int ax, int ay) { return 3 + 3 * (Math.abs(ax) + Math.abs(ay)); }

    private Biome biomeFor(int ax, int ay) {
        Biome b = Biome.at(ax, ay, st.seed);
        if (isTown(ax, ay) && b == Biome.CAVE) b = Biome.MOUNTAIN;
        return b;
    }

    private static final String[] TOWN_A = {"Moss", "Brine", "Cinder", "Frost", "Amber", "Willow", "Stone", "Gale", "Dusk", "Clover", "Tide", "Pine", "Sun", "Hollow", "Briar", "Lark"};
    private static final String[] TOWN_B = {"dale", "wick", "ford", "haven", "port", "field", "ton", "moor", "crest", "brook", "vale", "mouth"};

    private String townName(int ax, int ay) {
        Rng r = new Rng(Rng.hash(st.seed, ax, ay, 31));
        int d = Math.abs(ax) + Math.abs(ay);
        return r.pick(TOWN_A) + r.pick(TOWN_B) + (d >= 6 ? " City" : " Town");
    }

    public String nameOf(Area a) { return a.name; }

    private String hintFor(int ax, int ay, int dir) {
        int bx = ax + Actor.dx(dir), by = ay + Actor.dy(dir);
        Biome b = biomeFor(bx, by);
        String where = new String[] {"north", "south", "west", "east"}[dir];
        if (isTown(bx, by)) return "There's a town to the " + where + ". You can rest there.";
        return switch (b) {
            case MOUNTAIN -> "Head " + where + " and the land climbs into the highlands.";
            case SNOW -> "Up " + where + " it never stops snowing. Bring a warm team.";
            case BEACH -> "Go " + where + " and you'll smell the sea.";
            case DESERT -> "The dunes start " + where + " of here. Bring potions.";
            case CANYON -> "Red canyons lie " + where + ". Tough creatures live there.";
            case FOREST -> "The forest to the " + where + " is full of bug-eyed things.";
            case SWAMP -> "The swamp " + where + " is spooky after dark.";
            case CAVE -> "There's a cave " + where + " of here. It's pitch dark inside.";
            default -> "The meadows " + where + " are gentle. Good for new trainers.";
        };
    }

    // ---------------------------------------------------------------- outdoor areas

    private Area outdoor(String key) {
        int[] c = coords(key);
        int ax = c[0], ay = c[1];
        Area a = new Area(key, RW, RH);
        cache.put(key, a); // register first: building interiors look their exterior up
        a.ax = ax;
        a.ay = ay;
        a.seed = Rng.hash(st.seed, ax, ay);
        a.town = isTown(ax, ay);
        a.biome = biomeFor(ax, ay);
        a.cave = a.biome == Biome.CAVE;
        a.level = levelFor(ax, ay);
        Rng r = new Rng(a.seed);
        Tile wall = a.cave ? Tile.CAVE_WALL : Tile.TREE;
        for (int y = 0; y < a.h; y++)
            for (int x = 0; x < a.w; x++)
                if (x < 2 || y < 2 || x >= a.w - 2 || y >= a.h - 2) a.set(x, y, wall);
        Tile road = a.cave ? Tile.GRASS : Tile.PATH;
        for (int dir = 0; dir < 4; dir++) {
            a.exits[dir] = edgeOpen(ax, ay, dir);
            a.gap[dir] = gapPos(ax, ay, dir);
        }
        if (a.town) {
            a.name = townName(ax, ay);
            buildTown(a, r);
        } else {
            a.name = st.areaNames.computeIfAbsent(key, k -> a.cave ? "Cave " + (char) ('A' + Math.floorMod(Rng.hash(st.seed, ax, ay), 26)) : "Route " + (++st.routeCounter));
            buildRoute(a, r, wall, road);
        }
        carveGaps(a, road);
        return a;
    }

    private void carveGaps(Area a, Tile road) {
        for (int dir = 0; dir < 4; dir++) {
            if (!a.exits[dir]) continue;
            int g = a.gap[dir];
            for (int i = 0; i < 3; i++)
                for (int d = 0; d < 3; d++) {
                    switch (dir) {
                        case Actor.UP -> a.set(g + i, d, road);
                        case Actor.DOWN -> a.set(g + i, a.h - 1 - d, road);
                        case Actor.LEFT -> a.set(d, g + i, road);
                        default -> a.set(a.w - 1 - d, g + i, road);
                    }
                }
        }
    }

    private int[] gapInner(Area a, int dir) {
        int g = a.gap[dir] + 1;
        return switch (dir) {
            case Actor.UP -> new int[] {g, 2};
            case Actor.DOWN -> new int[] {g, a.h - 3};
            case Actor.LEFT -> new int[] {2, g};
            default -> new int[] {a.w - 3, g};
        };
    }

    private static void carve(Area a, int x0, int y0, int x1, int y1, Tile road, boolean verticalFirst) {
        int x = x0, y = y0;
        if (verticalFirst) {
            while (y != y1) { two(a, x, y, road); y += Integer.signum(y1 - y); }
            while (x != x1) { two(a, x, y, road); x += Integer.signum(x1 - x); }
        } else {
            while (x != x1) { two(a, x, y, road); x += Integer.signum(x1 - x); }
            while (y != y1) { two(a, x, y, road); y += Integer.signum(y1 - y); }
        }
        two(a, x, y, road);
    }

    private static void two(Area a, int x, int y, Tile road) {
        if (x < 1 || y < 1 || x >= a.w - 1 || y >= a.h - 1) { a.set(x, y, road); return; }
        a.set(x, y, road);
        a.set(x + 1, y, road);
        a.set(x, y + 1, road);
        a.set(x + 1, y + 1, road);
    }

    private static void blob(Area a, int cx, int cy, int rx, int ry, Tile tile, Rng r, Set<Tile> onlyOn) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++) {
                if (x < 2 || y < 2 || x >= a.w - 2 || y >= a.h - 2) continue;
                double dx = (x - cx) / (rx + .5), dy = (y - cy) / (ry + .5);
                if (dx * dx + dy * dy <= 1 + (r.nextDouble() - .5) * .4 && (onlyOn == null || onlyOn.contains(a.at(x, y)))) a.set(x, y, tile);
            }
    }

    private void buildRoute(Area a, Rng r, Tile wall, Tile road) {
        int cx = a.w / 2 - 1 + r.range(-3, 3), cy = a.h / 2 - 1 + r.range(-2, 2);
        Biome b = a.biome;
        int clumps = r.range(4, 8);
        for (int i = 0; i < clumps; i++) blob(a, r.range(3, a.w - 4), r.range(3, a.h - 4), r.range(0, 2), r.range(0, 2), (a.cave || b == Biome.CANYON) && r.chance(.5) ? Tile.ROCK : wall, r, null);
        int ponds = (b == Biome.BEACH || b == Biome.SWAMP) ? 3 : a.cave ? 1 : r.chance(.35) ? 1 : 0;
        for (int i = 0; i < ponds; i++) blob(a, r.range(4, a.w - 5), r.range(4, a.h - 5), r.range(2, 4), r.range(1, 3), Tile.WATER, r, null);
        for (int i = 0; i < r.range(2, 5); i++) a.set(r.range(3, a.w - 4), r.range(3, a.h - 4), Tile.ROCK);
        if (b == Biome.BEACH) for (int y = 2; y < a.h - 2; y++) for (int x = 2; x < a.w - 2; x++) if (a.at(x, y) == Tile.GRASS && r.chance(.25)) a.set(x, y, Tile.SAND);
        // roads from every exit to the middle
        for (int dir = 0; dir < 4; dir++) {
            if (!a.exits[dir]) continue;
            int[] g = gapInner(a, dir);
            carve(a, g[0], g[1], cx, cy, road, dir == Actor.UP || dir == Actor.DOWN);
        }
        if (!a.cave) {
            Set<Tile> soft = EnumSet.of(Tile.GRASS, Tile.FLOWER, Tile.SAND);
            int patches = r.range(3, 6);
            for (int i = 0; i < patches; i++) {
                Set<Tile> on = i == 0 ? EnumSet.of(Tile.GRASS, Tile.FLOWER, Tile.SAND, Tile.PATH) : soft;
                blob(a, r.range(3, a.w - 4), r.range(3, a.h - 4), r.range(2, 4), r.range(1, 3), Tile.TALL, r, on);
            }
            if (b != Biome.DESERT && b != Biome.SNOW && b != Biome.CANYON)
                for (int i = 0; i < 10; i++) {
                    int x = r.range(2, a.w - 3), y = r.range(2, a.h - 3);
                    if (a.at(x, y) == Tile.GRASS) a.set(x, y, Tile.FLOWER);
                }
        }
        boolean[][] reach = flood(a, cx, cy);
        List<int[]> spots = new ArrayList<>();
        for (int y = 4; y < a.h - 4; y++)
            for (int x = 4; x < a.w - 4; x++)
                if (reach[y][x] && (a.at(x, y) == Tile.GRASS || a.at(x, y) == Tile.PATH || a.at(x, y) == Tile.SAND)) spots.add(new int[] {x, y});
        Collections.shuffle(spots, new Random(a.seed));
        // route sign beside the first open exit
        for (int dir = 0; dir < 4; dir++) {
            if (!a.exits[dir]) continue;
            int[] g = gapInner(a, dir);
            int sx = g[0] + (dir == Actor.UP || dir == Actor.DOWN ? -2 : dir == Actor.LEFT ? 1 : -1);
            int sy = g[1] + (dir == Actor.UP ? 1 : dir == Actor.DOWN ? -1 : -2);
            if (a.at(sx, sy) == Tile.GRASS || a.at(sx, sy) == Tile.FLOWER) {
                a.set(sx, sy, Tile.SIGN);
                StringBuilder sb = new StringBuilder(a.name.toUpperCase());
                for (int d2 = 0; d2 < 4; d2++) if (a.exits[d2]) sb.append("\n").append(new String[] {"North", "South", "West", "East"}[d2]).append(": ").append(peek(a.ax + Actor.dx(d2), a.ay + Actor.dy(d2)));
                a.signs.put(Area.k(sx, sy), sb.toString());
            }
            break;
        }
        int d = Math.abs(a.ax) + Math.abs(a.ay);
        int trainers = Math.min(3, 1 + r.nextInt(1 + d / 2));
        int si = 0;
        for (int i = 0; i < trainers && si < spots.size(); i++) {
            int[] p = spots.get(si++);
            Npc n = trainer(a, r, p[0], p[1], "tr:" + a.key + ":" + i);
            if (n != null) a.npcs.add(n);
        }
        if (r.chance(.65) && si < spots.size()) {
            int[] p = spots.get(si++);
            String flag = "item:" + a.key + ":0";
            if (!st.flags.contains(flag)) {
                Npc it = new Npc(p[0], p[1], null);
                it.kind = Npc.Kind.ITEM;
                it.ball = true;
                it.flag = flag;
                double roll = r.nextDouble();
                it.item = roll < .35 ? GameState.Item.ORB : roll < .65 ? GameState.Item.POTION : roll < .8 ? (d >= 3 ? GameState.Item.SUPER_POTION : GameState.Item.POTION) : roll < .93 ? (d >= 3 ? GameState.Item.GREAT_ORB : GameState.Item.ORB) : GameState.Item.REVIVE;
                it.itemCount = it.item == GameState.Item.ORB ? 3 : 1;
                a.npcs.add(it);
            }
        }
        if (r.chance(.35) && si < spots.size()) {
            int[] p = spots.get(si++);
            a.npcs.add(questGiver(p[0], p[1], "side:" + a.key + ":0"));
        }
        if (r.chance(.6) && si < spots.size()) {
            int[] p = spots.get(si);
            Npc n = new Npc(p[0], p[1], CharArt.random(r));
            n.wander = true;
            int dir = r.nextInt(4);
            for (int tries = 0; tries < 4 && !a.exits[dir]; tries++) dir = (dir + 1) % 4;
            n.lines.add(hintFor(a.ax, a.ay, dir));
            n.lines.add(r.pick(new String[] {"Wild creatures hide in the tall grass. Walk through it and they'll jump out!",
                    "Weaken a wild creature before you throw an orb. It works better.",
                    "Hold B to run. My grandpa says running is how champions train.",
                    "The farther you go from home, the stronger the creatures get.",
                    "If your creatures faint, you'll wake up at the last healing center you visited."}));
            a.npcs.add(n);
        }
    }

    private Npc questGiver(int x, int y, String questId) {
        Npc n = new Npc(x, y, CharArt.random(new Rng(Rng.hash(questId, st.seed, 3))));
        n.kind = Npc.Kind.QUEST;
        n.questId = questId;
        n.facing = Actor.DOWN;
        return n;
    }

    private String peek(int ax, int ay) {
        if (isTown(ax, ay)) return townName(ax, ay);
        return biomeFor(ax, ay).label;
    }

    private static boolean[][] flood(Area a, int sx, int sy) {
        boolean[][] seen = new boolean[a.h][a.w];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (!a.at(sx, sy).solid) { q.add(new int[] {sx, sy}); seen[sy][sx] = true; }
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int d = 0; d < 4; d++) {
                int x = p[0] + Actor.dx(d), y = p[1] + Actor.dy(d);
                if (!a.inBounds(x, y) || seen[y][x] || a.at(x, y).solid) continue;
                seen[y][x] = true;
                q.add(new int[] {x, y});
            }
        }
        return seen;
    }

    // ---------------------------------------------------------------- trainers

    private static final String[] CLASSES = {"Youngster", "Lass", "Hiker", "Camper", "Picnicker", "Bug Fan", "Swimmer", "Ace Trainer", "Bird Keeper", "Scientist", "Ruin Maniac", "Fisher"};
    private static final String[] FIRST = {"Joey", "Mira", "Tobin", "Ada", "Rex", "Nell", "Otis", "Pia", "Quinn", "Sol", "Teo", "Una", "Vic", "Wren", "Yara", "Zed", "Bea", "Cal"};
    private static final String[] TAUNTS = {"Our eyes met! That means we battle!", "I've been training in this grass all week!",
            "You look like you just left home. Let's see what you've got!", "My creatures are tougher than they look!",
            "Hey! You can't pass without battling me!", "I caught my partner right here. Want to see?"};
    private static final String[] AFTER = {"You're really strong. Keep going!", "I need to train more...", "Losing to you was a good lesson.",
            "Next time I'll pick a type that beats yours!"};

    private Npc trainer(Area a, Rng r, int x, int y, String flag) {
        Npc n = new Npc(x, y, CharArt.random(r));
        n.kind = Npc.Kind.TRAINER;
        n.trainerName = r.pick(CLASSES) + " " + r.pick(FIRST);
        n.flag = flag;
        n.facing = r.nextInt(4);
        n.sight = 4;
        n.lines.add(r.pick(TAUNTS));
        n.afterLines.add(r.pick(AFTER));
        int d = Math.abs(a.ax) + Math.abs(a.ay);
        int count = 1 + r.nextInt(Math.min(3, 1 + d / 2));
        List<Integer> pool = st.dex.pools.get(a.biome);
        for (int i = 0; i < count; i++) {
            int lv = Math.max(2, a.level + r.range(-2, 1));
            n.team.add(new int[] {evolveFor(r.pick(pool), lv), lv});
        }
        n.prize = a.level * 40 * count;
        return n;
    }

    /** Walks a species up its evolution line to match a level. */
    public int evolveFor(int species, int level) {
        Species s = st.dex.get(species);
        while (s.evolvesTo > 0 && level >= s.evolveLevel + 2) s = st.dex.get(s.evolvesTo);
        return s.id;
    }

    /** Picks a wild creature for an area. */
    public Mon wild(Area a, Rng r) {
        List<Integer> pool = st.dex.pools.get(a.biome);
        int lv = Math.max(2, a.level + r.range(-2, 1));
        if (a.ax == 0 && Math.abs(a.ay) <= 1 || a.ay == 0 && Math.abs(a.ax) <= 1) lv = r.range(2, 5);
        int sp = pool.get(Math.min(pool.size() - 1, (int) (Math.pow(r.nextDouble(), 1.6) * pool.size())));
        return new Mon(evolveFor(sp, lv), lv);
    }

    // ---------------------------------------------------------------- towns

    private void building(Area a, TileArt.Kind kind, int x, int y, int w, int h, int doorX, Color roof, String interiorKey) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) a.set(xx, yy, Tile.BLD);
        int dx = x + doorX, dy = y + h - 1;
        a.set(dx, dy, Tile.DOOR);
        a.set(dx, dy + 1, Tile.PATH);
        a.buildings.add(new Area.Building(kind, x, y, w, h, doorX, roof));
        Area inner = get(interiorKey);
        int[] mat = matOf(inner);
        a.warps.put(Area.k(dx, dy), new Area.Warp(interiorKey, mat[0], mat[1] - 1, Actor.UP));
        inner.warps.put(Area.k(mat[0], mat[1]), new Area.Warp(a.key, dx, dy + 1, Actor.DOWN));
    }

    private static int[] matOf(Area inner) {
        for (int y = 0; y < inner.h; y++) for (int x = 0; x < inner.w; x++) if (inner.at(x, y) == Tile.MAT) return new int[] {x, y};
        return new int[] {inner.w / 2, inner.h - 1};
    }

    private void buildTown(Area a, Rng r) {
        int cx = 13, cy = 10;
        // ring road just inside the border + a cross through the middle + a lower street
        for (int x = 2; x < a.w - 2; x++) { a.set(x, 2, Tile.PATH); a.set(x, a.h - 3, Tile.PATH); a.set(x, cy, Tile.PATH); a.set(x, cy + 1, Tile.PATH); a.set(x, 16, Tile.PATH); }
        for (int y = 2; y < a.h - 2; y++) { a.set(2, y, Tile.PATH); a.set(a.w - 3, y, Tile.PATH); a.set(cx, y, Tile.PATH); a.set(cx + 1, y, Tile.PATH); }
        boolean home = a.ax == 0 && a.ay == 0;
        Color roofHouse = Color.getHSBColor((float) r.nextDouble(), .55f, .7f);
        building(a, TileArt.Kind.CENTER, 3, 5, 5, 4, 2, new Color(0xe05050), "center:" + a.key);
        if (hasGym(a.ax, a.ay)) building(a, TileArt.Kind.GYM, 18, 4, 6, 5, 2, new Color(0x6878a0), "gym:" + a.key);
        else building(a, TileArt.Kind.HOUSE, 19, 6, 4, 3, 1, roofHouse, "house:" + a.key + ":0");
        if (home) {
            building(a, TileArt.Kind.HOUSE, 3, 13, 4, 3, 1, new Color(0xd07838), "home:" + a.key);
            building(a, TileArt.Kind.LAB, 19, 12, 5, 4, 2, new Color(0x90a0b8), "lab:" + a.key);
        } else {
            building(a, TileArt.Kind.HOUSE, 3, 13, 4, 3, 1, Gfx.mix(roofHouse, new Color(0x3858a0), .5), "house:" + a.key + ":1");
            building(a, TileArt.Kind.HOUSE, 20, 13, 4, 3, 1, roofHouse, "house:" + a.key + ":2");
        }
        // decoration
        for (int i = 0; i < 24; i++) {
            int x = r.range(3, a.w - 4), y = r.range(3, a.h - 4);
            if (a.at(x, y) == Tile.GRASS && a.at(x, y + 1) != Tile.DOOR) a.set(x, y, r.chance(.7) ? Tile.FLOWER : Tile.GRASS);
        }
        for (int x = 8; x <= 11; x++) if (a.at(x, 6) == Tile.GRASS) a.set(x, 6, Tile.FENCE);
        for (int x = 16; x <= 17; x++) if (a.at(x, 14) == Tile.GRASS) a.set(x, 14, Tile.TREE);
        if (a.at(cx + 2, cy - 1) == Tile.GRASS) {
            a.set(cx + 2, cy - 1, Tile.SIGN);
            a.signs.put(Area.k(cx + 2, cy - 1), a.name.toUpperCase() + "\n" + (home ? "Where every journey starts." : "Rest up at the healing center before you head out."));
        }
        // quest givers: one in every town, a second in bigger towns farther out
        int givers = Math.abs(a.ax) + Math.abs(a.ay) >= 4 ? 2 : 1;
        for (int i = 0; i < givers; i++) {
            int qx = i == 0 ? 10 : 17, qy = 12;
            if (a.at(qx, qy).solid) continue;
            a.npcs.add(questGiver(qx, qy, "side:" + a.key + ":" + i));
        }
        // townsfolk
        int folks = 3;
        for (int i = 0; i < folks; i++) {
            int x = i == 0 ? cx - 4 : i == 1 ? cx + 4 : cx;
            int y = i == 2 ? 14 : cy + (i == 0 ? 0 : 1);
            Npc n = new Npc(x, y, CharArt.random(r));
            n.wander = true;
            int dir = i % 4;
            for (int tries = 0; tries < 4 && !a.exits[dir]; tries++) dir = (dir + 1) % 4;
            n.lines.add(hintFor(a.ax, a.ay, dir));
            if (home && i == 0) n.lines.add("The professor's lab is the building with the gray roof.");
            else if (hasGym(a.ax, a.ay) && i == 1) n.lines.add("The gym leader here uses " + gymType(a).label + "-type creatures. Bring something that beats them!");
            else n.lines.add(r.pick(new String[] {"The healing center fixes up your team for free. The clerk sells orbs too.",
                    "Every gym leader gives a badge. Collect them all!", "I heard there are creatures out there no one has ever recorded.",
                    "When a creature grows strong enough, it can evolve!"}));
            a.npcs.add(n);
        }
    }

    private Type gymType(Area a) {
        Biome b = biomeFor(a.ax, a.ay);
        Type t = b.mainType();
        if (t == Type.NORMAL) t = b.types[1];
        return t;
    }

    // ---------------------------------------------------------------- interiors

    private Area interior(String key) {
        String kind = key.substring(0, key.indexOf(':'));
        int[] c = coords(key);
        Area outside = get(key(c[0], c[1]));
        Area already = cache.get(key);
        if (already != null) return already; // built while the exterior was being generated
        Area a;
        Rng r = new Rng(Rng.hash(st.seed, key.hashCode()));
        switch (kind) {
            case "center" -> {
                a = room(key, 13, 10);
                for (int x = 2; x <= 10; x++) a.set(x, 4, Tile.COUNTER);
                a.set(1, 2, Tile.PLANT); a.set(11, 2, Tile.PLANT); a.set(1, 8, Tile.PLANT); a.set(11, 8, Tile.PLANT);
                for (int x = 5; x <= 7; x++) for (int y = 6; y <= 8; y++) a.set(x, y, Tile.RUG);
                a.set(6, 9, Tile.MAT);
                Npc nurse = new Npc(5, 3, CharArt.NURSE);
                nurse.kind = Npc.Kind.NURSE;
                Npc clerk = new Npc(9, 3, CharArt.CLERK);
                clerk.kind = Npc.Kind.CLERK;
                a.npcs.add(nurse);
                a.npcs.add(clerk);
                Npc visitor = new Npc(2, 7, CharArt.random(r));
                visitor.lines.add("The nurse on the left heals your team. The clerk on the right runs the shop.");
                a.npcs.add(visitor);
                a.name = outside.name;
            }
            case "gym" -> {
                a = room(key, 11, 14);
                Type t = gymType(outside);
                for (int y = 4; y <= 10; y += 3) { a.set(2, y, Tile.PLANT); a.set(8, y, Tile.PLANT); }
                for (int y = 3; y <= 12; y++) a.set(5, y, Tile.RUG);
                a.set(5, 13, Tile.MAT);
                int d = Math.abs(outside.ax) + Math.abs(outside.ay);
                Npc leader = new Npc(5, 2, CharArt.random(r));
                leader.kind = Npc.Kind.LEADER;
                leader.leaderType = t;
                leader.badge = t.label + " Badge";
                leader.trainerName = "Leader " + r.pick(FIRST);
                leader.flag = "gym:" + outside.key;
                leader.facing = Actor.DOWN;
                leader.lines.add("Welcome to the " + outside.name + " gym! I am " + leader.trainerName.substring(7) + ", master of " + t.label + "-type creatures.");
                leader.lines.add("Show me what you've learned out there!");
                leader.afterLines.add("Your bond with your creatures is real. Take the " + leader.badge + "!");
                int count = Math.min(4, 2 + d / 3);
                List<Integer> typed = new ArrayList<>();
                for (Species s : st.dex.all) if (s.stage == 0 && s.has(t)) typed.add(s.id);
                if (typed.isEmpty()) typed.addAll(st.dex.pools.get(outside.biome));
                for (int i = 0; i < count; i++) {
                    int lv = outside.level + 1 + i;
                    leader.team.add(new int[] {evolveFor(typed.get(r.nextInt(typed.size())), lv), lv});
                }
                leader.prize = outside.level * 120;
                a.npcs.add(leader);
                for (int i = 0; i < 2; i++) {
                    Npc tr = trainer(outside, r, i == 0 ? 3 : 7, i == 0 ? 9 : 6, "gymtr:" + outside.key + ":" + i);
                    tr.facing = i == 0 ? Actor.RIGHT : Actor.LEFT;
                    tr.sight = 3;
                    tr.trainerName = "Gym Ace " + r.pick(FIRST);
                    tr.team.clear();
                    int lv = outside.level;
                    tr.team.add(new int[] {evolveFor(typed.get(r.nextInt(typed.size())), lv), lv});
                    a.npcs.add(tr);
                }
                a.name = outside.name + " Gym";
            }
            case "lab" -> {
                a = room(key, 11, 9);
                for (int x = 1; x <= 9; x++) if (x != 5) a.set(x, 2, Tile.SHELF);
                a.set(2, 5, Tile.TABLE); a.set(3, 5, Tile.TABLE);
                for (int i = 0; i < 3; i++) {
                    a.set(6 + i, 4, Tile.TABLE);
                    if (st.flags.contains("starter:" + i)) continue; // already taken
                    Npc orb = new Npc(6 + i, 4, null);
                    orb.kind = Npc.Kind.STARTER;
                    orb.ball = true;
                    orb.species = st.dex.starters[i];
                    orb.flag = "starter:" + i;
                    a.npcs.add(orb);
                }
                a.set(5, 8, Tile.MAT);
                Npc prof = new Npc(5, 3, CharArt.PROF);
                prof.kind = Npc.Kind.PROF;
                prof.facing = Actor.DOWN;
                a.npcs.add(prof);
                a.name = "Professor's Lab";
            }
            case "home" -> {
                a = room(key, 9, 8);
                a.set(1, 2, Tile.SHELF); a.set(2, 2, Tile.SHELF);
                a.set(7, 2, Tile.BED);
                a.set(4, 4, Tile.TABLE);
                a.set(4, 7, Tile.MAT);
                Npc mom = new Npc(5, 4, CharArt.MOM);
                mom.kind = Npc.Kind.MOM;
                mom.facing = Actor.LEFT;
                a.npcs.add(mom);
                a.name = "Home";
            }
            default -> { // house
                a = room(key, 9, 8);
                a.set(1, 2, Tile.SHELF);
                a.set(7, 2, Tile.BED);
                a.set(3, 4, Tile.TABLE); a.set(4, 4, Tile.TABLE);
                for (int x = 3; x <= 5; x++) a.set(x, 6, Tile.RUG);
                a.set(4, 7, Tile.MAT);
                Npc n = new Npc(r.chance(.5) ? 2 : 6, 5, CharArt.random(r));
                String flag = "gift:" + key;
                if (r.chance(.45) && !st.flags.contains(flag)) {
                    n.kind = Npc.Kind.GIFT;
                    n.flag = flag;
                    n.item = r.pick(new GameState.Item[] {GameState.Item.SUPER_POTION, GameState.Item.GREAT_ORB, GameState.Item.REVIVE, GameState.Item.POTION});
                    n.lines.add("You're a trainer, right? My kid left on a journey too. Here, take this!");
                } else {
                    n.lines.add(r.pick(new String[] {"Make yourself at home! Just don't track mud in.",
                            "My grandma says creatures can sense when you're sad.",
                            "Did you know some creatures only appear far from home?",
                            "I used to be a trainer. Now I just watch the gym battles."}));
                }
                a.npcs.add(n);
                a.name = outside.name;
            }
        }
        a.interior = true;
        a.biome = outside.biome;
        a.ax = outside.ax;
        a.ay = outside.ay;
        a.level = outside.level;
        a.seed = Rng.hash(st.seed, key.hashCode(), 5);
        return a;
    }

    /** New worlds start in your bedroom at home, with the healing center as the respawn point. */
    public void spawn(GameState s) {
        Area town = get(key(0, 0));
        for (Area.Building b : town.buildings) {
            int dx = b.x() + b.doorX(), dy = b.y() + b.h() - 1;
            Area.Warp w = town.warps.get(Area.k(dx, dy));
            if (w != null && w.area().startsWith("center:")) { s.healArea = town.key; s.healX = dx; s.healY = dy + 1; }
        }
        s.area = "home:" + town.key;
        get(s.area);
        s.px = 6;
        s.py = 3;
        s.facing = Actor.DOWN;
    }

    private static Area room(String key, int w, int h) {
        Area a = new Area(key, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                a.set(x, y, y < 2 ? Tile.WALL : Tile.FLOOR);
        return a;
    }
}
