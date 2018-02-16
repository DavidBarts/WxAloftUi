package name.blackcap.wxaloftuiservlet;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import static name.blackcap.wxaloftuiservlet.WorldPixel.*;

/**
 * Backs the obst.jsp (tabular or disabled-friendly observations) page.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class ObstBean
{
    private static final Logger LOGGER = Logger.getLogger(ObstBean.class.getCanonicalName());
    private static final SimpleDateFormat LOCAL_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /* maybe put these in a common file? or do we want separate defaults? */
    private static final String DEFAULT_DURATION = "PT2H";
    private static final long MAX_DURATION = 6L * 60L * 60L * 1000L;
    private static final String DEFAULT_SORT_BY = "observed";
    private static final String DEFAULT_SORT_ORDER = "asc";

    private int areaId;
    private String rawDuration, sortBy, sortOrder;
    private String sinceString, shortArea, longArea;
    private long since;
    private HashMap<String, String> columns;
    private ArrayList<Map> rows;

    public ObstBean()
    {
        areaId = -1;
        sortBy = sortOrder = shortArea = longArea = null;
        since = 0L;
        columns = new HashMap<String, String>();
        rows = new ArrayList<Map>();
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

        /* get column names */
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            /* XXX - Catalog name is database in MySQL and PostgreSQL but not
               most others. Alas the stupid JDBC has no standard mapping between
               calls to getColumns and database names. */
            ResultSet rawColumns = metaData.getColumns("wx_aloft", null, "observations", null);
            while (rawColumns.next())
                columns.put(rawColumns.getString("COLUMN_NAME").toLowerCase(), null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get columns", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get columns)");
            return false;
        }

        /* get the optional order parameter */
        String rawOrder = req.getParameter("order");
        if (rawOrder == null) {
            /* default ordering */
            sortBy = DEFAULT_SORT_BY;
            sortOrder = DEFAULT_SORT_ORDER;
        } else {
            int blank = rawOrder.indexOf(' ');
            if (blank == -1 || blank == rawOrder.length()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid order)");
                return false;
            }
            sortBy = rawOrder.substring(0, blank);
            sortOrder = rawOrder.substring(blank + 1);
            if (!columns.containsKey(sortBy) || (!"asc".equals(sortOrder) && !"desc".equals(sortOrder))) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid order)");
                return false;
            }
        }

        /* build query and column links */
        StringBuilder query = new StringBuilder("select");
        boolean needsComma = false;
        for (String key: columns.keySet()) {
            columns.put(key, getColLink(key));
            if (needsComma)
                query.append(',');
            else
                needsComma = true;
            query.append(" observations.");
            query.append(key);
        }
        query.append(" from observations join obs_area on observations.id = obs_area.observation_id where observations.observed > ? and obs_area.area_id = ? order by observations.");
        query.append(sortBy);
        query.append(' ');
        query.append(sortOrder);

        /* get table data */
        try (PreparedStatement stmt = conn.prepareStatement(query.toString())) {
            stmt.setTimestamp(1, new Timestamp(since));
            stmt.setInt(2, areaId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                HashMap<String, Object> row = new HashMap<String, Object>();
                for (String key: columns.keySet()) {
                    Object obj = rs.getObject("observations." + key);
                    if (rs.wasNull())
                        row.put(key, "(missing)");
                    else if (obj instanceof Timestamp)
                        row.put(key, dFormat.format((java.util.Date) obj));
                    else
                        row.put(key, obj);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get observations", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get observations)");
            return false;
        }

        return true;
    }

    private String getColLink(String column)
    {
        String newOrder = null;
        if (column.equals(sortBy)) {
            switch (sortOrder) {
            case "asc":
                newOrder = "desc";
                break;
            case "desc":
                newOrder = "asc";
                break;
            default:
                throw new RuntimeException("invalid sortOrder!");
            }
        } else {
            newOrder = DEFAULT_SORT_ORDER;
        }
        return String.format("obst.jsp?area=%s&since=%s&order=%s+%s",
            shortArea, rawDuration, column, newOrder);
    }

    private Connection getConnection() throws NamingException, SQLException
    {
        Context c = (Context) (new InitialContext()).lookup("java:comp/env");
        DataSource d = (DataSource) c.lookup("jdbc/WxDB");
        return d.getConnection();
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

    public String getOrder()
    {
        return sortBy + " " + sortOrder;
    }

    public HashMap<String, String> getColumns()
    {
        return columns;
    }

    public List<Map> getRows()
    {
        return rows;
    }
}
