package name.blackcap.wxaloftuiservlet;

import java.awt.Image;
import java.io.IOException;

/**
 * @author me@blackcap.name
 * @since 2017-11-27
 *
 * A single map tile. Note that tiles are neither square nor uniform! They
 * are rectangles that get increasingly short and squat as we move away
 * from the equator. Tiles render lazily, and it is allowed to pass a null
 * tile provider (in which case, the tile can only be used for retrieving
 * the basic properties of a tile or tilespace).
 */
public class Tile
{
    public static final int SIZE = 256;
    public static final int MAXZOOM = 18;
    public static final double MAXLAT = Math.toDegrees(Math.atan(Math.sinh(Math.PI)));
    public static final double MAXLON = 180.0;

    private static final double LOG2 = Math.log(2.0);

    private TileProvider p;
    private Image image;
    private int x, y;
    private int zoom;
    private int numTiles;

    /**
     * Constructor.
     *
     * @param x         Column
     * @param y         Row
     * @param z         Zoom level
     * @param p         TileProvider to furnish images
     */
    public Tile(int x, int y, int z, TileProvider p)
    {
        this.x = x;
        this.y = y;
        this.zoom = z;
        this.numTiles = 1 << zoom;
        this.p = p;
        this.image = null;
    }

    /**
     * Make tile for specified latitude and longitude.
     *
     * @param lat       Latitude
     * @param lon       Longitude
     * @param z         Zoom level
     * @param p         TileProvider to furnish images
     */
    public static Tile forLatLong(double lat, double lon, int z, TileProvider p)
    {
        int numTiles = 1 << z;
        int x = (int) Math.floor(_toTileX(lon, numTiles));
        int y = (int) Math.floor(_toTileY(lat, numTiles));
        return new Tile(x, y, z, p);
    }

    /**
     * Get row.
     *
     * @return          Tile row number.
     */
    public int getX()
    {
        return x;
    }

    /**
     * Get column.
     *
     * @return          Tile column number.
     */
    public int getY()
    {
        return y;
    }

    /**
     * Get zoom level.
     *
     * @return          Tile zoom level.
     */
    public int getZoom()
    {
        return zoom;
    }

    /**
     * Get number of tiles in each direction at current zoom level.
     *
     * @return          Number of tiles.
     */
    public int getNumTiles()
    {
        return numTiles;
    }

    /**
     * Get tile image.
     *
     * @return          Tile map image.
     */
    public Image getImage() throws IOException
    {
        if (p == null)
            throw new IllegalStateException("No tile provider available.");
        if (image == null)
            image = p.getTile(x, y, zoom);
        return image;
    }

    /* FIXME: probably should be replaced by one or more functions in
       a world pixel calculations class */
    /**
     * Convert tilespace x coordinate to longitude.
     *
     * @param           Tilespace X coordinate.
     * @return          Longitude
     */
    public double toLongitude(double x)
    {
        return x / (double) numTiles * 360.0 - 180.0;
    }

    /**
     * Get westmost longitude.
     *
     * @return          Longitude
     */
    public double west()
    {
        return toLongitude((double) x);
    }

    /**
     * Get eastmost longitude.
     *
     * @return          Longitude
     */
    public double east()
    {
        int ex = x + 1;
        if (ex >= numTiles)
            ex = 0;
        return toLongitude((double) ex);
    }

    /* FIXME: probably should be replaced by one or more functions in
       a world pixel calculations class */
    /**
     * Convert tilespace y coordinate to latitude
     *
     * @param           Tilespace y coordinate, fractional values allowed.
     * @return          Latitude
     */
    public double toLatitude(double y)
    {
        double n = Math.PI - (2.0 * Math.PI * y) / (double) numTiles;
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /**
     * Get northmost latitude.
     *
     * @return          Latitude.
     */
    public double north()
    {
        return toLatitude((double) y);
    }

    /**
     * Get southmost latitude.
     *
     * @return          Latitude.
     */
    public double south()
    {
        return toLatitude((double) y + 1.0);
    }

    /**
     * Get eastern neighbor.
     *
     * @return          Neighboring tile.
     */
    public Tile eastTile()
    {
        int ex = x + 1;
        if (ex >= numTiles)
            ex = 0;
        return new Tile(ex, y, zoom, p);
    }

    /**
     * Get western neighbor.
     *
     * @return          Neighboring tile.
     */
    public Tile westTile()
    {
        int wx = x - 1;
        if (wx < 0)
            wx = numTiles - 1;
        return new Tile(wx, y, zoom, p);
    }

    /**
     * Get northern neighbor.
     *
     * @return          Neighboring tile, or null of no such tile exists.
     */
    public Tile northTile()
    {
        int ny = y - 1;
        if (ny < 0)
            return null;
        return new Tile(x, ny, zoom, p);
    }

    /**
     * Get southern neighbor.
     *
     * @return          Neighboring tile, or null of no such tile exists.
     */
    public Tile southTile()
    {
        int sy = y + 1;
        if (sy >= numTiles)
            return null;
        return new Tile(x, sy, zoom, p);
    }

    /* FIXME: probably should be replaced by one or more functions in
       a world pixel calculations class */
    /**
     * Convert longitude to tilespace coordinate.
     *
     * @param longitude Longitude to convert
     * @return          Possibly fractional x coordinate
     */
    public double toTileX(double longitude)
    {
        return _toTileX(longitude, numTiles);
    }

    private static double _toTileX(double longitude, int numTiles)
    {
        if (Math.abs(longitude) > MAXLON)
            throw new IllegalArgumentException("Illegal longitude: " + longitude);
        double ret = numTiles * ((longitude + 180.0) / 360.0);
        if (ret >= numTiles)
            ret -= numTiles;
        return ret;
    }

    /* FIXME: probably should be replaced by one or more functions in
       a world pixel calculations class */
    /**
     * Convert latitude to tilespace coordinate.
     *
     * @param latitude  Latitude to convert
     * @return          Possibly fractional y coordinate
     */
    public double toTileY(double latitude)
    {
        return _toTileY(latitude, numTiles);
    }

    private static double _toTileY(double latitude, int numTiles)
    {
        if (Math.abs(latitude) > MAXLAT)
            throw new IllegalArgumentException("Illegal latitude: " + latitude);
        double rl = Math.toRadians(latitude);
        return numTiles * (1.0 - (Math.log(Math.tan(rl) + 1.0/Math.cos(rl)) / Math.PI)) / 2.0;
    }
}
