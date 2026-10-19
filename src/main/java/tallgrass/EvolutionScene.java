package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** "What? X is evolving!" The sprite flickers between forms faster and faster. B stops it. */
public final class EvolutionScene implements Scene {
    private final Game game;
    private final Mon mon;
    private final Species from, to;
    private final DialogBox dlg = new DialogBox();
    private int t, stage; // 0 intro text, 1 flicker, 2 result text, 3 cancelled text
    private final String oldName;

    public EvolutionScene(Game game, Mon mon) {
        this.game = game;
        this.mon = mon;
        this.from = mon.sp();
        this.to = Dex.current.get(from.evolvesTo);
        this.oldName = mon.name();
        dlg.open("What? " + oldName + " is evolving!");
    }

    @Override
    public void update() {
        Input in = game.in;
        switch (stage) {
            case 0 -> { if (dlg.update(in)) stage = 1; }
            case 1 -> {
                t++;
                if (in.pressed(Input.B) && t < 150) {
                    mon.pendingEvolution = false;
                    dlg.open("Huh? " + oldName + " stopped evolving!");
                    stage = 3;
                    return;
                }
                if (t >= 200) {
                    mon.evolve();
                    game.st.own(mon.species);
                    StringBuilder sb = new StringBuilder("Congratulations! Your " + oldName + " evolved into " + to.name + "!");
                    for (Move m : mon.movesAt(mon.level)) {
                        if (mon.knows(m)) continue;
                        Move old = mon.learn(m);
                        sb.append("\n").append(old == null ? to.name + " learned " + m.name + "!" : to.name + " forgot " + old.name + " and learned " + m.name + "!");
                    }
                    dlg.open(sb.toString());
                    stage = 2;
                }
            }
            default -> { if (dlg.update(in)) game.pop(); }
        }
    }

    @Override
    public void render(Graphics2D g) {
        for (int y = 0; y < Main.H; y++) {
            g.setColor(Gfx.mix(new Color(0x183050), new Color(0x4878a8), y / (double) Main.H));
            g.fillRect(0, y, Main.W, 1);
        }
        g.setColor(new Color(255, 255, 255, 40));
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + game.tick * .01;
            g.fillArc(120 - 140, 56 - 140, 280, 280, (int) Math.toDegrees(a), 8);
        }
        BufferedImage im = from.front();
        if (stage == 1) {
            int period = Math.max(2, 24 - t / 9);
            boolean showNew = (t / period) % 2 == 1;
            im = showNew ? to.silhouette() : from.silhouette();
            if (t > 180) {
                g.setColor(new Color(255, 255, 255, Math.min(255, (t - 180) * 13)));
                g.fillRect(0, 0, Main.W, Main.H);
            }
        } else if (stage == 2) im = to.front();
        g.drawImage(im, 88, 26, null);
        dlg.render(g);
    }
}
