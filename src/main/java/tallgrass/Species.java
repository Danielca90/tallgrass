package tallgrass;

import java.awt.image.BufferedImage;
import java.io.Serializable;

/** A generated creature species. Sprites are drawn on demand from its seed and colors. */
public final class Species implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Shape { BIPED, QUAD, BLOB, SERPENT, BIRD, FLOATER }

    public int id;               // dex number, 1-based
    public String name;
    public Type t1, t2;
    public int hp, atk, def, spd;
    public Shape shape;
    public int c1, c2, c3;
    public long spriteSeed;
    public int stage;            // 0 = base form
    public int evolvesTo = -1, evolveLevel;
    public int catchRate, xpYield;
    public int[] learnLv, learnMove;
    public String desc;
    public String category;

    private transient BufferedImage front, back, icon, silhouette;

    /** Hand-made high-resolution art (art/creatures), or null to use the generated sprites. */
    public BufferedImage art() { return CreatureArt.get(name); }

    public BufferedImage front() {
        if (front == null) front = art() != null ? CreatureArt.classic(art(), false) : SpriteGen.render(this, SpriteGen.View.FRONT);
        return front;
    }
    public BufferedImage back() { if (back == null) back = SpriteGen.render(this, SpriteGen.View.BACK); return back; }
    public BufferedImage icon() { if (icon == null) icon = SpriteGen.render(this, SpriteGen.View.ICON); return icon; }
    public BufferedImage silhouette() {
        if (silhouette == null) silhouette = art() != null ? CreatureArt.classic(art(), true) : SpriteGen.render(this, SpriteGen.View.SILHOUETTE);
        return silhouette;
    }

    public boolean has(Type t) { return t1 == t || t2 == t; }
}
