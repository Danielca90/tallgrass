package tallgrass;

import java.awt.Color;

public enum Type {
    NORMAL("Normal", 0xa8a878),
    EMBER("Ember", 0xf08030),
    TIDE("Tide", 0x6890f0),
    BLOOM("Bloom", 0x78c850),
    VOLT("Volt", 0xe8c020),
    STONE("Stone", 0xb8a038),
    GALE("Gale", 0x98a8f0),
    SHADE("Shade", 0x705898),
    FROST("Frost", 0x80c8d8),
    SPIRIT("Spirit", 0xe878a8),
    IRON("Iron", 0x9898b8);

    public final String label;
    public final Color color;

    Type(String label, int rgb) {
        this.label = label;
        this.color = new Color(rgb);
    }

    private static final Type[][] STRONG = new Type[values().length][];

    static {
        set(NORMAL);
        set(EMBER, BLOOM, FROST, IRON);
        set(TIDE, EMBER, STONE);
        set(BLOOM, TIDE, STONE);
        set(VOLT, TIDE, GALE);
        set(STONE, EMBER, VOLT, GALE, FROST);
        set(GALE, BLOOM, SPIRIT);
        set(SHADE, SPIRIT);
        set(FROST, GALE, BLOOM, STONE);
        set(SPIRIT, SHADE, IRON);
        set(IRON, FROST, STONE);
    }

    private static void set(Type t, Type... beats) { STRONG[t.ordinal()] = beats; }

    private static boolean beats(Type a, Type d) {
        for (Type t : STRONG[a.ordinal()]) if (t == d) return true;
        return false;
    }

    /** Multiplier for a move of type {@code atk} hitting a creature of type {@code def}. */
    public static double eff(Type atk, Type def) {
        if (def == null || atk == NORMAL) return 1;
        if (beats(atk, def)) return 2;
        if (beats(def, atk) || atk == def) return 0.5;
        return 1;
    }

    public static double eff(Type atk, Type d1, Type d2) { return eff(atk, d1) * eff(atk, d2); }
}
