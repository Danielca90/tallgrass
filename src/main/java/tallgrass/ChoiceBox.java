package tallgrass;

import java.awt.Graphics2D;

/** A small vertical menu window, e.g. YES / NO. */
public final class ChoiceBox {
    public final String[] options;
    public int sel;
    private final boolean cancellable;

    public ChoiceBox(boolean cancellable, String... options) {
        this.options = options;
        this.cancellable = cancellable;
    }

    /** -2 while choosing, -1 if cancelled, otherwise the chosen index. */
    public int update(Input in) {
        if (in.repeat(Input.UP)) sel = (sel + options.length - 1) % options.length;
        if (in.repeat(Input.DOWN)) sel = (sel + 1) % options.length;
        if (in.pressed(Input.A)) return sel;
        if (cancellable && in.pressed(Input.B)) return -1;
        return -2;
    }

    public void renderHd(Graphics2D g, int rightX, int bottomY) {
        int w = 0;
        for (String o : options) w = Math.max(w, HdUi.width(o));
        w += 48;
        int h = options.length * 28 + 16;
        int x = rightX - w, y = bottomY - h;
        HdUi.panel(g, x, y, w, h, 12);
        for (int i = 0; i < options.length; i++) {
            HdUi.text(g, options[i], x + 30, y + 14 + i * 28);
            if (i == sel) HdUi.cursor(g, x + 14, y + 15 + i * 28);
        }
    }

    public void render(Graphics2D g, int rightX, int bottomY) {
        int w = 0;
        for (String o : options) w = Math.max(w, Font.width(o));
        w += 28;
        int h = options.length * 16 + 10;
        int x = rightX - w, y = bottomY - h;
        Gfx.window(g, x, y, w, h);
        for (int i = 0; i < options.length; i++) {
            Font.draw(g, options[i], x + 18, y + 8 + i * 16);
            if (i == sel) Gfx.cursor(g, x + 9, y + 8 + i * 16);
        }
    }
}
