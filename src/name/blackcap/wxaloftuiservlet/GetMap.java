package name.blackcap.wxaloftuiservlet;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import name.blackcap.acarsutils.AcarsObservation;

import static name.blackcap.wxaloftuiservlet.WorldPixel.*;

/**
 * Retrieve a weather observations map, based on area of interest,
 * observation times, and a given bounds and zoom level.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class GetMap extends HttpServlet {
    private static final long serialVersionUID = -812260914481768455L;

    private static final Logger LOGGER = Logger.getLogger(GetMap.class.getCanonicalName());
    public static final int PIXELS = 640;
    public static final int LIMIT = 25;
    public static final int RADIUS = 4;

    /**
     * Process a GET request by returning all appropriate observations.
     * @param req     HttpServletRequest
     * @param resp    HttpServletResponse
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try (Connection conn = getConnection()) {
            doGetWithConnection(req, resp, conn);
        } catch (NamingException|SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to obtain database connection", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to obtain DB connection)");
            return;
        }
    }

    private void doGetWithConnection(HttpServletRequest req, HttpServletResponse resp, Connection conn) throws IOException
    {
        /* get (mandatory) database bounds */
        Long from = null, to = null;
        try {
            from = getLong(req, "from");
            to = getLong(req, "to");
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid integer)");
            return;
        }
        if (from == null || to == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (missing from= or to= parameter)");
            return;
        }

        /* get the mandatory area and translate it into a numeric ID */
        String area = req.getParameter("area");
        if (area == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (missing area= parameter)");
            return;
        }
        int areaId = -1;
        try {
            areaId = Integer.parseInt(area);
        } catch (NumberFormatException e) {
            try (PreparedStatement stmt = conn.prepareStatement("select id from areas where name = ?")) {
                stmt.setString(1, area);
                ResultSet rs = stmt.executeQuery();
                if (rs.next())
                    areaId = rs.getInt(1);
                else {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid area)");
                    return;
                }
            } catch (SQLException e2) {
                LOGGER.log(Level.SEVERE, "Unable to resolve area", e2);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to resolve area)");
                return;
            }
        }

        /* get the mandatory bounds and zoom level */
        Integer north = null, south = null, east = null, west = null, zoom = null;
        try {
            north = getInteger(req, "north");
            south = getInteger(req, "south");
            east = getInteger(req, "east");
            west = getInteger(req, "west");
            zoom = getInteger(req, "zoom");
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid integer)");
            return;
        }
        if (north == null || south == null || east == null || west == null || zoom == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (bounds or zoom not specified)");
            return;
        }

        /* get bounds and zoom limits from session (also mandatory) */
        HttpSession sess = req.getSession();
        Integer northLimit = (Integer) sess.getAttribute("north");
        Integer southLimit = (Integer) sess.getAttribute("south");
        Integer eastLimit = (Integer) sess.getAttribute("east");
        Integer westLimit = (Integer) sess.getAttribute("west");
        Integer zoomLimit = (Integer) sess.getAttribute("zoom");
        if (northLimit == null || southLimit == null || eastLimit == null || westLimit == null || zoomLimit == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (incomplete session)");
            return;
        }

        /* reject requests with invalid bounds or zoom */
        boolean invalidBounds =
            northOf(toZoom(north, zoom, zoomLimit), northLimit) ||
            southOf(toZoom(south, zoom, zoomLimit), southLimit) ||
            eastOf(toZoom(east, zoom, zoomLimit), eastLimit, zoomLimit) ||
            westOf(toZoom(west, zoom, zoomLimit), westLimit, zoomLimit);
        boolean invalidZoom = zoom < zoomLimit || zoom > MAXZOOM;
        if (invalidBounds || invalidZoom) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid bounds or zoom)");
            return;
        }

        /* reject requests for overly large maps */
        if (south - north > PIXELS || eastFrom(west, east, zoom) > PIXELS) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (map too large)");
            return;
        }

        /* get the data to plot */
        ArrayList<AcarsObservation> obs = new ArrayList<AcarsObservation>();
        try (PreparedStatement stmt = conn.prepareStatement("select observations.latitude, observations.longitude, observations.altitude, observations.observed from observations join obs_area on observations.id = obs_area.observation_id where observations.observed >= ? and observations.observed <= ? and obs_area.area_id = ?")) {
            stmt.setTimestamp(1, new Timestamp(from));
            stmt.setTimestamp(2, new Timestamp(to));
            stmt.setInt(3, areaId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next())
                obs.add(new AcarsObservation(rs.getDouble(1), rs.getDouble(2), rs.getInt(3), new Date(rs.getTimestamp(4).getTime())));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to get observations", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get observations)");
            return;
        }

        /* get cache directory and a provider */
        String cachePath = getServletContext().getInitParameter("cache");
        if (cachePath == null) {
            LOGGER.log(Level.SEVERE, "No cache defined!");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (no cache defined)");
            return;
        }
        LimitingTileProvider p = new LimitingTileProvider(LIMIT,
            new CachingTileProvider(new File(cachePath), new OsmTileProvider()));

        /* OK, finally ready to generate a map */
        Map m = new Map(south, west, north, east, zoom, p);
        BufferedImage image = null;
        try {
            image = m.getImage();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to create map", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to create map)");
            return;
        }
        Graphics2D g = null;
        try {
            g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            final int DIAMETER = RADIUS * 2;
            final int OFFSET = RADIUS;
            for (AcarsObservation o : obs) {
                int x = m.longToPixel(o.getLongitude()) - OFFSET;
                if (badX(image, x) || badX(image, x + DIAMETER))
                    continue;
                int y = m.latToPixel(o.getLatitude()) - OFFSET;
                if (badY(image, y) || badY(image, y + DIAMETER))
                    continue;
                g.setColor(getColor(o.getAltitude()));
                g.fillOval(x, y, DIAMETER, DIAMETER);
                g.setColor(Color.BLACK);
                g.drawOval(x, y, DIAMETER, DIAMETER);
            }

            /* now return it */
            resp.setStatus(200);
            resp.setContentType("image/png");
            OutputStream out = resp.getOutputStream();
            ImageIO.write(image, "png", out);
            out.flush();
        } finally {
            if (g != null)
                g.dispose();
            image.flush();
        }
    }

    private boolean badX(RenderedImage image, int x)
    {
        return x < 0 || x >= image.getWidth();
    }

    private boolean badY(RenderedImage image, int y)
    {
        return y < 0 || y >= image.getHeight();
    }

    private Color getColor(int altitude)
    {
        final int MIN = 0;
        final int MAX = 40000;
        altitude = Math.min(MAX, Math.max(MIN, altitude));
        int mid = (MIN + MAX) / 2;
        if (altitude <= mid) {
            int red = (int) (255.0 * (double) altitude / (double) mid);
            return new Color(red, 255, 0);
        } else {
            int green = (int) (255.0 * (1.0 - (double) (altitude - mid) / (double) mid));
            return new Color(255, green, 0);
        }
    }

    private Integer getInteger(HttpServletRequest req, String name)
    {
        String raw = req.getParameter(name);
        if (raw == null)
            return null;
        return new Integer(raw);
    }

    private Long getLong(HttpServletRequest req, String name)
    {
        String raw = req.getParameter(name);
        if (raw == null)
            return null;
        return new Long(raw);
    }

    private Connection getConnection() throws NamingException, SQLException {
        Context c = (Context) (new InitialContext()).lookup("java:comp/env");
        DataSource d = (DataSource) c.lookup("jdbc/WxDB");
        return d.getConnection();
    }
}
