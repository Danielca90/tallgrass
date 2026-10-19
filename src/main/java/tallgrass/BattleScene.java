package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;

/** GBA-layout turn-based battle: enemy top-right, your creature's back bottom-left, text box and menus below. */
public final class BattleScene implements Scene {
    public enum Result { WIN, LOSE, RUN, CAUGHT }

    private static final int ME = 0, FOE = 1;

    private final Game game;
    private final GameState st;
    private final List<Mon> enemy;
    private final String trainer;
    private final Biome biome;
    private final Consumer<Result> done;

    private int me, foe;
    private final int[] atkStage = new int[2], defStage = new int[2];
    private final double[] dispHp = new double[2];
    private double dispXp;
    private final Set<Mon> participants = new LinkedHashSet<>();
    private int runAttempts;

    private enum Phase { INTRO, STEPS, MENU, FIGHT, WAIT, END }
    private Phase phase = Phase.INTRO;
    private int introT, menuSel, moveSel, tick;
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private Step cur;

    // visuals
    private String msg = "";
    private int msgShown;
    private final boolean[] visible = {false, false};
    private final int[] faintY = new int[2], lunge = new int[2], popIn = {0, 0};
    private int blinkSide = -1, blinkT;
    private int ballT = -1, ballShakes, ballPhaseShakes;
    private boolean ballSuccess;
    private boolean waitingSwitch;
    private boolean finished;

    public BattleScene(Game game, List<Mon> enemy, String trainer, Biome biome, Consumer<Result> done) {
        this.game = game;
        this.st = game.st;
        this.enemy = enemy;
        this.trainer = trainer;
        this.biome = biome;
        this.done = done;
        me = 0;
        for (int i = 0; i < st.party.size(); i++) if (!st.party.get(i).fainted()) { me = i; break; }
        dispHp[ME] = my().hp;
        dispHp[FOE] = foe().hp;
        dispXp = my().xpRatio();
        participants.add(my());
        for (Mon m : enemy) st.see(m.species);
    }

    private Mon my() { return st.party.get(me); }
    private Mon foe() { return enemy.get(foe); }
    private Mon mon(int side) { return side == ME ? my() : foe(); }
    private boolean wild() { return trainer == null; }
    private String pre(int side) { return side == ME ? "" : wild() ? "Wild " : "Foe "; }

    @Override public boolean overlay() { return phase == Phase.INTRO && introT < 34; }

    // ---------------------------------------------------------------- steps

    private abstract static class Step {
        boolean started;
        void start() {}
        abstract boolean update();
    }

    private void add(Step... s) { steps.addAll(Arrays.asList(s)); }

    private void insert(List<Step> list) { for (int i = list.size() - 1; i >= 0; i--) steps.addFirst(list.get(i)); }

    private Step say(String text) { return say(text, true); }

    private Step say(String text, boolean waitForA) {
        return new Step() {
            int hold;
            void start() { msg = text.replace("{P}", st.player); msgShown = 0; }
            boolean update() {
                if (msgShown < msg.length()) {
                    msgShown += game.in.held(Input.A) || game.in.held(Input.B) ? 3 : 1;
                    if (game.in.pressed(Input.A)) msgShown = msg.length();
                    return false;
                }
                if (!waitForA) return ++hold > 32;
                return game.in.pressed(Input.A) || game.in.pressed(Input.B);
            }
        };
    }

    private Step act(Runnable r) {
        return new Step() {
            void start() { r.run(); }
            boolean update() { return true; }
        };
    }

    private Step pause(int frames) {
        return new Step() {
            int t;
            boolean update() { return ++t >= frames; }
        };
    }

    private Step hpAnim(int side) {
        return new Step() {
            boolean update() {
                Mon m = mon(side);
                double target = m.hp, step = Math.max(.35, m.maxHp() / 50.0);
                if (Math.abs(dispHp[side] - target) <= step) { dispHp[side] = target; return true; }
                dispHp[side] += Math.signum(target - dispHp[side]) * step;
                return false;
            }
        };
    }

    private Step xpAnim() {
        return new Step() {
            boolean update() {
                double target = my().xpRatio();
                if (target < dispXp) dispXp = 0;
                if (Math.abs(dispXp - target) < .02) { dispXp = target; return true; }
                dispXp += .02;
                return false;
            }
        };
    }

    private Step hitAnim(int attacker, int target) {
        return new Step() {
            int t;
            boolean update() {
                t++;
                lunge[attacker] = t < 8 ? t / 2 : t < 14 ? (14 - t) / 2 : 0;
                if (t > 8) { blinkSide = target; blinkT = t - 8; }
                if (t >= 32) { blinkSide = -1; lunge[attacker] = 0; return true; }
                return false;
            }
        };
    }

    private Step faintAnim(int side) {
        return new Step() {
            boolean update() {
                faintY[side] += 4;
                if (faintY[side] >= 64) { visible[side] = false; faintY[side] = 0; return true; }
                return false;
            }
        };
    }

    private Step sendOut(int side) {
        return new Step() {
            void start() { visible[side] = true; popIn[side] = 1; faintY[side] = 0; dispHp[side] = mon(side).hp; if (side == ME) dispXp = my().xpRatio(); }
            boolean update() {
                popIn[side]++;
                if (popIn[side] >= 14) { popIn[side] = 0; return true; }
                return false;
            }
        };
    }

    private Step ballAnim() {
        return new Step() {
            void start() { ballT = 0; }
            boolean update() {
                ballT++;
                if (ballT == 34) visible[FOE] = false;
                int end = 50 + ballShakes * 28 + 20;
                if (ballT >= end) {
                    if (!ballSuccess) visible[FOE] = true;
                    ballT = -1;
                    return true;
                }
                return false;
            }
        };
    }

    // ---------------------------------------------------------------- update

    @Override
    public void update() {
        tick++;
        Input in = game.in;
        switch (phase) {
            case INTRO -> {
                introT++;
                if (introT == 34) visible[FOE] = wild();
                if (introT >= 84) {
                    phase = Phase.STEPS;
                    if (wild()) add(say("A wild " + foe().name() + " appeared!"));
                    else add(say(trainer + " wants to battle!"), say(trainer + " sent out " + foe().name() + "!", false), sendOut(FOE));
                    add(say("Go! " + my().name() + "!", false), sendOut(ME), act(this::toMenu));
                }
            }
            case STEPS -> runSteps();
            case MENU -> {
                msg = "What will " + my().name() + " do?";
                msgShown = msg.length();
                if (in.pressed(Input.LEFT) || in.pressed(Input.RIGHT)) menuSel ^= 1;
                if (in.pressed(Input.UP) || in.pressed(Input.DOWN)) menuSel ^= 2;
                if (in.pressed(Input.A)) {
                    switch (menuSel) {
                        case 0 -> { phase = Phase.FIGHT; if (moveSel >= my().moves.length) moveSel = 0; }
                        case 1 -> { phase = Phase.WAIT; game.push(new BagScene(game, true, this::useItem)); }
                        case 2 -> { phase = Phase.WAIT; game.push(new PartyScene(game, PartyScene.Mode.BATTLE, me, this::chooseSwitch)); }
                        default -> tryRun();
                    }
                }
            }
            case FIGHT -> {
                int n = my().moves.length;
                if (in.pressed(Input.LEFT) && moveSel % 2 == 1) moveSel--;
                if (in.pressed(Input.RIGHT) && moveSel % 2 == 0 && moveSel + 1 < n) moveSel++;
                if (in.pressed(Input.UP) && moveSel >= 2) moveSel -= 2;
                if (in.pressed(Input.DOWN) && moveSel + 2 < n) moveSel += 2;
                if (in.pressed(Input.B)) phase = Phase.MENU;
                if (in.pressed(Input.A)) {
                    if (my().pp[moveSel] <= 0) {
                        phase = Phase.STEPS;
                        add(say("There's no PP left for this move!"), act(() -> phase = Phase.FIGHT));
                    } else {
                        turn(Move.get(my().moves[moveSel]), null);
                    }
                }
            }
            case WAIT, END -> {}
        }
    }

    private void runSteps() {
        if (waitingSwitch) return;
        for (int guard = 0; guard < 8; guard++) {
            if (cur == null) {
                cur = steps.poll();
                if (cur == null) return;
                cur.start();
            }
            if (!cur.update()) return;
            cur = null;
            if (phase != Phase.STEPS || waitingSwitch) return;
        }
    }

    private void toMenu() { phase = Phase.MENU; }

    @Override
    public void resumed() {
        if (phase == Phase.WAIT) phase = steps.isEmpty() && cur == null ? Phase.MENU : Phase.STEPS;
    }

    // ---------------------------------------------------------------- turn logic

    private Move foeMove() {
        Mon f = foe();
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < f.moves.length; i++) if (f.pp[i] > 0) ok.add(i);
        if (ok.isEmpty()) return Move.TACKLE;
        if (Rng.FX.chance(.5)) {
            for (int i : ok) {
                Move m = Move.get(f.moves[i]);
                if (m.kind == Move.Kind.HIT && Type.eff(m.type, my().sp().t1, my().sp().t2) > 1) return m;
            }
        }
        return Move.get(f.moves[ok.get(Rng.FX.nextInt(ok.size()))]);
    }

    /** One full turn. {@code playerMove} null means the player already acted (switch/item/run) via {@code before}. */
    private void turn(Move playerMove, List<Step> before) {
        phase = Phase.STEPS;
        Move fm = foeMove();
        if (playerMove != null) {
            Mon a = my(), b = foe();
            boolean meFirst = a.spd() > b.spd() || (a.spd() == b.spd() && Rng.FX.chance(.5));
            if (meFirst) { queueAttack(ME, playerMove); queueAttack(FOE, fm); }
            else { queueAttack(FOE, fm); queueAttack(ME, playerMove); }
        } else {
            if (before != null) steps.addAll(before);
            queueAttack(FOE, fm);
        }
        add(act(this::endTurn));
    }

    private void endTurn() {
        if (phase == Phase.END) return;
        if (!my().fainted() && !foe().fainted()) phase = Phase.MENU;
    }

    private void queueAttack(int side, Move mv) {
        final Mon attacker = mon(side);
        add(act(() -> {
            if (phase == Phase.END || attacker != mon(side) || attacker.fainted() || mon(1 - side).fainted()) return;
            insert(attackSteps(side, mv));
        }));
    }

    private static double stageMul(int s) { return s >= 0 ? (2 + s) / 2.0 : 2.0 / (2 - s); }

    private List<Step> attackSteps(int side, Move mv) {
        List<Step> out = new ArrayList<>();
        Mon a = mon(side), d = mon(1 - side);
        for (int i = 0; i < a.moves.length; i++) if (a.moves[i] == mv.id && a.pp[i] > 0) { a.pp[i]--; break; }
        int other = 1 - side;
        out.add(say(pre(side) + a.name() + " used " + mv.name + "!", false));
        if (Rng.FX.nextInt(100) >= mv.acc) {
            out.add(say(pre(side) + a.name() + "'s attack missed!", false));
            return out;
        }
        switch (mv.kind) {
            case HIT -> {
                double eff = Type.eff(mv.type, d.sp().t1, d.sp().t2);
                boolean crit = Rng.FX.nextInt(16) == 0;
                double stab = a.sp().has(mv.type) ? 1.5 : 1;
                double atk = a.atk() * stageMul(atkStage[side]), def = d.def() * stageMul(defStage[other]);
                double base = ((2.0 * a.level / 5 + 2) * mv.power * atk / Math.max(1, def)) / 50 + 2;
                int dmg = (int) Math.max(1, Math.floor(base * stab * eff * (crit ? 1.5 : 1) * (0.85 + Rng.FX.nextDouble() * .15)));
                out.add(hitAnim(side, other));
                out.add(act(() -> d.hp = Math.max(0, d.hp - dmg)));
                out.add(hpAnim(other));
                if (crit) out.add(say("A critical hit!", false));
                if (eff > 1) out.add(say("It's super effective!", false));
                else if (eff < 1) out.add(say("It's not very effective...", false));
                out.add(act(() -> { if (d.fainted()) insert(faintSteps(other)); }));
            }
            case ATK_DOWN, DEF_DOWN -> {
                int[] arr = mv.kind == Move.Kind.ATK_DOWN ? atkStage : defStage;
                String stat = mv.kind == Move.Kind.ATK_DOWN ? "attack" : "defense";
                if (arr[other] <= -4) out.add(say(pre(other) + d.name() + "'s " + stat + " won't go any lower!", false));
                else {
                    out.add(hitAnim(side, other));
                    out.add(act(() -> arr[other]--));
                    out.add(say(pre(other) + d.name() + "'s " + stat + " fell!", false));
                }
            }
            case ATK_UP, DEF_UP -> {
                int[] arr = mv.kind == Move.Kind.ATK_UP ? atkStage : defStage;
                String stat = mv.kind == Move.Kind.ATK_UP ? "attack" : "defense";
                if (arr[side] >= 4) out.add(say(pre(side) + a.name() + "'s " + stat + " won't go any higher!", false));
                else {
                    out.add(act(() -> arr[side]++));
                    out.add(say(pre(side) + a.name() + "'s " + stat + " rose!", false));
                }
            }
            case HEAL -> {
                if (a.hp >= a.maxHp()) out.add(say(pre(side) + a.name() + "'s HP is already full!", false));
                else {
                    out.add(act(() -> a.hp = Math.min(a.maxHp(), a.hp + a.maxHp() / 2)));
                    out.add(hpAnim(side));
                    out.add(say(pre(side) + a.name() + " regained health!", false));
                }
            }
        }
        return out;
    }

    private List<Step> faintSteps(int side) {
        List<Step> out = new ArrayList<>();
        Mon m = mon(side);
        out.add(faintAnim(side));
        out.add(say(pre(side) + m.name() + " fainted!"));
        if (side == FOE) {
            List<Mon> earners = new ArrayList<>();
            for (Mon p : participants) if (!p.fainted() && st.party.contains(p)) earners.add(p);
            if (earners.isEmpty()) earners.add(my());
            int total = (int) (m.sp().xpYield * m.level / 7.0 * (wild() ? 1 : 1.5));
            int each = Math.max(1, total / earners.size());
            for (Mon p : earners) {
                out.add(act(() -> {
                    List<Integer> ups = p.gainXp(each);
                    List<Step> more = new ArrayList<>();
                    more.add(say(p.name() + " gained " + each + " EXP. Points!"));
                    if (p == my()) more.add(xpAnim());
                    for (int lv : ups) {
                        if (p == my()) more.add(act(() -> { dispHp[ME] = my().hp; dispXp = 0; }));
                        more.add(say(p.name() + " grew to Lv. " + lv + "!"));
                        for (Move nm : p.movesAt(lv)) {
                            if (p.knows(nm)) continue;
                            Move old = p.learn(nm);
                            more.add(say(old == null ? p.name() + " learned " + nm.name + "!"
                                    : "1, 2, and... Poof! " + p.name() + " forgot " + old.name + " and learned " + nm.name + "!"));
                        }
                    }
                    if (p == my()) more.add(xpAnim());
                    insert(more);
                }));
            }
            out.add(act(() -> {
                int next = -1;
                for (int i = foe + 1; i < enemy.size(); i++) if (!enemy.get(i).fainted()) { next = i; break; }
                if (next >= 0) {
                    final int n = next;
                    insert(List.of(
                            act(() -> { foe = n; atkStage[FOE] = defStage[FOE] = 0; participants.clear(); participants.add(my()); }),
                            say(trainer + " sent out " + enemy.get(n).name() + "!", false),
                            sendOut(FOE)));
                } else {
                    insert(List.of(
                            wild() ? pause(4) : say("You defeated " + trainer + "!"),
                            act(() -> finish(Result.WIN))));
                }
            }));
        } else {
            out.add(act(() -> {
                if (st.anyAlive()) {
                    waitingSwitch = true;
                    game.push(new PartyScene(game, PartyScene.Mode.FORCED, me, j -> {
                        waitingSwitch = false;
                        insert(List.of(
                                act(() -> { me = j; atkStage[ME] = defStage[ME] = 0; participants.add(my()); dispHp[ME] = my().hp; dispXp = my().xpRatio(); }),
                                say("Go! " + st.party.get(j).name() + "!", false),
                                sendOut(ME)));
                    }));
                } else {
                    insert(List.of(act(() -> finish(Result.LOSE))));
                }
            }));
        }
        return out;
    }

    private void finish(Result r) {
        if (finished) return;
        finished = true;
        phase = Phase.END;
        steps.clear();
        game.fade(() -> {
            game.pop();
            done.accept(r);
        });
    }

    private void tryRun() {
        if (!wild()) {
            phase = Phase.STEPS;
            add(say("No! There's no running from a trainer battle!"), act(() -> phase = Phase.MENU));
            return;
        }
        runAttempts++;
        int a = my().spd(), b = Math.max(1, foe().spd());
        boolean ok = a >= b || Rng.FX.nextInt(256) < (a * 128 / b + 30 * runAttempts) % 256;
        if (ok) {
            phase = Phase.STEPS;
            add(say("Got away safely!"), act(() -> finish(Result.RUN)));
        } else {
            turn(null, List.of(say("Can't escape!")));
        }
    }

    private void chooseSwitch(int j) {
        if (j < 0 || j == me) { phase = Phase.MENU; return; }
        Mon back = my();
        turn(null, List.of(
                say(back.name() + ", come back!", false),
                act(() -> visible[ME] = false),
                act(() -> { me = j; atkStage[ME] = defStage[ME] = 0; participants.add(my()); }),
                say("Go! " + st.party.get(j).name() + "!", false),
                sendOut(ME)));
    }

    /** Callback from the bag. target = party index for potions, -1 otherwise; item null = cancelled. */
    private void useItem(GameState.Item item, int target) {
        if (item == null) { phase = Phase.MENU; return; }
        if (item.isOrb()) {
            if (!wild()) {
                phase = Phase.STEPS;
                add(say("The trainer blocked the orb! You can't catch someone else's creature!"), act(() -> phase = Phase.MENU));
                return;
            }
            st.add(item, -1);
            Mon f = foe();
            Species s = f.sp();
            double bonus = item == GameState.Item.GREAT_ORB ? 1.5 : 1;
            double a = (3.0 * f.maxHp() - 2.0 * f.hp) * s.catchRate * bonus / (3.0 * f.maxHp());
            int shakes;
            if (a >= 255) { shakes = 3; ballSuccess = true; }
            else {
                double b = 1048560 / Math.sqrt(Math.sqrt(16711680 / Math.max(1, a)));
                shakes = 0;
                while (shakes < 4 && Rng.FX.nextInt(65536) < b) shakes++;
                ballSuccess = shakes == 4;
                shakes = Math.min(shakes, 3);
            }
            ballShakes = shakes;
            List<Step> seq = new ArrayList<>();
            seq.add(say("{P} threw a " + item.label + "!", false));
            seq.add(ballAnim());
            if (ballSuccess) {
                phase = Phase.STEPS;
                steps.addAll(seq);
                add(say("Gotcha! " + f.name() + " was caught!"),
                        act(() -> {
                            boolean toBox = st.party.size() >= 6;
                            Mon caught = f;
                            caught.healFull();
                            caught.hp = f.hp;
                            st.addMon(caught);
                            if (toBox) insert(List.of(say(caught.name() + " was sent to the storage box.")));
                        }),
                        act(() -> finish(Result.CAUGHT)));
            } else {
                String[] fail = {"Oh no! It broke free!", "Aww! It appeared to be caught!", "Aargh! Almost had it!", "Shoot! It was so close, too!"};
                seq.add(say(fail[shakes]));
                turn(null, seq);
            }
            return;
        }
        Mon m = st.party.get(target);
        int healed;
        if (item == GameState.Item.REVIVE) {
            m.hp = m.maxHp() / 2;
            healed = m.hp;
        } else {
            int amt = item == GameState.Item.POTION ? 20 : 60;
            healed = Math.min(amt, m.maxHp() - m.hp);
            m.hp += healed;
        }
        st.add(item, -1);
        List<Step> seq = new ArrayList<>();
        if (target == me) seq.add(hpAnim(ME));
        seq.add(say(item == GameState.Item.REVIVE ? m.name() + " was revived!" : m.name() + "'s HP was restored by " + healed + " points.", false));
        turn(null, seq);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(Graphics2D g) {
        if (phase == Phase.INTRO && introT < 34) {
            renderTransition(g);
            return;
        }
        drawField(g);
        if (phase == Phase.INTRO) {
            int t = introT - 34;
            if (t < 12) {
                g.setColor(Color.BLACK);
                int h = (12 - t) * 7;
                g.fillRect(0, 0, Main.W, h);
                g.fillRect(0, Main.H - h, Main.W, h);
            }
        }
        drawBottom(g);
    }

    private void renderTransition(Graphics2D g) {
        int t = introT;
        if (t < 18) {
            if ((t / 3) % 2 == 0) {
                g.setColor(new Color(255, 255, 255, 170));
                g.fillRect(0, 0, Main.W, Main.H);
            }
        } else {
            int p = t - 18;
            g.setColor(Color.BLACK);
            for (int i = 0; i < 8; i++) {
                int y = i * 20;
                int w = Math.min(Main.W, p * 16);
                if (i % 2 == 0) g.fillRect(0, y, w, 20);
                else g.fillRect(Main.W - w, y, w, 20);
            }
        }
    }

    private void drawField(Graphics2D g) {
        Color sky = biome.sky, ground = Gfx.lighter(biome.ground, .25);
        for (int y = 0; y < 112; y++) {
            double f = y / 112.0;
            g.setColor(y < 50 ? Gfx.mix(Gfx.lighter(sky, .35), sky, f * 2) : Gfx.mix(ground, Gfx.darker(ground, .12), (y - 50) / 62.0));
            g.fillRect(0, y, Main.W, 1);
        }
        g.setColor(Gfx.lighter(sky, .5));
        g.fillRect(0, 48, Main.W, 2);
        int slide = phase == Phase.INTRO ? Math.max(0, 50 - (introT - 34)) * 4 : 0;
        platform(g, 176 - slide, 74, 46, 9);
        platform(g, 62 + slide, 108, 56, 12);

        // foe
        Mon f = foe();
        if (visible[FOE] || (phase == Phase.INTRO)) {
            if (phase == Phase.INTRO && wild()) drawMon(g, f.sp(), 144 - slide + lunge[FOE] * -2, 12, FOE, true);
            else if (visible[FOE]) drawMon(g, f.sp(), 144 + lunge[FOE] * -2, 12, FOE, true);
        }
        if (ballT >= 0) drawBall(g);
        // me
        if (visible[ME]) drawMon(g, my().sp(), 30 + lunge[ME] * 2, 50, ME, false);

        if (visible[FOE]) foeBox(g);
        if (visible[ME]) myBox(g);
    }

    private void platform(Graphics2D g, int cx, int cy, int rx, int ry) {
        Color base = Gfx.darker(biome.ground, .1);
        g.setColor(Gfx.darker(base, .3));
        g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
        g.setColor(base);
        g.fillOval(cx - rx + 2, cy - ry + 1, rx * 2 - 4, ry * 2 - 4);
        g.setColor(Gfx.lighter(base, .15));
        g.fillOval(cx - rx + 8, cy - ry + 2, rx * 2 - 16, ry - 1);
    }

    private void drawMon(Graphics2D g, Species sp, int x, int y, int side, boolean front) {
        if (blinkSide == side && (blinkT / 4) % 2 == 0) return;
        int pop = popIn[side];
        BufferedImage art = sp.art();
        // hand-made art has no back view yet: both sides show the front, mirrored to face each other
        if (art != null) {
            double s = pop > 0 ? Math.min(1, pop / 12.0) : 1;
            int breathe = pop == 0 && faintY[side] == 0 && front && phase == Phase.MENU && (tick / 30) % 2 == 0 ? 1 : 0;
            if (HdSprite.draw(art, x, y + breathe, s, front, faintY[side])) {
                if (pop > 0 && pop < 6) {
                    g.setColor(new Color(255, 255, 255, 180));
                    g.fillOval(x + 32 - pop * 4, y + 48 - pop * 4, pop * 8, pop * 8);
                }
                return;
            }
        }
        BufferedImage im = front ? sp.front() : sp.back();
        if (pop > 0) {
            double s = Math.min(1, pop / 12.0);
            int w = (int) (64 * s), h = (int) (64 * s);
            g.drawImage(im, x + (64 - w) / 2, y + 64 - h, w, h, null);
            if (pop < 6) {
                g.setColor(new Color(255, 255, 255, 180));
                g.fillOval(x + 32 - pop * 4, y + 48 - pop * 4, pop * 8, pop * 8);
            }
            return;
        }
        int fy = faintY[side];
        if (fy > 0) {
            Shape old = g.getClip();
            g.setClip(x, y, 64, 64);
            g.drawImage(im, x, y + fy, null);
            g.setClip(old);
            return;
        }
        int breathe = front && phase == Phase.MENU && (tick / 30) % 2 == 0 ? 1 : 0;
        g.drawImage(im, x, y + breathe, null);
    }

    private void drawBall(Graphics2D g) {
        int t = ballT;
        int bx, by;
        if (t < 30) {
            double p = t / 30.0;
            bx = (int) (50 + (170 - 50) * p);
            by = (int) (96 - Math.sin(p * Math.PI) * 70 + (60 - 96) * p);
        } else {
            bx = 170;
            by = 60;
            if (t >= 34 && t < 44) {
                g.setColor(new Color(255, 255, 255, 200));
                int r = (44 - t) * 4;
                g.fillOval(bx + 5 - r, by + 5 - r, r * 2, r * 2);
            }
            if (t >= 44 && t < 54) by = 60 + (t - 44); // drop to platform
            if (t >= 54) by = 70;
            int sh = t - 60;
            if (sh >= 0 && sh < ballShakes * 28) {
                int ph = sh % 28;
                bx += ph < 6 ? -2 : ph < 12 ? 2 : 0;
            }
            if (ballSuccess && t > 60 + ballShakes * 28) {
                g.setColor(new Color(0xf8e858));
                for (int i = 0; i < 3; i++) g.fillRect(bx - 6 + i * 9, by - 6 - (i % 2) * 3, 2, 2);
            }
        }
        OverworldScene.drawOrb(g, bx, by);
    }

    private void infoFrame(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x405040));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(new Color(0xf8f8d8));
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 6, 6);
        g.setColor(new Color(0xd8d8b0));
        g.fillRect(x + 4, y + h - 5, w - 8, 2);
    }

    private void foeBox(Graphics2D g) {
        Mon f = foe();
        int x = 8, y = 12;
        infoFrame(g, x, y, 104, 32);
        Font.draw(g, f.name(), x + 7, y + 5);
        String lv = "Lv" + f.level;
        Font.draw(g, lv, x + 97 - Font.width(lv), y + 5);
        if (st.caught.contains(f.species)) OverworldScene.drawOrbSmall(g, x + 7 + Font.width(f.name()) + 3, y + 4);
        Font.draw(g, "HP", x + 18, y + 18, new Color(0xf8b030), new Color(0x705020));
        Gfx.hpBar(g, x + 32, y + 19, 64, dispHp[FOE] / f.maxHp());
    }

    private void myBox(Graphics2D g) {
        Mon m = my();
        int x = 126, y = 70;
        infoFrame(g, x, y, 110, 40);
        Font.draw(g, m.name(), x + 9, y + 5);
        String lv = "Lv" + m.level;
        Font.draw(g, lv, x + 102 - Font.width(lv), y + 5);
        Font.draw(g, "HP", x + 22, y + 17, new Color(0xf8b030), new Color(0x705020));
        Gfx.hpBar(g, x + 36, y + 18, 64, dispHp[ME] / m.maxHp());
        String hp = (int) Math.ceil(dispHp[ME]) + "/" + m.maxHp();
        Font.draw(g, hp, x + 100 - Font.width(hp), y + 24);
        Gfx.expBar(g, x + 20, y + 35, 82, dispXp);
    }

    private void drawBottom(Graphics2D g) {
        if (phase == Phase.MENU) {
            Gfx.battleText(g, 0, 112, 130, 48);
            List<String> l = Font.wrap(msg, 108);
            for (int i = 0; i < Math.min(2, l.size()); i++) Font.draw(g, l.get(i), 11, 122 + i * 16, Gfx.WHITE_TEXT, Gfx.DARK_SHADOW);
            Gfx.window(g, 128, 112, 112, 48);
            String[] o = {"FIGHT", "BAG", "PARTY", "RUN"};
            for (int i = 0; i < 4; i++) {
                int ox = 146 + (i % 2) * 48, oy = 123 + (i / 2) * 16;
                Font.draw(g, o[i], ox, oy);
                if (i == menuSel) Gfx.cursor(g, ox - 9, oy);
            }
            return;
        }
        if (phase == Phase.FIGHT) {
            Gfx.window(g, 0, 112, 162, 48);
            Mon m = my();
            for (int i = 0; i < 4; i++) {
                int ox = 16 + (i % 2) * 72, oy = 123 + (i / 2) * 16;
                String n = i < m.moves.length ? Move.get(m.moves[i]).name : "-";
                Font.draw(g, n, ox, oy);
                if (i == moveSel) Gfx.cursor(g, ox - 9, oy);
            }
            Gfx.window(g, 160, 112, 80, 48);
            if (moveSel < m.moves.length) {
                Move mv = Move.get(m.moves[moveSel]);
                Font.draw(g, "PP", 170, 122);
                String pp = m.pp[moveSel] + "/" + mv.pp;
                Font.draw(g, pp, 230 - Font.width(pp), 122, m.pp[moveSel] == 0 ? Gfx.RED : Gfx.TEXT, Gfx.SHADOW);
                Gfx.typeTag(g, mv.type, 170, 137);
                String pw = mv.kind == Move.Kind.HIT ? String.valueOf(mv.power) : "-";
                Font.draw(g, pw, 230 - Font.width(pw), 139);
            }
            return;
        }
        Gfx.battleText(g, 0, 112, 240, 48);
        String vis = msg.substring(0, Math.min(msgShown, msg.length()));
        List<String> l = Font.wrap(vis, 214);
        for (int i = 0; i < Math.min(2, l.size()); i++) Font.draw(g, l.get(i), 11, 122 + i * 16, Gfx.WHITE_TEXT, Gfx.DARK_SHADOW);
    }
}
