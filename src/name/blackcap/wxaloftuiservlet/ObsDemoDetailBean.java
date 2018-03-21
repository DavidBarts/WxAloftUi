package info.koosah.wxaloftuiservlet;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
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

/**
 * Backs the obs_demo_detail.jsp page.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class ObsDemoDetailBean
{
    private static final Logger LOGGER = Logger.getLogger(ObsDemoDetailBean.class.getCanonicalName());
    private static final SimpleDateFormat LOCAL_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final SimpleDateFormat UTC_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static {
        UTC_TIME.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private int areaId, obsId;
    private String details;

    public boolean processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        /* get and parse the two mandatory parameters */
        String value = req.getParameter("area");
        if (value == null) {
            LOGGER.log(Level.SEVERE, "Missing area= parameter");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (missing area= parameter)");
            return false;
        }
        try {
            areaId = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Invalid area ID", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid area ID)");
            return false;
        }
        value = req.getParameter("id");
        if (value == null) {
            LOGGER.log(Level.SEVERE, "Missing id= parameter");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (missing area= parameter)");
            return false;
        }
        try {
            obsId = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Invalid observation ID", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid observation ID)");
            return false;
        }

        /* everythiong else requires a database connection, so... */
        try (Connection conn = getConnection()) {
            return processWithConnection(req, resp, conn);
        } catch (NamingException|SQLException e) {
            throw new ServletException(e);
        }
    }

    public boolean processWithConnection(HttpServletRequest req, HttpServletResponse resp, Connection conn) throws IOException
    {
        /* get terminal time zone */
        SimpleDateFormat dFormat = (SimpleDateFormat) LOCAL_TIME.clone();
        double termLat = 0.0, termLong = 0.0;
        try (PreparedStatement stmt = conn.prepareStatement("select timezone from areas where id = ?")) {
            stmt.setInt(1, areaId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                LOGGER.log(Level.SEVERE, String.format("Area ID %s unknown", areaId));
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (area ID unknown)");
                return false;
            }
            dFormat.setTimeZone(TimeZone.getTimeZone(rs.getString(1)));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get terminal information", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get terminal location)");
            return false;
        }

        /* read in stuff from database */
        int firstId = -1, lastId = -1;
        try (PreparedStatement stmt = conn.prepareStatement("select received, observed, frequency, altitude, wind_speed, wind_dir, temperature, source, latitude, longitude from observations where id = ?")) {
            stmt.setInt(1, obsId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                LOGGER.log(Level.SEVERE, "Observation ID " + obsId + " unknown!");
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Not found (unknown observation ID)");
                return false;
            }

            /* be careful, some fields can be null (missing) */
            Date received = new Date(rs.getTimestamp(1).getTime());
            Date observed = new Date(rs.getTimestamp(2).getTime());
            double frequency = rs.getDouble(3);
            int altitude = rs.getInt(4);
            Short wind_speed = rs.getShort(5);
            if (rs.wasNull()) wind_speed = null;
            Short wind_dir = rs.getShort(6);
            if (rs.wasNull()) wind_dir = null;
            Float temperature = rs.getFloat(7);
            if (rs.wasNull()) temperature = null;
            String source = rs.getString(8);
            if (rs.wasNull()) source = null;
            double latitude = rs.getDouble(9);
            double longitude = rs.getDouble(10);

            /* \n causes line breaks in <pre> blocks for all browsers */
            details = String.join("\n", new String[] {
                listIt("Altitude", altitude, " ft"),
                listIt("Latitude", latitude, "°"),
                listIt("Longitude", longitude, "°"),
                listIt("Temperature", temperature, "°C"),
                listIt("Wind direction", wind_dir, "°"),
                listIt("Wind speed", wind_speed, " kn"),
                listIt("Time observed", dFormat.format(observed), ""),
                listIt("Time received", dFormat.format(received), ""),
                listIt("Frequency", frequency, " MHz"),
                listIt("Source", source, "") });
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to read observation", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to read observation)");
            return false;
        }
        return true;
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

    public String getDetails()
    {
        return details;
    }
}
