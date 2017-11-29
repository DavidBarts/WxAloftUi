// package name.blackcap.wxaloftuiservlet;

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
 * A tiles provider that caches another one.
 */
public class CachingTileProvider extends TileProvider
{
    private static final String TYPE = "png";
    private static final long MAXLIFE = 30L * 24L * 60L * 60L * 1000L;  /* 30 days */

    File cacheDir;
    TileProvider orig;

    public CachingTileProvider(File cacheDir, TileProvider orig)
    {
        this.orig = orig;
        this.cacheDir = cacheDir;
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
        /* if it's in the cache, use that */
        File cz = new File(cacheDir, Integer.toString(z));
        File czy = new File(cz, Integer.toString(y));
        File czyx = new File(czy, String.format("%d.%s", x, TYPE));
        if (czyx.exists() && (System.currentTimeMillis() - czyx.lastModified()) < MAXLIFE) {
            Lock tLock = ThreadLock.readLock(czyx);
            try (
                FileInputStream in = new FileInputStream(czyx);
                FileLock pLock = in.getChannel().lock(0L, Long.MAX_VALUE, true)
            ) {
                return ImageIO.read(in);
            } catch (IOException e) {
                /* assume it's a corrupt file, let a new one get fetched */
            } finally {
                tLock.unlock();
            }
        }

        /* else fetch it, cache it, return it */
        Image ret = orig.getTile(x, y, z);
        if (!czy.exists())
            czy.mkdirs();
        Lock tLock = ThreadLock.writeLock(czyx);
        try (
            FileOutputStream out = new FileOutputStream(czyx);
            FileLock pLock = out.getChannel().lock(0L, Long.MAX_VALUE, false)
        ) {
            ImageIO.write((RenderedImage) ret, TYPE, out);
        } finally {
            tLock.unlock();
        }
        return ret;
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
}
