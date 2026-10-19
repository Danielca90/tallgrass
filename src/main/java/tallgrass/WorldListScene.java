package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** "Select World": saved worlds plus Create / Delete / Back, like Minecraft's singleplayer screen. */
public final class WorldListScene implements Scene {
    private final Game game;
    private List<GameState> worlds;
    private int sel;          // index into worlds while in the list
    private int button = -1;  // -1 = list has focus, otherwise 0..3
    private int scroll;
    private GameState confirmDelete;
    private int confirmSel = 1;
    private String note;

    private static final String[] BUTTONS = {"Play", "Create New", "Delete", "Back"};

    public WorldListScene(Game game) {
        this.game = game;
        reload();
    }

    private void reload() {
        worlds = GameState.listWorlds();
        sel = Math.min(sel, Math.max(0, worlds.size() - 1));
        if (worlds.isEmpty()) button = 1;
    }

    @Override public void resumed() { reload(); }

    @Override
    public void update() {
        Input in = game.in;
        in.setTextMode(false);
        if (confirmDelete != null) {
            if (in.pressed(Input.LEFT) || in.pressed(Input.RIGHT)) confirmSel ^= 1;
            if (in.pressed(Input.B)) confirmDelete = null;
            if (in.pressed(Input.A)) {
                if (confirmSel == 0) {
                    try { GameState.deleteWorld(confirmDelete.worldId); note = "Deleted \"" + confirmDelete.worldName + "\"."; }
                    catch (Exception e) { note = "Couldn't delete the world: " + e.getMessage(); }
                    reload();
                    if (worlds.isEmpty()) button = 1;
                }
                confirmDelete = null;
            }
            return;
        }
        if (in.pressed(Input.B)) { game.pop(); return; }
        if (button < 0) {
            if (in.repeat(Input.UP)) sel = Math.max(0, sel - 1);
            if (in.repeat(Input.DOWN)) {
                if (sel < worlds.size() - 1) sel++;
                else button = 0;
            }
            if (in.pressed(Input.A) || in.pressed(Input.START)) play();
        } else {
            if (in.repeat(Input.LEFT)) button = button % 2 == 1 ? button - 1 : button;
            if (in.repeat(Input.RIGHT)) button = button % 2 == 0 ? button + 1 : button;
            if (in.repeat(Input.UP)) {
                if (button >= 2) button -= 2;
                else if (!worlds.isEmpty()) button = -1;
            }
            if (in.repeat(Input.DOWN) && button < 2) button += 2;
            if (in.pressed(Input.A) || in.pressed(Input.START)) {
                switch (button) {
                    case 0 -> play();
                    case 1 -> game.push(new CreateWorldScene(game));
                    case 2 -> { if (!worlds.isEmpty()) { confirmDelete = worlds.get(sel); confirmSel = 1; } }
                    default -> game.pop();
                }
            }
        }
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + 3) scroll = sel - 2;
    }

    private void play() {
        if (worlds.isEmpty()) return;
        GameState st = worlds.get(sel);
        game.fade(() -> {
            game.startWorld(st);
            game.clearTo(new OverworldScene(game));
        });
    }

    @Override
    public void render(Graphics2D g) {
        McUi.dirt(g, 0);
        McUi.title(g, "Select World", 6);
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 18, Main.W, 96);
        if (worlds.isEmpty()) {
            McUi.title(g, "No worlds yet.", 52);
            McUi.title(g, "Create one to start your adventure!", 64);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm");
        for (int row = 0; row < 3; row++) {
            int i = scroll + row;
            if (i >= worlds.size()) break;
            GameState w = worlds.get(i);
            int y = 21 + row * 31;
            boolean on = i == sel && button < 0;
            if (i == sel) {
                g.setColor(on ? new Color(0xd0d0d0) : new Color(0x808080));
                g.fillRect(14, y, 212, 29);
                g.setColor(Color.BLACK);
                g.fillRect(15, y + 1, 210, 27);
            }
            // world icon: the lead creature, or the trainer if none yet
            g.setColor(new Color(0x3a5a3a));
            g.fillRect(17, y + 2, 25, 25);
            if (!w.party.isEmpty()) {
                Species s = w.dex.get(w.party.get(0).species);
                g.drawImage(s.icon(), 14, y - 1, null);
            } else {
                g.drawImage(CharArt.get(w.look, Actor.DOWN, 0), 21, y + 4, null);
            }
            Font.draw(g, w.worldName, 47, y + 3, Color.WHITE, new Color(0x3a3a3a));
            String meta = w.worldId + " (" + (w.lastPlayed > 0 ? fmt.format(new Date(w.lastPlayed)) : "never") + ")";
            Font.draw(g, meta, 47, y + 12, new Color(0x909090), null);
            String info = w.player + " - " + w.badges.size() + " badges, " + w.caught.size() + " caught";
            Font.draw(g, info, 47, y + 20, new Color(0x909090), null);
        }
        if (worlds.size() > 3) {
            int barH = 96 * 3 / worlds.size();
            int barY = 18 + (96 - barH) * scroll / Math.max(1, worlds.size() - 3);
            g.setColor(new Color(0x808080));
            g.fillRect(232, barY, 4, barH);
        }
        for (int b = 0; b < 4; b++) {
            int bx = 28 + (b % 2) * 94, by = 120 + (b / 2) * 20;
            boolean enabled = b == 1 || b == 3 || !worlds.isEmpty();
            McUi.button(g, bx, by, 90, 17, BUTTONS[b], button == b, enabled);
        }
        if (note != null) Font.draw(g, note, 6, 112, new Color(0xffff80), null);
        if (confirmDelete != null) {
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, Main.W, Main.H);
            McUi.title(g, "Are you sure you want to delete", 40);
            McUi.title(g, "\"" + confirmDelete.worldName + "\"?", 52);
            McUi.title(g, "It will be lost forever. (A long time!)", 70);
            McUi.button(g, 28, 100, 88, 18, "Delete", confirmSel == 0, true);
            McUi.button(g, 124, 100, 88, 18, "Cancel", confirmSel == 1, true);
        }
    }
}
