package name.blackcap.wxaloftuiservlet;

/**
 * Contains the observations in ObsDemoBean (and probably a couple other
 * backing beans, eventually).
 *
 * @author David Barts <n5jrn@me.com>
 */
public class Observation
{
    private int id, x, y;
    private double latitude, longitude;
    private String details;

    public Observations(double lat, double lon, int id, String details)
    {
        latitude = lat;
        longitude = lon;
        this.id = id;
        this.details = details;
        x = y = -1;
    }

    public double getLatitude()
    {
        return latitude;
    }

    public void setLatitude(double value)
    {
        latitude = value;
    }

    public double getLongitude()
    {
        return longitude;
    }

    public void setLongitude(double value)
    {
        longitude = value;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int value)
    {
        id = value;
    }

    public String getDetails()
    {
        return details;
    }

    public void setDetails(String value)
    {
        details = value;
    }

    public int getX()
    {
        return x;
    }

    public void setX(int value)
    {
        x = value;
    }

    public int getY()
    {
        return y;
    }

    public void setY(int value)
    {
        y = value;
    }
}