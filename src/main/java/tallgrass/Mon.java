package tallgrass;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** One creature you or a trainer owns. */
public final class Mon implements Serializable {
    private static final long serialVersionUID = 1L;

    public int species;
    public String nick;
    public int level, xp, hp;
    public int[] moves = new int[0];
    public int[] pp = new int[0];
    public boolean pendingEvolution;

    public Mon(int species, int level) {
        this.species = species;
        this.level = level;
        this.xp = xpFor(level);
        Species s = sp();
        List<Integer> known = new ArrayList<>();
        for (int i = 0; i < s.learnLv.length; i++) {
            if (s.learnLv[i] > level) break;
            int m = s.learnMove[i];
            if (!known.contains(m)) known.add(m);
            if (known.size() > 4) known.remove(0);
        }
        moves = new int[known.size()];
        pp = new int[known.size()];
        for (int i = 0; i < moves.length; i++) {
            moves[i] = known.get(i);
            pp[i] = Move.get(moves[i]).pp;
        }
        hp = maxHp();
    }

    public Species sp() { return Dex.current.get(species); }
    public String name() { return nick != null ? nick : sp().name; }

    public static int xpFor(int level) { return level <= 1 ? 0 : level * level * level; }

    private int stat(int base) { return base * 2 * level / 100 + 5; }
    public int maxHp() { return sp().hp * 2 * level / 100 + level + 10; }
    public int atk() { return stat(sp().atk); }
    public int def() { return stat(sp().def); }
    public int spd() { return stat(sp().spd); }

    public boolean fainted() { return hp <= 0; }

    public void healFull() {
        hp = maxHp();
        for (int i = 0; i < moves.length; i++) pp[i] = Move.get(moves[i]).pp;
    }

    public double xpRatio() {
        if (level >= 100) return 1;
        int a = xpFor(level), b = xpFor(level + 1);
        return (xp - a) / (double) (b - a);
    }

    /** Adds experience; returns the levels reached (empty if none). */
    public List<Integer> gainXp(int amount) {
        List<Integer> ups = new ArrayList<>();
        xp += amount;
        while (level < 100 && xp >= xpFor(level + 1)) {
            int oldMax = maxHp();
            level++;
            hp += maxHp() - oldMax;
            ups.add(level);
            Species s = sp();
            if (s.evolvesTo > 0 && level >= s.evolveLevel) pendingEvolution = true;
        }
        return ups;
    }

    /** Moves the species learns at exactly this level. */
    public List<Move> movesAt(int lv) {
        List<Move> out = new ArrayList<>();
        Species s = sp();
        for (int i = 0; i < s.learnLv.length; i++) if (s.learnLv[i] == lv) out.add(Move.get(s.learnMove[i]));
        return out;
    }

    public boolean knows(Move m) {
        for (int id : moves) if (id == m.id) return true;
        return false;
    }

    /** Learns a move, forgetting the weakest one if all four slots are full. Returns the forgotten move or null. */
    public Move learn(Move m) {
        if (knows(m)) return null;
        if (moves.length < 4) {
            moves = java.util.Arrays.copyOf(moves, moves.length + 1);
            pp = java.util.Arrays.copyOf(pp, pp.length + 1);
            moves[moves.length - 1] = m.id;
            pp[pp.length - 1] = m.pp;
            return null;
        }
        int worst = 0;
        for (int i = 1; i < 4; i++) if (score(Move.get(moves[i])) < score(Move.get(moves[worst]))) worst = i;
        Move old = Move.get(moves[worst]);
        moves[worst] = m.id;
        pp[worst] = m.pp;
        return old;
    }

    private double score(Move m) {
        if (m.kind != Move.Kind.HIT) return 30;
        return m.power * (sp().has(m.type) ? 1.5 : 1) * m.acc / 100.0;
    }

    public void evolve() {
        Species s = sp();
        if (s.evolvesTo <= 0) return;
        int oldMax = maxHp();
        species = s.evolvesTo;
        hp += maxHp() - oldMax;
        pendingEvolution = false;
    }
}
