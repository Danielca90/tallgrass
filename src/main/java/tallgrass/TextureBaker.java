package tallgrass;

import javax.imageio.ImageIO;
import java.io.File;

/**
 * Writes every generated texture to PNG files.
 *
 * The build runs this to put the default pack inside the jar. You can also run it yourself to get an
 * editable copy, then change any PNG and the game will use your version:
 *
 *   java -cp Tallgrass.jar tallgrass.TextureBaker ~/.tallgrass/resourcepack/textures
 */
public final class TextureBaker {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File out = new File(args.length > 0 ? args[0] : "textures");
        out.mkdirs();
        int n = 0;
        for (String name : Textures.all()) {
            ImageIO.write(Textures.make(name), "png", new File(out, name + ".png"));
            n++;
        }
        System.out.println("Baked " + n + " textures into " + out.getPath());
    }
}
