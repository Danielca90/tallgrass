package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;

/** Title screen: animated landscape, a parade of creatures, and Minecraft-style buttons. */
public final class TitleScene implements Scene {
    private final Game game;
    private final Dex demo = Dex.generate(20241016L);
    private int sel;
    private static final String[] SPLASH = {"Now with 100% more grass!", "Every seed is a new world!", "Gotta find 'em all!", "Procedurally adorable!", "No two worlds alike!", "Also try the tall grass!"};
    private final String splash = SPLASH[(int) (System.nanoTime() % SPLASH.length + SPLASH.length) % SPLASH.length];

    public TitleScene(Game game) { this.game = game; }

    @Override
    public void update() {
        Input in = game.in;
        in.setTextMode(false);
        if (in.repeat(Input.UP)) sel = (sel + 1) % 2;
        if (in.repeat(Input.DOWN)) sel = (sel + 1) % 2;
        if (in.pressed(Input.A) || in.pressed(Input.START)) {
            if (sel == 0) game.push(new WorldListScene(game));
            else System.exit(0);
        }
    }

    @Override
    public void render(Graphics2D g) {
        int t = game.tick;
        for (int y = 0; y < Main.H; y++) {
            g.setColor(Gfx.mix(new Color(0x2858c8), new Color(0xa8e0f8), Math.min(1, y / 110.0)));
            g.fillRect(0, y, Main.W, 1);
        }
        for (int x = 0; x < Main.W; x++) {
            int h1 = (int) (100 + Math.sin((x + t * .2) * .03) * 8);
            g.setColor(new Color(0x60a858));
            g.fillRect(x, h1, 1, Main.H - h1);
            int h2 = (int) (118 + Math.sin((x + t * .5) * .05 + 1) * 5);
            g.setColor(new Color(0x48903c));
            g.fillRect(x, h2, 1, Main.H - h2);
        }
        java.awt.image.BufferedImage grass = TileArt.get(Tile.TALL, Biome.MEADOW, 0, (t / 30) % 2);
        int off = (t / 2) % 16;
        for (int x = -16; x < Main.W + 16; x += 16) {
            g.drawImage(grass, x - off, 136, null);
            g.drawImage(grass, x - off, 148, null);
        }
        int cycle = 300, p = t % cycle;
        Species s = demo.all.get((t / cycle) % demo.size());
        int x = Main.W - p * (Main.W + 64) / cycle;
        g.drawImage(s.front(), x, 82 - (int) Math.abs(Math.sin(p * .15) * 4), null);

        String title = "TALLGRASS";
        int tw = Font.bigWidth(title, 3);
        Font.big(g, title, (Main.W - tw) / 2, 14, 3, new Color(0xf8d030), new Color(0x284080));
        // Minecraft-style yellow splash text, pulsing
        double pulse = 1 + Math.sin(t * .15) * .06;
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(186, 44);
        g.rotate(-0.3);
        g.scale(pulse, pulse);
        Font.draw(g, splash, -Font.width(splash) / 2, -4, new Color(0xffff40), new Color(0x3f3f00));
        g.setTransform(old);

        McUi.button(g, 60, 70, 120, 18, "Singleplayer", sel == 0, true);
        McUi.button(g, 60, 94, 120, 18, "Quit Game", sel == 1, true);
        String keys = "Arrows: move  Z: A  X: B  Enter: Start";
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 150, Main.W, 10);
        Font.draw(g, keys, (Main.W - Font.width(keys)) / 2, 151, Gfx.WHITE_TEXT, null);
    }
}
