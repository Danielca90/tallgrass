package tallgrass;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

/** Window + fixed-step game loop. The game draws into a 240x160 buffer (the GBA screen) that is scaled up with nearest-neighbour. */
public final class Main extends Canvas implements Runnable {
    /** UI space for the classic screens (menus, battles), drawn 2x. */
    public static final int W = 240, H = 160;
    /** The real framebuffer: HD scenes draw here directly at 32px tiles. */
    public static final int SW = 480, SH = 320;

    private final BufferedImage screen = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_RGB);
    private final Input input = new Input();
    private final Game game;

    private Main(int scale) {
        setPreferredSize(new Dimension(SW * scale, SH * scale));
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(input);
        game = new Game(input);
    }

    @Override
    public void run() {
        createBufferStrategy(2);
        BufferStrategy bs = getBufferStrategy();
        final double step = 1e9 / 60.0;
        long last = System.nanoTime();
        double acc = 0;
        while (true) {
            long now = System.nanoTime();
            acc += now - last;
            last = now;
            int updates = 0;
            while (acc >= step && updates < 4) {
                input.poll();
                game.update();
                acc -= step;
                updates++;
            }
            if (acc > step * 8) acc = 0; // after a stall, don't fast-forward
            if (updates > 0) {
                Graphics2D g = screen.createGraphics();
                game.render(g);
                g.dispose();
                present(bs);
            } else {
                try { Thread.sleep(1); } catch (InterruptedException e) { return; }
            }
        }
    }

    private void present(BufferStrategy bs) {
        do {
            do {
                Graphics2D g = (Graphics2D) bs.getDrawGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int cw = getWidth(), ch = getHeight();
                double s = Math.min(cw / (double) SW, ch / (double) SH);
                if (s >= 1) s = Math.floor(s * 2) / 2; // keep pixels even-ish
                int dw = (int) (SW * s), dh = (int) (SH * s);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, cw, ch);
                g.drawImage(screen, (cw - dw) / 2, (ch - dh) / 2, dw, dh, null);
                g.dispose();
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
        Toolkit.getDefaultToolkit().sync();
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Tallgrass");
        SwingUtilities.invokeLater(() -> {
            Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
            int scale = Math.max(1, Math.min(3, Math.min((sd.width - 80) / SW, (sd.height - 140) / SH)));
            JFrame f = new JFrame("Tallgrass");
            Main m = new Main(scale);
            f.add(m);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            m.requestFocus();
            Thread t = new Thread(m, "tallgrass-loop");
            t.setDaemon(true);
            t.start();
        });
    }
}
