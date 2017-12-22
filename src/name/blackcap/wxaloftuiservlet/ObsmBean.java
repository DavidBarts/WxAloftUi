package name.blackcap.wxaloftuiservlet;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import name.blackcap.acarsutils.AcarsObservation;

import static name.blackcap.wxaloftuiservlet.WorldPixel.*;

/**
 * Backs the obsm.jsp (mobile-friendly observations) page.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class ObsmBean
{
    private static final Logger LOGGER = Logger.getLogger(ObsmBean.class.getCanonicalName());
    private static final SimpleDateFormat LOCAL_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /* maybe put these in a common file? or do we want separate defaults? */
    private static final String DEFAULT_DURATION = "PT2H";
    private static final long MAX_DURATION = 6L * 60L * 60L * 1000L;

    private int areaId;
    private Integer north, south, east, west, zoom;
    private ArrayList<Observation> observations;
    private String mapParams, rawDuration, shortArea, longArea, sinceString;
    private String zoomIn, zoomOut;
    private String panNorth, panSouth, panEast, panWest;

    private boolean hasBounds;
    private long since;

    public ObsmBean()
    {
        areaId = -1;
        north = south = east = west = zoom = 0;
        observations = new ArrayList<Observation>();
        since = 0L;
        mapParams = rawDuration = shortArea = longArea = sinceString = null;
        zoomIn = zoomOut = null;
        panNorth = panSouth = panEast = panWest = null;
    }

    public boolean processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        /* get and parse the optional since parameter */
        rawDuration = req.getParameter("since");
        if (rawDuration == null)
            rawDuration = DEFAULT_DURATION;
        Duration d = null;
        try {
            d = Duration.parse(rawDuration);
        } catch (DateTimeParseException e) {
            LOGGER.log(Level.SEVERE, "Invalid duration", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid duration)");
            return false;
        }
        long millis = d.getSeconds() * 1000L + d.getNano() / 1000000;
        if (millis > MAX_DURATION) {
            LOGGER.log(Level.SEVERE, "Duration too long!");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (excessive duration)");
            return false;
        }
        since = System.currentTimeMillis() - millis;

        /* get specified bounds, if they exist */
        north = getInteger(req, "north");
        south = getInteger(req, "south");
        east = getInteger(req, "east");
        west = getInteger(req, "west");
        zoom = getInteger(req, "zoom");

        /* if bounds were not completely specified, (a) it doesn't count, and
           (b) we wipe the session bounds clean, else use session bounds to
           check the bounds we just got fed */
        HttpSession sess = req.getSession();
        hasBounds = false;
        if (north == null || south == null || east == null || west == null || zoom == null) {
            sess.removeAttribute("north");
            sess.removeAttribute("south");
            sess.removeAttribute("east");
            sess.removeAttribute("west");
            sess.removeAttribute("zoom");
        } else {
            Integer northLimit = (Integer) sess.getAttribute("north");
            Integer southLimit = (Integer) sess.getAttribute("south");
            Integer eastLimit = (Integer) sess.getAttribute("east");
            Integer westLimit = (Integer) sess.getAttribute("west");
            Integer zoomLimit = (Integer) sess.getAttribute("zoom");
            if (northLimit == null || southLimit == null || eastLimit == null || westLimit == null || zoomLimit == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (incomplete session)");
                return false;
            }
            boolean invalidBounds =
                northOf(toZoom(north, zoom, zoomLimit), northLimit) ||
                southOf(toZoom(south, zoom, zoomLimit), southLimit) ||
                eastOf(toZoom(east, zoom, zoomLimit), eastLimit, zoomLimit) ||
                westOf(toZoom(west, zoom, zoomLimit), westLimit, zoomLimit);
            boolean invalidZoom = zoom < zoomLimit || zoom > MAXZOOM;
            if (invalidBounds || invalidZoom) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid bounds or zoom)");
                return false;
            }
            hasBounds = true;
        }

        /* debug */
        if (hasBounds)
            LOGGER.log(Level.INFO,
                String.format("north=%d, south=%d, east=%d, west=%d, zoom=%d",
                    north, south, east, west, zoom));
        else
            LOGGER.log(Level.INFO, "no bounds supplied");

        /* everythiong else requires a database connection, so... */
        try (Connection conn = getConnection()) {
            return processWithConnection(req, resp, conn);
        } catch (NamingException|SQLException e) {
            throw new ServletException(e);
        }
    }

    public boolean processWithConnection(HttpServletRequest req, HttpServletResponse resp, Connection conn) throws IOException
    {
        /* get the mandatory area and translate it into a numeric ID */
        String area = req.getParameter("area");
        if (area == null) {
            LOGGER.log(Level.SEVERE, "Missing area= parameter");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (missing area= parameter)");
            return false;
        }
        try {
            areaId = Integer.parseInt(area);
        } catch (NumberFormatException e) {
            try (PreparedStatement stmt = conn.prepareStatement("select id from areas where name = ?")) {
                stmt.setString(1, area);
                ResultSet rs = stmt.executeQuery();
                if (rs.next())
                    areaId = rs.getInt(1);
                else {
                    LOGGER.log(Level.SEVERE, "Invalid area");
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid area)");
                    return false;
                }
            } catch (SQLException e2) {
                LOGGER.log(Level.SEVERE, "Unable to resolve area", e2);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to resolve area)");
                return false;
            }
        }

        /* get terminal latitude, longitude, time zone */
        SimpleDateFormat dFormat = (SimpleDateFormat) LOCAL_TIME.clone();
        double termLat = 0.0, termLong = 0.0;
        try (PreparedStatement stmt = conn.prepareStatement("select name, city, region, country, timezone, latitude, longitude from areas where id = ?")) {
            stmt.setInt(1, areaId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                LOGGER.log(Level.SEVERE, String.format("Area ID %s unknown", areaId));
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (area ID unknown)");
                return false;
            }
            shortArea = rs.getString(1);
            longArea = String.format("%s, %s, %s", rs.getString(2), rs.getString(3), rs.getString(4));
            dFormat.setTimeZone(TimeZone.getTimeZone(rs.getString(5)));
            sinceString = dFormat.format(new java.util.Date(since));
            termLat = rs.getDouble(6);
            termLong = rs.getDouble(7);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get terminal location", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get terminal location)");
            return false;
        }

        /* get some starting bounds if we need them */
        double myNorth = 0.0, mySouth = 0.0, myEast = 0.0, myWest = 0.0;
        if (!hasBounds) {
            myNorth = termLat + 0.125;
            mySouth = termLat - 0.125;
            myEast = LatLong.normalizeLong(termLong + 0.125);
            myWest = LatLong.normalizeLong(termLong - 0.125);
        }

        /* read in stuff from database and possibly determine map extents */
        long first = -1L, last = -1L;
        ArrayList<Observation> all = new ArrayList<Observation>();
        try (PreparedStatement stmt = conn.prepareStatement("select observations.id, observations.received, observations.observed, observations.frequency, observations.altitude, observations.wind_speed, observations.wind_dir, observations.temperature, observations.source, observations.latitude, observations.longitude from observations join obs_area on observations.id = obs_area.observation_id where observations.observed > ? and obs_area.area_id = ? order by observations.id asc")) {
            stmt.setTimestamp(1, new Timestamp(since));
            stmt.setInt(2, areaId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long id = rs.getLong(1);
                Date received = new Date(rs.getTimestamp(2).getTime());
                last = rs.getTimestamp(3).getTime();
                if (first == -1L)
                    first = last;
                Date observed = new Date(last);
                double frequency = rs.getDouble(4);
                int altitude = rs.getInt(5);
                Short wind_speed = rs.getShort(6);
                if (rs.wasNull()) wind_speed = null;
                Short wind_dir = rs.getShort(7);
                if (rs.wasNull()) wind_dir = null;
                Float temperature = rs.getFloat(8);
                if (rs.wasNull()) temperature = null;
                String source = rs.getString(9);
                if (rs.wasNull()) source = null;
                double latitude = rs.getDouble(10);
                double longitude = rs.getDouble(11);
                if (!hasBounds) {
                    if (latitude > myNorth)
                        myNorth = latitude;
                    if (latitude < mySouth)
                        mySouth = latitude;
                    if (LatLong.eastOf(longitude, myEast))
                        myEast = longitude;
                    if (LatLong.westOf(longitude, myWest))
                        myWest = longitude;
                }
                /* browsers use \r to indicate line breaks in tool tips */
                String details = escapeIt(String.join("\r", new String[] {
                    listIt("Altitude", altitude, " ft"),
                    listIt("Latitude", latitude, "°"),
                    listIt("Longitude", longitude, "°"),
                    listIt("Temperature", temperature, "°C"),
                    listIt("Wind direction", wind_dir, "°"),
                    listIt("Wind speed", wind_speed, " kn"),
                    listIt("Time observed", dFormat.format(observed), ""),
                    listIt("Time received", dFormat.format(received), ""),
                    listIt("Frequency", frequency, " MHz"),
                    listIt("Source", source, "") }));
                all.add(new Observation(latitude, longitude, id, details));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to read observations", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to read observations)");
            return false;
        }

        /* get map params and pixel addresses */
        Map dummy = null;
        if (hasBounds)
            dummy = new Map(south, west, north, east, zoom, null);
        else {
            double[] extents = new double[] { mySouth, myWest, myNorth, myEast };
            int[] size = new int[] { GetMap.PIXELS, GetMap.PIXELS };
            dummy = Map.withSize(extents, size, null);
            north = dummy.northPixel();
            south = dummy.southPixel();
            east = dummy.eastPixel();
            west = dummy.westPixel();
            zoom = dummy.getZoom();
            HttpSession sess = req.getSession();
            sess.setAttribute("north", north);
            sess.setAttribute("south", south);
            sess.setAttribute("east", east);
            sess.setAttribute("west", west);
            sess.setAttribute("zoom", zoom);
        }
        int height = south - north;
        int width = eastFrom(west, east);
        for (Observation o : all) {
            int y = dummy.latToPixel(o.getLatitude());
            if (y < 0 || y > height)
                continue;
            int x = dummy.longToPixel(o.getLongitude());
            if (x < 0 || x > width)
                continue;
            o.setY(y);
            o.setX(x);
            observations.add(o);
        }

        /* pan/zoom params */
        setPanZoom(req.getSession());

        /* set mapParams and return */
        mapParams = String.format("?from=%d&to=%d&area=%d&south=%d&west=%d&north=%d&east=%d&zoom=%d",
            first, last, areaId, south, west, north, east, zoom);
        return true;
    }

    /* we just leave the instance variables alone if an operation is
       not pertinent (the constructor has already nulled them for us)
       NOTE: this logic assumes width and height are multiples of 4 */
    private void setPanZoom(HttpSession sess)
    {
        String template = "obsm.jsp?area=" + shortArea + "&since=" + rawDuration
            + "&north=%d&south=%d&east=%d&west=%d&zoom=%d";

        /* get limits */
        int northLimit = (Integer) sess.getAttribute("north");
        int southLimit = (Integer) sess.getAttribute("south");
        int eastLimit = (Integer) sess.getAttribute("east");
        int westLimit = (Integer) sess.getAttribute("west");
        int zoomLimit = (Integer) sess.getAttribute("zoom");

        /* zooms */
        if (zoom < MAXZOOM) {
            int margin = GetMap.PIXELS / 2;
            int nzoom = zoom + 1;
            int nnorth = (north << 1) + margin;
            int nsouth = (south << 1) - margin;
            int neast = normalizeX((east << 1) - margin, nzoom);
            int nwest = normalizeX((west << 1) + margin, nzoom);
            zoomIn = String.format(template, nnorth, nsouth, neast, nwest, nzoom);
        }
        if (zoom > zoomLimit) {
            int margin = GetMap.PIXELS / 4;
            int nzoom = zoom - 1;
            int nlimit = toZoom(northLimit, zoomLimit, nzoom);
            int slimit = toZoom(southLimit, zoomLimit, nzoom);
            int nnorth = (north >> 1) - margin;
            int nsouth = (south >> 1) + margin;
            if (nnorth < nlimit) {
                nnorth = nlimit;
                nsouth = GetMap.PIXELS;
            }
            if (nsouth > slimit) {
                nsouth = slimit;
                nnorth = slimit - GetMap.PIXELS;
            }
            int elimit = toZoom(eastLimit, zoomLimit, nzoom);
            int wlimit = toZoom(westLimit, zoomLimit, nzoom);
            int neast = normalizeX((east >> 1) + margin, nzoom);
            int nwest = normalizeX((west >> 1) - margin, nzoom);
            if (eastOf(neast, elimit, nzoom)) {
                neast = elimit;
                nwest = normalizeX(elimit - GetMap.PIXELS, nzoom);
            }
            if (westOf(nwest, wlimit, nzoom)) {
                nwest = wlimit;
                neast = normalizeX(wlimit + GetMap.PIXELS, nzoom);
            }
            zoomOut = String.format(template, nnorth, nsouth, neast, nwest, nzoom);
        }

        /* pans */
        int pan = GetMap.PIXELS / 2;
        int limit = toZoom(northLimit, zoomLimit, zoom);
        if (north > limit) {
            int amount = Math.min(pan, north - limit);
            int nnorth = north - amount;
            int nsouth = south - amount;
            panNorth = String.format(template, nnorth, nsouth, east, west, zoom);
        }
        limit = toZoom(southLimit, zoomLimit, zoom);
        if (south < limit) {
            int amount = Math.min(pan, limit - south);
            int nnorth = north + amount;
            int nsouth = south + amount;
            panSouth = String.format(template, nnorth, nsouth, east, west, zoom);
        }
        limit = toZoom(westLimit, zoomLimit, zoom);
        if (eastOf(west, limit, zoom)) {
            int amount = Math.min(pan, eastFrom(limit, west, zoom));
            int neast = normalizeX(east - amount, zoom);
            int nwest = normalizeX(west - amount, zoom);
            panWest = String.format(template, north, south, neast, nwest, zoom);
        }
        limit = toZoom(eastLimit, zoomLimit, zoom);
        if (westOf(east, limit, zoom)) {
            int amount = Math.min(pan, eastFrom(east, limit, zoom));
            int neast = normalizeX(east + amount, zoom);
            int nwest = normalizeX(west + amount, zoom);
            panEast = String.format(template, north, south, neast, nwest, zoom);
        }
    }

    private String escapeIt(String raw)
    {
        final String TOXIC = "\"&'<>";
        int length = raw.length();
        StringBuffer sb = new StringBuffer((int) (length * 1.25));
        for (int i=0; i<length; i++) {
            char ch = raw.charAt(i);
            if (Character.isISOControl(ch) || Character.getType(ch) == Character.CONTROL || TOXIC.indexOf(ch) != -1) {
                sb.append("&#");
                sb.append((int) ch);
                sb.append(';');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String listIt(String name, Object value, String suffix)
    {
        if (value == null)
            return name + ": (missing)";
        else
            return name + ": " + value.toString() + suffix;
    }

    private Connection getConnection() throws NamingException, SQLException
    {
        Context c = (Context) (new InitialContext()).lookup("java:comp/env");
        DataSource d = (DataSource) c.lookup("jdbc/WxDB");
        return d.getConnection();
    }

    private Integer getInteger(HttpServletRequest req, String name)
    {
        String raw = req.getParameter(name);
        if (raw == null)
            return null;
        return new Integer(raw);
    }

    public int getAreaId()
    {
        return areaId;
    }

    public String getMapParams()
    {
        return mapParams;
    }

    public int getRadius()
    {
        return GetMap.RADIUS;
    }

    public List<Observation> getObservations()
    {
        return observations;
    }

    public String getShortArea()
    {
        return shortArea;
    }

    public String getLongArea()
    {
        return longArea;
    }

    public String getSince()
    {
        return sinceString;
    }

    public String getRawDuration()
    {
        return rawDuration;
    }

    public String getZoomIn()
    {
        return zoomIn;
    }

    public String getZoomOut()
    {
        return zoomOut;
    }

    public String getPanNorth()
    {
        return panNorth;
    }

    public String getPanSouth()
    {
        return panSouth;
    }

    public String getPanEast()
    {
        return panEast;
    }

    public String getPanWest()
    {
        return panWest;
    }
}