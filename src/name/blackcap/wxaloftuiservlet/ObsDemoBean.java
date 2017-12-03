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

/**
 * Backs the obs_demo.jsp page.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class ObsDemoBean
{
    private static final Logger LOGGER = Logger.getLogger(ObsDemoBean.class.getCanonicalName());
    private static final SimpleDateFormat LOCAL_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final SimpleDateFormat UTC_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static {
        UTC_TIME.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private static final String DEFAULT_DURATION = "PT2H";
    private int areaId;
    private double north, south, east, west;
    private ArrayList<Observation> observations;
    private String mapParams, rawDuration, shortArea, longArea, sinceString;
    // private String zoomParams;
    // private String panNorthParams, panSouthParams, panEastParams, panWestParams;

    private boolean hasBounds;
    private long since;
    private Double myNorth, mySouth, myEast, myWest;

    public ObsDemoBean()
    {
        areaId = -1;
        north = south = east = west = 0.0;
        observations = new ArrayList<Observation>();
        since = 0L;
        mapParams = rawDuration = shortArea = longArea = sinceString = null;
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
        since = System.currentTimeMillis() -
            Math.abs(d.getSeconds() * 1000 + d.getNano() / 1000000);

        /* get specified bounds, if they exist */
        HttpSession sess = req.getSession();
        myNorth = getDouble(req, "north");
        mySouth = getDouble(req, "south");
        myEast = getDouble(req, "east");
        myWest = getDouble(req, "west");

        /* if bounds were not completely specified, (a) it doesn't count, and
           (b) we wipe the session bounds clean, else use them to limit the
           bounds we just got fed */
        boolean hasBounds = false;
        if (myNorth == null || mySouth == null || myEast == null || myWest == null) {
            myNorth = mySouth = myEast = myWest = null;
            sess.removeAttribute("north");
            sess.removeAttribute("south");
            sess.removeAttribute("east");
            sess.removeAttribute("west");
        } else {
            hasBounds = true;
            north = (double) sess.getAttribute("north");
            myNorth = Math.min(north, myNorth);
            south = (double) sess.getAttribute("south");
            mySouth = Math.max(south, mySouth);
            east = (double) sess.getAttribute("east");
            if (LatLong.eastOf(myEast, east))
                myEast = east;
            west = (double) sess.getAttribute("west");
            if (LatLong.westOf(myWest, west))
                myWest = west;
        }

        /* everythiong else requires a database connection, so... */
        try (Connection conn = getConnection()) {
            return processWithConnection(req, resp, conn);
        } catch (NamingException|SQLException e) {
            throw new ServletException(e);
        }
    }

    /* XXX: this duplicates logic in GetMap.java and ObsDemo.java */
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
            sinceString = dFormat.format(new Date(since));
            termLat = rs.getDouble(6);
            termLong = rs.getDouble(7);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get terminal location", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get terminal location)");
            return false;
        }

        /* get some starting bounds if we need them */
        if (!hasBounds) {
            myNorth = termLat + 0.125;
            mySouth = termLat - 0.125;
            myEast = LatLong.normalizeLong(termLong + 0.125);
            myWest = LatLong.normalizeLong(termLong - 0.125);
        }

        /* read in stuff from database and possibly determine map extents */
        long first = -1L, last = -1L;
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
                if (hasBounds) {
                    if (latitude > myNorth || latitude < mySouth || LatLong.westOf(longitude, myWest) || LatLong.eastOf(longitude, myEast)) {
                        continue;
                    }
                } else {
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
                observations.add(new Observation(latitude, longitude, id, details));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to read observations", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to read observations)");
            return false;
        }

        /* get mapParams */
        mapParams = String.format("?from=%d&to=%d&area=%d", first, last, areaId);
        if (hasBounds)
            mapParams = String.format("%s&south=%f&west=%f&north=%f&east=%f", mapParams, mySouth, myWest, myNorth, myEast);

        /* get pixel addresses */
        double[] extents = new double[] { mySouth, myWest, myNorth, myEast };
        int[] size = new int[] { GetMap.PIXELS, GetMap.PIXELS };
        Map dummy = Map.withSize(extents, size, null);
        for (Observation o : observations) {
            o.setY(dummy.latToPixel(o.getLatitude()));
            o.setX(dummy.longToPixel(o.getLongitude()));
        }
        return true;
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

    private Double getDouble(HttpServletRequest req, String name)
    {
        String raw = req.getParameter(name);
        if (raw == null)
            return null;
        return new Double(raw);
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
}