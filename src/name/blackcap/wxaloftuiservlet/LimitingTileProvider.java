package name.blackcap.wxaloftuiservlet;

import java.awt.Image;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.Lock;
import javax.imageio.ImageIO;

/**
 * @author me@blackcap.name
 * @since 2017-11-27
 *
 * A tiles provider that imposes tile-requesting limits on another one.
 * Note that it is simpler to just furnish a null provider if you want
 * to prevent any tile requests; this class is intended for imposing
 * nonzero limits.
 */
public class LimitingTileProvider extends TileProvider
{

    int limit, requests;
    TileProvider orig;

    public LimitingTileProvider(int limit, TileProvider orig)
    {
        this.orig = orig;
        this.limit = limit;
        this.requests = 0;
    }

    /**
     * Gets the tile for the specified column, row, and zoom level.
     * @param x         Column
     * @param y         Row
     * @param z         Zoom level
     * @return          Image of the tile
     */
    public Image getTile(int x, int y, int z) throws IOException
    {
        if (requests++ >= limit)
            throw new TileLimitException(String.format("Limit of %d tiles exceeded", limit));
        return orig.getTile(x, y, z);
    }

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
        return orig.getTileUrl(x, y, z);
    }

    /**
     * Exception thrown when a tile limit is exceeded.
     */
    public class TileLimitException extends IOException
    {
        public TileLimitException(String message)
        {
            super(message);
        }
        public TileLimitException(String message, Throwable cause)
        {
            super(message, cause);
        }
        public TileLimitException(Throwable cause)
        {
            super(cause);
        }
    }
}
