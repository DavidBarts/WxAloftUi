package name.blackcap.wxaloftuiservlet;

/**
 * @author me@blackcap.name
 * @since 2017-11-27
 *
 * Static utilities for comparing latitude/longitude (mostly the latter)
 * values.
 */
public class LatLong
{
    /**
     * Returns true if lat1 is north of lat2. Here mostly for symmetry,
     * because the logic is so simple.
     * @param           First latitude
     * @param           Second latitude
     * @return          Boolean value
     */
    public static boolean northOf(double lat1, double lat2)
    {
        return lat1 > lat2;
    }

    /**
     * Returns true if lat1 is south of lat2. Here mostly for symmetry,
     * because the logic is so simple.
     * @param           First latitude
     * @param           Second latitude
     * @return          Boolean value
     */
    public static boolean southOf(double lat1, double lat2)
    {
        return lat1 < lat2;
    }

    /**
     * How far it is (in degrees) if you head east from start to finish.
     * You will always get there, just sometimes the long way 'round...
     *
     * @param start     Starting longitude
     * @param end       Ending longitude
     * @return          Difference in degrees, always positive
     */
    public static double eastFrom(double start, double finish)
    {
        /* normalize any 180° values */
        if (start == -180.0)
            start = 180.0;
        if (finish == -180.0)
            finish = 180.0;
        /* trivial case */
        if (finish >= start)
            return finish - start;
        /* this is what (180 - start) + (finish - -180) simplifies to */
        return 360.0 - start + finish;
    }

    /**
     * Normalize a longitude.
     *
     * @param longitude Value to normalize
     * @return          Normalized value
     */
    public static double normalizeLong(double longitude) {
        /* there should be only one representation for the 180 degree value */
        if (longitude == -180.0)
            return 180.0;
        if (longitude > 180.0)
            return normalizeLong(longitude - 360.0);
        if (longitude < -180.0)
            return normalizeLong(longitude + 360.0);
        return longitude;
    }

    /**
     * Returns true if long1 is east of long2.
     * @param           First longitude
     * @param           Second longitude
     * @return          Boolean value
     */
    public static boolean eastOf(double long1, double long2)
    {
        return eastFrom(long2, long1) < eastFrom(long1, long2);
    }

    /**
     * Returns true if long1 is west of long2.
     * @param           First longitude
     * @param           Second longitude
     * @return          Boolean value
     */
    public static boolean westOf(double long1, double long2)
    {
        return eastFrom(long2, long1) > eastFrom(long1, long2);
    }
}
