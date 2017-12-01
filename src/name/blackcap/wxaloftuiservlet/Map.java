package name.blackcap.wxaloftuiservlet;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A raster-based map that uses tiles from the OSM tile server.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class Map {

    // Our extents
    private double south, west, north, east;
    // Pixels per degree (almost never the same in the two directions)
    private double ewScale, nsScale;
    // Image of the map
    BufferedImage image;
    // Pessimistic estimate of width and height in tiles we will need
    private int width, height;
    // First (and presumably representative) tile we generate, currently
    // the NW corner of the image.
    private Tile start;
    // Width and height in degrees of the first tile.
    private double tdwidth, tdheight;
    // Width and height of this map in degrees
    private double dwidth, dheight;

    /**
     * A raster-based map, with extents specified by latitude and longitude,
     * and the specified zoom level, using the specified provider to obtain
     * tiles. Maps render lazily, and it is allowed to pass a null tile
     * provider (in which case, the map can only be used for retrieving
     * the basic properties of a map).
     *
     * @param south     Southernmost extent
     * @param west      Westernmost extent
     * @param north     Northernmost extent
     * @param east      Easternmost extent
     * @param zoom      Zoom level
     * @param p         TileProvider to use
     */
    public Map(double south, double west, double north, double east, int zoom, TileProvider p)
    {
        this.south = south;
        this.west = west;
        this.north = north;
        this.east = east;

        // Get starting tile
        start = Tile.forLatLong(north, west, zoom, p);

        // Calculate various size-related parameters
        final int SLOP = 2;  /* very pessimistic */
        dheight = north - south;
        dwidth = LatLong.eastFrom(west, east);
        tdheight = start.north() - start.south();
        height = (int) Math.ceil(dheight / tdheight) + SLOP;
        tdwidth = LatLong.eastFrom(start.west(), start.east());
        width = (int) Math.ceil(dwidth / tdwidth) + SLOP;

        // Calculate scaling factors
        ewScale = (double) Tile.SIZE / tdwidth;
        nsScale = (double) Tile.SIZE / tdheight;
    }

    /**
     * Constructor that uses bounds in a { south, west, north, east } array.
     *
     * @param bounds    Array of four doubles.
     * @param zoom      Zoom level
     * @param p         TileProvider
     */
    public Map(double[] bounds, int zoom, TileProvider p)
    {
        this(bounds[0], bounds[1], bounds[2], bounds[3], zoom, p);
    }

    public static Map withSize(double[] bounds, int[] size, TileProvider p)
    {
        // "unzip" the arrays to meaningful variable names
        double south = bounds[0];
        double west  = bounds[1];
        double north = bounds[2];
        double east  = bounds[3];
        int xpixels  = size[0];
        int ypixels = size[1];

        // determine zoom level (tricky!)
        int zoom = 0;
        int width = 0, nwidth = 0, height = 0, nheight = 0;
        for (int z=0; z <= Tile.MAXZOOM; zoom=z++) {
            Tile dummy = new Tile(0, 0, z, null);
            double tSouth = dummy.toTileY(south);
            double tWest  = dummy.toTileX(west);
            double tNorth = dummy.toTileY(north);
            double tEast  = dummy.toTileX(east);
            height = nheight;
            nheight = (int) Math.ceil((tSouth - tNorth) * Tile.SIZE);
            width = nwidth;
            nwidth = (int) Math.ceil((tEast - tWest) * Tile.SIZE);
            if (nwidth > xpixels || nheight > ypixels)
                break;
        }
        System.out.format("xpixels=%d, ypixels=%d%n", xpixels, ypixels);
        System.out.format("width=%d, height=%d%n", width, height);
        System.out.format("nwidth=%d, nheight=%d%n", nwidth, nheight);

        // deal with size discrepancies
        int hextra = xpixels - width;
        int vextra = ypixels - height;
        double vdeg = north - south;
        double hdeg = LatLong.eastFrom(west, east);
        System.out.format("vdeg=%f, hdeg=%f%n", vdeg, hdeg);
        double hmargin = hdeg * (double) hextra / (double) width / 2.0;
        double vmargin = vdeg * (double) vextra / (double) height / 2.0;
        System.out.format("vmargin=%f, hmargin=%f%n", vmargin, hmargin);
        south -= vmargin;
        west = LatLong.normalizeLong(west - hmargin);
        north += vmargin;
        east = LatLong.normalizeLong(east + hmargin);

        // done?!
        return new Map(south, west, north, east, zoom, p);
    }

    /**
     * Given bounds and an image size, return the most detailed (i.e.
     * highest zoom level) map that will show all of specified bounds.
     *
     * @param bounds    Array of four doubles { S, W, N, E }
     * @param size      Array of two ints { width, height }
     * @param p         TileProvider
     */
    /* public static Map withSize(double[] bounds, int[] size, TileProvider p)
    {
        // "unzip" the arrays to meaningful variable names
        double south = bounds[0];
        double west  = bounds[1];
        double north = bounds[2];
        double east  = bounds[3];
        int width  = size[0];
        int height = size[1];

        // determine requested width and height in degrees
        double dheight = north - south;
        double dwidth = LatLong.eastFrom(west, east);

        // determine zoom level and actual size in degrees we will map
        int zoom = Tile.calcZoom(south, west, north, east, width, height);
        int tilesWide = width / Tile.SIZE + (width % Tile.SIZE > 0 ? 1 : 0);
        int tilesHigh = height / Tile.SIZE + (height % Tile.SIZE > 0 ? 1 : 0);
        Tile t = Tile.forLatLong(north, east, zoom, null);
        double adheight = tilesHigh * (t.north() - t.south());
        double adwidth = tilesWide * LatLong.eastFrom(t.west(), t.east());

        // from that, calculate extra space we need to add
        double vmargin = (adheight - dheight) / 2.0;
        double hmargin = (adwidth - dwidth) / 2.0;

        // get correct bounds and return map
        south -= vmargin;
        north += vmargin;
        east = LatLong.normalizeLong(east + hmargin);
        west = LatLong.normalizeLong(west - hmargin);
        return new Map(south, west, north, east, zoom, p);
    } */

    /**
     * Get the image representing the map. It is allowed to draw on the
     * image.
     *
     * @return          BufferedImage object.
     */
    public BufferedImage getImage() throws IOException
    {
        if (image == null)
            image = makeImage();
        return image;
    }

    private BufferedImage makeImage() throws IOException
    {
        // Make a raw image
        BufferedImage rawImage = new BufferedImage(Tile.SIZE*width, Tile.SIZE*height, BufferedImage.TYPE_INT_RGB);
        Graphics g = rawImage.getGraphics();

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

        // Crop it generate our image
        int cnorth = (int) (nsScale * (start.north() - north));
        int cwest = (int) (ewScale * LatLong.eastFrom(start.west(), west));
        int cwidth = (int) (ewScale * dwidth);
        int cheight = (int) (nsScale * dheight);
        return rawImage.getSubimage(cwest, cnorth, cwidth, cheight);
    }

    /**
     * Translate latitude to pixel coordinate. Does not sanity-check its
     * argument.
     *
     * @param           Latitude
     * @return          Pixel coordinate
     */
    public int latToPixel(double latitude)
    {
        return (int) (nsScale * (north - latitude));
    }

    /**
     * Translate longitude to pixel coordinate. Does not sanity-check its
     * argument.
     *
     * @param           Longitude
     * @return          Pixel coordinate
     */
    public int longToPixel(double longitude)
    {
        return (int) (ewScale * LatLong.eastFrom(west, longitude));
    }

    /**
     * Get westmost longitude.
     *
     * @return          Longitude
     */
    public double west()
    {
        return west;
    }

    /**
     * Get eastmost longitude.
     *
     * @return          Longitude
     */
    public double east()
    {
        return east;
    }

    /**
     * Get northmost latitude.
     *
     * @return          Latitude.
     */
    public double north()
    {
        return north;
    }

    /**
     * Get southmost latitude.
     *
     * @return          Latitude.
     */
    public double south()
    {
        return south;
    }

    /**
     * Get zoom level of this map
     *
     * @return          Zoom level
     */
    public int getZoom()
    {
        return start.getZoom();
    }
}
