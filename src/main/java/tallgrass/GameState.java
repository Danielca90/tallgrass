package tallgrass;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Everything that gets saved. Maps are rebuilt from the world seed, so only progress is stored. */
public final class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Item {
        POTION("Potion", 200, "Restores 20 HP to one creature."),
        SUPER_POTION("Super Potion", 600, "Restores 60 HP to one creature."),
        REVIVE("Revive", 1200, "Revives a fainted creature with half its HP."),
        ORB("Capture Orb", 200, "Throw at a weakened wild creature to catch it."),
        GREAT_ORB("Great Orb", 600, "A better orb with a higher catch rate."),
        FAST_BOOTS("Fast Boots", 0, "Hold B to dash. Press Shift or R to switch auto-run on and off.");

        public final String label, desc;
        public final int price;
        Item(String label, int price, String desc) { this.label = label; this.price = price; this.desc = desc; }
        public boolean isOrb() { return this == ORB || this == GREAT_ORB; }
        public boolean isKey() { return price == 0; }
    }

    public long seed;
    public String seedText = "";
    public String worldId, worldName = "New World";
    public long lastPlayed;
    public CharArt.Look look = CharArt.PLAYER;
    public String player = "Rowan";
    public Dex dex;
    public final List<Mon> party = new ArrayList<>();
    public final List<Mon> box = new ArrayList<>();
    public final EnumMap<Item, Integer> bag = new EnumMap<>(Item.class);
    public int money = 3000;
    public boolean autoRun;
    // quests: id -> progress per objective (not final, so saves from before quests load cleanly)
    private Map<String, int[]> quests = new LinkedHashMap<>();
    private Set<String> questsDone = new HashSet<>();

    public Map<String, int[]> quests() { if (quests == null) quests = new LinkedHashMap<>(); return quests; }
    public Set<String> questsDone() { if (questsDone == null) questsDone = new HashSet<>(); return questsDone; }
    public final List<String> badges = new ArrayList<>();
    public final Set<String> flags = new HashSet<>();
    public final Set<Integer> seen = new TreeSet<>(), caught = new TreeSet<>();
    public final Map<String, String> areaNames = new HashMap<>();
    public int routeCounter = 0;

    // position
    public String area = "0,0";
    public int px, py, facing = Actor.DOWN;
    public String healArea = "0,0";
    public int healX, healY;
    public long playFrames;

    public int count(Item i) { return bag.getOrDefault(i, 0); }
    public void add(Item i, int n) { bag.merge(i, n, Integer::sum); if (count(i) <= 0) bag.remove(i); }

    public Mon lead() {
        for (Mon m : party) if (!m.fainted()) return m;
        return party.isEmpty() ? null : party.get(0);
    }

    public boolean anyAlive() { for (Mon m : party) if (!m.fainted()) return true; return false; }
    public void healAll() { for (Mon m : party) m.healFull(); }

    public void see(int id) { seen.add(id); }
    public void own(int id) { seen.add(id); caught.add(id); }

    public void addMon(Mon m) {
        own(m.species);
        if (party.size() < 6) party.add(m); else box.add(m);
    }

    // ---------- worlds (one folder per world, like Minecraft's saves/) ----------
    private static Path root() {
        Path dir = Path.of(System.getProperty("user.home"), ".tallgrass", "worlds");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        migrateLegacy(dir);
        return dir;
    }

    /** Saves from before multiple worlds lived at ~/.tallgrass/save.dat. */
    private static void migrateLegacy(Path worlds) {
        Path old = worlds.getParent().resolve("save.dat");
        if (!Files.exists(old)) return;
        try {
            Path dest = worlds.resolve("first-world");
            Files.createDirectories(dest);
            Files.move(old, dest.resolve("save.dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    public void save() throws IOException {
        lastPlayed = System.currentTimeMillis();
        Path dir = root().resolve(worldId);
        Files.createDirectories(dir);
        Path tmp = dir.resolve("save.tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeObject(this);
        }
        Files.move(tmp, dir.resolve("save.dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static GameState read(Path file) {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            GameState s = (GameState) in.readObject();
            String id = file.getParent().getFileName().toString();
            s.worldId = id;
            if (s.worldName == null) s.worldName = id.equals("first-world") ? "My First World" : id;
            if (s.look == null) s.look = CharArt.PLAYER;
            if (s.seedText == null) s.seedText = String.valueOf(s.seed);
            if (s.party.size() > 0) s.flags.add("starter");
            if (s.count(Item.FAST_BOOTS) == 0) s.add(Item.FAST_BOOTS, 1);
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** All saved worlds, most recently played first. */
    public static List<GameState> listWorlds() {
        List<GameState> out = new ArrayList<>();
        try (var dirs = Files.list(root())) {
            for (Path d : (Iterable<Path>) dirs::iterator) {
                Path f = d.resolve("save.dat");
                if (Files.exists(f)) {
                    GameState s = read(f);
                    if (s != null) out.add(s);
                }
            }
        } catch (IOException ignored) {}
        out.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        return out;
    }

    public void activate() { Dex.current = dex; }

    public static void deleteWorld(String id) throws IOException {
        Path dir = root().resolve(id);
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    /** Minecraft-style seeds: a number is used as-is, any other text is hashed, blank picks a random one. */
    public static long seedFrom(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return new Random().nextLong();
        try { return Long.parseLong(t); } catch (NumberFormatException e) { return t.hashCode(); }
    }

    public static GameState create(String worldName, String seedText, String player, CharArt.Look look) {
        GameState s = new GameState();
        s.worldName = worldName.isBlank() ? "New World" : worldName.trim();
        s.seed = seedFrom(seedText);
        s.seedText = seedText.isBlank() ? String.valueOf(s.seed) : seedText.trim();
        s.player = player.isBlank() ? "Rowan" : player.trim();
        s.look = look;
        String base = s.worldName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "world";
        String id = base;
        for (int i = 2; Files.exists(root().resolve(id)); i++) id = base + "-" + i;
        s.worldId = id;
        s.dex = Dex.generate(s.seed);
        Dex.current = s.dex;
        s.add(Item.POTION, 3);
        s.add(Item.FAST_BOOTS, 1);
        return s;
    }
}
