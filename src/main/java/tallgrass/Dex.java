package tallgrass;

import java.awt.Color;
import java.io.Serializable;
import java.util.*;

/** Generates every species for a world seed, grouped into evolution families and biome pools. */
public final class Dex implements Serializable {
    private static final long serialVersionUID = 1L;
    public static Dex current;

    public final List<Species> all = new ArrayList<>();
    public final EnumMap<Biome, List<Integer>> pools = new EnumMap<>(Biome.class);
    public final int[] starters = new int[3];
    private final Set<String> names = new HashSet<>();

    private static final Map<Type, String[]> STEMS = new EnumMap<>(Type.class);
    static {
        STEMS.put(Type.NORMAL, new String[] {"Pip", "Bun", "Fluff", "Wig", "Mun", "Tup", "Nib", "Pud"});
        STEMS.put(Type.EMBER, new String[] {"Cind", "Pyr", "Ash", "Blaz", "Emb", "Sear", "Char", "Kiln"});
        STEMS.put(Type.TIDE, new String[] {"Brin", "Aqu", "Rip", "Mar", "Tid", "Splash", "Kelp", "Wav"});
        STEMS.put(Type.BLOOM, new String[] {"Fern", "Bud", "Thorn", "Moss", "Pet", "Sap", "Clov", "Bram"});
        STEMS.put(Type.VOLT, new String[] {"Zap", "Volt", "Jolt", "Spar", "Watt", "Ion", "Fizz", "Amp"});
        STEMS.put(Type.STONE, new String[] {"Peb", "Rok", "Grav", "Bould", "Shal", "Crag", "Flint", "Tor"});
        STEMS.put(Type.GALE, new String[] {"Gust", "Zeph", "Aer", "Sky", "Puff", "Flit", "Swoo", "Cirr"});
        STEMS.put(Type.SHADE, new String[] {"Gloom", "Murk", "Shad", "Dusk", "Noct", "Umbr", "Grim", "Vex"});
        STEMS.put(Type.FROST, new String[] {"Frost", "Rime", "Glac", "Chill", "Sleet", "Brr", "Floe", "Hail"});
        STEMS.put(Type.SPIRIT, new String[] {"Wisp", "Lum", "Glim", "Aur", "Fae", "Mys", "Halo", "Sprit"});
        STEMS.put(Type.IRON, new String[] {"Rivet", "Ferr", "Cog", "Bolt", "Alloy", "Tin", "Gear", "Chrom"});
    }
    private static final String[] SMALL = {"let", "by", "kit", "ling", "pup", "ee", "o", "ip", "sy", "mite", "bit", "chu"};
    private static final String[] MID = {"or", "on", "ax", "ard", "ros", "ion", "eon", "ish", "ane", "ix"};
    private static final String[] BIG = {"king", "drake", "maw", "titan", "lord", "golem", "zard", "gon", "rex", "mane"};
    private static final String[] CATEGORY = {"Pebble", "Ember", "Tide", "Sprout", "Spark", "Crag", "Breeze", "Dusk", "Rime", "Wisp", "Rivet"};

    public static Dex generate(long seed) {
        Dex d = new Dex();
        Rng r = new Rng(Rng.hash(seed, 1));
        Type[] st = {Type.BLOOM, Type.EMBER, Type.TIDE};
        Species.Shape[] ss = {Species.Shape.QUAD, Species.Shape.BIPED, Species.Shape.QUAD};
        for (int i = 0; i < 3; i++) {
            d.starters[i] = d.family(r, st[i], r.chance(.3) ? Type.NORMAL : null, ss[i], 3, 280, new int[] {16, 34});
            d.useV2Starter(i);
        }
        for (Biome b : Biome.values()) {
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Type t1 = b.types[i % b.types.length];
                Type t2 = r.chance(.35) ? b.types[(i + 1 + r.nextInt(b.types.length - 1)) % b.types.length] : null;
                if (t2 == t1) t2 = null;
                Species.Shape shape = r.pick(Species.Shape.values());
                boolean three = i == 0 && r.chance(.5);
                int first = d.family(r, t1, t2, shape, three ? 3 : 2, 250 + r.nextInt(50), three ? new int[] {18, 36} : new int[] {18 + r.nextInt(10)});
                pool.add(first);
                if (i >= 3) pool.add(first + 1); // a few evolved forms show up in the wild too
            }
            d.pools.put(b, pool);
        }
        return d;
    }

    /** The v2 roster's starter lines (design/creatures.txt): fixed names and colours, so they pick up their art. */
    private static final String[][] V2_STARTERS = {
            {"Sproutle", "6cc24a f4f0c4 2f8a3a"}, {"Thornox", "4fae46 f0eabc d9506a"}, {"Grovemaw", "3f8f3c d8c89a 8a5a30"},
            {"Cindrel", "f07830 f8e2a8 ffd040"}, {"Blazert", "e45a28 f6d8a0 ffcf3a"}, {"Pyrothane", "d84424 f2cf94 3a6ad0"},
            {"Pudlet", "58aee8 f4e2b0 a8784a"}, {"Tidalon", "3f8ed8 f0dcaa 8a6440"}, {"Krakashell", "2f6fc0 e8d6a4 9aa4b4"},
    };

    private void useV2Starter(int line) {
        for (int st = 0; st < 3; st++) {
            Species s = get(starters[line] + st);
            String[] v = V2_STARTERS[line * 3 + st];
            names.remove(s.name);
            s.name = v[0];
            names.add(v[0]);
            String[] c = v[1].split(" ");
            s.c1 = Integer.parseInt(c[0], 16);
            s.c2 = Integer.parseInt(c[1], 16);
            s.c3 = Integer.parseInt(c[2], 16);
        }
    }

    private String name(Rng r, Type t, int stage, String stem) {
        String[] suf = stage == 0 ? SMALL : stage == 1 ? MID : BIG;
        for (int tries = 0; tries < 40; tries++) {
            String n = stem + r.pick(suf);
            if (n.length() > 10) n = n.substring(0, 10);
            if (names.add(n)) return n;
            stem = r.pick(STEMS.get(t));
        }
        String n = stem + names.size();
        names.add(n);
        return n;
    }

    private int family(Rng r, Type t1, Type t2, Species.Shape shape, int stages, int baseTotal, int[] evoLv) {
        String stem = r.pick(STEMS.get(t1));
        float[] hsb = Color.RGBtoHSB(t1.color.getRed(), t1.color.getGreen(), t1.color.getBlue(), null);
        float hue = (float) (hsb[0] + (r.nextDouble() - .5) * .12);
        float sat = (float) Math.min(.85, Math.max(.35, hsb[1] * (0.8 + r.nextDouble() * .4)));
        float bri = (float) (0.72 + r.nextDouble() * .22);
        int c1 = Color.HSBtoRGB(hue, sat, bri) & 0xffffff;
        int c2 = r.chance(.5) ? Color.HSBtoRGB(hue, sat * .3f, .97f) & 0xffffff : 0xf0e8c8;
        Type accentT = t2 != null ? t2 : Type.values()[r.nextInt(Type.values().length)];
        float[] h2 = Color.RGBtoHSB(accentT.color.getRed(), accentT.color.getGreen(), accentT.color.getBlue(), null);
        int c3 = Color.HSBtoRGB(r.chance(.5) ? h2[0] : hue + .5f, .7f, .9f) & 0xffffff;
        // stat personality from shape
        double[] w = switch (shape) {
            case BIPED -> new double[] {1, 1.25, .9, 1};
            case QUAD -> new double[] {1.15, 1, 1.15, .9};
            case BLOB -> new double[] {1.4, .9, 1, .7};
            case SERPENT -> new double[] {1, 1.1, .9, 1.2};
            case BIRD -> new double[] {.9, 1, .8, 1.4};
            case FLOATER -> new double[] {1, 1.05, 1.2, .9};
        };
        for (int i = 0; i < 4; i++) w[i] *= 0.85 + r.nextDouble() * .3;
        double ws = w[0] + w[1] + w[2] + w[3];
        // learnset is shared by the family
        Type second = t2 != null ? t2 : Type.NORMAL;
        int[] lv = {1, 1, 5, 9, 13, 17, 22, 28, 34, 42};
        Move[] mv = {
                r.chance(.5) ? Move.TACKLE : Move.SCRATCH,
                r.chance(.5) ? Move.GROWL : Move.LEER,
                Move.tier(t1, 0),
                r.chance(.5) ? Move.HARDEN : Move.FOCUS,
                Move.tier(t1, 1),
                t2 != null ? Move.tier(second, 1) : Move.HEADBUTT,
                Move.tier(t1, 2),
                r.chance(.6) ? Move.BODY_SLAM : Move.RECOVER,
                Move.tier(t1, 3),
                t2 != null ? Move.tier(second, 2) : Move.HYPER_RUSH
        };
        int[] mvIds = new int[mv.length];
        for (int i = 0; i < mv.length; i++) mvIds[i] = mv[i].id;
        long seed = r.nextInt(Integer.MAX_VALUE) * 7919L;
        int firstId = all.size() + 1;
        for (int st = 0; st < stages; st++) {
            Species s = new Species();
            s.id = all.size() + 1;
            s.stage = st;
            s.name = name(r, t1, stages == 2 && st == 1 ? 2 : st, stem);
            s.t1 = t1;
            s.t2 = t2;
            s.shape = shape;
            s.c1 = st == 0 ? c1 : (Color.HSBtoRGB(hue, Math.min(.95f, sat + .08f * st), bri - .06f * st) & 0xffffff);
            s.c2 = c2;
            s.c3 = c3;
            s.spriteSeed = seed;
            int total = baseTotal + st * 110 + (stages == 2 && st == 1 ? 40 : 0);
            s.hp = (int) (total * w[0] / ws);
            s.atk = (int) (total * w[1] / ws);
            s.def = (int) (total * w[2] / ws);
            s.spd = (int) (total * w[3] / ws);
            s.catchRate = st == 0 ? 190 : st == 1 ? 90 : 45;
            s.xpYield = 55 + st * 75;
            s.learnLv = lv;
            s.learnMove = mvIds;
            s.category = CATEGORY[t1.ordinal()] + (shape == Species.Shape.BIRD ? " Bird" : shape == Species.Shape.SERPENT ? " Serpent" : shape == Species.Shape.FLOATER ? " Drifter" : " Creature");
            if (st < stages - 1) {
                s.evolvesTo = s.id + 1;
                s.evolveLevel = evoLv[Math.min(st, evoLv.length - 1)];
            }
            s.desc = describe(r, s);
            all.add(s);
        }
        return firstId;
    }

    private static String describe(Rng r, Species s) {
        String[] a = {"It hides in tall grass and pounces on anything that moves.",
                "Travelers say its cry carries for miles on quiet nights.",
                "It is fiercely loyal to trainers who share their food with it.",
                "Its body temperature changes with its mood.",
                "Flocks of them gather wherever the wind shifts.",
                "It marks its territory by scratching symbols into stones.",
                "It sleeps through the day and forages by moonlight.",
                "Ancient murals show it guarding the old roads.",
                "It can sense a storm coming days ahead.",
                "Its shell, fur or scales glow faintly when it is happy."};
        String t = switch (s.t1) {
            case EMBER -> "Embers drift from its back when it is excited.";
            case TIDE -> "It can hold its breath for an hour while hunting.";
            case BLOOM -> "Seeds sprout wherever it naps in the sun.";
            case VOLT -> "Static makes its fur stand on end before it attacks.";
            case STONE -> "It chews gravel to keep its teeth sharp.";
            case GALE -> "It rides warm updrafts without flapping once.";
            case SHADE -> "It can vanish into any shadow larger than itself.";
            case FROST -> "Its breath leaves frost patterns on windows.";
            case SPIRIT -> "Lost children report it guiding them home.";
            case IRON -> "It polishes its plates with sand every morning.";
            default -> "It adapts to almost any place it is taken.";
        };
        return t + " " + r.pick(a);
    }

    public Species get(int id) { return all.get(id - 1); }
    public int size() { return all.size(); }
}
