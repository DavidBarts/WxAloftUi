// package name.blackcap.wxaloftuiservlet;

/**
 * @author me@blackcap.name
 * @since 2017-11-27
 *
 * Provider that pesters tile.openstreetmap.org for tiles.
 */
public class OsmTileProvider extends TileProvider
{
    /**
     * Returns the URL of a tile for the specified column, row, and
     * zoom level.
     * @param x         Column
     * @param y         Row
     * @param z         Zoom level
     * @return          URL of the tile
     */
    public String getTileUrl(int x, int y, int z)
    {
        return String.format("http://tile.openstreetmap.org/%d/%d/%d.png", z, x, y);
    }
}
