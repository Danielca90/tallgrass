package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Walking around: tile map, NPCs, trainers, warps, encounters, and a tiny event script runner. */
public final class OverworldScene implements Scene {
    private final Game game;
    private final GameState st;
    private Area area;
    private final Actor player = new Actor();

    private final ArrayDeque<Cmd> script = new ArrayDeque<>();
    private Cmd cur;
    private final DialogBox dlg = new DialogBox(true);
    private AreaRenderer view;              // HD pre-render of the current area
    private BufferedImage hdVignette;
    private static final Map<String, BufferedImage> CHARS = new HashMap<>();
    private ChoiceBox choice;
    private boolean subscene;

    private int turnDelay, stepsSinceEncounter;
    private String popup;
    private int popupT;
    private Npc emote;
    private BufferedImage vignette;
    private Species showcase;   // creature shown in a window while the player decides
    private final ArrayDeque<String> toasts = new ArrayDeque<>();
    private int toastT = 110;

    /** WoW-style yellow notice in the middle of the screen (quest progress, auto-run...). */
    void toast(String s) {
        if (toasts.isEmpty()) toastT = 110;
        if (toasts.size() < 4) toasts.add(s);
    }

    public OverworldScene(Game game) {
        this.game = game;
        this.st = game.st;
        player.look = st.look != null ? st.look : CharArt.PLAYER;
        enter(game.world.get(st.area), st.px, st.py, st.facing);
    }

    // ---------------------------------------------------------------- script runner

    private abstract static class Cmd {
        void start() {}
        abstract boolean update();
    }

    private void run(Cmd... cmds) { script.addAll(Arrays.asList(cmds)); }

    private void runNext(List<Cmd> cmds) {
        for (int i = cmds.size() - 1; i >= 0; i--) script.addFirst(cmds.get(i));
    }

    private String fill(String s) { return s.replace("{P}", st.player); }

    private Cmd say(String text) {
        return new Cmd() {
            void start() { dlg.open(fill(text)); }
            boolean update() { return dlg.update(game.in); }
        };
    }

    private Cmd ask(String text, String[] options, IntConsumer onPick) {
        return new Cmd() {
            void start() { dlg.open(fill(text)); }
            boolean update() {
                if (!dlg.waitingOnLastPage()) { dlg.update(game.in); return false; }
                if (choice == null) choice = new ChoiceBox(true, options);
                int r = choice.update(game.in);
                if (r == -2) return false;
                choice = null;
                dlg.close();
                onPick.accept(r < 0 ? options.length - 1 : r);
                return true;
            }
        };
    }

    private Cmd act(Runnable r) {
        return new Cmd() {
            void start() { r.run(); }
            boolean update() { return true; }
        };
    }

    private Cmd wait(int frames) {
        return new Cmd() {
            int t;
            boolean update() { return ++t >= frames; }
        };
    }

    private Cmd scene(Supplier<Scene> s) {
        return new Cmd() {
            void start() { subscene = true; game.push(s.get()); }
            boolean update() { return !subscene; }
        };
    }

    private Cmd emoteCmd(Npc n) {
        return new Cmd() {
            int t;
            void start() { emote = n; }
            boolean update() {
                if (++t < 40) return false;
                emote = null;
                return true;
            }
        };
    }

    private Cmd walkTo(Npc n) {
        return new Cmd() {
            boolean update() {
                if (n.moving()) return false;
                int nx = n.x + Actor.dx(n.facing), ny = n.y + Actor.dy(n.facing);
                if ((nx == player.x && ny == player.y) || area.at(nx, ny).solid) return true;
                n.startMove(n.facing, 2);
                return false;
            }
        };
    }

    private Cmd battle(List<Mon> enemy, String trainer, Consumer<BattleScene.Result> after) {
        return new Cmd() {
            BattleScene.Result result;
            void start() {
                for (Mon m : enemy) st.see(m.species);
                game.push(new BattleScene(game, enemy, trainer, area.biome, r -> {
                    result = r;
                    // evolutions go in first so the battle's follow-up messages (queued in front) play before them
                    if (r != BattleScene.Result.LOSE) runNext(evolutions());
                    after.accept(r);
                }));
            }
            boolean update() { return result != null; }
        };
    }

    private List<Cmd> evolutions() {
        List<Cmd> out = new ArrayList<>();
        for (Mon m : st.party) {
            if (!m.pendingEvolution) continue;
            out.add(scene(() -> new EvolutionScene(game, m)));
        }
        return out;
    }

    public boolean busy() { return cur != null || !script.isEmpty(); }

    @Override public void resumed() { subscene = false; }

    // ---------------------------------------------------------------- moving between areas

    private void enter(Area a, int x, int y, int facing) {
        boolean newPlace = area == null || (!a.interior && (area.interior ? !a.key.equals(st.area) && !a.name.equals(area.name) : !a.key.equals(area.key)));
        String prevKey = area == null ? null : area.key;
        area = a;
        player.x = x;
        player.y = y;
        player.facing = facing;
        player.moveDir = -1;
        st.area = a.key;
        st.px = x;
        st.py = y;
        st.facing = facing;
        if (prevKey == null || (newPlace && !a.interior)) {
            popup = a.name;
            popupT = 150;
        }
        if (a.cave && vignette == null) vignette = makeVignette();
        if (!a.interior && game.quests != null) game.quests.onVisit(a);
        if (prevKey != null) autosave();
    }

    private void autosave() {
        try { st.save(); } catch (Exception ignored) {}
    }

    private void changeArea() {
        int dir = player.x < 0 ? Actor.LEFT : player.x >= area.w ? Actor.RIGHT : player.y < 0 ? Actor.UP : Actor.DOWN;
        int nax = area.ax + Actor.dx(dir), nay = area.ay + Actor.dy(dir);
        int px = player.x, py = player.y;
        game.fade(() -> {
            Area na = game.world.get(WorldGen.key(nax, nay));
            int nx = dir == Actor.LEFT ? na.w - 1 : dir == Actor.RIGHT ? 0 : px;
            int ny = dir == Actor.UP ? na.h - 1 : dir == Actor.DOWN ? 0 : py;
            enter(na, nx, ny, dir);
        });
    }

    private void warp(Area.Warp w) {
        game.fade(() -> enter(game.world.get(w.area()), w.x(), w.y(), w.facing()));
    }

    private int exitAt(int tx, int ty) {
        if (area.interior) return -1;
        int dir = tx < 0 ? Actor.LEFT : tx >= area.w ? Actor.RIGHT : ty < 0 ? Actor.UP : ty >= area.h ? Actor.DOWN : -1;
        if (dir < 0 || !area.exits[dir]) return -1;
        int along = (dir == Actor.LEFT || dir == Actor.RIGHT) ? ty : tx;
        return along >= area.gap[dir] && along < area.gap[dir] + 3 ? dir : -1;
    }

    private boolean canEnter(int tx, int ty) {
        if (!area.inBounds(tx, ty)) return exitAt(tx, ty) >= 0;
        if (area.at(tx, ty).solid) return false;
        return area.npcAt(tx, ty) == null;
    }

    // ---------------------------------------------------------------- update

    @Override
    public void update() {
        Input in = game.in;
        if (popupT > 0) popupT--;
        for (String t : game.quests.drainToasts()) toast(t);
        if (!toasts.isEmpty() && --toastT <= 0) { toasts.poll(); toastT = 110; }
        for (Npc n : area.npcs) if (n.moving()) n.update();

        if (cur == null && !script.isEmpty()) {
            cur = script.poll();
            cur.start();
        }
        if (cur != null) {
            if (cur.update()) cur = null;
            return;
        }

        boolean stepEnded = false;
        if (player.moving()) {
            if (!player.update()) return;
            stepEnded = true;
            if (onStep()) return;
        }
        wander();

        if (in.pressed(Input.R) && st.count(GameState.Item.FAST_BOOTS) > 0) {
            st.autoRun = !st.autoRun;
            toast(st.autoRun ? "Auto-run ON  (hold B to walk)" : "Auto-run OFF  (hold B to dash)");
        }
        if (in.pressed(Input.START)) {
            subscene = true;
            game.push(new StartMenu(game, this));
            return;
        }
        if (in.pressed(Input.A)) {
            interact();
            if (busy()) return;
        }
        int dir = in.held(Input.UP) ? Actor.UP : in.held(Input.DOWN) ? Actor.DOWN : in.held(Input.LEFT) ? Actor.LEFT : in.held(Input.RIGHT) ? Actor.RIGHT : -1;
        if (dir < 0) { turnDelay = 0; return; }
        if (player.facing != dir && !stepEnded) {
            player.facing = dir;
            turnDelay = 5;
            return;
        }
        if (turnDelay > 0) { turnDelay--; return; }
        int tx = player.x + Actor.dx(dir), ty = player.y + Actor.dy(dir);
        player.facing = dir;
        if (!area.inBounds(tx, ty) && exitAt(tx, ty) >= 0 && !st.flags.contains("starter")) {
            final int back = Actor.opposite(dir);
            run(say("A voice calls out: \"Hey, wait! Don't go out there without a creature of your own!\""),
                    say("It's Professor Alder's voice. His lab is the building with the gray roof."),
                    act(() -> { if (canEnter(player.x + Actor.dx(back), player.y + Actor.dy(back))) player.startMove(back, 1); }));
            return;
        }
        boolean dash = st.count(GameState.Item.FAST_BOOTS) > 0 && (st.autoRun != in.held(Input.B));
        if (canEnter(tx, ty)) player.startMove(dir, dash ? (area.interior ? 2 : 3) : 1);
    }

    /** Called when the player lands on a new tile. Returns true if something took over. */
    private boolean onStep() {
        st.px = player.x;
        st.py = player.y;
        st.facing = player.facing;
        if (!area.inBounds(player.x, player.y)) { changeArea(); return true; }
        Area.Warp w = area.warps.get(Area.k(player.x, player.y));
        if (w != null) { warp(w); return true; }
        if (checkTrainers()) return true;
        Tile t = area.at(player.x, player.y);
        if ((t == Tile.TALL || (area.cave && t == Tile.GRASS)) && st.anyAlive()) {
            stepsSinceEncounter++;
            double p = area.cave ? 1 / 14.0 : 1 / 9.0;
            if (stepsSinceEncounter > 2 && Rng.FX.chance(p)) {
                stepsSinceEncounter = 0;
                wildBattle();
                return true;
            }
        }
        return false;
    }

    private void wander() {
        for (Npc n : area.npcs) {
            if (!n.wander || n.moving()) continue;
            if (--n.wanderTimer > 0) continue;
            n.wanderTimer = 60 + Rng.FX.nextInt(140);
            int d = Rng.FX.nextInt(4);
            n.facing = d;
            int tx = n.x + Actor.dx(d), ty = n.y + Actor.dy(d);
            if (Math.abs(tx - n.homeX) > 2 || Math.abs(ty - n.homeY) > 2) continue;
            if (!area.inBounds(tx, ty) || area.at(tx, ty).solid || area.npcAt(tx, ty) != null) continue;
            if (area.warps.containsKey(Area.k(tx, ty)) || area.at(tx, ty) == Tile.DOOR) continue;
            if (tx == player.x && ty == player.y) continue;
            n.startMove(d, 1);
        }
    }

    private boolean checkTrainers() {
        for (Npc n : area.npcs) {
            if (n.kind != Npc.Kind.TRAINER || n.sight <= 0 || st.flags.contains(n.flag)) continue;
            int dx = Actor.dx(n.facing), dy = Actor.dy(n.facing);
            for (int i = 1; i <= n.sight; i++) {
                int x = n.x + dx * i, y = n.y + dy * i;
                if (x == player.x && y == player.y) {
                    trainerBattle(n, true);
                    return true;
                }
                if (area.at(x, y).solid || area.npcAt(x, y) != null) break;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- events

    private void wildBattle() {
        Mon wild = game.world.wild(area, Rng.FX);
        run(battle(List.of(wild), null, r -> {
            if (r == BattleScene.Result.WIN) game.quests.onDefeat(wild);
            if (r == BattleScene.Result.CAUGHT) game.quests.onCatch(wild);
            if (r == BattleScene.Result.LOSE) runNext(whiteout());
        }));
    }

    private void trainerBattle(Npc n, boolean spotted) {
        if (spotted) run(emoteCmd(n), walkTo(n));
        run(act(() -> { player.facing = Actor.opposite(n.facing); }));
        for (String l : n.lines) run(say(n.trainerName + ": " + l));
        List<Mon> team = new ArrayList<>();
        for (int[] t : n.team) team.add(new Mon(t[0], t[1]));
        run(battle(team, n.trainerName, r -> {
            List<Cmd> next = new ArrayList<>();
            if (r == BattleScene.Result.WIN) {
                game.quests.onTrainer();
                st.flags.add(n.flag);
                st.money += n.prize;
                next.add(say("{P} got $" + n.prize + " for winning!"));
                if (n.kind == Npc.Kind.LEADER && n.badge != null && !st.badges.contains(n.badge)) {
                    st.badges.add(n.badge);
                    game.quests.refreshLive();
                    next.add(say("{P} received the " + n.badge + "!"));
                    st.add(GameState.Item.GREAT_ORB, 2);
                    next.add(say("The leader also handed over 2 Great Orbs."));
                }
                for (String l : n.afterLines) next.add(say(n.trainerName + ": " + l));
            } else if (r == BattleScene.Result.LOSE) {
                next.addAll(whiteout());
            }
            runNext(next);
        }));
    }

    private List<Cmd> whiteout() {
        return List.of(
                say("{P} has no creatures left that can fight!"),
                act(() -> game.fade(() -> {
                    st.healAll();
                    st.money /= 2;
                    Area a = game.world.get(st.healArea);
                    enter(a, st.healX, st.healY, Actor.DOWN);
                })),
                wait(2),
                say("{P} rushed back to safety and rested. The team is healed, but half your money was dropped on the way."));
    }

    private void setHealPoint() {
        for (Area.Warp w : area.warps.values()) {
            st.healArea = w.area();
            st.healX = w.x();
            st.healY = w.y();
            return;
        }
    }

    private void interact() {
        int tx = player.x + Actor.dx(player.facing), ty = player.y + Actor.dy(player.facing);
        Tile t = area.at(tx, ty);
        if (t == Tile.COUNTER) { tx += Actor.dx(player.facing); ty += Actor.dy(player.facing); }
        Npc n = area.npcAt(tx, ty);
        if (n != null && !n.moving()) { talkTo(n); return; }
        if (t == Tile.SIGN) {
            String s = area.signs.get(Area.k(tx, ty));
            run(say(s != null ? s : "It's a weathered sign. You can't read it."));
        } else if (t == Tile.SHELF) run(say("It's crammed with books about creatures and their habitats."));
        else if (t == Tile.WATER) run(say("The water is deep and clear. Something shimmers below the surface."));
        else if (t == Tile.BED) run(say("A soft, freshly made bed."));
    }

    private void talkTo(Npc n) {
        if (!n.ball) n.facing = Actor.opposite(player.facing);
        switch (n.kind) {
            case CLERK, ITEM, GIFT, MOM, PROF, STARTER -> talkTo2(n);
            case TALK -> { for (String l : n.lines) run(say(l)); }
            case TRAINER, LEADER -> {
                if (st.flags.contains(n.flag)) for (String l : n.afterLines) run(say(n.trainerName + ": " + l));
                else trainerBattle(n, false);
            }
            case NURSE -> {
                if (game.quests.hasDeliveryFor(area.key)) {
                    List<String> got = game.quests.onNurse(area.key);
                    run(say("Nurse: Oh, is that for me? From " + (got.isEmpty() ? "a friend" : "\"" + got.get(0) + "\"") + "... Thank you so much!"),
                            say("{P} handed over the delivery."));
                }
                nurse();
            }
            case QUEST -> questTalk(n.questId);
            default -> {}
        }
    }

    private String activeMain() {
        for (String id : game.quests.active()) if (id.startsWith("main:")) return id;
        return null;
    }

    /** Offer, remind or turn in a quest, WoW style. */
    private void questTalk(String id) {
        QuestBook qb = game.quests;
        Quest q = qb.def(id);
        switch (qb.status(id)) {
            case AVAILABLE -> {
                for (int i = 0; i < q.offer.size() - 1; i++) run(say(q.offer.get(i)));
                run(ask(q.offer.get(q.offer.size() - 1), new String[] {"ACCEPT", "DECLINE"}, i -> {
                    if (i == 0) {
                        qb.accept(id);
                        toast("Quest accepted: " + q.title);
                        runNext(List.of(act(this::autosave)));
                    } else runNext(List.of(say(q.giver + ": Maybe another time, then.")));
                }));
            }
            case ACTIVE -> run(say(q.reminder));
            case READY -> {
                run(say(q.done));
                run(act(() -> {
                    List<Cmd> out = new ArrayList<>();
                    for (String line : qb.turnIn(id)) out.add(say(line));
                    out.addAll(evolutions());
                    out.add(act(this::autosave));
                    if (q.next != null) out.add(act(() -> questTalk(q.next)));
                    runNext(out);
                    toast("Quest complete: " + q.title);
                }));
            }
            case DONE -> run(say(q.giver + ": Thanks again for your help, {P}. Safe travels!"));
        }
    }

    private void nurse() {
        run(ask("Welcome to the healing center! Shall I restore your creatures to full health?", new String[] {"YES", "NO"}, i -> {
                if (i == 0) runNext(List.of(
                        say("Okay, I'll take your creatures for a few seconds."),
                        act(() -> { st.healAll(); setHealPoint(); }),
                        wait(45),
                        say("Thank you for waiting! Your creatures are fighting fit."),
                        say("We hope to see you again!")));
                else runNext(List.of(say("We hope to see you again!")));
            }));
    }

    private void talkTo2(Npc n) {
        switch (n.kind) {
            case CLERK -> run(say("Welcome! What can I get for you?"), scene(() -> new MartScene(game)));
            case ITEM -> {
                String label = n.itemCount > 1 ? n.itemCount + " " + n.item.label + "s" : "a " + n.item.label;
                run(act(() -> { st.add(n.item, n.itemCount); st.flags.add(n.flag); area.npcs.remove(n); }),
                        say("{P} found " + label + "!"));
            }
            case GIFT -> {
                for (String l : n.lines) run(say(l));
                run(act(() -> {
                    st.add(n.item, 1);
                    st.flags.add(n.flag);
                    n.kind = Npc.Kind.TALK;
                    n.lines = new ArrayList<>(List.of("Take good care of your creatures out there!"));
                }), say("{P} received a " + n.item.label + "!"));
            }
            case MOM -> {
                if (!st.flags.contains("starter")) {
                    run(say("Mom: Good morning, {P}! You slept in on the big day!"),
                            say("Mom: You're wearing the Fast Boots I gave you? Hold B to dash, or press Shift to keep running."),
                            say("Mom: Professor Alder came by earlier. He has something for you at his lab."),
                            say("Mom: It's the building with the gray roof, down the road. Go on, he's waiting!"));
                } else {
                    run(say("Mom: Oh, {P}! You look worn out. Take a quick rest."),
                            act(() -> { st.healAll(); setHealPoint(); }), wait(30),
                            say("Mom: There! Your creatures look full of energy again. Be careful out there!"));
                }
            }
            case PROF -> {
                if (!st.flags.contains("starter")) {
                    run(say("Professor Alder: Ah, {P}, there you are! Today you become a trainer."),
                            say("Professor Alder: This world is unlike any other. It takes shape as you explore, and every direction leads somewhere new."),
                            say("Professor Alder: On that table are three creatures I've raised. Have a look and choose the one you like best."));
                } else if (activeMain() != null) {
                    questTalk(activeMain());
                } else if (game.quests.nextMain() != null) {
                    questTalk(game.quests.nextMain());
                } else {
                    run(say("Professor Alder: So far you've seen " + st.seen.size() + " kinds of creatures and caught " + st.caught.size() + "."),
                            say("Professor Alder: North turns cold, south reaches the sea, east dries into desert, and west grows wild. Where will you go?"),
                            say("Professor Alder: Gym leaders guard badges in the towns out there. Collect them all!"));
                }
            }
            case STARTER -> {
                Species s = st.dex.get(n.species);
                if (st.flags.contains("starter")) {
                    run(say("It's one of the professor's creatures, napping inside its orb. It's waiting for a trainer of its own."));
                    return;
                }
                run(act(() -> { showcase = s; st.see(s.id); }),
                        ask("It's " + s.name + ", the " + s.t1.label + "-type creature. " + s.desc.split("\\. ")[0] + ". Choose " + s.name + "?",
                                new String[] {"YES", "NO"}, i -> {
                                    showcase = null;
                                    if (i != 0) return;
                                    runNext(List.of(
                                            act(() -> {
                                                st.addMon(new Mon(s.id, 5));
                                                st.flags.add("starter");
                                                st.flags.add(n.flag);
                                                area.npcs.remove(n);
                                            }),
                                            say("{P} chose " + s.name + "! It hopped out of the orb and looks right at you."),
                                            say("Professor Alder: A fine choice! " + s.name + " seems to like you already."),
                                            act(() -> { st.add(GameState.Item.ORB, 5); st.flags.add("prof:orbs"); }),
                                            say("{P} received 5 Capture Orbs!"),
                                            say("Professor Alder: Weaken wild creatures in battle, then throw an orb. The lower their HP, the better your chances."),
                                            act(this::autosave),
                                            act(() -> questTalk("main:1"))));
                                }));
            }
        }
    }

    // ---------------------------------------------------------------- rendering

    private BufferedImage makeVignette() {
        BufferedImage im = Gfx.image(Main.W, Main.H);
        for (int y = 0; y < Main.H; y++)
            for (int x = 0; x < Main.W; x++) {
                double dx = (x - 120) / 120.0, dy = (y - 82) / 90.0;
                double d = Math.sqrt(dx * dx + dy * dy);
                int a = (int) Math.max(0, Math.min(200, (d - .45) * 380));
                im.setRGB(x, y, a << 24 | 0x080808);
            }
        return im;
    }

    @Override public boolean hd() { return true; }

    private static BufferedImage hdChar(CharArt.Look l, int facing, int frame) {
        return CHARS.computeIfAbsent(l.hashCode() + ":" + facing + ":" + frame, k -> HdArt.character(l, facing, frame));
    }

    /** Objective tracker in the top-right corner, like WoW's quest tracker. */
    private void renderTracker(Graphics2D g) {
        List<String> ids = game.quests.active();
        if (ids.isEmpty() || dlg.isOpen()) return;
        ids.sort(Comparator.comparing(id -> id.startsWith("main:") ? 0 : 1));
        List<String[]> rows = new ArrayList<>();   // {text, colour}
        for (String id : ids.subList(0, Math.min(3, ids.size()))) {
            Quest q = game.quests.def(id);
            boolean ready = game.quests.complete(id);
            rows.add(new String[] {(q.isMain() ? "* " : "") + q.title, "title"});
            if (ready) rows.add(new String[] {"  Return to " + q.giver, "ready"});
            else for (int i = 0; i < q.objs.size(); i++) {
                Quest.Obj o = q.objs.get(i);
                rows.add(new String[] {"  - " + o.label + ": " + game.quests.progress(id, i) + "/" + o.count, "obj"});
            }
        }
        int w = 0;
        for (String[] r : rows) w = Math.max(w, Font.width(r[0]));
        w += 16;
        int x = Main.SW - w - 8, y = 48;
        g.setColor(new Color(10, 14, 26, 120));
        g.fillRoundRect(x - 6, y - 6, w + 8, rows.size() * 11 + 10, 8, 8);
        for (int i = 0; i < rows.size(); i++) {
            Color c = switch (rows.get(i)[1]) {
                case "title" -> new Color(0xffd21a);
                case "ready" -> new Color(0x8cf07a);
                default -> new Color(0xf0f0f0);
            };
            Font.draw(g, rows.get(i)[0], x, y + i * 11, c, new Color(0x101010));
        }
    }

    private BufferedImage makeHdVignette() {
        BufferedImage im = new BufferedImage(Main.SW, Main.SH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < Main.SH; y++)
            for (int x = 0; x < Main.SW; x++) {
                double dx = (x - 240) / 240.0, dy = (y - 164) / 180.0;
                double d = Math.sqrt(dx * dx + dy * dy);
                int a = (int) Math.max(0, Math.min(215, (d - .42) * 400));
                im.setRGB(x, y, a << 24 | 0x06080c);
            }
        return im;
    }

    @Override
    public void render(Graphics2D g) {
        final int T = AreaRenderer.T;
        if (view == null || view.area != area) view = new AreaRenderer(area);
        int mw = area.w * T, mh = area.h * T;
        int camX = player.pixelX() * 2 + T / 2 - Main.SW / 2;
        int camY = player.pixelY() * 2 + T / 2 - Main.SH / 2;
        camX = mw <= Main.SW ? (mw - Main.SW) / 2 : Math.max(0, Math.min(mw - Main.SW, camX));
        camY = mh <= Main.SH ? (mh - Main.SH) / 2 : Math.max(0, Math.min(mh - Main.SH, camY));
        int tick = game.tick;
        g.setColor(area.interior ? Color.BLACK : new Color(0x10180c));
        g.fillRect(0, 0, Main.SW, Main.SH);
        g.drawImage(view.ground, -camX, -camY, null);
        if (view.waves != null) g.drawImage(view.waves[(tick / 14) % 4], -camX, -camY, null);

        // tall grass tiles (under everything that stands on them)
        int tx0 = Math.floorDiv(camX, T) - 1, ty0 = Math.floorDiv(camY, T) - 1;
        BufferedImage tall = Textures.get(Textures.tall(area.biome, (tick / 40) % 2));
        for (int ty = ty0; ty <= ty0 + 12; ty++)
            for (int tx = tx0; tx <= tx0 + 17; tx++)
                if (area.at(tx, ty) == Tile.TALL) g.drawImage(tall, tx * T - camX, ty * T - camY, null);

        // contact shadows for things that move
        for (Npc n : area.npcs) AreaRenderer.dropShadow(g, n.pixelX() * 2 + 16 - camX, n.pixelY() * 2 + 30 - camY, n.ball ? 8 : 11, 4);
        AreaRenderer.dropShadow(g, player.pixelX() * 2 + 16 - camX, player.pixelY() * 2 + 30 - camY, 11, 4);

        // depth-sorted sprites: statics + characters
        record Draw(int baseY, Runnable r) {}
        List<Draw> list = new ArrayList<>();
        final int cx = camX, cy = camY;
        for (AreaRenderer.Sprite s : view.statics) {
            if (s.x() - cx > Main.SW || s.x() + s.img().getWidth() - cx < 0 || s.y() - cy > Main.SH || s.y() + s.img().getHeight() - cy < 0) continue;
            list.add(new Draw(s.baseY(), () -> g.drawImage(s.img(), s.x() - cx, s.y() - cy, null)));
        }
        List<Actor> actors = new ArrayList<>(area.npcs);
        actors.add(player);
        for (Actor a : actors) {
            int sx = a.pixelX() * 2 - cx, sy = a.pixelY() * 2 - cy;
            if (sx < -T || sy < -T * 2 || sx > Main.SW + T || sy > Main.SH + T) continue;
            list.add(new Draw(a.pixelY() * 2 + T, () -> {
                if (a instanceof Npc n && n.ball) { g.drawImage(Textures.get("orb"), sx + 6, sy + 8, null); return; }
                g.drawImage(hdChar(a.look, a.facing, a.frame()), sx, sy - 16, null);
                int gx = a.x, gy = a.y;
                if (a.moving() && a.progress >= 8) { gx += Actor.dx(a.moveDir); gy += Actor.dy(a.moveDir); }
                if (area.at(gx, gy) == Tile.TALL) {   // grass in front of the legs
                    int dx = gx * T - cx, dy = gy * T - cy;
                    g.drawImage(tall, dx, dy + 16, dx + T, dy + T, 0, 16, T, T, null);
                }
            }));
        }
        list.sort(Comparator.comparingInt(Draw::baseY));
        for (Draw d : list) d.r().run();

        // quest markers over heads: yellow ! = new quest, yellow ? = ready to turn in, grey ? = in progress
        for (Npc n : area.npcs) {
            char mark = 0;
            boolean bright = true;
            if (n.kind == Npc.Kind.QUEST) {
                QuestBook.Status s = game.quests.status(n.questId);
                if (s == QuestBook.Status.AVAILABLE) mark = '!';
                else if (s != QuestBook.Status.DONE) { mark = '?'; bright = s == QuestBook.Status.READY; }
            } else if (n.kind == Npc.Kind.PROF && st.flags.contains("starter")) {
                String m = activeMain();
                if (m != null) { mark = '?'; bright = game.quests.status(m) == QuestBook.Status.READY; }
                else if (game.quests.nextMain() != null) mark = '!';
            } else if (n.kind == Npc.Kind.NURSE && game.quests.hasDeliveryFor(area.key)) mark = '?';
            if (mark == 0) continue;
            int mx = n.pixelX() * 2 - camX + 11, my = n.pixelY() * 2 - camY - 40 + (int) Math.round(Math.sin(tick * .12) * 2);
            String ms = String.valueOf(mark);
            Color fill = bright ? new Color(0xffd21a) : new Color(0xb8b8b8);
            for (int ox = -2; ox <= 2; ox++) for (int oy = -2; oy <= 2; oy++) if (ox != 0 || oy != 0) Font.raw(g, ms, mx + ox, my + oy, new Color(0x2a1a00), 3);
            Font.raw(g, ms, mx, my, fill, 3);
        }
        renderTracker(g);
        if (!toasts.isEmpty()) {
            String t = toasts.peek();
            int a = Math.min(255, toastT * 12);
            int tw = HdUi.width(t);
            int ty = dlg.isOpen() ? 200 : 214;   // just above the dialog box, clear of the tracker
            Font.raw(g, t, (Main.SW - tw) / 2 + 1, ty + 1, new Color(0, 0, 0, a), 2);
            Font.raw(g, t, (Main.SW - tw) / 2, ty, new Color(255, 214, 40, a), 2);
        }
        if (emote != null) {
            int ex = emote.pixelX() * 2 - camX + 4, ey = emote.pixelY() * 2 - camY - 44;
            g.drawImage(Icons.EMOTE, ex, ey, 24, 24, null);
        }
        if (area.cave) {
            if (hdVignette == null) hdVignette = makeHdVignette();
            g.drawImage(hdVignette, 0, 0, null);
        }
        if (popupT > 0 && popup != null) {
            int w = HdUi.width(popup) + 32;
            int slide = popupT > 135 ? (popupT - 135) * 4 : popupT < 15 ? (15 - popupT) * 4 : 0;
            HdUi.panel(g, 10, 10 - slide, w, 30, 15);
            HdUi.text(g, popup, 26, 18 - slide);
        }
        if (showcase != null) {
            HdUi.panel(g, 160, 24, 160, 172, 14);
            g.setColor(Gfx.lighter(showcase.t1.color, .55));
            g.fillRoundRect(172, 36, 136, 120, 12, 12);
            BufferedImage art = showcase.art();
            if (art != null) g.drawImage(art, 240 - art.getWidth() / 2, 154 - art.getHeight(), null);
            else g.drawImage(showcase.front(), 176, 32, 128, 128, null);
            BufferedImage tagImg = new BufferedImage(34, 12, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = tagImg.createGraphics();
            Gfx.typeTag(tg, showcase.t1, 0, 0);
            tg.dispose();
            g.drawImage(tagImg, 206, 162, 68, 24, null);
        }
        dlg.renderHd(g);
        if (choice != null) choice.renderHd(g, Main.SW - 16, 228);
    }

    public static void drawOrb(Graphics2D g, int x, int y) { g.drawImage(Icons.ORB, x - 1, y - 1, null); }

    public static void drawOrbSmall(Graphics2D g, int x, int y) { g.drawImage(Icons.ORB_SMALL, x, y, null); }

    public Area area() { return area; }
    public Actor player() { return player; }
}
