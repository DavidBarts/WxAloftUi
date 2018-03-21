package info.koosah.wxaloftuiservlet;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import javax.imageio.ImageIO;


/**
 * Entry point.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class MakeMap {

    public static final String MYNAME = "MakeMap";
    private static final int PIXELS = 512;  /* size of our square image */
    private static final String TYPE = "png";

    public static void main(String[] args) throws Exception
    {
        // Parse command-line options
        if (args.length < 4 || args.length > 5) {
            System.err.format("%s: syntax: %s south west north east [output]%n", MYNAME, MYNAME);
            System.exit(2);
        }
        double south = Double.parseDouble(args[0]);
        double west = Double.parseDouble(args[1]);
        double north = Double.parseDouble(args[2]);
        double east = Double.parseDouble(args[3]);
        String output = args.length == 5 ? args[4] : "noname." + TYPE;

        // Enter headless mode
        System.setProperty("java.awt.headless", "true");

        // Get a tile provider
        File home = new File(System.getenv("HOME"));
        File temp = new File(home, "temp");
        File cache = new File(temp, "cache");
        TileProvider p = new LimitingTileProvider(16, new CachingTileProvider(cache, new OsmTileProvider()));

        // Request map
        double[] bounds = new double[] { south, west, north, east };
        int[] size = new int[] { PIXELS, PIXELS };
        Map m = Map.withSize(bounds, size, p);

        // Summarize what happened
        System.out.format("Requested: south=%f, west=%f, north=%f, east=%f%n",
            south, west, north, east);
        System.out.format("Actual   : south=%f, west=%f, north=%f, east=%f%n",
            m.south(), m.west(), m.north(), m.east());
        System.out.format("Zoom level is %d.%n", m.getZoom());

        // Render map
        BufferedImage image = m.getImage();
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        int psouth = m.latToPixel(south);
        int pnorth = m.latToPixel(north);
        int peast = m.longToPixel(east);
        int pwest = m.longToPixel(west);
        int[] xPoints = new int[] { pwest,  pwest,  peast,  peast  };
        int[] yPoints = new int[] { psouth, pnorth, pnorth, psouth };
        g.drawPolygon(xPoints, yPoints, 4);

        ImageIO.write(m.getImage(), TYPE, new File(output));
        System.out.format("Map saved to \"%s\".%n", output);
    }
}
