package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;

/** Creature summary: sprite, types, stats and moves. Up/down flips through the party. */
public final class SummaryScene implements Scene {
    private final Game game;
    private int idx;

    public SummaryScene(Game game, int idx) {
        this.game = game;
        this.idx = idx;
    }

    @Override
    public void update() {
        Input in = game.in;
        int n = game.st.party.size();
        if (in.repeat(Input.UP)) idx = (idx + n - 1) % n;
        if (in.repeat(Input.DOWN)) idx = (idx + 1) % n;
        if (in.pressed(Input.B) || in.pressed(Input.A)) game.pop();
    }

    @Override
    public void render(Graphics2D g) {
        Mon m = game.st.party.get(idx);
        Species s = m.sp();
        g.setColor(new Color(0xf8e8c0));
        g.fillRect(0, 0, Main.W, Main.H);
        g.setColor(new Color(0xe86840));
        g.fillRect(0, 0, Main.W, 18);
        Font.draw(g, "CREATURE INFO", 8, 5, Gfx.WHITE_TEXT, Gfx.darker(new Color(0xe86840), .5));
        Font.draw(g, "No." + String.format("%03d", s.id), 186, 5, Gfx.WHITE_TEXT, Gfx.darker(new Color(0xe86840), .5));
        // sprite card
        g.setColor(new Color(0x506880));
        g.fillRoundRect(6, 24, 84, 84, 10, 10);
        g.setColor(Gfx.lighter(s.t1.color, .55));
        g.fillRoundRect(8, 26, 80, 80, 8, 8);
        if (s.art() == null || !HdSprite.draw(s.art(), 16, 34, 1, false, 0)) g.drawImage(s.front(), 16, 34, null);
        Font.draw(g, m.name(), 8, 112);
        Font.draw(g, "Lv" + m.level, 64, 112);
        Gfx.typeTag(g, s.t1, 6, 124);
        if (s.t2 != null) Gfx.typeTag(g, s.t2, 42, 124);
        Font.draw(g, s.category, 8, 140, Gfx.TEXT, Gfx.SHADOW);
        // stats
        Gfx.window(g, 96, 22, 140, 58);
        String[][] rows = {{"HP", m.hp + "/" + m.maxHp()}, {"ATTACK", "" + m.atk()}, {"DEFENSE", "" + m.def()}, {"SPEED", "" + m.spd()}};
        for (int i = 0; i < rows.length; i++) {
            Font.draw(g, rows[i][0], 106, 30 + i * 11);
            Font.draw(g, rows[i][1], 226 - Font.width(rows[i][1]), 30 + i * 11);
        }
        int toNext = m.level >= 100 ? 0 : Mon.xpFor(m.level + 1) - m.xp;
        Gfx.window(g, 96, 80, 140, 20);
        Font.draw(g, "Next Lv", 106, 86);
        Font.draw(g, toNext + " EXP", 226 - Font.width(toNext + " EXP"), 86);
        // moves
        Gfx.window(g, 96, 100, 140, 58);
        for (int i = 0; i < m.moves.length; i++) {
            Move mv = Move.get(m.moves[i]);
            int y = 107 + i * 12;
            g.setColor(mv.type.color);
            g.fillRect(104, y + 1, 4, 6);
            Font.draw(g, mv.name, 112, y);
            String pp = m.pp[i] + "/" + mv.pp;
            Font.draw(g, pp, 226 - Font.width(pp), y);
        }
    }
}
