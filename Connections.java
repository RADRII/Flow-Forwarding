import java.util.ArrayList;

public class Connections
{
    ArrayList<Connection> list;
    Connections()
    {
        list = new ArrayList<Connection>();
    }

    public int contains(String query)
    {
        for(int i = 0; i < list.size(); i++)
        {
            if(list.get(i).getOrigin().equals(query))
                return i;
        }
        return -1;
    }

    public ArrayList<String> getAllByOrigin(String origin)
    {
        int index = this.contains(origin);
        if(index == -1)
            return null;
        
        return list.get(index).getConnections();
    }

    public void addConnection(String origin, String connectedTo)
    {
        int index = this.contains(origin);
        if(index != -1)
        {
            list.get(index).addConnection(connectedTo);
        }
        else
        {
            Connection toAdd = new Connection(origin);
            toAdd.addConnection(connectedTo);
            list.add(toAdd);
        }
    }

    public void removeConnection(String origin, String connectedTo)
    {
        int index = this.contains(origin);
        if(index == -1)
        {
           System.out.println("WARNING: " + origin + " and " + connectedTo + " were never connected, cannot remove");
        }
        else
        {
            list.get(index).removeConnection(connectedTo);
        }
    }
}




class Connection
{
    String origin;
    ArrayList<String> forwardersOrEndpoints;
    
    Connection(String fOrN)
    {
        origin = fOrN;
        forwardersOrEndpoints = new ArrayList<String>();
    }

    public String getOrigin()
    {
        return origin;
    }

    public ArrayList<String> getConnections()
    {
        return forwardersOrEndpoints;
    }

    public void addConnection(String connection)
    {
        if(this.isConnectedTo(connection) == -1)
            forwardersOrEndpoints.add(connection);
    }

    public void removeConnection(String connection)
    {
        if(this.isConnectedTo(connection) != -1)
            forwardersOrEndpoints.remove(connection);
    }

    public int numConnections()
    {
        return forwardersOrEndpoints.size();
    }

    public boolean isEqual(Connection c)
    {
        if(this.origin.equalsIgnoreCase(c.getOrigin()) && this.forwardersOrEndpoints.equals(c.getConnections()))
            return true;
        return false;
    }

    public int isConnectedTo(String query)
    {
        for(int i = 0; i < forwardersOrEndpoints.size(); i++)
        {
            if(forwardersOrEndpoints.get(i).equals(query))
                return i;
        }
        return -1;
    }
}
