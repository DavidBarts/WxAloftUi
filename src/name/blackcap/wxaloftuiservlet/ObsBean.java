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
 * Backs the obs.jsp (JS-based slippy map observations) page.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class ObsBean
{
    private static final Logger LOGGER = Logger.getLogger(ObsBean.class.getCanonicalName());
    private static final SimpleDateFormat LOCAL_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /* maybe put these in a common file? or do we want separate defaults? */
    private static final String DEFAULT_DURATION = "PT2H";
    private static final long MAX_DURATION = 6L * 60L * 60L * 1000L;

    private int areaId;
    private String longArea, shortArea, duration, sinceString;
    private long since;

    public ObsBean()
    {
        areaId = -1;
        since = 0L;
        longArea = shortArea = duration = null;
    }

    public boolean processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        /* get and parse the optional since parameter */
        duration = req.getParameter("since");
        if (duration == null)
            duration = DEFAULT_DURATION;
        Duration d = null;
        try {
            d = Duration.parse(duration);
        } catch (DateTimeParseException e) {
            LOGGER.log(Level.SEVERE, "Invalid duration", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid duration)");
            return false;
        }
        long millis = d.getSeconds() * 1000L + d.getNano() / 1000000;
        if (millis > MAX_DURATION) {
            LOGGER.log(Level.INFO, "Duration too long!");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (excessive duration)");
            return false;
        }
        since = System.currentTimeMillis() - millis;

        /* everythiong else requires a database connection, so... */
        try (Connection conn = getConnection()) {
            return processWithConnection(req, resp, conn);
        } catch (NamingException|SQLException e) {
            throw new ServletException(e);
        }
    }

    public boolean processWithConnection(HttpServletRequest req, HttpServletResponse resp, Connection conn) throws IOException
    {
        /* get the mandatory area (and from that time zone to use) */
        String area = req.getParameter("area");
        if (area == null) {
            LOGGER.log(Level.SEVERE, "Missing area= parameter");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (missing area= parameter)");
            return false;
        }
        try {
            areaId = Integer.parseInt(area);
            try (PreparedStatement stmt = conn.prepareStatement("select id, name, city, region, country, timezone from areas where id = ?")) {
                stmt.setInt(1, areaId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    setAreaParams(rs);
                } else {
                    LOGGER.log(Level.SEVERE, "Unknown area ID " + areaId);
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (unknown area ID)");
                    return false;
                }
            } catch (SQLException e2) {
                LOGGER.log(Level.SEVERE, "Unable to resolve area", e2);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to resolve area)");
                return false;
            }
        } catch (NumberFormatException e) {
            try (PreparedStatement stmt = conn.prepareStatement("select id, name, city, region, country, timezone from areas where name = ?")) {
                stmt.setString(1, area);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    setAreaParams(rs);
                } else {
                    LOGGER.log(Level.SEVERE, "Unknown area name " + area);
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (unknown area name)");
                    return false;
                }
            } catch (SQLException e2) {
                LOGGER.log(Level.SEVERE, "Unable to resolve area", e2);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to resolve area)");
                return false;
            }
        }
        
        return true;
    }

    private void setAreaParams(ResultSet rs) throws SQLException
    {
        areaId = rs.getInt("id");
        shortArea = rs.getString("name");
        longArea = String.format("%s, %s, %s", rs.getString("city"),
            rs.getString("region"), rs.getString("country"));
        SimpleDateFormat dFormat = (SimpleDateFormat) LOCAL_TIME.clone();
        dFormat.setTimeZone(TimeZone.getTimeZone(rs.getString("timezone")));
        sinceString = dFormat.format(new java.util.Date(since));
    }

    private Connection getConnection() throws NamingException, SQLException
    {
        Context c = (Context) (new InitialContext()).lookup("java:comp/env");
        DataSource d = (DataSource) c.lookup("jdbc/WxDB");
        return d.getConnection();
    }

    public int getAreaId()
    {
        return areaId;
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

    public String getDuration()
    {
        return duration;
    }
}