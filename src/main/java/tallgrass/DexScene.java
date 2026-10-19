package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** The Creaturedex: every species in this world, revealed as you see and catch them. */
public final class DexScene implements Scene {
    private final Game game;
    private int sel, scroll;

    public DexScene(Game game) { this.game = game; }

    @Override
    public void update() {
        Input in = game.in;
        int n = game.st.dex.size();
        if (in.repeat(Input.UP)) sel = Math.max(0, sel - 1);
        if (in.repeat(Input.DOWN)) sel = Math.min(n - 1, sel + 1);
        if (in.repeat(Input.LEFT)) sel = Math.max(0, sel - 7);
        if (in.repeat(Input.RIGHT)) sel = Math.min(n - 1, sel + 7);
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + 7) scroll = sel - 6;
        if (in.pressed(Input.B)) game.pop();
    }

    @Override
    public void render(Graphics2D g) {
        GameState st = game.st;
        g.setColor(new Color(0xd84838));
        g.fillRect(0, 0, Main.W, Main.H);
        g.setColor(new Color(0xb83028));
        g.fillRect(0, 0, Main.W, 16);
        Font.draw(g, "CREATUREDEX", 8, 4, Gfx.WHITE_TEXT, new Color(0x601810));
        String count = "SEEN " + st.seen.size() + "  OWN " + st.caught.size();
        Font.draw(g, count, 232 - Font.width(count), 4, Gfx.WHITE_TEXT, new Color(0x601810));
        // list
        Gfx.window(g, 118, 18, 120, 112);
        for (int row = 0; row < 7; row++) {
            int i = scroll + row;
            if (i >= st.dex.size()) break;
            Species s = st.dex.all.get(i);
            int y = 26 + row * 14;
            boolean seen = st.seen.contains(s.id);
            Font.draw(g, String.format("%03d", s.id), 132, y);
            Font.draw(g, seen ? s.name : "----------", 156, y);
            if (st.caught.contains(s.id)) OverworldScene.drawOrbSmall(g, 224, y - 1);
            if (i == sel) Gfx.cursor(g, 124, y);
        }
        // picture
        Species s = st.dex.all.get(sel);
        boolean seen = st.seen.contains(s.id), own = st.caught.contains(s.id);
        g.setColor(new Color(0x404040));
        g.fillRoundRect(8, 22, 102, 92, 8, 8);
        g.setColor(new Color(0xe8f0e0));
        g.fillRoundRect(10, 24, 98, 88, 6, 6);
        if (!seen || s.art() == null || !HdSprite.draw(s.art(), 27, 36, 1, false, 0)) g.drawImage(seen ? s.front() : s.silhouette(), 27, 36, null);
        if (seen) {
            Gfx.typeTag(g, s.t1, 12, 100);
            if (s.t2 != null) Gfx.typeTag(g, s.t2, 46, 100);
        }
        Gfx.window(g, 2, 130, 236, 28);
        String text = own ? s.name + ", the " + s.category + ". " + s.desc : seen ? s.name + ". Catch one to learn more about it." : "You haven't seen this creature yet.";
        List<String> l = Font.wrap(text, 400);
        // Scroll long descriptions slowly across the single line
        String line = l.isEmpty() ? "" : String.join(" ", l);
        int w = Font.width(line);
        int off = w > 214 ? (int) ((game.tick / 2) % (w + 60)) - 20 : 0;
        java.awt.Shape clip = g.getClip();
        g.setClip(10, 134, 220, 20);
        Font.draw(g, line, 12 - Math.max(0, off), 139);
        g.setClip(clip);
    }
}
