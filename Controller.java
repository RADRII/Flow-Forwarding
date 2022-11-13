import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * Forwarder class
 *
 * An instance accepts user input
 *
 */
public class Controller extends Node {
	static final int DEFAULT_SRC_PORT = 50000;

    ArrayList<Connection> connections = new ArrayList<Connection>();
    ArrayList<SocketAddress> forwarders = new ArrayList<SocketAddress>();
    
	/**
	 * Constructor
	 *
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	Controller(int srcPort) {
		try {
			socket= new DatagramSocket(srcPort);
			listener.go();
		}
		catch(java.lang.Exception e) {e.printStackTrace();}
	}


	/**
	 * Assume that incoming packets contain a String and print the string.
	 */
	public synchronized void onReceipt(DatagramPacket packet) {
		try {
			System.out.println("Received packet");

			PacketContent content= PacketContent.fromDatagramPacket(packet);

			if (content.getType()==PacketContent.TLVPACKET) 
            {
                String type = ((TLVPacket)content).getPacketT();
                if(type.equals("7"))
                {
					if(forwarders.contains(packet.getSocketAddress()))
					{
						System.out.println("Forwarder already connected to controller");
					}
					else
					{
                    	System.out.println("Adding" + packet.getSocketAddress() + " to list of forwarders.");
						forwarders.add(packet.getSocketAddress());
					}
                    DatagramPacket ack;
                    ack= new TLVPacket("5", "3", "ACK").toDatagramPacket();
                    ack.setSocketAddress(packet.getSocketAddress());
                    socket.send(ack);
                }
                else if(type.equals("8"))
                {
					Connection toAdd = new Connection(((TLVPacket)content).getPacketEncoding(), packet.getSocketAddress());
					if(inConnections(toAdd, connections))
					{
						System.out.println("Connection already recorded by controller");
					}
					else
					{
						System.out.println("Adding connection between " + packet.getSocketAddress() + " and " + ((TLVPacket) content).getPacketEncoding());
						connections.add(toAdd);
					}
                }
                else if(type.equals("6"))
                {
					Connection toRemove = new Connection(((TLVPacket)content).getPacketEncoding(), packet.getSocketAddress());
					if(!inConnections(toRemove, connections))
					{
						System.out.println("Connection was never in forwarding table.");
					}
					else
					{
						System.out.println("Removing connection between " + packet.getSocketAddress() + " and " + ((TLVPacket) content).getPacketEncoding());
						connections.remove(toRemove);
					}
                }
                else
                {
                    System.out.println("Not expected receive.");
                }
				
			}
		}
		catch(Exception e) {e.printStackTrace();}
	}


	/**
	 * Sender Method
	 *
	 */
	public synchronized void start() throws Exception 
    {
        System.out.println("~Waiting for Contact~");
		this.wait();
	}

	public static boolean inConnections(Connection c, ArrayList<Connection> connections)
	{
		for(int i = 0; i < connections.size(); i++)
		{
			if(c.isEqual(connections.get(i)))
				return true;
		}
		return false;
	}

	public static HashMap<Integer,String> readEncoding(int howMany, String encoding)
	{
		HashMap<Integer,String> toReturn = new HashMap<Integer,String>();

		for(int i = 0; i < howMany; i++)
		{
			Integer type =  Character.getNumericValue(encoding.charAt(0));
			Integer length =  Character.getNumericValue(encoding.charAt(1));

			String val;
			if(2+length > encoding.length())
				val = encoding.substring(2);
			else
				val = encoding.substring(2,2+length);

			toReturn.put(type,val);

			if(i != howMany-1)
				encoding = encoding.substring(2+length);
		}
		return toReturn;
	}

	/**
	 * Test method
	 *
	 * Sends a packet to a given address
	 */
	public static void main(String[] args) {
		try {
			(new Controller(DEFAULT_SRC_PORT)).start();
			System.out.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
