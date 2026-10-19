package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Comparator;
import java.util.List;

/** Quest log: active quests on the left, the selected quest's story, objectives and rewards on the right. */
public final class QuestLogScene implements Scene {
    private final Game game;
    private int sel;
    private ChoiceBox confirm;

    public QuestLogScene(Game game) { this.game = game; }

    private List<String> ids() {
        List<String> ids = game.quests.active();
        ids.sort(Comparator.comparing(id -> id.startsWith("main:") ? 0 : 1));
        return ids;
    }

    @Override
    public void update() {
        Input in = game.in;
        List<String> ids = ids();
        if (confirm != null) {
            int r = confirm.update(in);
            if (r == -2) return;
            if (r == 0 && sel < ids.size()) {
                game.st.quests().remove(ids.get(sel));
                sel = Math.max(0, sel - 1);
            }
            confirm = null;
            return;
        }
        if (in.repeat(Input.UP)) sel = Math.max(0, sel - 1);
        if (in.repeat(Input.DOWN)) sel = Math.min(Math.max(0, ids.size() - 1), sel + 1);
        if (in.pressed(Input.B)) { game.pop(); return; }
        if (in.pressed(Input.A) && sel < ids.size() && !ids.get(sel).startsWith("main:")) confirm = new ChoiceBox(true, "ABANDON", "KEEP");
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x3a2a1a));
        g.fillRect(0, 0, Main.W, Main.H);
        g.setColor(new Color(0x4a3622));
        for (int y = 0; y < Main.H; y += 6) g.fillRect(0, y, Main.W, 2);
        // parchment
        g.setColor(new Color(0x2a1a0a));
        g.fillRoundRect(4, 4, 232, 152, 8, 8);
        g.setColor(new Color(0xe8d8b0));
        g.fillRoundRect(6, 6, 228, 148, 6, 6);
        Color ink = new Color(0x3a2a18), soft = new Color(0xd8c498), gold = new Color(0x9a6a10);
        Font.draw(g, "QUEST LOG", 12, 10, ink, soft);
        String done = game.st.questsDone().size() + " completed";
        Font.draw(g, done, 228 - Font.width(done), 10, gold, soft);
        g.setColor(new Color(0xb8a070));
        g.fillRect(12, 20, 216, 1);
        g.fillRect(84, 24, 1, 126);
        List<String> ids = ids();
        if (ids.isEmpty()) {
            Font.draw(g, "No quests yet.", 12, 30, ink, soft);
            Font.draw(g, "Look for people with a yellow ! over their heads.", 12, 44, ink, soft);
            return;
        }
        for (int i = 0; i < ids.size() && i < 11; i++) {
            Quest q = game.quests.def(ids.get(i));
            int y = 26 + i * 11;
            if (i == sel) {
                g.setColor(new Color(0xd8c088));
                g.fillRect(9, y - 2, 73, 11);
            }
            String t = q.title;
            while (Font.width(t) > 66 && t.length() > 3) t = t.substring(0, t.length() - 2);
            if (!t.equals(q.title)) t = t + "..";
            Font.draw(g, t, 12, y, game.quests.complete(ids.get(i)) ? new Color(0x2a7a20) : q.isMain() ? gold : ink, null);
        }
        String id = ids.get(Math.min(sel, ids.size() - 1));
        Quest q = game.quests.def(id);
        int x = 90, y = 26;
        for (String l : Font.wrap(q.title, 138)) { Font.draw(g, l, x, y, gold, soft); y += 10; }
        Font.draw(g, "From: " + q.giver, x, y, new Color(0x6a5a40), null);
        y += 12;
        String story = q.offer.get(q.offer.size() - 1);
        int c = story.indexOf(": ");
        if (c > 0 && c < 26) story = story.substring(c + 2);
        for (String l : Font.wrap(story, 138)) { if (y > 100) break; Font.draw(g, l, x, y, ink, null); y += 9; }
        y += 3;
        for (int i = 0; i < q.objs.size(); i++) {
            Quest.Obj o = q.objs.get(i);
            int p = game.quests.progress(id, i);
            for (String l : Font.wrap((p >= o.count ? "+ " : "- ") + o.label + " " + p + "/" + o.count, 138)) {
                Font.draw(g, l, x, y, p >= o.count ? new Color(0x2a7a20) : ink, null);
                y += 9;
            }
        }
        y = Math.max(y + 2, 126);
        String reward = "Reward: $" + q.money + (q.item != null ? ", " + q.itemCount + "x " + q.item.label : "");
        for (String l : Font.wrap(reward, 138)) { Font.draw(g, l, x, y, gold, null); y += 9; }
        if (!q.isMain()) Font.draw(g, "A: abandon", 12, 146, new Color(0x8a7a5a), null);
        if (confirm != null) confirm.render(g, 230, 150);
    }
}
