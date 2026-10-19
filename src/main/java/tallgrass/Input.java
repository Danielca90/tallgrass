package tallgrass;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayDeque;

/** Keyboard mapped onto GBA buttons. Arrows/WASD = D-pad, Z/Space = A, X/Esc = B, Enter = Start. */
public final class Input implements KeyListener {
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3, A = 4, B = 5, START = 6, R = 7;
    private static final int N = 8;

    private final boolean[] raw = new boolean[N];
    private final boolean[] down = new boolean[N];
    private final boolean[] prev = new boolean[N];
    private final int[] held = new int[N];
    private final ArrayDeque<Character> typedQ = new ArrayDeque<>();
    private int backspaces;
    private final ArrayDeque<Character> typed = new ArrayDeque<>();
    private int frameBackspaces;
    private volatile boolean textMode;

    /** While on, letter keys type text instead of acting as A/B/D-pad (arrows, Enter, Esc still work). */
    public synchronized void setTextMode(boolean on) {
        if (textMode == on) return;
        textMode = on;
        java.util.Arrays.fill(raw, false); // avoid keys stuck down across the switch
        typedQ.clear();
    }

    public synchronized void poll() {
        for (int i = 0; i < N; i++) {
            prev[i] = down[i];
            down[i] = raw[i];
            held[i] = down[i] ? held[i] + 1 : 0;
        }
        typed.clear();
        typed.addAll(typedQ);
        typedQ.clear();
        frameBackspaces = backspaces;
        backspaces = 0;
    }

    public boolean held(int k) { return down[k]; }
    public boolean pressed(int k) { return down[k] && !prev[k]; }

    /** Pressed, or held long enough to auto-repeat (menu navigation). */
    public boolean repeat(int k) {
        return pressed(k) || (held[k] > 18 && held[k] % 5 == 0);
    }

    public int heldFrames(int k) { return held[k]; }

    /** Characters typed this frame (for name entry). */
    public ArrayDeque<Character> typed() { return typed; }
    public int backspaces() { return frameBackspaces; }

    private int map(int code) {
        if (textMode && (code == KeyEvent.VK_W || code == KeyEvent.VK_A || code == KeyEvent.VK_S || code == KeyEvent.VK_D
                || code == KeyEvent.VK_Z || code == KeyEvent.VK_X || code == KeyEvent.VK_J || code == KeyEvent.VK_K || code == KeyEvent.VK_SPACE
                || code == KeyEvent.VK_R || code == KeyEvent.VK_Q)) return -1;
        return switch (code) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> UP;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> DOWN;
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> LEFT;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> RIGHT;
            case KeyEvent.VK_Z, KeyEvent.VK_SPACE, KeyEvent.VK_J -> A;
            case KeyEvent.VK_X, KeyEvent.VK_ESCAPE, KeyEvent.VK_K -> B;
            case KeyEvent.VK_ENTER -> START;
            case KeyEvent.VK_SHIFT, KeyEvent.VK_R -> R;
            default -> -1;
        };
    }

    @Override public synchronized void keyPressed(KeyEvent e) {
        int k = map(e.getKeyCode());
        if (k >= 0) raw[k] = true;
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) backspaces++;
    }

    @Override public synchronized void keyReleased(KeyEvent e) {
        int k = map(e.getKeyCode());
        if (k >= 0) raw[k] = false;
    }

    @Override public synchronized void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        if (textMode && c >= 32 && c < 127) typedQ.add(c);
    }
}
