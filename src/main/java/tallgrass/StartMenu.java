package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** The Start-button menu on the right edge of the screen. */
public final class StartMenu implements Scene {
    private final Game game;
    private final OverworldScene world;
    private int sel;
    private String note;
    private boolean card;

    public StartMenu(Game game, OverworldScene world) {
        this.game = game;
        this.world = world;
    }

    @Override public boolean overlay() { return true; }

    private String[] options() { return new String[] {"DEX", "PARTY", "BAG", "QUESTS", game.st.player.toUpperCase(), "SAVE", "SAVE & QUIT", "EXIT"}; }

    @Override
    public void update() {
        Input in = game.in;
        if (note != null) {
            if (in.pressed(Input.A) || in.pressed(Input.B)) note = null;
            return;
        }
        if (card) {
            if (in.pressed(Input.A) || in.pressed(Input.B)) card = false;
            return;
        }
        String[] o = options();
        if (in.repeat(Input.UP)) sel = (sel + o.length - 1) % o.length;
        if (in.repeat(Input.DOWN)) sel = (sel + 1) % o.length;
        if (in.pressed(Input.B) || in.pressed(Input.START)) { game.pop(); return; }
        if (!in.pressed(Input.A)) return;
        switch (sel) {
            case 0 -> game.push(new DexScene(game));
            case 1 -> game.push(new PartyScene(game, PartyScene.Mode.VIEW, -1, null));
            case 2 -> game.push(new BagScene(game, false, null));
            case 3 -> game.push(new QuestLogScene(game));
            case 4 -> card = true;
            case 5 -> {
                try {
                    game.st.save();
                    note = game.st.player + " saved the game.";
                } catch (Exception e) {
                    note = "The game couldn't be saved: " + e.getMessage();
                }
            }
            case 6 -> {
                try { game.st.save(); } catch (Exception ignored) {}
                game.fade(() -> game.clearTo(new TitleScene(game)));
            }
            default -> game.pop();
        }
    }

    @Override
    public void render(Graphics2D g) {
        String[] o = options();
        int w = 82, h = o.length * 15 + 12;
        Gfx.window(g, Main.W - w - 2, 2, w, h);
        for (int i = 0; i < o.length; i++) {
            Font.draw(g, o[i], Main.W - w + 16, 10 + i * 15);
            if (i == sel) Gfx.cursor(g, Main.W - w + 7, 10 + i * 15);
        }
        if (note != null) {
            Gfx.window(g, 2, 112, 236, 46);
            List<String> l = Font.wrap(note, 214);
            for (int i = 0; i < Math.min(2, l.size()); i++) Font.draw(g, l.get(i), 13, 121 + i * 16);
        }
        if (card) drawCard(g);
    }

    private void drawCard(Graphics2D g) {
        GameState st = game.st;
        int x = 14, y = 14, w = 212, h = 132;
        g.setColor(new Color(0x305080));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(0x88b8e8));
        g.fillRoundRect(x + 3, y + 3, w - 6, h - 6, 10, 10);
        g.setColor(new Color(0xf0f8f8));
        g.fillRoundRect(x + 8, y + 22, w - 16, h - 30, 8, 8);
        Font.draw(g, "TRAINER CARD", x + 10, y + 8, Gfx.WHITE_TEXT, new Color(0x305080));
        long secs = st.playFrames / 60;
        String[][] rows = {
                {"NAME", st.player},
                {"WORLD", st.worldName},
                {"MONEY", "$" + st.money},
                {"DEX", st.caught.size() + " caught / " + st.seen.size() + " seen"},
                {"TIME", String.format("%d:%02d", secs / 3600, (secs / 60) % 60)},
                {"BADGES", String.valueOf(st.badges.size())}
        };
        for (int i = 0; i < rows.length; i++) {
            Font.draw(g, rows[i][0], x + 16, y + 30 + i * 14);
            Font.draw(g, rows[i][1], x + 72, y + 30 + i * 14);
        }
        java.awt.image.BufferedImage me = CharArt.get(st.look, Actor.DOWN, 0);
        g.drawImage(me, x + w - 50, y + 30, 32, 40, null);
        for (int i = 0; i < 8; i++) {
            int bx = x + 18 + i * 22, by = y + h - 22;
            g.setColor(new Color(0xc8d0d8));
            g.fillOval(bx, by, 14, 14);
            if (i < st.badges.size()) {
                g.setColor(new Color(0xf8c830));
                g.fillOval(bx + 1, by + 1, 12, 12);
                g.setColor(new Color(0xfff0a0));
                g.fillOval(bx + 3, by + 3, 4, 4);
            }
        }
    }
}
