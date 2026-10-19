package tallgrass;

import java.awt.Graphics2D;

public interface Scene {
    void update();
    void render(Graphics2D g);
    /** Overlay scenes are drawn on top of the scene below them (menus, dialogs). */
    default boolean overlay() { return false; }
    /** Called when the scene above this one is popped. */
    default void resumed() {}
    /** HD scenes draw straight into the 480x320 framebuffer; others draw at 240x160 and are scaled 2x. */
    default boolean hd() { return false; }
}
