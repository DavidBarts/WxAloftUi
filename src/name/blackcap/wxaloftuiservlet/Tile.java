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

    private static final double MAXLAT = Math.toDegrees(Math.atan(Math.sinh(Math.PI)));
    private static final double MAXLON = 180.0;
    private static final double LOG2 = Math.log(2.0);

    private TileProvider p;
    private Image image;
    private int x, y;
    private int zoom;
    private int rowsAndCols;

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
        this.rowsAndCols = 1 << zoom;
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
        int rowsAndCols = 1 << z;
        int x = (int) Math.floor(_toTileX(lon, rowsAndCols));
        int y = (int) Math.floor(_toTileY(lat, rowsAndCols));
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

    /**
     * Convert tilespace x coordinate to longitude.
     *
     * @param           Tilespace X coordinate.
     * @return          Longitude
     */
    public double toLongitude(double x)
    {
        return x / (double) rowsAndCols * 360.0 - 180.0;
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
        if (ex >= rowsAndCols)
            ex = 0;
        return toLongitude((double) ex);
    }

    /**
     * Convert tilespace y coordinate to latitude
     *
     * @param           Tilespace y coordinate, fractional values allowed.
     * @return          Latitude
     */
    public double toLatitude(double y)
    {
        double n = Math.PI - (2.0 * Math.PI * y) / (double) rowsAndCols;
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
        if (ex >= rowsAndCols)
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
            wx = rowsAndCols - 1;
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
        if (sy >= rowsAndCols)
            return null;
        return new Tile(x, sy, zoom, p);
    }

    /**
     * Convert longitude to tilespace coordinate.
     *
     * @param longitude Longitude to convert
     * @return          Possibly fractional x coordinate
     */
    public double toTileX(double longitude)
    {
        return _toTileX(longitude, rowsAndCols);
    }

    private static double _toTileX(double longitude, int rowsAndCols)
    {
        if (Math.abs(longitude) > MAXLON)
            throw new IllegalArgumentException("Illegal longitude: " + longitude);
        return rowsAndCols * ((longitude + 180.0) / 360.0);
    }

    /**
     * Convert latitude to tilespace coordinate.
     *
     * @param latitude  Latitude to convert
     * @return          Possibly fractional y coordinate
     */
    public double toTileY(double latitude)
    {
        return _toTileY(latitude, rowsAndCols);
    }

    private static double _toTileY(double latitude, int rowsAndCols)
    {
        if (Math.abs(latitude) > MAXLAT)
            throw new IllegalArgumentException("Illegal latitude: " + latitude);
        double rl = Math.toRadians(latitude);
        return rowsAndCols * (1.0 - (Math.log(Math.tan(rl) + 1.0/Math.cos(rl)) / Math.PI)) / 2.0;
    }

    /**
     * Calculate zoom level necessary for a map of size xpixels by ypixels
     * to display everything within the specified bounds. The map will show
     * at least all the bounds specified (and probably more).
     *
     * @param south     Latitude of southwest corner.
     * @param west      Longitude of southwest corner.
     * @param north     Latitude of northeast corner.
     * @param east      Longitude of northeast corner.
     * @param xpixels   Minimum number of E-W pixels.
     * @param ypixels   Minimum number of N-S pixels.
     * @return          Recommended zoom level.
     */
    /* public static int calcZoom(double south, double west, double north, double east, int xpixels, int ypixels)
    {
        int oldz = 0;
        for (int z=0, n=1; z <= MAXZOOM; oldz=z++, n<<=1) {
            double tSouth = _toTileY(south, n);
            double tWest  = _toTileX(west,  n);
            double tNorth = _toTileY(north, n);
            double tEast  = _toTileX(east,  n);
            int width  = (int) Math.ceil((tSouth - tNorth) * SIZE);
            int height = (int) Math.ceil((tEast - tWest) * SIZE);
            if (width > xpixels || height > ypixels)
                break;
        }
        return oldz;
    } */
}
