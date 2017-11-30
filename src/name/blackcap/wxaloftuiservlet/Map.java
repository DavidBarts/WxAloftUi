package name.blackcap.wxaloftuiservlet;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A raster-based map that uses tiles from the OSM tile server.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class Map {

    // Our extents
    private double south, west, north, east;
    // Pixels per degree (almost never the same in the two directions)
    private double ewScale, nsScale;
    // Image of the map
    BufferedImage image;
    // Pessimistic estimate of width and height in tiles we will need
    private int width, height;
    // First (and presumably representative) tile we generate, currently
    // the NW corner of the image.
    private Tile start;
    // Width and height in degrees of the first tile.
    private double tdwidth, tdheight;
    // Width and height of this map in degrees
    private double dwidth, dheight;
    
    /**
     * A raster-based map, with extents specified by latitude and longitude,
     * and the specified minimum resolutions, using the specified provider
     * to obtain tiles. Maps render lazily, and it is allowed to pass a null
     * tile provider (in which case, the map can only be used for retrieving
     * the basic properties of a map).
     *
     * @param south     Southernmost extent
     * @param west      Westernmost extent
     * @param north     Northernmost extent
     * @param east      Easternmost extent
     * @param minX      Minimum number of pixels in east/west direction
     * @param munY      Minimum number of pixels in north/south direction
     * @param p         TileProvider to use
     */
    public Map(double south, double west, double north, double east, int minX, int minY, TileProvider p)
    {
        this.south = south;
        this.west = west;
        this.north = north;
        this.east = east;

        // Get starting tile
        int zoom = Tile.calcZoom(south, west, north, east, minX, minY);
        start = Tile.forLatLong(north, west, zoom, p);

        // Calculate various size-related parameters
        final int SLOP = 2;
        dheight = north - south;
        dwidth = LatLong.eastFrom(west, east);
        tdheight = start.north() - start.south();
        height = (int) Math.ceil(dheight / tdheight) + SLOP;
        tdwidth = LatLong.eastFrom(start.west(), start.east());
        width = (int) Math.ceil(dwidth / tdwidth) + SLOP;

        // Calculate scaling factors
        ewScale = (double) Tile.SIZE / tdwidth;
        nsScale = (double) Tile.SIZE / tdheight;
    }

    /**
     * Get the image representing the map. It is allowed to draw on the
     * image.
     *
     * @return          BufferedImage object.
     */
    public BufferedImage getImage() throws IOException
    {
        if (image == null)
            image = makeImage();
        return image;
    }

    private BufferedImage makeImage() throws IOException
    {
        // Make a raw image
        BufferedImage rawImage = new BufferedImage(Tile.SIZE*width, Tile.SIZE*height, BufferedImage.TYPE_INT_RGB);
        Graphics g = rawImage.getGraphics();

        // Tile it
        Tile y = start;
        int xi = 0, yi = 0;
        do {
            g.drawImage(y.getImage(), xi, yi, null);
            Tile x = y;
            while (LatLong.westOf(x.east(), east)) {
                x = x.eastTile();
                xi += Tile.SIZE;
                g.drawImage(x.getImage(), xi, yi, null);
            }
            xi = 0;
            yi += Tile.SIZE;
            y = y.southTile();
        } while (y != null && LatLong.northOf(y.north(), south));

        // Crop it generate our image
        int cnorth = (int) (nsScale * (start.north() - north));
        int cwest = (int) (ewScale * LatLong.eastFrom(start.west(), west));
        int cwidth = (int) (ewScale * dwidth);
        int cheight = (int) (nsScale * dheight);
        return rawImage.getSubimage(cwest, cnorth, cwidth, cheight);
    }

    /**
     * Translate latitude to pixel coordinate. Does not sanity-check its
     * argument.
     *
     * @param           Latitude
     * @return          Pixel coordinate
     */
    public int latToPixel(double latitude)
    {
        return (int) (nsScale * (north - latitude));
    }

    /**
     * Translate longitude to pixel coordinate. Does not sanity-check its
     * argument.
     *
     * @param           Longitude
     * @return          Pixel coordinate
     */
    public int longToPixel(double longitude)
    {
        return (int) (ewScale * LatLong.eastFrom(west, longitude));
    }

    /**
     * Get westmost longitude.
     *
     * @return          Longitude
     */
    public double west()
    {
        return west;
    }

    /**
     * Get eastmost longitude.
     *
     * @return          Longitude
     */
    public double east()
    {
        return east;
    }

    /**
     * Get northmost latitude.
     *
     * @return          Latitude.
     */
    public double north()
    {
        return north;
    }

    /**
     * Get southmost latitude.
     *
     * @return          Latitude.
     */
    public double south()
    {
        return south;
    }
}
