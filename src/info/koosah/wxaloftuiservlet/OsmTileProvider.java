package info.koosah.wxaloftuiservlet;

/**
 * @author David Barts <n5jrn@me.com>
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
        return String.format("https://tile.openstreetmap.org/%d/%d/%d.png", z, x, y);
    }
}
