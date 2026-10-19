package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** "Generating world": builds the creature list and the areas around home, a few per frame, then drops you in. */
public final class GeneratingScene implements Scene {
    private final Game game;
    private final String worldName, seed, player;
    private final CharArt.Look look;
    private GameState st;
    private final List<String> todo = new ArrayList<>();
    private int total, step;
    private String stage = "Preparing";

    public GeneratingScene(Game game, String worldName, String seed, String player, CharArt.Look look) {
        this.game = game;
        this.worldName = worldName;
        this.seed = seed;
        this.player = player;
        this.look = look;
    }

    @Override
    public void update() {
        step++;
        if (step == 2) {
            st = GameState.create(worldName, seed, player, look);
            game.startWorld(st);
            stage = "Inventing " + st.dex.size() + " creatures";
            for (int r = 0; r <= 2; r++)
                for (int x = -r; x <= r; x++)
                    for (int y = -r; y <= r; y++)
                        if (Math.abs(x) + Math.abs(y) == r) todo.add(WorldGen.key(x, y));
            total = todo.size();
            return;
        }
        if (st == null) return;
        if (step < 20) return; // let the creature count read for a moment
        if (!todo.isEmpty()) {
            String k = todo.remove(0);
            Area a = game.world.get(k);
            stage = "Building " + a.name;
            return;
        }
        if (todo.isEmpty() && step > 20 + total * 4) {
            game.world.spawn(st);
            try { st.save(); } catch (Exception ignored) {}
            game.fade(() -> game.clearTo(new OverworldScene(game)));
        }
    }

    @Override
    public void render(Graphics2D g) {
        McUi.dirt(g, 0);
        McUi.title(g, "Generating world", 58);
        Font.draw(g, stage, (Main.W - Font.width(stage)) / 2, 74, new Color(0xa0a0a0), null);
        double p = total == 0 ? 0 : (total - todo.size()) / (double) total;
        g.setColor(new Color(0x808080));
        g.fillRect(60, 90, 120, 4);
        g.setColor(new Color(0x80ff20));
        g.fillRect(60, 90, (int) (120 * p), 4);
    }
}
