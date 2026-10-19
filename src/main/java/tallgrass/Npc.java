package tallgrass;

import java.util.ArrayList;
import java.util.List;

public final class Npc extends Actor {
    public enum Kind { TALK, TRAINER, LEADER, NURSE, CLERK, ITEM, GIFT, MOM, PROF, STARTER, QUEST }

    public Kind kind = Kind.TALK;
    public List<String> lines = new ArrayList<>();
    public List<String> afterLines = new ArrayList<>();   // trainers, after being beaten
    public String trainerName;
    public final List<int[]> team = new ArrayList<>();    // {species, level}
    public int prize;
    public String flag;        // set once beaten / collected
    public String badge;
    public Type leaderType;
    public int sight;          // tiles a trainer can see
    public boolean wander;
    public int wanderTimer;
    public int homeX, homeY;
    public GameState.Item item;
    public int itemCount = 1;
    public boolean ball;       // drawn as an item orb instead of a person
    public int species;        // STARTER orbs: the creature inside
    public String questId;     // QUEST givers

    public Npc(int x, int y, CharArt.Look look) {
        this.x = x;
        this.y = y;
        this.homeX = x;
        this.homeY = y;
        this.look = look;
    }

    public boolean isTrainer() { return kind == Kind.TRAINER || kind == Kind.LEADER; }
}
