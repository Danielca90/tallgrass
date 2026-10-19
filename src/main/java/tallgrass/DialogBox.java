package tallgrass;

import java.awt.Graphics2D;
import java.util.List;

/** Bottom-of-screen text box with a typewriter effect and two lines per page. */
public final class DialogBox {
    private List<String> lines = List.of();
    private int page, shown;
    private boolean open;
    private int tick;
    private final boolean hd;
    private String speaker;

    public DialogBox() { this(false); }

    /** HD boxes are drawn with {@link #renderHd} and show "Name: text" lines with a name tag. */
    public DialogBox(boolean hd) { this.hd = hd; }

    public void open(String text) {
        speaker = null;
        if (hd) {
            int c = text.indexOf(": ");
            if (c > 0 && c < 26 && text.substring(0, c).matches("[A-Z][A-Za-z .']+")) {
                speaker = text.substring(0, c);
                text = text.substring(c + 2);
            }
        }
        lines = Font.wrap(text, hd ? 200 : 210);
        page = 0;
        shown = 0;
        open = true;
    }

    public boolean isOpen() { return open; }

    private String pageText() {
        String a = page * 2 < lines.size() ? lines.get(page * 2) : "";
        String b = page * 2 + 1 < lines.size() ? lines.get(page * 2 + 1) : "";
        return a + "\n" + b;
    }

    public boolean pageDone() { return shown >= pageText().length(); }
    public boolean lastPage() { return (page + 1) * 2 >= lines.size(); }

    /** Returns true once the player has read the final page. */
    public boolean update(Input in) {
        if (!open) return true;
        tick++;
        if (!pageDone()) {
            shown += (in.held(Input.A) || in.held(Input.B)) ? 3 : 1;
            if (in.pressed(Input.A) || in.pressed(Input.B)) shown = pageText().length();
            return false;
        }
        if (in.pressed(Input.A) || in.pressed(Input.B)) {
            if (lastPage()) { open = false; return true; }
            page++;
            shown = 0;
        }
        return false;
    }

    /** For prompts that show a choice box once the text is fully on screen. */
    public boolean waitingOnLastPage() { return open && lastPage() && pageDone(); }

    public void close() { open = false; }

    public void renderHd(Graphics2D g) {
        if (!open) return;
        int x = 16, y = 236, w = Main.SW - 32, h = 72;
        HdUi.panel(g, x, y, w, h, 14);
        if (speaker != null) HdUi.tag(g, speaker, x + 12, y - 10);
        String txt = pageText();
        String[] parts = txt.substring(0, Math.min(shown, txt.length())).split("\n", -1);
        HdUi.text(g, parts[0], x + 20, y + 20);
        if (parts.length > 1) HdUi.text(g, parts[1], x + 20, y + 42);
        if (pageDone() && !lastPage()) HdUi.moreArrow(g, x + w - 28, y + h - 18, tick);
    }

    public void render(Graphics2D g) {
        if (!open) return;
        Gfx.window(g, 2, 112, 236, 46);
        String txt = pageText();
        int n = Math.min(shown, txt.length());
        String vis = txt.substring(0, n);
        String[] parts = vis.split("\n", -1);
        Font.draw(g, parts[0], 13, 121);
        if (parts.length > 1) Font.draw(g, parts[1], 13, 137);
        if (pageDone() && !lastPage()) Gfx.moreArrow(g, 222, 145, tick);
    }
}
