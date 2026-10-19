package tallgrass;

import java.util.*;

/** Accepting, tracking and turning in quests. Progress lives in {@link GameState#quests}. */
public final class QuestBook {
    public enum Status { AVAILABLE, ACTIVE, READY, DONE }

    private final GameState st;
    private final WorldGen world;
    private final Map<String, Quest> defs = new HashMap<>();

    public QuestBook(GameState st, WorldGen world) {
        this.st = st;
        this.world = world;
    }

    public Quest def(String id) { return defs.computeIfAbsent(id, k -> Quest.def(st, world, k)); }

    public Status status(String id) {
        if (st.questsDone().contains(id)) return Status.DONE;
        if (!st.quests().containsKey(id)) return Status.AVAILABLE;
        return complete(id) ? Status.READY : Status.ACTIVE;
    }

    public List<String> active() { return new ArrayList<>(st.quests().keySet()); }

    /** The professor's next story quest, or null when there's nothing new to offer. */
    public String nextMain() {
        if (!st.flags.contains("starter")) return null;
        for (int n = 1; n <= 5; n++) {
            String id = "main:" + n;
            if (st.questsDone().contains(id)) continue;
            return st.quests().containsKey(id) ? null : id;
        }
        return null;
    }

    public void accept(String id) {
        st.quests().put(id, new int[def(id).objs.size()]);
        refreshLive();
    }

    public int progress(String id, int i) {
        Quest q = def(id);
        Quest.Obj o = q.objs.get(i);
        return switch (o.kind) {
            case BADGE -> Math.min(o.count, st.badges.size());
            case DEX -> Math.min(o.count, st.caught.size());
            default -> {
                int[] p = st.quests().get(id);
                yield p == null ? 0 : Math.min(o.count, p[i]);
            }
        };
    }

    public boolean complete(String id) {
        Quest q = def(id);
        for (int i = 0; i < q.objs.size(); i++) if (progress(id, i) < q.objs.get(i).count) return false;
        return true;
    }

    // ---------------------------------------------------------------- events -> toast lines

    private final List<String> toasts = new ArrayList<>();

    /** Messages produced since the last call, for the on-screen tracker toasts. */
    public List<String> drainToasts() {
        List<String> out = new ArrayList<>(toasts);
        toasts.clear();
        return out;
    }

    private void bump(String id, int i, int amount) {
        int[] p = st.quests().get(id);
        Quest q = def(id);
        Quest.Obj o = q.objs.get(i);
        if (p == null || p[i] >= o.count) return;
        boolean wasDone = complete(id);
        p[i] = Math.min(o.count, p[i] + amount);
        toasts.add(o.label + ": " + p[i] + "/" + o.count);
        if (!wasDone && complete(id)) toasts.add("Quest complete: " + q.title + "! Return to " + q.giver + ".");
    }

    private interface ObjVisitor { void visit(String id, int i, Quest.Obj o); }

    private void each(ObjVisitor v) {
        for (String id : active()) {
            Quest q = def(id);
            for (int i = 0; i < q.objs.size(); i++) v.visit(id, i, q.objs.get(i));
        }
    }

    public void onDefeat(Mon m) {
        Species s = m.sp();
        each((id, i, o) -> {
            if (o.kind == Quest.Kind.DEFEAT && (o.type == null || s.has(o.type))) bump(id, i, 1);
            if (o.kind == Quest.Kind.COLLECT && s.has(o.type) && Rng.FX.chance(.6)) bump(id, i, 1);
        });
        refreshLive();
    }

    public void onCatch(Mon m) {
        Species s = m.sp();
        each((id, i, o) -> { if (o.kind == Quest.Kind.CATCH && (o.type == null || s.has(o.type))) bump(id, i, 1); });
        refreshLive();
    }

    public void onTrainer() {
        each((id, i, o) -> { if (o.kind == Quest.Kind.TRAINERS) bump(id, i, 1); });
        refreshLive();
    }

    public void onVisit(Area a) {
        each((id, i, o) -> { if (o.kind == Quest.Kind.VISIT && a.key.equals(o.target)) bump(id, i, 1); });
    }

    /** Talking to a nurse: hands over any parcels addressed to this healing center. Returns the quest titles delivered. */
    public List<String> onNurse(String centerKey) {
        List<String> delivered = new ArrayList<>();
        each((id, i, o) -> {
            if (o.kind == Quest.Kind.DELIVER && centerKey.equals(o.target) && progress(id, i) < 1) {
                bump(id, i, 1);
                delivered.add(def(id).title);
            }
        });
        return delivered;
    }

    public boolean hasDeliveryFor(String centerKey) {
        for (String id : active()) {
            Quest q = def(id);
            for (int i = 0; i < q.objs.size(); i++)
                if (q.objs.get(i).kind == Quest.Kind.DELIVER && centerKey.equals(q.objs.get(i).target) && progress(id, i) < 1) return true;
        }
        return false;
    }

    /** Badge and Creaturedex objectives are read live; announce when they tip a quest over. */
    private final Set<String> announced = new HashSet<>();

    public void refreshLive() {
        for (String id : active()) {
            if (complete(id) && announced.add(id)) {
                Quest q = def(id);
                boolean live = q.objs.stream().anyMatch(o -> o.kind == Quest.Kind.BADGE || o.kind == Quest.Kind.DEX);
                if (live) toasts.add("Quest complete: " + q.title + "! Return to " + q.giver + ".");
            }
        }
    }

    // ---------------------------------------------------------------- rewards

    /** Pays out the reward and closes the quest. Returns the lines to show. */
    public List<String> turnIn(String id) {
        Quest q = def(id);
        List<String> out = new ArrayList<>();
        st.quests().remove(id);
        st.questsDone().add(id);
        out.add("Quest complete: " + q.title + "!");
        st.money += q.money;
        out.add("{P} received $" + q.money + ".");
        if (q.item != null && q.itemCount > 0) {
            st.add(q.item, q.itemCount);
            out.add("{P} received " + (q.itemCount > 1 ? q.itemCount + " " + q.item.label + "s" : "a " + q.item.label) + ".");
        }
        if (q.xp > 0 && !st.party.isEmpty()) {
            out.add("Your team gained " + q.xp + " EXP. Points each!");
            for (Mon m : st.party) {
                if (m.fainted()) continue;
                List<Integer> ups = m.gainXp(q.xp);
                for (int lv : ups) {
                    out.add(m.name() + " grew to Lv. " + lv + "!");
                    for (Move mv : m.movesAt(lv)) {
                        if (m.knows(mv)) continue;
                        Move old = m.learn(mv);
                        out.add(old == null ? m.name() + " learned " + mv.name + "!" : m.name() + " forgot " + old.name + " and learned " + mv.name + "!");
                    }
                }
            }
        }
        return out;
    }
}
