package name.blackcap.wxaloftuiservlet;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A raster-based map that uses tiles from the OSM tile server. Things that
 * accept lat/long arguments tend to do sanity checks which make it
 * impossible to print maps that cover more than half the world, because
 * those look like their western limit is east of their eastern one. A
 * side effect of the latter is that this class generally can't deal with
 * any map that spans 180 or more degrees of longitude.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class Map {

    // Our extents in world pixels (see constructor documantation)
    private int south, west, north, east;
    // Our extents in lat/long
    private double lsouth, lwest, lnorth, least;
    // Zoom level of this map
    private int zoom;
    // Number of world pixels in both X and Y directions
    private int numPixels;
    // Starting tile for rendering this map
    private Tile start;
    // Image we render
    private BufferedImage image;

    /**
     * A raster-based map, with extents specified in world pixels. Each
     * pixel in each tile at each zoom level has its own unique world
     * pixel coordinates. World pixels start with (0,0) at
     * 180 W, Tile.MAXLAT N and increase going south and east.
     *
     * @param south     Southernmost extent
     * @param west      Westernmost extent
     * @param north     Northernmost extent
     * @param east      Easternmost extent
     * @param zoom      Zoom level
     * @param p         TileProvider to use
     */
    public Map(int south, int west, int north, int east, int zoom, TileProvider p) {
        initZoom(zoom);
        init(south, west, north, east, p);
    }

    /**
     * Variant with bounds in an array of four ints.
     *
     * @param bounds    Array of four ints { S, W, N, E }
     * @param zoom      Zoom level
     * @param p         TileProvider to use
     */
    public Map(int[] bounds, int zoom, TileProvider p) {
        initZoom(zoom);
        init(bounds[0], bounds[1], bounds[2], bounds[3], p);
    }

    /**
     * Constructor that uses more traditional (and more user-friendly)
     * lat/long bounds.
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
        sanityCheck(south, west, north, east);
        initZoom(zoom);
        Tile convert = new Tile(0, 0, zoom, null);
        init(
            toPixel(convert.toTileY(south)), toPixel(convert.toTileX(west)),
            toPixel(convert.toTileY(north)), toPixel(convert.toTileX(east)),
            p);
    }

    /**
     * Lat/long variant with bounds in an array of four doubles.
     *
     * @param bounds    Array of four doubles { S, W, N, E }
     * @param zoom      Zoom level
     * @param p         TileProvider to use
     */
    public Map(double[] bounds, int zoom, TileProvider p) {
        this(bounds[0], bounds[1], bounds[2], bounds[3], zoom, p);
    }

    private void init(int south, int west, int north, int east, TileProvider p)
    {
        /* a final round of sanity checks */
        if (south < 0 || south >= numPixels)
            throw new IllegalArgumentException("invalid south bound " + south);
        if (west < 0 || west >= numPixels)
            throw new IllegalArgumentException("invalid west bound " + west);
        if (east < 0 || east >= numPixels)
            throw new IllegalArgumentException("invalid east bound " + east);
        if (west < 0 || west >= numPixels)
            throw new IllegalArgumentException("invalid west bound " + west);
        if (south <= north)
            throw new IllegalArgumentException(
                String.format("%d not south of %d!", south, north));
        if (eastFrom(east, west) < eastFrom(west, east))
            throw new IllegalArgumentException(
                String.format("%d not east of %d!", east, west));

        /* record bounds */
        this.south = south;
        this.west = west;
        this.north = north;
        this.east = east;

        /* get a starting tile */
        this.start = new Tile((int) toTileXY(west), (int) toTileXY(north),
            zoom, p);

        /* get lat/long extents. note that these probably won't exactly match
           any passed to our lat/long constructor, due to pixellation */
        lsouth = start.toLatitude(toTileXY(south));
        lwest = start.toLongitude(toTileXY(west));
        lnorth = start.toLatitude(toTileXY(north));
        least = start.toLongitude(toTileXY(east));

        /* we haven't rendered anything yet */
        image = null;
    }

    private void initZoom(int zoom)
    {
        if (zoom < 0 || zoom > Tile.MAXZOOM)
            throw new IllegalArgumentException("invalid zoom " + zoom);
        this.zoom = zoom;
        this.numPixels = 1 << (zoom + 8);
    }

    /**
     * Convert a tile X or Y coordinate into world pixels. Does not
     * sanity-check its argument.
     *
     * @param tilexy    Tilespace coordinate.
     * @return          World pixel coordinate.
     */
    public int toPixel(double tilexy)
    {
        int whole = (int) tilexy;
        double frac = tilexy - (double) whole;
        return (whole << 8) | (int) (frac * Tile.SIZE);
    }

    /**
     * Convert a world pixel coordinate into a tile X or Y coordinate.
     * Does not sanity-check its argument.
     *
     * @param           World pixel coordinate.
     * @return          Tilespace coordinate.
     */
    public double toTileXY(int pixel)
    {
        double whole = (double) (pixel >> 8);
        double frac = (double) (pixel & 0xff) / (double) Tile.SIZE;
        return whole + frac;
    }

    /**
     * Get the number of pixels in each direction in world pixel space.
     *
     * @return          Number of pixels
     */
    public int getNumPixels()
    {
        return numPixels;
    }

    /**
     * Given lat/long bounds and an image size, return a map that will
     * render to the specified size and include all the specified bounds.
     * Returns null if asked to to the impossible (e.g. render a map so
     * tiny that even zoom level 0 can't accommodate it, or render a
     * map for which no zoom level can fill the requested width and
     * height).
     *
     * @param bounds    Array of four doubles { S, W, N, E }
     * @param size      Array of two ints { width, height }
     * @param p         TileProvider
     * @return          Map object or null
     */
    public static Map withSize(double[] bounds, int[] size, TileProvider p)
    {
        // "Unzip" the arrays to meaningful variable names
        double south = bounds[0];
        double west  = bounds[1];
        double north = bounds[2];
        double east  = bounds[3];
        sanityCheck(south, west, north, east);
        int width = size[0];
        int height = size[1];

        // Determine zoom level
        int psouth = -1, pwest = -1, pnorth = -1, peast = -1;
        int zoom = 0;
        Map dummy = null;
        do {
            Tile c0 = new Tile(0, 0, zoom, null);
            Map c1 = new Map(1, 0, 0, 1, zoom, null);
            int npsouth = c1.toPixel(c0.toTileY(south));
            int npwest = c1.toPixel(c0.toTileX(west));
            int npnorth = c1.toPixel(c0.toTileY(north));
            int npeast = c1.toPixel(c0.toTileX(east));
            if ((npsouth - npnorth) >= height || c1.eastFrom(npwest, npeast) >= width)
                break;
            psouth = npsouth;
            pwest = npwest;
            pnorth = npnorth;
            peast = npeast;
            dummy = c1;
        } while (zoom++ < Tile.MAXZOOM);

        // Check if caller requested the impossible
        if (dummy == null)
            return null;
        if (width > dummy.getNumPixels() || height > dummy.getNumPixels())
            return null;

        // Unless we're *really* lucky, the scale we get will almost always
        // result in a map with at least one dimension being smaller than
        // requested.
        int xswidth = width - dummy.eastFrom(pwest, peast);
        int xsheight = height - (psouth - pnorth);
        int nmargin = xsheight / 2;
        int smargin = xsheight - nmargin;
        int wmargin = xswidth / 2;
        int emargin = xswidth - wmargin;

        // Longitude just wraps around but we can go too far north or south.
        int xssmargin = psouth + smargin - dummy.getNumPixels() + 1;
        if (xssmargin > 0) {
            smargin -= xssmargin;
            nmargin += xssmargin;
        } else if (nmargin > pnorth) {
            smargin += nmargin - pnorth;
            nmargin = pnorth;
        }

        // Done?!
        return new Map(
            psouth + smargin, dummy.normalizeX(pwest - wmargin),
            pnorth - nmargin, dummy.normalizeX(peast + emargin),
            dummy.getZoom(), p);
    }

    /**
     * How far it is (in world pixels) if you head east from start to finish.
     * You will always get there, just sometimes the long way 'round...
     *
     * @param start     Starting longitude
     * @param end       Ending longitude
     * @return          Distance in pixels, always positive
     */
    public int eastFrom(int start, int end)
    {
        if (start < 0 || start >= numPixels)
            throw new IllegalArgumentException("invalid start " + start);
        if (end < 0 || end >= numPixels)
            throw new IllegalArgumentException("invalid end " + end);
        if (end >= start)
            return end - start;
        else
            return numPixels - start + end;
    }

    /**
     * Normalize an X world pixel coordinate. Necessary after any addition
     * or subtraction.
     *
     * @param x         An un-normalized X value.
     * @return          Normalized X value
     */
    public int normalizeX(int unnormalized)
    {
        while (unnormalized >= numPixels)
            unnormalized -= numPixels;
        while (unnormalized < 0)
            unnormalized += numPixels;
        return unnormalized;
    }

    /*
     * This class is prone to hose the tile server if we don't do this.
     */
    private static void sanityCheck(double south, double west, double north, double east)
    {
        sanityCheckLat(north);
        sanityCheckLat(south);
        sanityCheckLong(east);
        sanityCheckLong(west);
        if (south >= north)
            throw new IllegalArgumentException(String.format("%f not north of %f!", north, south));
        if (!LatLong.eastOf(east, west))
            throw new IllegalArgumentException(String.format("%f not east of %f!", east, west));
    }
    private static void sanityCheckLat(double latitude)
    {
        if (Math.abs(latitude) > Tile.MAXLAT)
            throw new IllegalArgumentException(String.format("invalid or unsupported latitude %f", latitude));
    }
    private static void sanityCheckLong(double longitude)
    {
        if (Math.abs(longitude) > Tile.MAXLON)
            throw new IllegalArgumentException(String.format("invalid or unsupported longitude %f", longitude));
    }

    /**
     * Get the image representing the map. It is allowed to draw on the
     * image.  XXX - this can't handle maps that span 180 or more degrees
     * of longitude.
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
        int SLOP = 2 * Tile.SIZE;
        BufferedImage rawImage = new BufferedImage(
            calcSize(eastFrom(west, east)), calcSize(south - north),
            BufferedImage.TYPE_INT_RGB);
        Graphics g = rawImage.getGraphics();

        // Tile it
        Tile y = start;
        int xi = 0, yi = 0;
        int yn = y.getY() << 8;
        int xe1 = normalizeX((y.getX() + 1) << 8);
        do {
            g.drawImage(y.getImage(), xi, yi, null);
            Tile x = y;
            int xe = xe1;
            while (eastFrom(xe, east) < eastFrom(east, xe)) {
                x = x.eastTile();
                xi += Tile.SIZE;
                xe = normalizeX(xe + Tile.SIZE);
                g.drawImage(x.getImage(), xi, yi, null);
            }
            xi = 0;
            yi += Tile.SIZE;
            yn += Tile.SIZE;
            y = y.southTile();
        } while (yn < south);

        // Crop it and return our image
        int cnorth = north - (start.getY() << 8);
        int cwest = eastFrom(start.getX() << 8, west);
        int cwidth = eastFrom(west, east);
        int cheight = south - north;
        return rawImage.getSubimage(cwest, cnorth, cwidth, cheight);
    }

    private int calcSize(int pixels)
    {
        int SLOP = 2;
        return Tile.SIZE * ((pixels - 1) / Tile.SIZE + 1 + SLOP);
    }

    /**
     * Translate latitude to output image pixel coordinate. Does not
     * sanity-check its argument.
     *
     * @param           Latitude
     * @return          Pixel coordinate
     */
    public int latToPixel(double latitude)
    {
        return toPixel(start.toTileY(latitude)) - north;
    }

    /**
     * Translate longitude to output image pixel coordinate. Does not
     * sanity-check its argument.
     *
     * @param           Longitude
     * @return          Pixel coordinate
     */
    public int longToPixel(double longitude)
    {
        return eastFrom(west, toPixel(start.toTileX(longitude)));
    }

    /**
     * Get westmost longitude.
     *
     * @return          Longitude
     */
    public double west()
    {
        return lwest;
    }

    /**
     * Get eastmost longitude.
     *
     * @return          Longitude
     */
    public double east()
    {
        return least;
    }

    /**
     * Get northmost latitude.
     *
     * @return          Latitude.
     */
    public double north()
    {
        return lnorth;
    }

    /**
     * Get southmost latitude.
     *
     * @return          Latitude.
     */
    public double south()
    {
        return lsouth;
    }

    /**
     * Get eastmost world pixel coordinate.
     *
     * @return          Pixel coordinate.
     */
    public int eastPixel()
    {
        return east;
    }

    /**
     * Get westmost world pixel coordinate.
     *
     * @return          Pixel coordinate.
     */
    public int westPixel()
    {
        return west;
    }

    /**
     * Return northmost world pixel coordinate.
     *
     * @return          Pixel coordinate.
     */
    public int northPixel()
    {
        return north;
    }

    /**
     * Return southmost world pixel coordinate.
     *
     * @return          Pixel coordinate.
     */
    public int southPixel()
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
