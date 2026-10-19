package tallgrass;

import java.util.ArrayList;
import java.util.List;

/** Fixed move list shared by every world. Each elemental type has four tiers of attacks. */
public final class Move {
    public enum Kind { HIT, ATK_DOWN, DEF_DOWN, ATK_UP, DEF_UP, HEAL }

    public final int id;
    public final String name;
    public final Type type;
    public final int power, acc, pp;
    public final Kind kind;

    private static final List<Move> ALL = new ArrayList<>();
    private static final Move[][] TIERS = new Move[Type.values().length][];

    private Move(String name, Type type, int power, int acc, int pp, Kind kind) {
        this.id = ALL.size();
        this.name = name;
        this.type = type;
        this.power = power;
        this.acc = acc;
        this.pp = pp;
        this.kind = kind;
        ALL.add(this);
    }

    public static Move get(int id) { return ALL.get(id); }

    private static void tiers(Type t, String a, String b, String c, String d) {
        TIERS[t.ordinal()] = new Move[] {
                new Move(a, t, 40, 100, 30, Kind.HIT),
                new Move(b, t, 60, 100, 20, Kind.HIT),
                new Move(c, t, 80, 95, 15, Kind.HIT),
                new Move(d, t, 105, 85, 8, Kind.HIT)
        };
    }

    public static final Move TACKLE = new Move("Tackle", Type.NORMAL, 40, 100, 35, Kind.HIT);
    public static final Move SCRATCH = new Move("Scratch", Type.NORMAL, 40, 100, 35, Kind.HIT);
    public static final Move HEADBUTT = new Move("Headbutt", Type.NORMAL, 70, 100, 15, Kind.HIT);
    public static final Move BODY_SLAM = new Move("Body Slam", Type.NORMAL, 85, 100, 15, Kind.HIT);
    public static final Move HYPER_RUSH = new Move("Hyper Rush", Type.NORMAL, 110, 90, 5, Kind.HIT);
    public static final Move GROWL = new Move("Growl", Type.NORMAL, 0, 100, 40, Kind.ATK_DOWN);
    public static final Move LEER = new Move("Leer", Type.NORMAL, 0, 100, 30, Kind.DEF_DOWN);
    public static final Move FOCUS = new Move("Focus", Type.NORMAL, 0, 100, 20, Kind.ATK_UP);
    public static final Move HARDEN = new Move("Harden", Type.NORMAL, 0, 100, 30, Kind.DEF_UP);
    public static final Move RECOVER = new Move("Recover", Type.NORMAL, 0, 100, 10, Kind.HEAL);

    static {
        tiers(Type.EMBER, "Spark Nip", "Flame Lick", "Kiln Burst", "Inferno");
        tiers(Type.TIDE, "Bubble Jet", "Brine Splash", "Rip Current", "Tidal Slam");
        tiers(Type.BLOOM, "Leaf Flick", "Vine Lash", "Thorn Volley", "Grove Hammer");
        tiers(Type.VOLT, "Static Nip", "Arc Flash", "Thunder Peck", "Storm Surge");
        tiers(Type.STONE, "Pebble Toss", "Rock Bash", "Quarry Crush", "Landslide");
        tiers(Type.GALE, "Gust Cut", "Wing Slice", "Squall Dive", "Tempest");
        tiers(Type.SHADE, "Gloom Bite", "Shade Claw", "Night Rend", "Eclipse");
        tiers(Type.FROST, "Rime Shard", "Chill Breath", "Ice Fang", "Blizzard");
        tiers(Type.SPIRIT, "Wisp Touch", "Echo Pulse", "Soul Beam", "Starfall");
        tiers(Type.IRON, "Rivet Jab", "Gear Grind", "Steel Ram", "Anvil Drop");
        TIERS[Type.NORMAL.ordinal()] = new Move[] {TACKLE, HEADBUTT, BODY_SLAM, HYPER_RUSH};
    }

    public static Move tier(Type t, int tier) { return TIERS[t.ordinal()][Math.max(0, Math.min(3, tier))]; }
}
