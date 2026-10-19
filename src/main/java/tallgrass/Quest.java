package tallgrass;

import java.util.ArrayList;
import java.util.List;

/**
 * World-of-Warcraft-style quests. A quest's definition is rebuilt from its id and the world seed, the same
 * way maps are, so a save only stores which quests you hold and how far along each objective is.
 *
 * Ids: {@code main:1..5} is the professor's story chain; {@code side:<areaKey>:<n>} are quests offered by
 * townsfolk and travellers, generated from where they stand.
 */
public final class Quest {
    public enum Kind { DEFEAT, COLLECT, CATCH, TRAINERS, DELIVER, VISIT, BADGE, DEX }

    public static final class Obj {
        public final Kind kind;
        public final Type type;        // DEFEAT / COLLECT / CATCH (null = any)
        public final int count;
        public final String label;     // what the tracker shows
        public final String target;    // DELIVER: center area key; VISIT: area key
        Obj(Kind kind, Type type, int count, String label, String target) {
            this.kind = kind; this.type = type; this.count = count; this.label = label; this.target = target;
        }
    }

    public String id, title, giver, giverArea;
    public final List<String> offer = new ArrayList<>();   // dialogue when offering
    public String reminder, done;                           // while in progress / on turn-in
    public final List<Obj> objs = new ArrayList<>();
    public int money, xp, itemCount;
    public GameState.Item item;
    public String next;                                     // next quest in the chain

    public boolean isMain() { return id.startsWith("main:"); }

    // ---------------------------------------------------------------- generation

    static final String[] DROP = {"Soft Tuft", "Smoldering Scale", "Brine Pearl", "Sweet Petal", "Static Fur", "Glittering Pebble",
            "Downy Feather", "Shadow Wisp", "Frost Shard", "Glowing Mote", "Rusty Gear"};
    static final String[] JOBS = {"Farmer", "Miner", "Old", "Fisher", "Scout", "Herbalist", "Courier", "Ranger", "Collector", "Hunter"};
    static final String[] NAMES = {"Nell", "Rex", "Wren", "Teo", "Mira", "Otis", "Bea", "Sol", "Ada", "Cal", "Juno", "Pell"};

    public static String drop(Type t) { return DROP[t.ordinal()]; }

    /** Rebuilds a quest from its id. */
    public static Quest def(GameState st, WorldGen world, String id) {
        return id.startsWith("main:") ? main(st, world, Integer.parseInt(id.substring(5))) : side(st, world, id);
    }

    private static String northTown(WorldGen w) { return WorldGen.key(0, -3); }

    private static Quest main(GameState st, WorldGen w, int n) {
        Quest q = new Quest();
        q.id = "main:" + n;
        q.giver = "Professor Alder";
        q.giverArea = "lab:0,0";
        String town = w.get(northTown(w)).name;
        switch (n) {
            case 1 -> {
                q.title = "Into the Tall Grass";
                q.offer.add("Professor Alder: Every trainer starts the same way: in the tall grass.");
                q.offer.add("Professor Alder: Defeat 3 wild creatures on the routes outside town. Your partner will grow stronger with every battle.");
                q.objs.add(new Obj(Kind.DEFEAT, null, 3, "Wild creatures defeated", null));
                q.reminder = "Professor Alder: Head into any patch of tall grass outside town. Three battles, then come back!";
                q.done = "Professor Alder: Wonderful! I can see the bond between you two already.";
                q.money = 300; q.xp = 60; q.item = GameState.Item.POTION; q.itemCount = 3; q.next = "main:2";
            }
            case 2 -> {
                q.title = "A Parcel for " + town;
                q.offer.add("Professor Alder: I need a favour. This parcel holds research notes for the healing center in " + town + ", three areas north of here.");
                q.offer.add("Professor Alder: Hand it to the nurse there. The road north is the safest way.");
                q.objs.add(new Obj(Kind.DELIVER, null, 1, "Parcel delivered to " + town, "center:" + northTown(w)));
                q.reminder = "Professor Alder: The parcel goes to the nurse at the " + town + " healing center, straight north.";
                q.done = "Professor Alder: The nurse wrote to say it arrived safely. Thank you, " + st.player + "!";
                q.money = 600; q.xp = 150; q.item = GameState.Item.GREAT_ORB; q.itemCount = 3; q.next = "main:3";
            }
            case 3 -> {
                q.title = "The First Badge";
                q.offer.add("Professor Alder: The gym leaders guard this region's badges. Each one masters a single type.");
                q.offer.add("Professor Alder: Win a badge from any gym and show me. " + town + " has one, if you're looking.");
                q.objs.add(new Obj(Kind.BADGE, null, 1, "Gym badges earned", null));
                q.reminder = "Professor Alder: Any gym will do. Bring a creature with a type that beats the leader's!";
                q.done = "Professor Alder: A real badge! The leaders will start taking you seriously now.";
                q.money = 1000; q.xp = 300; q.item = GameState.Item.REVIVE; q.itemCount = 2; q.next = "main:4";
            }
            case 4 -> {
                q.title = "Field Research";
                q.offer.add("Professor Alder: Every world is home to creatures nobody has recorded. Help me fill the Creaturedex!");
                q.offer.add("Professor Alder: Catch 8 different kinds of creatures. Each direction from here has its own.");
                q.objs.add(new Obj(Kind.DEX, null, 8, "Kinds of creatures caught", null));
                q.reminder = "Professor Alder: Try new directions. Cold north, the sea to the south, dunes east, woods west.";
                q.done = "Professor Alder: Eight! Your notes are already better than mine.";
                q.money = 1500; q.xp = 500; q.item = GameState.Item.GREAT_ORB; q.itemCount = 5; q.next = "main:5";
            }
            default -> {
                q.title = "The Badge Collector";
                q.offer.add("Professor Alder: Trainers say whoever holds four badges can challenge anyone in the region.");
                q.offer.add("Professor Alder: Collect four gym badges. I believe in you.");
                q.objs.add(new Obj(Kind.BADGE, null, 4, "Gym badges earned", null));
                q.reminder = "Professor Alder: Four badges. The farther gyms are tougher, so train on the way.";
                q.done = "Professor Alder: Four badges... You've become a true trainer, " + st.player + ".";
                q.money = 5000; q.xp = 1500; q.item = GameState.Item.REVIVE; q.itemCount = 5; q.next = null;
            }
        }
        return q;
    }

    private static Quest side(GameState st, WorldGen w, String id) {
        String[] p = id.split(":");
        String areaKey = p[1];
        int[] c = WorldGen.coords(areaKey);
        Area a = w.get(areaKey);
        Rng r = new Rng(Rng.hash(id, st.seed));
        Quest q = new Quest();
        q.id = id;
        q.giverArea = areaKey;
        q.giver = r.pick(JOBS) + " " + r.pick(NAMES);
        int lvl = a.level;
        Type local = a.biome.types[r.nextInt(a.biome.types.length)];
        if (local == Type.NORMAL) local = a.biome.types[(r.nextInt(a.biome.types.length - 1) + 1) % a.biome.types.length];
        // a neighbouring biome's type, to send the player somewhere new
        int dir = r.nextInt(4);
        Biome nb = Biome.at(c[0] + Actor.dx(dir) * 2, c[1] + Actor.dy(dir) * 2, st.seed);
        Type far = nb.types[r.nextInt(nb.types.length)];
        String where = new String[] {"north", "south", "west", "east"}[dir];
        int kind = a.town ? r.nextInt(6) : r.nextInt(3);
        String me = q.giver + ": ";
        switch (kind) {
            case 0 -> {
                int n = r.range(4, 6);
                q.title = local.label + " Trouble";
                q.offer.add(me + "Wild " + local.label + "-type creatures keep raiding my stores. I can't keep them away on my own.");
                q.offer.add(me + "Defeat " + n + " of them around here and they'll learn to stay clear.");
                q.objs.add(new Obj(Kind.DEFEAT, local, n, local.label + "-type creatures defeated", null));
                q.reminder = me + "Still " + local.label + "-types everywhere! Tall grass nearby is full of them.";
                q.done = me + "It's finally quiet. You have my thanks, trainer.";
            }
            case 1 -> {
                int n = r.range(3, 5);
                String item = drop(local);
                q.title = "A Handful of " + item + "s";
                q.offer.add(me + "I need " + item + "s for my work. " + local.label + "-type creatures sometimes drop them after a battle.");
                q.offer.add(me + "Bring me " + n + " and I'll make it worth your while.");
                q.objs.add(new Obj(Kind.COLLECT, local, n, item + "s", null));
                q.reminder = me + "Any luck with the " + item + "s? Only " + local.label + "-types carry them.";
                q.done = me + "Perfect, these are exactly what I needed!";
            }
            case 2 -> {
                q.title = "Seen in the " + nb.label;
                q.offer.add(me + "I've heard stories about " + far.label + "-type creatures out in the " + nb.label + " to the " + where + ".");
                q.offer.add(me + "Catch one and come show me. You can keep it, of course.");
                q.objs.add(new Obj(Kind.CATCH, far, 1, far.label + "-type creature caught", null));
                q.reminder = me + "A " + far.label + "-type! Head " + where + " toward the " + nb.label + ".";
                q.done = me + "So they're real! What a beauty.";
            }
            case 3 -> {
                int n = r.range(2, 4);
                q.title = "Prove Your Mettle";
                q.offer.add(me + "You want my respect? Trainers on the roads around here test everyone who passes.");
                q.offer.add(me + "Beat " + n + " of them and we'll talk.");
                q.objs.add(new Obj(Kind.TRAINERS, null, n, "Trainers defeated", null));
                q.reminder = me + "Trainers stand watch along the routes. Walk into their line of sight.";
                q.done = me + "Ha! Word travels fast. Not bad at all.";
            }
            case 4 -> {
                String target = nearestOtherTown(w, c[0], c[1]);
                String tname = w.get(target).name;
                q.title = "Special Delivery";
                q.offer.add(me + "My sister works at the healing center in " + tname + ". Could you take her this letter?");
                q.offer.add(me + "Just hand it to the nurse at the counter.");
                q.objs.add(new Obj(Kind.DELIVER, null, 1, "Letter delivered to " + tname, "center:" + target));
                q.reminder = me + "The letter goes to the nurse in " + tname + ".";
                q.done = me + "She got it? Wonderful. Here, for your trouble.";
            }
            default -> {
                int ax = c[0] + Actor.dx(dir) * 2, ay = c[1] + Actor.dy(dir) * 2;
                String target = WorldGen.key(ax, ay);
                q.title = "Scouting the " + where.substring(0, 1).toUpperCase() + where.substring(1);
                q.offer.add(me + "Nobody has mapped the land two areas " + where + " of here in years. It's said to be " + nb.label + " now.");
                q.offer.add(me + "Go take a look and come back to tell me what you found.");
                q.objs.add(new Obj(Kind.VISIT, null, 1, "Reach the " + nb.label + " to the " + where, target));
                q.reminder = me + "Two areas " + where + ", toward the " + nb.label + ".";
                q.done = me + "That far already? You're braver than me.";
            }
        }
        q.money = lvl * 60 + r.range(0, 10) * 10;
        q.xp = lvl * 18;
        q.item = r.pick(new GameState.Item[] {GameState.Item.SUPER_POTION, GameState.Item.GREAT_ORB, GameState.Item.POTION, GameState.Item.REVIVE});
        q.itemCount = q.item == GameState.Item.REVIVE ? 1 : 2;
        return q;
    }

    private static String nearestOtherTown(WorldGen w, int ax, int ay) {
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -7; dx <= 7; dx++)
            for (int dy = -7; dy <= 7; dy++) {
                if (dx == 0 && dy == 0) continue;
                int x = ax + dx, y = ay + dy;
                if (!w.isTown(x, y)) continue;
                int d = Math.abs(dx) + Math.abs(dy);
                if (d < bestD) { bestD = d; best = WorldGen.key(x, y); }
            }
        return best != null ? best : WorldGen.key(0, 0);
    }
}
