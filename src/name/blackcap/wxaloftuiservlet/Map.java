package name.blackcap.wxaloftuiservlet;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

import static name.blackcap.wxaloftuiservlet.WorldPixel.*;

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
     * 180 W, Tile.MAXLAT N and increase going south and east. I.e. world
     * pixels are 32-bit integers with the following usage of bits:
     *       xxxxxx tttttttttttttttttt pppppppp
     *   MSB 31  26 25               8 7      0 LSB
     * x = currently unused (always 0)
     * t = tile number, max of 2**Z - 1 at zoom level Z
     * p = pixel number in tile, 0-255
     *
     * @param south     Southernmost extent
     * @param west      Westernmost extent
     * @param north     Northernmost extent
     * @param east      Easternmost extent
     * @param zoom      Zoom level
     * @param p         TileProvider to use
     *
     * Note: the northernmost and westernmost extents are inclusive; the
     * southernmost and easternmost extents are exclusive.
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
        this(bounds[0], bounds[1], bounds[2], bounds[3], zoom, p);
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
        int psouth = fromLatitude(south, zoom);
        int pwest = fromLongitude(west, zoom);
        int pnorth = fromLatitude(north, zoom);
        int peast = fromLatitude(east, zoom);
        initZoom(zoom);
        init(psouth, pwest, pnorth, peast, p);
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
        if (!eastOf(east, west, zoom))
            throw new IllegalArgumentException(
                String.format("%d not east of %d!", east, west));

        /* record bounds */
        this.south = south;
        this.west = west;
        this.north = north;
        this.east = east;

        /* get a starting tile */
        this.start = new Tile(getTile(west), getTile(north), zoom, p);

        /* get lat/long extents. note that these probably won't exactly match
           any passed to our lat/long constructor, due to pixellation */
        lsouth = toLatitude(south, zoom);
        lwest = toLongitude(west, zoom);
        lnorth = toLatitude(north, zoom);
        least = toLongitude(east, zoom);

        /* we haven't rendered anything yet */
        image = null;
    }

    private void initZoom(int zoom)
    {
        if (zoom < 0 || zoom > MAXZOOM)
            throw new IllegalArgumentException("invalid zoom " + zoom);
        this.zoom = zoom;
        this.numPixels = makePixel(1 << zoom, 0);
    }

    /**
     * Given lat/long bounds and an image size, return a map that will
     * render to the specified size and include all the specified bounds.
     * Returns null if asked to do the impossible (e.g. render a map so
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
        // "Unzip" the arrays to zoom level 18 coordinates with meaningful
        // names.
        sanityCheck(bounds[0], bounds[1], bounds[2], bounds[3]);
        int south = fromLatitude(bounds[0]);
        int west = fromLongitude(bounds[1]);
        int north = fromLatitude(bounds[2]);
        int east = fromLongitude(bounds[3]);
        int width = size[0];
        int height = size[1];

        // Determine zoom level and get coordinates in that zoom level
        // (bit shifting world pixel coordinates zooms them).
        int zoom;
        for (zoom = MAXZOOM; zoom >= 0; --zoom) {
            if ((south - north) <= height && eastFrom(west, east, zoom) <= width)
                break;
            south >>= 1;
            west >>= 1;
            north >>= 1;
            east >>= 1;
        }

        // Check if caller requested the impossible
        if (zoom < 0 || south == north || east == west)
            return null;
        int numPixels = makePixel(1 << zoom, 0);
        if (width > numPixels || height > numPixels)
            return null;

        // Unless we're *really* lucky, the scale we get will almost always
        // result in a map with at least one dimension being smaller than
        // requested.
        int xswidth = width - eastFrom(west, east, zoom);
        int xsheight = height - (south - north);
        int nmargin = xsheight / 2;
        int smargin = xsheight - nmargin;
        int wmargin = xswidth / 2;
        int emargin = xswidth - wmargin;

        // Longitude just wraps around but we can go too far north or south.
        int xssmargin = south + smargin - numPixels;
        if (xssmargin > 0) {
            smargin -= xssmargin;
            nmargin += xssmargin;
        } else if (nmargin > north) {
            smargin += nmargin - north;
            nmargin = north;
        }

        // Done?!
        return new Map(south + smargin, normalizeX(west - wmargin, zoom),
            north - nmargin, normalizeX(east + emargin, zoom), zoom, p);
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
        if (Math.abs(latitude) > MAXLAT)
            throw new IllegalArgumentException(String.format("invalid or unsupported latitude %f", latitude));
    }
    private static void sanityCheckLong(double longitude)
    {
        if (Math.abs(longitude) > MAXLON)
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
        BufferedImage rawImage = new BufferedImage(
            calcSize(eastFrom(west, east, zoom)), calcSize(south - north),
            BufferedImage.TYPE_INT_RGB);
        Graphics g = rawImage.getGraphics();
        try {
            // Tile it
            Tile y = start;
            int xi = 0, yi = 0;
            int yn = y.north();
            do {
                g.drawImage(y.getImage(), xi, yi, null);
                Tile x = y;
                int xe = y.east();
                while (westOf(xe, east, zoom)) {
                    x = x.eastTile();
                    xi += TILE_SIZE;
                    xe = normalizeX(xe + TILE_SIZE, zoom);
                    g.drawImage(x.getImage(), xi, yi, null);
                }
                xi = 0;
                yi += TILE_SIZE;
                yn += TILE_SIZE;
                y = y.southTile();
            } while (northOf(yn, south));
        } finally {
            g.dispose();
        }

        // Crop it and return our image
        int cnorth = north - start.north();
        int cwest = eastFrom(start.west(), west, zoom);
        int cwidth = eastFrom(west, east, zoom);
        int cheight = south - north;
        return rawImage.getSubimage(cwest, cnorth, cwidth, cheight);
    }

    private int calcSize(int pixels)
    {
        int SLOP = 2;
        return TILE_SIZE * ((pixels - 1) / TILE_SIZE + 1 + SLOP);
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
        return fromLatitude(latitude, zoom) - north;
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
        return eastFrom(west, fromLongitude(longitude, zoom), zoom);
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
