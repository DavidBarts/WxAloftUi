// package name.blackcap.wxaloftuiservlet;

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
 * the basic properties of a tile).
 */
public class Tile
{
    private static final double MAXLAT = Math.toDegrees(Math.atan(Math.sinh(Math.PI)));
    private static final double MAXLON = 180.0;
    public static final int SIZE = 256;
    private static final int MAXZOOM = 18;
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
        if (Math.abs(lat) > MAXLAT)
            throw new IllegalArgumentException("Illegal latitude: " + lat);
        if (Math.abs(lon) > MAXLON)
            throw new IllegalArgumentException("Illegal longitude: " + lon);
        int scale = 1 << z;
        int x = (int) Math.floor((lon + 180.0) / 360.0 * scale);
        double rlat = Math.toRadians(lat);
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(rlat) + 1.0 / Math.cos(rlat)) / Math.PI) / 2.0 * scale);
        x = Math.max(0, Math.min(x, scale - 1));
        y = Math.max(0, Math.min(y, scale - 1));
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
        if (image == null)
            image = p.getTile(x, y, zoom);
        return image;
    }

    private double tile2lon(int x)
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
        return tile2lon(x);
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
        return tile2lon(ex);
    }

    private double tile2lat(int y)
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
        return tile2lat(y);
    }

    /**
     * Get southmost latitude.
     *
     * @return          Latitude.
     */
    public double south()
    {
        return tile2lat(y + 1);
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

    private static int _calcZoom(double degrees, int pixels)
    {
        return (int) Math.ceil(Math.log((pixels*360.0)/(SIZE*degrees))/LOG2);
    }

    /**
     * Calculate zoom level necessary for a map from (swx, swy) to
     * (nex, ney) to have at least xpixels and ypixels resolution.
     *
     * @param swy       Latitude of southwest corner.
     * @param swx       Longitude of southwest corner.
     * @param ney       Latitude of northeast corner.
     * @param nex       Longitude of northeast corner.
     * @param xpixels   Minimum number of E-W pixels.
     * @param ypixels   Minimum number of N-S pixels.
     * @return          Recommended zoom level.
     */
    public static int calcZoom(double swy, double swx, double ney, double nex, int xpixels, int ypixels)
    {
        double xdelta = LatLong.eastFrom(swx, nex);
        if (LatLong.eastFrom(nex, swx) < xdelta)
            throw new IllegalArgumentException(String.format("%f is not west of %f!", swx, nex));
        double ydelta = ney - swy;
        if (ydelta < 0.0)
            throw new IllegalArgumentException(String.format("%f is not south of %f!", swy, ney));
        int xzoom = _calcZoom(xdelta, xpixels);
        int yzoom = _calcZoom(ydelta, ypixels);
        return Math.min(MAXZOOM, Math.max(xzoom, yzoom));
    }
}
