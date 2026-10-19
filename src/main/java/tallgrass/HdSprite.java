package tallgrass;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Lets a classic (240x160) scene put high-resolution art on screen at 1:1 framebuffer pixels.
 * When a sprite is drawn, everything the scene drew so far is flushed (scaled 2x) to the
 * framebuffer, the art goes on top, and the scene carries on in a cleared layer, so draw order
 * is kept: HUD boxes drawn afterwards still cover the sprite.
 */
public final class HdSprite {
    private HdSprite() {}

    private static Graphics2D fb;
    private static BufferedImage low;

    static void begin(Graphics2D framebuffer, BufferedImage layer) { fb = framebuffer; low = layer; }

    static void end() { fb = null; low = null; }

    /**
     * Draws art standing in a 64x64 classic slot at (x, y): bottom-centred, 1:1 on the framebuffer.
     * {@code scale} shrinks it (pop-in), {@code flip} mirrors it, {@code dropY} sinks it into the
     * slot (fainting). Returns false when there is no framebuffer (the caller draws classic art).
     */
    public static boolean draw(BufferedImage art, int x, int y, double scale, boolean flip, int dropY) {
        if (fb == null) return false;
        fb.drawImage(low, 0, 0, Main.SW, Main.SH, null);
        Graphics2D lg = low.createGraphics();
        lg.setComposite(AlphaComposite.Clear);
        lg.fillRect(0, 0, Main.W, Main.H);
        lg.dispose();

        int w = (int) Math.round(art.getWidth() * scale), h = (int) Math.round(art.getHeight() * scale);
        if (w <= 0 || h <= 0) return true;
        int dx = x * 2 + (128 - w) / 2, dy = y * 2 + 126 - h + dropY * 2;
        Shape old = fb.getClip();
        if (dropY > 0) fb.clipRect(x * 2 - 16, y * 2 - 16, 160, 144);
        if (flip) fb.drawImage(art, dx + w, dy, -w, h, null);
        else fb.drawImage(art, dx, dy, w, h, null);
        fb.setClip(old);
        return true;
    }
}
