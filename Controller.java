import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
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
    ArrayList<InetAddress> forwarders = new ArrayList<InetAddress>();
    
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

                if(type.equals(CON_FORWARDER))
                {
					if(forwarders.contains(packet.getAddress()))
					{
						System.out.println("Forwarder already connected to controller");
					}
					else
					{
                    	System.out.println("Adding" + packet.getAddress() + " to list of forwarders.");
						forwarders.add(packet.getAddress());
					}

                    DatagramPacket ack;
					String val = T_MESSAGE + "3ACK";
                    ack= new TLVPacket("1", "1", val).toDatagramPacket();
                    ack.setSocketAddress(packet.getSocketAddress());
                    socket.send(ack);
                }
                else if(type.equals(CON_ENDPOINT))
                {
					HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();
					Connection connect = new Connection(tlvs.get(T_CONTAINER), packet.getAddress());
					
					if(inConnections(connect, connections))
					{
						if(tlvs.containsKey(T_MESSAGE))
						{
							int removeID = removeID(connect, connections);
							System.out.println("Removing connection between " + packet.getAddress() + " and " + tlvs.get(T_CONTAINER));
							connections.remove(removeID);
						}
						else
							System.out.println("Connection already recorded by controller, can't add.");
					}
					else
					{
						if(tlvs.containsKey(T_PORT))
						{
							System.out.println("Adding connection between " + packet.getSocketAddress() + " and " + ((TLVPacket) content).getPacketEncoding());
							connections.add(connect);
						}
						else	
							System.out.println("Connection not in controllers table, can't delete.");
					}
                }
                else
                {
                    System.out.println("Not expected receive.");
                }
				
			}
			else
			{
				System.out.println("WARNING: UNSUPPORTED PACKET TYPE");
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

	public static int removeID(Connection c, ArrayList<Connection> connections)
	{
		for(int i = 0; i < connections.size(); i++)
		{
			if(c.isEqual(connections.get(i)))
				return i;
		}
		return -1;
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
