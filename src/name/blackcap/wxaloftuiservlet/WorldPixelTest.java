package name.blackcap.wxaloftuiservlet;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import static name.blackcap.wxaloftuiservlet.WorldPixel.*;

/**
 * Tests the WorldPixel module.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class WorldPixelTest
{
    private static final double SEA_LAT = 47.4489;
    private static final double SEA_LON = -122.3094;
    private static final double YVR_LAT = 49.1947;
    private static final double YVR_LON = -123.1839;
    private static final double PDX_LAT = 45.5886;
    private static final double PDX_LON = -122.5975;
    private static final double GEG_LAT = 47.6200;
    private static final double GEG_LON = -117.5339;

    @Test
    public void extremes()
    {
        assertEquals(toLongitude(0), -180.0, 0.00001);
        assertEquals(toLongitude(1<<26), 180.0, 0.00001);
        assertEquals(toLatitude(0), MAXLAT, 0.00001);
        assertEquals(toLatitude(1<<26), -MAXLAT, 0.00001);

        assertEquals(fromLongitude(-180.0), 0);
        assertEquals(fromLongitude(180.0), 0);  /* normalizes */
        assertEquals(fromLatitude(MAXLAT), 0);
        assertEquals(fromLatitude(-MAXLAT), (1<<26) - 1);
    }

    @Test
    public void zeroes()
    {
        assertEquals(toLongitude(1<<25), 0.0, 0.00001);
        assertEquals(toLatitude(1<<25), 0.0, 0.00001);

        assertEquals(fromLatitude(0.0), 1<<25);
        assertEquals(fromLongitude(0.0), 1<<25);
    }

    @Test
    public void defaultZoom()
    {
        assertEquals(toLatitude(50000), toLatitude(50000, MAXZOOM), 0.00001);
        assertEquals(toLongitude(50000), toLongitude(50000, MAXZOOM),  0.00001);

        assertEquals(fromLatitude(SEA_LAT), fromLatitude(SEA_LAT, MAXZOOM));
        assertEquals(fromLongitude(SEA_LON), fromLongitude(SEA_LON, MAXZOOM));
    }

    @Test
    public void nsew()
    {
        assertTrue(northOf(fromLatitude(YVR_LAT), fromLatitude(SEA_LAT)));
        assertTrue(southOf(fromLatitude(PDX_LAT), fromLatitude(SEA_LAT)));
        assertTrue(eastOf(fromLongitude(GEG_LON), fromLongitude(SEA_LON)));
        assertTrue(westOf(fromLongitude(YVR_LON), fromLongitude(SEA_LON)));
    }

    @Test
    public void wraparound()
    {
        assertTrue(westOf(fromLongitude(-1.0), fromLongitude(1.0)));
        assertTrue(eastOf(fromLongitude(1.0), fromLongitude(-1.0)));
        assertTrue(westOf(fromLongitude(179.0), fromLongitude(-179.0)));
        assertTrue(eastOf(fromLongitude(-179.0), fromLongitude(179.0)));
    }

    @Test
    public void self()
    {
        int sea_x = fromLongitude(SEA_LON);
        int sea_y = fromLatitude(SEA_LAT);
        assertFalse(westOf(sea_x, sea_x));
        assertFalse(eastOf(sea_x, sea_x));
        assertFalse(northOf(sea_y, sea_y));
        assertFalse(southOf(sea_y, sea_y));
    }

    @Test
    public void zoom()
    {
        int sea_x = fromLongitude(SEA_LON);
        int sea_y = fromLatitude(SEA_LAT);
        assertEquals(toZoom(sea_x, 17), fromLongitude(SEA_LON, 17));
        assertEquals(toZoom(sea_y, 17), fromLatitude(SEA_LAT, 17));
        assertEquals(toZoom(sea_x, 18, 17), toZoom(sea_x, 17));
    }
}
