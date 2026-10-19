package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.function.IntConsumer;

/** Party screen: six panels in a 2x3 grid. Used for viewing, switching in battle, and choosing an item target. */
public final class PartyScene implements Scene {
    public enum Mode { VIEW, BATTLE, FORCED, ITEM }

    private final Game game;
    private final GameState st;
    private final Mode mode;
    private final int active;
    private final IntConsumer cb;
    private int sel, swapFrom = -1;
    private ChoiceBox menu;
    private String note;
    private int noteT;

    public PartyScene(Game game, Mode mode, int active, IntConsumer cb) {
        this.game = game;
        this.st = game.st;
        this.mode = mode;
        this.active = active;
        this.cb = cb;
        if (mode == Mode.FORCED) for (int i = 0; i < st.party.size(); i++) if (!st.party.get(i).fainted()) { sel = i; break; }
    }

    private void close(int result) {
        game.pop();
        if (cb != null) cb.accept(result);
    }

    @Override
    public void update() {
        Input in = game.in;
        if (noteT > 0) noteT--;
        int n = st.party.size();
        if (menu != null) {
            int r = menu.update(in);
            if (r == -2) return;
            String pick = r < 0 ? "CANCEL" : menu.options[r];
            menu = null;
            switch (pick) {
                case "SUMMARY" -> game.push(new SummaryScene(game, sel));
                case "SWITCH" -> swapFrom = sel;
                case "SHIFT" -> tryShift();
                default -> {}
            }
            return;
        }
        if (in.repeat(Input.LEFT) && sel % 2 == 1) sel--;
        if (in.repeat(Input.RIGHT) && sel % 2 == 0 && sel + 1 < n) sel++;
        if (in.repeat(Input.UP) && sel >= 2) sel -= 2;
        if (in.repeat(Input.DOWN) && sel + 2 < n) sel += 2;
        if (in.pressed(Input.B)) {
            if (swapFrom >= 0) { swapFrom = -1; return; }
            if (mode != Mode.FORCED) close(-1);
            return;
        }
        if (!in.pressed(Input.A)) return;
        if (swapFrom >= 0) {
            if (swapFrom != sel) Collections.swap(st.party, swapFrom, sel);
            swapFrom = -1;
            return;
        }
        switch (mode) {
            case VIEW -> menu = new ChoiceBox(true, "SUMMARY", "SWITCH", "CANCEL");
            case BATTLE -> menu = new ChoiceBox(true, "SHIFT", "SUMMARY", "CANCEL");
            case FORCED -> tryShift();
            case ITEM -> close(sel);
        }
    }

    private void tryShift() {
        Mon m = st.party.get(sel);
        if (m.fainted()) { say(m.name() + " has no energy left to battle!"); return; }
        if (sel == active && mode != Mode.FORCED) { say(m.name() + " is already in battle!"); return; }
        close(sel);
    }

    private void say(String s) { note = s; noteT = 100; }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x38a0b0));
        g.fillRect(0, 0, Main.W, Main.H);
        g.setColor(new Color(0x48b0c0));
        for (int y = 0; y < Main.H; y += 8) g.fillRect(0, y, Main.W, 4);
        for (int i = 0; i < 6; i++) {
            int x = 3 + (i % 2) * 118, y = 3 + (i / 2) * 36;
            if (i >= st.party.size()) {
                g.setColor(new Color(0x287888));
                g.fillRoundRect(x, y, 116, 34, 10, 10);
                continue;
            }
            Mon m = st.party.get(i);
            boolean selected = i == sel, swapping = i == swapFrom;
            Color base = m.fainted() ? new Color(0xe8a090) : i == active && mode != Mode.VIEW && mode != Mode.ITEM ? new Color(0xa8e0a0) : new Color(0x78c8f0);
            g.setColor(selected ? new Color(0xf87828) : swapping ? new Color(0xf8d030) : new Color(0x305868));
            g.fillRoundRect(x, y, 116, 34, 10, 10);
            g.setColor(selected ? Gfx.lighter(base, .3) : base);
            g.fillRoundRect(x + 2, y + 2, 112, 30, 8, 8);
            g.drawImage(m.sp().icon(), x + 1, y + 1 + (selected && (game.tick / 10) % 2 == 0 ? -1 : 0), null);
            Font.draw(g, m.name(), x + 35, y + 5, Gfx.WHITE_TEXT, Gfx.DARK_SHADOW);
            Font.draw(g, "Lv" + m.level, x + 35, y + 17, Gfx.WHITE_TEXT, Gfx.DARK_SHADOW);
            Gfx.hpBar(g, x + 62, y + 19, 48, m.hp / (double) m.maxHp());
            String hp = m.hp + "/" + m.maxHp();
            Font.draw(g, hp, x + 110 - Font.width(hp), y + 24, Gfx.WHITE_TEXT, Gfx.DARK_SHADOW);
        }
        Gfx.window(g, 2, 114, 236, 44);
        String msg = noteT > 0 ? note : swapFrom >= 0 ? "Move to where?" : switch (mode) {
            case ITEM -> "Use on which creature?";
            case FORCED -> "Choose the next creature to send out.";
            case BATTLE -> "Choose a creature. (B: back)";
            default -> "Choose a creature. (B: back)";
        };
        java.util.List<String> lines = Font.wrap(msg, 214);
        for (int i = 0; i < Math.min(2, lines.size()); i++) Font.draw(g, lines.get(i), 12, 123 + i * 15);
        if (menu != null) menu.render(g, 236, 112);
    }
}
