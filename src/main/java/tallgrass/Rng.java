package tallgrass;

import java.util.List;
import java.util.SplittableRandom;

/** Deterministic randomness: the same world seed always rebuilds the same maps and species. */
public final class Rng {
    private final SplittableRandom r;

    public Rng(long seed) { r = new SplittableRandom(seed); }

    public static long hash(long... parts) {
        long h = 0x9E3779B97F4A7C15L;
        for (long p : parts) {
            h ^= p + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
        }
        return h;
    }

    public static long hash(String s, long... parts) {
        long[] all = new long[parts.length + 1];
        all[0] = s.hashCode() * 31L + s.length();
        System.arraycopy(parts, 0, all, 1, parts.length);
        return hash(all);
    }

    public int nextInt(int bound) { return r.nextInt(bound); }
    public int range(int lo, int hiInclusive) { return lo + r.nextInt(hiInclusive - lo + 1); }
    public double nextDouble() { return r.nextDouble(); }
    public boolean chance(double p) { return r.nextDouble() < p; }
    public <T> T pick(T[] a) { return a[r.nextInt(a.length)]; }
    public <T> T pick(List<T> a) { return a.get(r.nextInt(a.size())); }

    // Shared non-deterministic source for battles and encounters.
    public static final Rng FX = new Rng(System.nanoTime());
}
