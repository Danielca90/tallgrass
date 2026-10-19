package tallgrass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Hand-drawn proportional bitmap font (7px cap height, 1px descender), drawn with a GBA-style drop shadow. */
public final class Font {
    public static final int LINE = 14;
    private static final Map<Character, String[]> G = new HashMap<>();
    private static final Map<Long, BufferedImage> CACHE = new HashMap<>();

    private static void d(char c, String rows) { G.put(c, rows.split(" ")); }

    static {
        d('A', ".###. #...# #...# ##### #...# #...# #...#");
        d('B', "####. #...# #...# ####. #...# #...# ####.");
        d('C', ".###. #...# #.... #.... #.... #...# .###.");
        d('D', "####. #...# #...# #...# #...# #...# ####.");
        d('E', "##### #.... #.... ####. #.... #.... #####");
        d('F', "##### #.... #.... ####. #.... #.... #....");
        d('G', ".###. #...# #.... #.### #...# #...# .####");
        d('H', "#...# #...# #...# ##### #...# #...# #...#");
        d('I', "### .#. .#. .#. .#. .#. ###");
        d('J', "..### ...#. ...#. ...#. ...#. #..#. .##..");
        d('K', "#...# #..#. #.#.. ##... #.#.. #..#. #...#");
        d('L', "#.... #.... #.... #.... #.... #.... #####");
        d('M', "#...# ##.## #.#.# #.#.# #...# #...# #...#");
        d('N', "#...# ##..# #.#.# #..## #...# #...# #...#");
        d('O', ".###. #...# #...# #...# #...# #...# .###.");
        d('P', "####. #...# #...# ####. #.... #.... #....");
        d('Q', ".###. #...# #...# #...# #.#.# #..#. .##.#");
        d('R', "####. #...# #...# ####. #.#.. #..#. #...#");
        d('S', ".###. #...# #.... .###. ....# #...# .###.");
        d('T', "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..");
        d('U', "#...# #...# #...# #...# #...# #...# .###.");
        d('V', "#...# #...# #...# #...# #...# .#.#. ..#..");
        d('W', "#...# #...# #...# #.#.# #.#.# #.#.# .#.#.");
        d('X', "#...# #...# .#.#. ..#.. .#.#. #...# #...#");
        d('Y', "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..");
        d('Z', "##### ....# ...#. ..#.. .#... #.... #####");
        d('a', "..... ..... .###. ....# .#### #...# .####");
        d('b', "#.... #.... ####. #...# #...# #...# ####.");
        d('c', "..... ..... .###. #.... #.... #...# .###.");
        d('d', "....# ....# .#### #...# #...# #...# .####");
        d('e', "..... ..... .###. #...# ##### #.... .###.");
        d('f', "..## .#.. .#.. ###. .#.. .#.. .#..");
        d('g', "..... ..... .#### #...# #...# .#### ....# .###.");
        d('h', "#.... #.... #.##. ##..# #...# #...# #...#");
        d('i', "# . # # # # #");
        d('j', "..# ... .## ..# ..# ..# ..# ##.");
        d('k', "#... #... #..# #.#. ##.. #.#. #..#");
        d('l', "## .# .# .# .# .# .#");
        d('m', "..... ..... ##.#. #.#.# #.#.# #.#.# #.#.#");
        d('n', "..... ..... #.##. ##..# #...# #...# #...#");
        d('o', "..... ..... .###. #...# #...# #...# .###.");
        d('p', "..... ..... ####. #...# #...# ####. #.... #....");
        d('q', "..... ..... .#### #...# #...# .#### ....# ....#");
        d('r', ".... .... #.## ##.. #... #... #...");
        d('s', "..... ..... .#### #.... .###. ....# ####.");
        d('t', ".#.. .#.. ###. .#.. .#.. .#.# ..#.");
        d('u', "..... ..... #...# #...# #...# #..## .##.#");
        d('v', "..... ..... #...# #...# #...# .#.#. ..#..");
        d('w', "..... ..... #...# #...# #.#.# #.#.# .#.#.");
        d('x', "..... ..... #...# .#.#. ..#.. .#.#. #...#");
        d('y', "..... ..... #...# #...# #...# .#### ....# .###.");
        d('z', "..... ..... ##### ...#. ..#.. .#... #####");
        d('0', ".###. #...# #..## #.#.# ##..# #...# .###.");
        d('1', ".#. ##. .#. .#. .#. .#. ###");
        d('2', ".###. #...# ....# ...#. ..#.. .#... #####");
        d('3', "##### ...#. ..#.. ...#. ....# #...# .###.");
        d('4', "...#. ..##. .#.#. #..#. ##### ...#. ...#.");
        d('5', "##### #.... ####. ....# ....# #...# .###.");
        d('6', "..##. .#... #.... ####. #...# #...# .###.");
        d('7', "##### ....# ...#. ..#.. .#... .#... .#...");
        d('8', ".###. #...# #...# .###. #...# #...# .###.");
        d('9', ".###. #...# #...# .#### ....# ...#. .##..");
        d('.', ". . . . . . #");
        d(',', ".. .. .. .. .. .# .# #.");
        d('!', "# # # # # . #");
        d('?', ".###. #...# ....# ...#. ..#.. ..... ..#..");
        d('\'', "# # . . . . .");
        d('"', "#.# #.# ... ... ... ... ...");
        d('-', ".... .... .... #### .... .... ....");
        d(':', ". . # . . # .");
        d(';', ".. .. .# .. .. .# #.");
        d('/', "....# ...#. ...#. ..#.. .#... .#... #....");
        d('(', "..# .#. #.. #.. #.. .#. ..#");
        d(')', "#.. .#. ..# ..# ..# .#. #..");
        d('+', "..... ..#.. ..#.. ##### ..#.. ..#.. .....");
        d('=', "..... ..... ##### ..... ##### ..... .....");
        d('%', "##..# ##.#. ...#. ..#.. .#... .#.## #..##");
        d('*', "..... #.#.# .###. ##### .###. #.#.# .....");
        d('#', ".#.#. ##### .#.#. .#.#. .#.#. ##### .#.#.");
        d('$', "..#.. .#### #.#.. .###. ..#.# ####. ..#..");
        d('&', ".##.. #..#. .##.. .#... #.#.# #..#. .##.#");
        d('>', "#... ##.. ###. #### ###. ##.. #...");   // menu cursor
        d('<', "...# ..## .### #### .### ..## ...#");
        d('^', "..#.. .###. ##### ..... ..... ..... .....");
        d('_', "..... ..... ..... ..... ..... ..... #####");
        d('~', "..... ..... .#... #.#.# ...#. ..... .....");
    }

    private static String[] glyph(char c) {
        String[] g = G.get(c);
        return g != null ? g : G.get('?');
    }

    public static int adv(char c) {
        if (c == ' ') return 4;
        return glyph(c)[0].length() + 1;
    }

    public static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += adv(s.charAt(i));
        return Math.max(0, w - 1);
    }

    private static BufferedImage img(char c, int rgb) {
        long key = ((long) c << 32) | (rgb & 0xffffffffL);
        BufferedImage im = CACHE.get(key);
        if (im != null) return im;
        String[] g = glyph(c);
        im = new BufferedImage(g[0].length(), 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < g.length && y < 8; y++)
            for (int x = 0; x < g[y].length(); x++)
                if (g[y].charAt(x) == '#') im.setRGB(x, y, 0xff000000 | rgb);
        CACHE.put(key, im);
        return im;
    }

    /** Draws text with its top-left at (x, y). Pass shadow = null for no shadow. */
    public static void draw(Graphics2D g, String s, int x, int y, Color c, Color shadow) {
        if (shadow != null) {
            raw(g, s, x + 1, y, shadow, 1);
            raw(g, s, x, y + 1, shadow, 1);
            raw(g, s, x + 1, y + 1, shadow, 1);
        }
        raw(g, s, x, y, c, 1);
    }

    public static void draw(Graphics2D g, String s, int x, int y) { draw(g, s, x, y, Gfx.TEXT, Gfx.SHADOW); }

    public static void raw(Graphics2D g, String s, int x, int y, Color c, int scale) {
        int rgb = c.getRGB() & 0xffffff;
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != ' ') {
                BufferedImage im = img(ch, rgb);
                g.drawImage(im, cx, y, im.getWidth() * scale, im.getHeight() * scale, null);
            }
            cx += adv(ch) * scale;
        }
    }

    /** Big outlined text for titles. */
    public static void big(Graphics2D g, String s, int x, int y, int scale, Color fill, Color outline) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 2; dy++)
                if (dx != 0 || dy != 0) raw(g, s, x + dx * scale / 2 + dx, y + dy * scale / 2 + dy, outline, scale);
        raw(g, s, x, y, fill, scale);
    }

    public static int bigWidth(String s, int scale) { return width(s) * scale; }

    /** Word-wraps text to the given pixel width. Honours explicit '\n'. */
    public static java.util.List<String> wrap(String text, int maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (width(trial) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(trial);
                }
            }
            out.add(line.toString());
        }
        return out;
    }
}
