package info.koosah.wxaloftuiservlet;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.net.URL;

/**
 * @author David Barts <n5jrn@me.com>
 * @since 2017-11-27
 *
 * Generic tiles provider
 */
abstract public class TileProvider
{
    /**
     * Gets the tile for the specified column, row, and zoom level.
     * @param x         Column
     * @param y         Row
     * @param z         Zoom level
     * @return          Image of the tile
     */
    public Image getTile(int x, int y, int z) throws IOException
    {
        String urlString = getTileUrl(x, y, z);
        try {
            return ImageIO.read(new URL(urlString));
        } catch (IOException e) {
            throw new IOException("Unable to read URL: " + urlString, e);
        }
    }

    /**
     * Returns the URL of a tile for the specified column, row, and
     * zoom level.
     * @param x         Column
     * @param y         Row
     * @param z         Zoom level
     * @return          URL of the tile
     */
    abstract public String getTileUrl(int x, int y, int z);
}
