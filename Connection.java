import java.net.SocketAddress;

public class Connection
{
    String endpointName;
    SocketAddress forwarder;
    
    Connection(String endpointName, SocketAddress forwarder)
    {
        this.endpointName = endpointName;
        this.forwarder = forwarder;
    }

    public String getEndpoint()
    {
        return endpointName;
    }

    public SocketAddress getForwarder()
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
