package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Scene stack, fades and the shared game state. */
public final class Game {
    public final Input in;
    public GameState st;
    public WorldGen world;
    public QuestBook quests;
    public int tick;

    private final List<Scene> stack = new ArrayList<>();
    private int fade;          // 0..FADE
    private int fadeDir;       // +1 fading out, -1 fading in
    private Runnable fadeMid;
    private Color fadeColor = Color.BLACK;
    private static final int FADE = 14;

    public Game(Input in) {
        this.in = in;
        push(new TitleScene(this));
    }

    public void startWorld(GameState st) {
        this.st = st;
        Dex.current = st.dex;
        this.world = new WorldGen(st);
        this.quests = new QuestBook(st, world);
    }

    public void push(Scene s) { stack.add(s); }

    public void pop() {
        if (!stack.isEmpty()) stack.remove(stack.size() - 1);
        if (!stack.isEmpty()) top().resumed();
    }

    public void replace(Scene s) {
        if (!stack.isEmpty()) stack.remove(stack.size() - 1);
        stack.add(s);
    }

    public void clearTo(Scene s) {
        stack.clear();
        stack.add(s);
    }

    public Scene top() { return stack.get(stack.size() - 1); }

    public boolean fading() { return fadeDir != 0; }

    /** Fade to black, run the action at full black, fade back in. */
    public void fade(Runnable mid) { fade(mid, Color.BLACK); }

    public void fade(Runnable mid, Color c) {
        if (fadeDir != 0) return;
        fadeMid = mid;
        fadeDir = 1;
        fadeColor = c;
    }

    public void update() {
        tick++;
        if (st != null) st.playFrames++;
        if (fadeDir != 0) {
            fade += fadeDir;
            if (fade >= FADE) {
                fade = FADE;
                fadeDir = -1;
                if (fadeMid != null) { Runnable r = fadeMid; fadeMid = null; r.run(); }
            } else if (fade <= 0) {
                fade = 0;
                fadeDir = 0;
            }
            return;
        }
        if (!stack.isEmpty()) top().update();
    }

    private final java.awt.image.BufferedImage low = new java.awt.image.BufferedImage(Main.W, Main.H, java.awt.image.BufferedImage.TYPE_INT_ARGB);

    /** Renders into the 480x320 framebuffer. Classic scenes go through a 240x160 layer scaled 2x. */
    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Main.SW, Main.SH);
        int start = stack.size() - 1;
        while (start > 0 && stack.get(start).overlay()) start--;
        for (int i = Math.max(0, start); i < stack.size(); i++) {
            Scene s = stack.get(i);
            if (s.hd()) { s.render(g); continue; }
            Graphics2D lg = low.createGraphics();
            lg.setComposite(java.awt.AlphaComposite.Clear);
            lg.fillRect(0, 0, Main.W, Main.H);
            lg.setComposite(java.awt.AlphaComposite.SrcOver);
            HdSprite.begin(g, low);
            s.render(lg);
            HdSprite.end();
            lg.dispose();
            g.drawImage(low, 0, 0, Main.SW, Main.SH, null);
        }
        if (fade > 0) {
            int a = Math.min(255, fade * 255 / FADE);
            g.setColor(new Color(fadeColor.getRed(), fadeColor.getGreen(), fadeColor.getBlue(), a));
            g.fillRect(0, 0, Main.SW, Main.SH);
        }
    }

    public OverworldScene overworld() {
        for (Scene s : stack) if (s instanceof OverworldScene o) return o;
        return null;
    }
}
