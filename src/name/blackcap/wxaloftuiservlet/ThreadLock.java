// package name.blackcap.wxaloftuiservlet;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.HashMap;

/**
 * @author me@blackcap.name
 * @since 2017-11-27
 *
 * Per-thread file locking. Not a substutute for per-process locking!
 * Must be used in conjunction with the latter.
 */
public class ThreadLock
{
    private static HashMap<String, ReadWriteLock> locks =
        new HashMap<String, ReadWriteLock>();

    private static ReadWriteLock getLock(File f) throws IOException
    {
        String canon = f.getCanonicalPath();
        ReadWriteLock ret = null;
        synchronized (locks) {
            ret = locks.get(canon);
            if (ret == null) {
                ret = new ReentrantReadWriteLock();
                locks.put(canon, ret);
            }
        }
        return ret;
    }

    /**
     * Establish a per-thread read lock on a file. Return the lock object
     * so that it may be later unlocked.
     * @param           File to lock
     * @return          Lock object
     */
    public static Lock readLock(File f) throws IOException
    {
        Lock ret = getLock(f).readLock();
        ret.lock();
        return ret;
    }

    /**
     * Establish a per-thread write lock on a file. Return the lock object
     * so that it may be later unlocked.
     * @param           File to lock
     * @return          Lock object
     */
    public static Lock writeLock(File f) throws IOException
    {
        Lock ret = getLock(f).writeLock();
        ret.lock();
        return ret;
    }
}
