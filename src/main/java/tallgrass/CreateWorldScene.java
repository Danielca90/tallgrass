package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;

/** "Create New World": world name, seed, and a character creator with a live preview. */
public final class CreateWorldScene implements Scene {
    private final Game game;
    private String worldName = "New World", seed = "", player = "";
    private int row;
    private int skin, hair, style, hat = 1, outfit;
    private int col; // on the button row: 0 = create, 1 = cancel
    private String error;

    static final int[] SKINS = {0xf8d0b0, 0xe8b088, 0xc08860, 0x8c5a3a};
    static final String[] SKIN_NAMES = {"Light", "Tan", "Brown", "Deep"};
    static final int[] HAIRS = {0x302018, 0x704020, 0xd8a040, 0xb04828, 0x202838, 0xe8e0d0, 0x6848a0, 0x48a088};
    static final String[] HAIR_NAMES = {"Black", "Brown", "Blond", "Red", "Navy", "White", "Violet", "Teal"};
    static final String[] STYLES = {"Short", "Long"};
    static final int[] HATS = {0, 0xe03838, 0x3878d8, 0x48a848, 0x282828, 0xf0f0f0, 0xf0c030};
    static final String[] HAT_NAMES = {"None", "Red cap", "Blue cap", "Green cap", "Black cap", "White cap", "Gold cap"};
    static final int[][] OUTFITS = {{0x3868c8, 0x404858}, {0xe04848, 0x383838}, {0x48a858, 0x504030}, {0xf0a030, 0x384868}, {0x9058c8, 0x303040}, {0xf0f0f0, 0x2858a8}, {0x303038, 0x585060}};
    static final String[] OUTFIT_NAMES = {"Blue", "Red", "Green", "Orange", "Purple", "White", "Black"};

    private static final String[] ROWS = {"World Name", "Seed", "Your Name", "Skin", "Hair", "Hair Style", "Hat", "Outfit"};

    public CreateWorldScene(Game game) {
        this.game = game;
        Rng r = new Rng(System.nanoTime());
        skin = r.nextInt(SKINS.length);
        hair = r.nextInt(HAIRS.length);
        outfit = r.nextInt(OUTFITS.length);
    }

    private CharArt.Look look() {
        return new CharArt.Look(HATS[hat], HAIRS[hair], SKINS[skin], OUTFITS[outfit][0], OUTFITS[outfit][1], hat != 0, style == 1);
    }

    private boolean textRow() { return row <= 2; }

    @Override
    public void update() {
        Input in = game.in;
        in.setTextMode(textRow());
        if (in.repeat(Input.UP) && row > 0) row--;
        if (in.repeat(Input.DOWN) && row < ROWS.length) row++;
        switch (row) {
            case 0 -> worldName = (McUi.edit(in, worldName, 18));
            case 1 -> seed = (McUi.edit(in, seed, 20));
            case 2 -> player = (McUi.edit(in, player, 10));
            default -> {}
        }
        int d = in.repeat(Input.RIGHT) ? 1 : in.repeat(Input.LEFT) ? -1 : 0;
        if (d != 0) {
            switch (row) {
                case 3 -> skin = Math.floorMod(skin + d, SKINS.length);
                case 4 -> hair = Math.floorMod(hair + d, HAIRS.length);
                case 5 -> style = Math.floorMod(style + d, STYLES.length);
                case 6 -> hat = Math.floorMod(hat + d, HATS.length);
                case 7 -> outfit = Math.floorMod(outfit + d, OUTFITS.length);
                case 8 -> col ^= 1;
                default -> {}
            }
        }
        if (in.pressed(Input.B)) { in.setTextMode(false); game.pop(); return; }
        boolean confirm = in.pressed(Input.START) || (!textRow() && in.pressed(Input.A));
        if (!confirm) return;
        if (row < ROWS.length) { row++; return; } // Enter moves to the next field
        if (col == 1) { in.setTextMode(false); game.pop(); return; }
        if (player.isBlank()) { error = "Give your trainer a name first."; row = 2; return; }
        in.setTextMode(false);
        String wn = worldName, sd = seed, pn = player;
        CharArt.Look lk = look();
        game.fade(() -> game.clearTo(new GeneratingScene(game, wn, sd, pn, lk)));
    }

    private String value(int r) {
        return switch (r) {
            case 3 -> SKIN_NAMES[skin];
            case 4 -> HAIR_NAMES[hair];
            case 5 -> STYLES[style];
            case 6 -> HAT_NAMES[hat];
            case 7 -> OUTFIT_NAMES[outfit];
            default -> "";
        };
    }

    @Override
    public void render(Graphics2D g) {
        McUi.dirt(g, 0);
        if (error != null) Font.draw(g, error, (Main.W - Font.width(error)) / 2, 4, new Color(0xff8080), new Color(0x401010));
        else McUi.title(g, "Create New World", 4);
        for (int r = 0; r < ROWS.length; r++) {
            int y = 17 + r * 15;
            boolean on = r == row;
            McUi.label(g, ROWS[r], 6, y + 3);
            if (r <= 2) {
                String txt = r == 0 ? worldName : r == 1 ? seed : player;
                String ph = r == 1 ? "blank = random" : r == 2 ? "type a name" : "";
                McUi.field(g, 62, y, 96, 13, txt, on, game.tick, ph);
            } else {
                McUi.button(g, 62, y, 96, 13, "< " + value(r) + " >", on, true);
            }
        }
        McUi.button(g, 8, 140, 110, 16, "Create New World", row == 8 && col == 0, true);
        McUi.button(g, 122, 140, 110, 16, "Cancel", row == 8 && col == 1, true);
        // character preview, turning slowly
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(166, 17, 68, 118);
        g.setColor(new Color(0x5a8a48));
        g.fillOval(172, 104, 56, 14);
        int dirs[] = {Actor.DOWN, Actor.LEFT, Actor.UP, Actor.RIGHT};
        int facing = dirs[(game.tick / 50) % 4];
        int frame = (game.tick / 10) % 4 == 1 ? 1 : (game.tick / 10) % 4 == 3 ? 2 : 0;
        g.drawImage(CharArt.get(look(), facing, frame), 176, 44, 48, 60, null);
        String n = player.isBlank() ? "???" : player;
        Font.draw(g, n, 200 - Font.width(n) / 2, 122, Color.WHITE, new Color(0x303030));
        String hint = textRow() ? "Type + Enter" : row < 8 ? "Left / Right" : "";
        if (!hint.isEmpty()) Font.draw(g, hint, 200 - Font.width(hint) / 2, 22, new Color(0xb0b0b0), null);
    }
}
