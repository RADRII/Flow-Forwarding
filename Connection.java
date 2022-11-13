import java.net.InetAddress;

public class Connection
{
    String endpointName;
    InetAddress forwarder;
    
    Connection(String endpointName, InetAddress forwarder)
    {
        this.endpointName = endpointName;
        this.forwarder = forwarder;
    }

    public String getEndpoint()
    {
        return endpointName;
    }

    public InetAddress getForwarder()
    {
        return forwarder;
    }

    public boolean isEqual(Connection c)
    {
        if(this.endpointName.equalsIgnoreCase(c.getEndpoint()) && this.forwarder.equals(c.getForwarder()))
            return true;
        return false;
    }
}
