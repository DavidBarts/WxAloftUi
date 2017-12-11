package name.blackcap.wxaloftuiservlet;

import java.awt.Image;
import java.io.IOException;

import static name.blackcap.wxaloftuiservlet.WorldPixel.*;

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
     * Convenience routine to make tile for specified latitude and longitude.
     *
     * @param lat       Latitude
     * @param lon       Longitude
     * @param z         Zoom level
     * @param p         TileProvider to furnish images
     */
    public static Tile forLatLong(double lat, double lon, int z, TileProvider p)
    {
        int x = getTile(fromLatitude(lat, z));
        int y = getTile(fromLongitude(lon, z));
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

    /**
     * Get inclusive westmost extent.
     *
     * @return          World pixel X coordinate at tile zoom level
     */
    public int west()
    {
        return makePixel(x, 0);
    }

    /**
     * Get exclusive eastmost extent.
     *
     * @return          World pixel X coordinate at tile zoom level
     */
    public int east()
    {
        return normalizeX(west() + TILE_SIZE, zoom);
    }
    /**
     * Get inclusive northmost extent.
     *
     * @return          World pixel Y coordinate at tile zoom level
     */
    public int north()
    {
        return makePixel(y, 0);
    }

    /**
     * Get exclusive southmost extent.
     *
     * @return          World pixel Y coordinate at tile zoom level
     */
    public int south()
    {
        return north() + TILE_SIZE;
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
}
