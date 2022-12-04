import java.util.ArrayList;

public class Graph {
    
    ArrayList<Link> graph;
    Graph()
    {
        graph = new ArrayList<Link>();
    }

    public ArrayList<Link> getGraph()
    {
        return graph;
    }

    public ArrayList<String> getAllByEnd(String origin)
    {
        ArrayList<String> toReturn = new ArrayList<String>();
        for(int i = 0; i < graph.size(); i++)
        {
            Link l = graph.get(i);
            String add = "";
            if(l.start.equals(origin))
                add = l.end;
            else if(l.end.equals(origin))
                add = l.start;

            if(!toReturn.contains(add) && !add.equals(""))
                toReturn.add(add);
        }
        return toReturn;
    }

    public void removeAllByEnd(String origin)
    {
        for(int i = 0; i < graph.size(); i++)
        {
            Link l = graph.get(i);
            if(l.start.equals(origin) || l.end.equals(origin))
                graph.remove(i);
        }
    }

    public int contains(Link link)
    {
        for(int i = 0; i < graph.size(); i++)
        {
            if(graph.get(i).isEqual(link))
                return i;
        }
        return -1;
    }

    public void removeLink(String start, String end)
    {
        Link toAdd = new Link(start, end);
        int index = contains(toAdd);

        if(index != -1)
            graph.remove(index);
    }

    public void addLink(String start, String end)
    {
        Link toAdd = new Link(start, end);
        if(contains(toAdd) == -1)
            graph.add(toAdd);
    }
}

class Link
{
    public String start;
    public String end;

    Link(String start, String end)
    {
        this.start = start;
        this.end = end;
    }

    public boolean isEqual(Link link)
    {
        if(this.start.equals(link.start) && this.end.equals(link.end))
            return true;
        else if(this.start.equals(link.end) && this.end.equals(link.start))
            return true;
        return false;
    }
}