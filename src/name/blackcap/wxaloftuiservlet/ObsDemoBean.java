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

    private static final String DEFAULT_DURATION = "P2H";
    private int areaId;
    private double north, south, east, west;
    private ArrayList<Observation> observations;
    private String mapParams, rawDuration;
    // private String zoomParams;
    // private String panNorthParams, panSouthParams, panEastParams, panWestParams;

    private boolean hasBounds;
    private long since;

    public ObsDemoBean()
    {
        areaId = -1;
        north = south = east = west = 0.0;
        observations = new ArrayList<ObservationData>();
        since = 0L;
        mapParams = rawDuration = null;
    }

    public boolean processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        /* get and parse the optional since parameter */
        rawDuration = req.getParameter("since");
        if (rawDuration == null) {
            rawDuration = DEFAULT_DURATION;
        Duration d = null;
        try {
            d = Duration.parse(rawDuration);
        } catch (DateTimeParseException e) {
            Logger.log(Level.SEVERE, "Invalid duration", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid duration)");
            return false;
        }
        since = System.currentTimeMillis() -
            Math.abs(d.getSeconds() * 1000 + d.getNano() / 1000000);

        /* get specified bounds, if they exist */
        Session sess = req.getSession();
        Double myNorth = getDouble(req, "north");
        Double mySouth = getDouble(req, "south");
        Double myEast = getDouble(req, "east");
        Double myWest = getDouble(req, "west");

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
            north = (double) sess.getAttribute(north);
            myNorth = Math.min(north, myNorth);
            south = (double) sess.getAttribute(south);
            mySouth = Math.max(south, mySouth);
            east = (double) sess.getAttribute(east);
            if (LatLong.eastOf(myEast, east))
                myEast = east;
            west = (double) sess.getAttribute(west);
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
            LOGGER.log(level.SEVERE, "Missing area= parameter");
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
        double termLatitude = 0.0, termLongitude = 0.0;
        try (PreparedStatement stmt = conn.prepareStatement("select latitude, longitude, timezone from areas where id = ?")) {
            stmt.setInt(1, areaId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                LOGGER.log(Level.SEVERE, String.format("Area ID %s unknown", areaId));
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (area ID unknown)");
                return;
            }
            termLat = rs.getDouble(1);
            termLong = rs.getDouble(2);
            dFormat.setTimeZone(TimeZone.getTimeZone(rs.getString(3)));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get terminal location", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get terminal location)");
            return;
        }

        /* get some starting bounds if we need them */
        if (!hasBounds) {
            myNorth = termLat + 0.125;
            mySouth = termLat - 0.125;
            myEast = LatLong.normalizeLong(termLong + 0.125);
            myWest = LatLong.normalizeLong(termLong - 0.125);
        }

        /* read in stuff from database and possibly determine map extents */
        int firstId = 0, lastId = 0;
        try (PreparedStatement stmt = conn.prepareStatement("select observations.id, observations.received, observations.observed, observations.frequency, observations.altitude, observations.wind_speed, observations.wind_dir, observations.temperature, observations.source, observations.latitude, observations.longitude from observations join obs_area on observations.id = obs_area.observation_id where observations.observed > ? and obs_area.area_id = ? order by observations.id asc")) {
            stmt.setTimestamp(1, new Timestamp(since));
            stmt.setInt(2, areaId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt(1);
                to = id;
                if (from == 0)
                    from = id;
                Date received = new Date(rs.getTimestamp(2).getTime());
                Date observed = new Date(rs.getTimestamp(3).getTime());
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
                boolean skip = hasBounds && ;
                if (hasBounds) {
                    if (latitude > myNorth || latitude < mySouth || LatLong.westOf(longitude, myWest) || latLong.eastOf(longitude, myEast)) {
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
                String details = String.join("\r", new String[] {
                    listIt("Altitude", altitude, " ft"),
                    listIt("Latitude", latitude, "°"),
                    listIt("Longitude", longitude, "°"),
                    listIt("Temperature", temperature, "°C"),
                    listIt("Wind direction", wind_dir, "°"),
                    listIt("Wind speed", wind_speed, " kn"),
                    listIt("Time observed", dFormat.format(observed), ""),
                    listIt("Time received", dFormat.format(received), ""),
                    listIt("Frequency", obs[i].frequency, " MHz"),
                    listIt("Source", obs[i].source, "") });
                observations.add(new Observation(latitude, longitude, id, details));
            }
        }

        /* get mapParams */
        mapParams = String.format("?from=%d&to=%d&area=%d", firstId, lastId, areaId);
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
    }

    function listIt(String name, Object value, String suffix)
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

    public new List<Observation>() getObservations()
    {
        return observations;
    }
}