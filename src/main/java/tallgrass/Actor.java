package tallgrass;

/** Anything that walks on the tile grid: the player and NPCs. Movement is tile-to-tile with pixel interpolation. */
public class Actor {
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    public int x, y, facing = DOWN;
    public int moveDir = -1;
    public int progress;      // pixels travelled into the current step (0..15)
    public int speed = 1;     // pixels per frame
    public int steps;         // total steps taken, drives the walk cycle
    public CharArt.Look look;

    public static int dx(int d) { return d == LEFT ? -1 : d == RIGHT ? 1 : 0; }
    public static int dy(int d) { return d == UP ? -1 : d == DOWN ? 1 : 0; }
    public static int opposite(int d) { return d == UP ? DOWN : d == DOWN ? UP : d == LEFT ? RIGHT : LEFT; }

    public boolean moving() { return moveDir >= 0; }

    public void startMove(int dir, int speed) {
        facing = dir;
        moveDir = dir;
        progress = 0;
        this.speed = speed;
    }

    /** Advances the current step; returns true on the frame the actor arrives on the next tile. */
    public boolean update() {
        if (moveDir < 0) return false;
        progress += speed;
        if (progress >= 16) {
            x += dx(moveDir);
            y += dy(moveDir);
            moveDir = -1;
            progress = 0;
            steps++;
            return true;
        }
        return false;
    }

    public int pixelX() { return x * 16 + (moveDir >= 0 ? dx(moveDir) * progress : 0); }
    public int pixelY() { return y * 16 + (moveDir >= 0 ? dy(moveDir) * progress : 0); }

    /** 0 = standing, 1/2 = alternating stride frames. */
    public int frame() {
        if (moveDir < 0) return 0;
        if (progress < 4 || progress >= 12) return 0;
        return (steps % 2 == 0) ? 1 : 2;
    }
}
