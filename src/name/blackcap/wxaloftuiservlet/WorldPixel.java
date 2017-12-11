package name.blackcap.wxaloftuiservlet;

/**
 * @author David Barts <n5jrn@me.com>
 * @since 2017-12-10
 *
 * Static utilities for manipulating world pixel values. A world pixel
 * is represented by a 32 bit int:
 *       xxxxxx tttttttttttttttttt pppppppp
 *  MSB 31   26 25               8 7      0 LSB
 * x = currently unused (always 0)
 * t = tile number, max of 2**Z - 1 at zoom level Z
 * p = pixel number in tile, 0-255
 *
 * To avoid precision loss, it is recommended to work with pixels at the
 * maximum zoom level then zoom out as needed.
 *
 * For sake of speed, most of these routines do not sanity-check arguments.
 */
public class WorldPixel
{
    public static final int MAXZOOM = 18;

    // Maximum latitude and longitude that world pixel coordinates support.
    // Minima are the negative of the maxima.
    public static final double MAXLAT = Math.toDegrees(Math.atan(Math.sinh(Math.PI)));
    public static final double MAXLON = 180.0;

    // The following values all depend on this one.
    private static final int TILE_BITS = 8;  /* bits of tile pixel information */

    // These values all depend on the previous.
    public static final int TILE_SIZE = 1 << TILE_BITS;
    private static final int TILE_MASK = TILE_SIZE - 1;
    private static final int MAXMAX = 1 << (TILE_BITS + MAXZOOM);

    /**
     * Return a new world pixel in the specified zoom level. Note that
     * there will be pixellation if zooming in from a low zoom level
     * number to a higher one, and data loss when zooming out from a
     * high zoom level to a lower one.
     *
     * @param pixel     World pixel
     * @param from      Current zoom factor
     * @param to        Desired zoom factor
     * @return          Zoomed pixel
     */
    public static int toZoom(int pixel, int from, int to)
    {
        int ret = pixel;
        if (to < from)
            ret >>= from - to;
        else if (to > from)
            ret <<= to - from;
        return ret;
    }

    /**
     * Variant of toZoom that assumes current zoom level of MAXZOOM.
     *
     * @param pixel     World pixel
     * @param to        Desired zoom factor
     * @return          Zoomed pixel
     */
    public static int toZoom(int pixel, int to)
    {
        return toZoom(pixel, MAXZOOM, to);
    }

    /**
     * Get tile pixel of world pixel, i.e. pixel coordinate within tile.
     *
     * @param pixel     World pixel
     * @return          Tile pixel
     */
    public static int getPixel(int pixel)
    {
        return pixel & TILE_MASK;
    }

    /**
     * Get tile number of world pixel, i.e. which tile this pixel is on.
     *
     * @param pixel     World pixel
     * @return          Tile number
     */
    public static int getTile(int pixel)
    {
        return pixel >> TILE_BITS;
    }

    /**
     * Given a tile number and a tile pixel, return a world pixel.
     *
     * @param tileno    Tile number
     * @param tilepix   Tile pixel
     * @return          World pixel
     */
    public static int makePixel(int tileno, int tilepix)
    {
        return (tileno << TILE_BITS) | tilepix;
    }

    /**
     * Convert latitude into world pixel
     *
     * @param latitude  Latitude as a double precision value
     * @param zoom      Desired zoom level
     * @return          World pixel
     */
    public static int fromLatitude(double latitude, int zoom)
    {
        return _fromLatitude(latitude, 1 << (zoom + TILE_BITS));
    }

    /**
     * Variant of fromLatitude that assumes zoom level MAXZOOM.
     *
     * @param latitude  Latitude as a double precision value
     * @return          World pixel
     */
    public static int fromLatitude(double latitude)
    {
        return _fromLatitude(latitude, MAXMAX);
    }

    private static int _fromLatitude(double latitude, int numPixels)
    {
        if (Math.abs(latitude) > MAXLAT)
            throw new IllegalArgumentException("Illegal or unsupported latitude: " + latitude);
        double rl = Math.toRadians(latitude);
        return (int) ((double) numPixels * (1.0 - (Math.log(Math.tan(rl) + 1.0/Math.cos(rl)) / Math.PI)) / 2.0);
    }

    /**
     * Convert longitude into world pixel
     *
     * @param longitude Longitude as a double precision value
     * @param zoom      Desired zoom level
     * @return          World pixel
     */
    public static int fromLongitude(double longitude, int zoom)
    {
        return _fromLongitude(longitude, 1 << (zoom + TILE_BITS));
    }

    /**
     *  Variant of fromLongitude that assumes zoom level MAXZOOM.
     *
     * @param longitude Longitude as a double precision value
     * @return          World pixel
     */
    public static int fromLongitude(double longitude)
    {
        return _fromLongitude(longitude, MAXMAX);
    }

    private static int _fromLongitude(double longitude, int numPixels)
    {
        if (Math.abs(longitude) > MAXLON)
            throw new IllegalArgumentException("Illegal longitude: " + longitude);
        int ret = (int) ((double) numPixels * ((longitude + 180.0) / 360.0));
        return _normalizeX(ret, numPixels);
    }

    /**
     * Convert world pixel to latitude
     *
     * @param pixel     World pixel
     * @param zoom      Zoom level
     * @return          Latitude
     */
    public static double toLatitude(int pixel, int zoom)
    {
        return _toLatitude(pixel, 1 << (zoom + TILE_BITS));
    }

    /**
     * Variant of toLatitude that assumes zoom level MAXZOOM.
     *
     * @param pixel     World pixel
     * @return          Latitude
     */
    public static double toLatitude(int pixel)
    {
        return _toLatitude(pixel, MAXMAX);
    }

    private static double _toLatitude(int pixel, int numPixels)
    {
        double n = Math.PI - (2.0 * Math.PI * pixel) / (double) numPixels;
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /**
     * Convert world pixel to longitude
     *
     * @param pixel     World pixel
     * @param zoom      Zoom level
     * @return          Longitude
     */
    public static double toLongitude(int pixel, int zoom)
    {
        return _toLongitude(pixel, 1 << (zoom + TILE_BITS));
    }

    /**
     * Variant of toLongitude that assumes zoom level MAXZOOM.
     *
     * @param pixel     World pixel
     * @return          Latitude
     */
    public static double toLongitude(int pixel)
    {
        return _toLongitude(pixel, MAXMAX);
    }

    private static double _toLongitude(int pixel, int numPixels)
    {
        return (double) pixel / (double) numPixels * 360.0 - 180.0;
    }

    /**
     * Like LatLong.eastFrom (q.v.) but for WorldPixel X coordinates.
     * Both values passed are assumed to be in the same zoom level.
     *
     * @param start     Starting X coordinate
     * @param end       Ending X coordinate
     * @param zoom      Zoom level
     * @return          Distance in world pixels, always positive
     */
    public static int eastFrom(int start, int end, int zoom)
    {
        return _eastFrom(start, end, 1 << (zoom + TILE_BITS));
    }

    /**
     * Variant of eastFrom that assimes zoom level MAXZOOM.
     *
     * @param start     Starting X coordinate
     * @param end       Ending X coordinate
     * @return          Distance in world pixels, always positive
     */
    public static int eastFrom(int start, int end)
    {
        return _eastFrom(start, end, MAXMAX);
    }

    private static int _eastFrom(int start, int end, int numPixels)
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
     * Returns true of x1 is east of x2. Note that "east of" and "west of"
     * are ambiguous concepts! Every longitude is always "east of" every
     * other longitude, in that on a spherical Earth, you can always reach
     * every other longitude if you travel far enough east. What we do
     * is define a one longitude to be east of another if you can reach it
     * in less distance by travelling east than by travelling west.
     *
     * @param x1        First world pixel X coordinate
     * @param x2        Second world pixel X coordinate
     * @param zoom      Zoom level
     * @return          Boolean value
     */
    public static boolean eastOf(int x1, int x2, int zoom)
    {
        int max = 1 << (zoom + TILE_BITS);
        return _eastFrom(x2, x1, max) < _eastFrom(x1, x2, max);
    }

    /**
     * Variant of eastOf that assumes zoom level MAXZOOM.
     *
     * @param x1        First world pixel X coordinate
     * @param x2        Second world pixel X coordinate
     * @return          Boolean value
     */
    public static boolean eastOf(int x1, int x2)
    {
        return _eastFrom(x2, x1, MAXMAX) < _eastFrom(x1, x2, MAXMAX);
    }

    /**
     * Returns true of x1 is west of x2. This is an ambiguous concept; see
     * the description of eastOf for more details.
     *
     * @param x1        First world pixel X coordinate
     * @param x2        Second world pixel X coordinate
     * @param zoom      Zoom level
     * @return          Boolean value
     */
    public static boolean westOf(int x1, int x2, int zoom)
    {
        int max = 1 << (zoom + TILE_BITS);
        return _eastFrom(x2, x1, max) > _eastFrom(x1, x2, max);
    }

    /**
     * Variant of westOf that assumes zoom level MAXZOOM.
     *
     * @param x1        First world pixel X coordinate
     * @param x2        Second world pixel X coordinate
     * @return          Boolean value
     */
    public static boolean westOf(int x1, int x2)
    {
        return _eastFrom(x2, x1, MAXMAX) > _eastFrom(x1, x2, MAXMAX);
    }

    /**
     * Normalize a world pixel X coordinate after adding or subtracting.
     *
     * @param unnormalized  Unnormalized world pixel X coordinate
     * @param zoom          Zoom level
     * @return              Normalized X coordinate
     */
    public static int normalizeX(int unnormalized, int zoom)
    {
        return _normalizeX(unnormalized, 1 << (zoom + TILE_BITS));
    }

    /**
     * Variant of normalizeX that assumes zoom level MAXZOOM.
     *
     * @param unnormalized  Unnormalized world pixel X coordinate
     * @return              Normalized X coordinate
     */
    public static int normalizeX(int unnormalized)
    {
        return _normalizeX(unnormalized, MAXMAX);
    }

    private static int _normalizeX(int unnormalized, int numPixels)
    {
        int ret = unnormalized;
        while (ret >= numPixels)
            ret -= numPixels;
        while (ret < 0)
            ret += numPixels;
        return ret;
    }

    /**
     * Returns true if y1 is north of y2. Simple, but here for
     * sake of logical completeness with eastOf/westOf. Works
     * for y1 and y2 in any given zoom level.
     *
     * @param y1        First world pixel Y coordinate
     * @param y2        Second world pixel Y coordinate
     * @return          Boolean value
     */
    public static boolean northOf(int y1, int y2)
    {
        return y1 < y2;
    }

    /**
     * Variant of the above that accepts a zoom argument (which it ignores).
     * Here to make things orthogonal.
     *
     * @param y1        First world pixel Y coordinate
     * @param y2        Second world pixel Y coordinate
     * @param zoom      Zoom level (ignored)
     * @return          Boolean value
     */
    public static boolean northOf(int y1, int y2, int zoom)
    {
        return northOf(y1, y2);
    }

    /**
     * Returns true if y1 is south of y2. Simple, but here for
     * sake of logical completeness with eastOf/westOf. Works
     * for y1 and y2 in any given zoom level.
     *
     * @param y1        First world pixel Y coordinate
     * @param y2        Second world pixel Y coordinate
     * @return          Boolean value
     */
    public static boolean southOf(int y1, int y2)
    {
        return y1 > y2;
    }

    /**
     * Variant of the above that accepts a zoom argument (which it ignores).
     * Here to make things orthogonal.
     *
     * @param y1        First world pixel Y coordinate
     * @param y2        Second world pixel Y coordinate
     * @param zoom      Zoom level (ignored)
     * @return          Boolean value
     */
    public static boolean southOf(int y1, int y2, int zoom)
    {
        return southOf(y1, y2);
    }
}
