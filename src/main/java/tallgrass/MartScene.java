package tallgrass;

import java.awt.Graphics2D;

/** The shop counter in every healing center. */
public final class MartScene implements Scene {
    private final Game game;
    private final GameState st;
    private int sel, qty = 1;
    private boolean choosingQty;
    private String note = "Pick an item. (B: leave)";

    public MartScene(Game game) {
        this.game = game;
        this.st = game.st;
    }

    @Override public boolean overlay() { return true; }

    @Override
    public void update() {
        Input in = game.in;
        GameState.Item[] items = java.util.Arrays.stream(GameState.Item.values()).filter(i -> !i.isKey()).toArray(GameState.Item[]::new);
        if (choosingQty) {
            GameState.Item it = items[sel];
            int max = Math.max(1, Math.min(99, st.money / it.price));
            if (in.repeat(Input.UP)) qty = qty >= max ? 1 : qty + 1;
            if (in.repeat(Input.DOWN)) qty = qty <= 1 ? max : qty - 1;
            if (in.repeat(Input.RIGHT)) qty = Math.min(max, qty + 10);
            if (in.repeat(Input.LEFT)) qty = Math.max(1, qty - 10);
            if (in.pressed(Input.B)) { choosingQty = false; note = "Pick an item. (B: leave)"; }
            if (in.pressed(Input.A)) {
                int cost = it.price * qty;
                if (cost > st.money) note = "You don't have enough money.";
                else {
                    st.money -= cost;
                    st.add(it, qty);
                    note = "Here you are! " + qty + " " + it.label + (qty > 1 ? "s" : "") + ". Thank you!";
                }
                choosingQty = false;
            }
            return;
        }
        if (in.repeat(Input.UP)) sel = sel == 0 ? items.length : sel - 1;
        if (in.repeat(Input.DOWN)) sel = sel >= items.length ? 0 : sel + 1;
        if (in.pressed(Input.B) || (in.pressed(Input.A) && sel == items.length)) {
            game.pop();
            return;
        }
        if (in.pressed(Input.A)) {
            if (st.money < items[sel].price) note = "You don't have enough money for that.";
            else { choosingQty = true; qty = 1; note = items[sel].desc; }
        }
    }

    @Override
    public void render(Graphics2D g) {
        GameState.Item[] items = java.util.Arrays.stream(GameState.Item.values()).filter(i -> !i.isKey()).toArray(GameState.Item[]::new);
        Gfx.window(g, 4, 4, 88, 26);
        Font.draw(g, "$" + st.money, 14, 13);
        Gfx.window(g, 98, 4, 138, 22 + (items.length + 1) * 14);
        for (int i = 0; i <= items.length; i++) {
            int y = 13 + i * 14;
            if (i == items.length) Font.draw(g, "CANCEL", 114, y);
            else {
                Font.draw(g, items[i].label, 114, y);
                String p = "$" + items[i].price;
                Font.draw(g, p, 228 - Font.width(p), y);
            }
            if (i == sel) Gfx.cursor(g, 105, y);
        }
        if (choosingQty) {
            Gfx.window(g, 140, 96, 96, 18 + 4);
            String q = "x" + qty + "   $" + items[sel].price * qty;
            Font.draw(g, q, 150, 104);
        }
        Gfx.window(g, 2, 116, 236, 42);
        java.util.List<String> l = Font.wrap(note, 214);
        for (int i = 0; i < Math.min(2, l.size()); i++) Font.draw(g, l.get(i), 12, 124 + i * 15);
    }
}
