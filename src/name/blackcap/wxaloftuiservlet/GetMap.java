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

/**
 * Retrieve a weather observations map, based on observation ID's and possibly
 * lat/long bounds. In order to prevent this service from being abused as a
 * general-purpose map source, the latter are only allowed if an existing
 * session contains maximum bounds and if they are within those bounds.
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

        /* get optional output size */
        String rawSize = req.getParameter("size");
        int maxSize = PIXELS;
        if (rawSize != null) {
            boolean bad = false;
            try {
                maxSize = Integer.parseInt(rawSize);
                bad = maxSize < 256 || maxSize > 1024;
            } catch (NumberFormatException e) {
                bad = true;
            }
            if (bad) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid size)");
            }
        }

        /* get the optional bounds */
        Double north = null, south = null, east = null, west = null;
        try {
            north = getDouble(req, "north");
            south = getDouble(req, "south");
            east = getDouble(req, "east");
            west = getDouble(req, "west");
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (invalid floating-point number)");
            return;
        }
        boolean hasBounds = false;
        HttpSession sess = req.getSession();
        if (north != null && south != null && east != null && west != null) {
            Double northLimit = (Double) sess.getAttribute("north");
            Double southLimit = (Double) sess.getAttribute("south");
            Double eastLimit = (Double) sess.getAttribute("east");
            Double westLimit = (Double) sess.getAttribute("west");
            if (northLimit == null || southLimit == null || eastLimit == null || westLimit == null) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden (no valid active session)");
                return;
            }
            /* if any bounds are out of bounds, we draw the original map */
            boolean outOfBounds = LatLong.northOf(north, northLimit) ||
                LatLong.southOf(north, southLimit) ||
                LatLong.northOf(south, northLimit) ||
                LatLong.southOf(south, southLimit) ||
                LatLong.eastOf(east, eastLimit) ||
                LatLong.westOf(east, westLimit) ||
                LatLong.eastOf(west, eastLimit) ||
                LatLong.westOf(west, westLimit);
            if (outOfBounds) {
                north = northLimit;
                south = southLimit;
                east = eastLimit;
                west = westLimit;
            }
            hasBounds = true;
        } else if (north != null || south != null || east != null || west != null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request (incomplete bounds specified)");
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

        /* set bounds, if needed */
        if (!hasBounds) {
            /* we start with bounds of 7'30" from the terminal in each direction */
            double termLat = 0.0, termLong = 0.0;
            try (PreparedStatement stmt = conn.prepareStatement("select latitude, longitude from areas where id = ?")) {
                stmt.setInt(1, areaId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    LOGGER.log(Level.SEVERE, String.format("Area ID %s unknown", areaId));
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (area ID unknown)");
                    return;
                }
                termLat = rs.getDouble(1);
                termLong = rs.getDouble(2);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Unable to get terminal location", e);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error (unable to get terminal location)");
                return;
            }
            north = termLat + 0.125;
            south = termLat - 0.125;
            east = LatLong.normalizeLong(termLong + 0.125);
            west = LatLong.normalizeLong(termLong - 0.125);

            /* then we expand them to include observations */
            for (AcarsObservation o : obs) {
                if (LatLong.northOf(o.getLatitude(), north))
                    north = o.getLatitude();
                if (LatLong.southOf(o.getLatitude(), south))
                    south = o.getLatitude();
                if (LatLong.eastOf(o.getLongitude(), east))
                    east = o.getLongitude();
                if (LatLong.westOf(o.getLongitude(), west))
                    west = o.getLongitude();
            }

            /* add a tad more so no observation is ever right on the edge */
            double margin = 0.05 * Math.max(north-south, LatLong.eastFrom(west, east));
            north += margin;
            south -= margin;
            east = LatLong.normalizeLong(east + margin);
            west = LatLong.normalizeLong(west - margin);
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
        double[] bounds = new double[] { south, west, north, east };
        int[] size = new int[] { maxSize, maxSize };
        Map m = Map.withSize(bounds, size, p);
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
            }

            /* make note of the map extents if this is the first time we've
               made a map this session */
            if (!hasBounds) {
                sess.setAttribute("north", north);
                sess.setAttribute("south", south);
                sess.setAttribute("east", east);
                sess.setAttribute("west", west);
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

    private Double getDouble(HttpServletRequest req, String name)
    {
        String raw = req.getParameter(name);
        if (raw == null)
            return null;
        return new Double(raw);
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
