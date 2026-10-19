package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** The bag. In battle it hands the chosen item (and target) back to the battle; outside it applies potions directly. */
public final class BagScene implements Scene {
    private final Game game;
    private final GameState st;
    private final boolean battle;
    private final BiConsumer<GameState.Item, Integer> cb;
    private int sel;
    private String note;
    private int noteT;

    public BagScene(Game game, boolean battle, BiConsumer<GameState.Item, Integer> cb) {
        this.game = game;
        this.st = game.st;
        this.battle = battle;
        this.cb = cb;
    }

    private List<GameState.Item> items() {
        List<GameState.Item> out = new ArrayList<>();
        for (GameState.Item i : GameState.Item.values()) if (st.count(i) > 0 && !(battle && i.isKey())) out.add(i);
        return out;
    }

    @Override
    public void update() {
        Input in = game.in;
        if (noteT > 0) noteT--;
        List<GameState.Item> list = items();
        int n = list.size() + 1; // + CLOSE
        if (in.repeat(Input.UP)) sel = (sel + n - 1) % n;
        if (in.repeat(Input.DOWN)) sel = (sel + 1) % n;
        if (in.pressed(Input.B) || (in.pressed(Input.A) && sel == list.size())) {
            game.pop();
            if (cb != null) cb.accept(null, -1);
            return;
        }
        if (!in.pressed(Input.A)) return;
        GameState.Item item = list.get(sel);
        if (item.isKey()) { say(item.desc); return; }
        if (item.isOrb()) {
            if (!battle) { say("You can only throw that at a wild creature in battle."); return; }
            game.pop();
            cb.accept(item, -1);
            return;
        }
        game.push(new PartyScene(game, PartyScene.Mode.ITEM, -1, target -> {
            if (target < 0) return;
            Mon m = st.party.get(target);
            if (item == GameState.Item.REVIVE ? !m.fainted() : (m.fainted() || m.hp >= m.maxHp())) {
                say("It won't have any effect on " + m.name() + ".");
                return;
            }
            if (battle) {
                game.pop();
                cb.accept(item, target);
            } else {
                if (item == GameState.Item.REVIVE) m.hp = m.maxHp() / 2;
                else m.hp = Math.min(m.maxHp(), m.hp + (item == GameState.Item.POTION ? 20 : 60));
                st.add(item, -1);
                say(m.name() + (item == GameState.Item.REVIVE ? " was revived!" : "'s HP was restored."));
                if (sel >= items().size()) sel = Math.max(0, items().size() - 1);
            }
        }));
    }

    private void say(String s) { note = s; noteT = 120; }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0xf0b860));
        g.fillRect(0, 0, Main.W, Main.H);
        g.setColor(new Color(0xe0a050));
        for (int x = 0; x < Main.W; x += 12) g.fillRect(x, 0, 6, Main.H);
        // bag illustration
        g.setColor(new Color(0x704020));
        g.fillRoundRect(14, 26, 64, 62, 18, 18);
        g.setColor(new Color(0xc87838));
        g.fillRoundRect(16, 28, 60, 58, 16, 16);
        g.setColor(new Color(0xe89848));
        g.fillRoundRect(22, 40, 48, 30, 10, 10);
        g.setColor(new Color(0x704020));
        g.fillRect(40, 18, 12, 12);
        Font.draw(g, "ITEMS", 28, 94, Gfx.WHITE_TEXT, Gfx.darker(new Color(0xf0b860), .5));
        List<GameState.Item> list = items();
        Gfx.window(g, 94, 4, 142, 106);
        for (int i = 0; i <= list.size(); i++) {
            int y = 12 + i * 15;
            if (i == list.size()) Font.draw(g, "CLOSE BAG", 112, y);
            else {
                GameState.Item it = list.get(i);
                Font.draw(g, it.label, 112, y);
                String c = "x" + st.count(it);
                Font.draw(g, c, 226 - Font.width(c), y);
            }
            if (i == sel) Gfx.cursor(g, 103, y);
        }
        Gfx.window(g, 2, 112, 236, 46);
        String desc = noteT > 0 ? note : sel < list.size() ? list.get(sel).desc : "Close the bag and go back.";
        List<String> l = Font.wrap(desc, 214);
        for (int i = 0; i < Math.min(2, l.size()); i++) Font.draw(g, l.get(i), 12, 121 + i * 16);
        if (sel < list.size()) g.drawImage(Icons.item(list.get(sel)), 30, 38, 32, 32, null);
    }
}
