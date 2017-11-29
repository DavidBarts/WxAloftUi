// package name.blackcap.wxaloftuiservlet;

import java.awt.Graphics;
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
        TileProvider p = new CachingTileProvider(new File("cache"), new OsmTileProvider());

        // Get starting tile
        int zoom = Tile.calcZoom(south, west, north, east, PIXELS, PIXELS);
        Tile start = Tile.forLatLong(north, west, zoom, p);

        // Estimate size of BufferedImage that we will need, then create it
        final int SLOP = 2;
        double dheight = north - south;
        double dwidth = LatLong.eastFrom(west, east);
        double tdheight = start.north() - start.south();
        int height = (int) Math.ceil(dheight / tdheight) + SLOP;
        double tdwidth = LatLong.eastFrom(start.west(), start.east());
        int width = (int) Math.ceil(dwidth / tdwidth) + SLOP;
        BufferedImage image = new BufferedImage(Tile.SIZE*width, Tile.SIZE*height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();

        // Tile it
        Tile y = start;
        int xi = 0, yi = 0;
        do {
            g.drawImage(y.getImage(), xi, yi, null);
            Tile x = y;
            while (LatLong.westOf(x.east(), east)) {
                x = x.eastTile();
                xi += Tile.SIZE;
                g.drawImage(x.getImage(), xi, yi, null);
            }
            xi = 0;
            yi += Tile.SIZE;
            y = y.southTile();
        } while (y != null && LatLong.northOf(y.north(), south));

        // Crop it
        double ewps = (double) Tile.SIZE / tdwidth;
        double nsps = (double) Tile.SIZE / tdheight;
        int cnorth = (int) (nsps * (start.north() - north));
        int cwest = (int) (ewps * LatLong.eastFrom(start.west(), west));
        int cwidth = (int) (ewps * dwidth);
        int cheight = (int) (nsps * dheight);
        BufferedImage cropped = image.getSubimage(cwest, cnorth, cwidth, cheight);

        // Save it
        ImageIO.write(cropped, TYPE, new File(output));
        System.out.format("Map saved to \"%s\".%n", output);
    }
}
