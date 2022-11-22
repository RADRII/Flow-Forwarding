import java.net.DatagramSocket;
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

    Connections forwardersE = new Connections();
	Connections networksF = new Connections();
    
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
			HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();

			if (content.getType()==PacketContent.TLVPACKET) 
            {
                String type = ((TLVPacket)content).getPacketT();

                if(type.equals(CON_FORWARDER))
                {
					int length = Integer.parseInt(((TLVPacket)content).getPacketLength());
					String encoding = ((TLVPacket)content).getPacketEncoding();
					String forwarderName = encoding.substring(2, 2+Character.getNumericValue(encoding.charAt(1)));
					encoding = encoding.substring(2+Character.getNumericValue(encoding.charAt(1)));

					System.out.println(forwarderName + " is on " + (length-1) + " networks. Sending ACK");

					for(int i = 0; i < length-1; i++)
					{
						Integer l =  Character.getNumericValue(encoding.charAt(1));
						int beginIndex = 2;

						if(l == 0)
							break;

						String network = encoding.substring(beginIndex, beginIndex+l);
						networksF.addConnection(network,forwarderName);

						if(i != length-1)
							encoding = encoding.substring(beginIndex+l);
					}

                    DatagramPacket ack;
					String val = T_MESSAGE + "3ACK";
                    ack= new TLVPacket(ACK_PACKET, "1", val).toDatagramPacket();
                    ack.setSocketAddress(packet.getSocketAddress());
                    socket.send(ack);
                }
                else if(type.equals(CON_ENDPOINT))
                {
					String forwarderName = packet.getAddress().getHostName();
					forwarderName = forwarderName.substring(0,forwarderName.indexOf('.')); //removing netid from end of hostname
					int forwarderIndex = forwardersE.contains(forwarderName);

					if(forwarderIndex != -1 && !tlvs.containsKey(T_PORT))
					{
						forwardersE.removeAllByConnection(tlvs.get(T_CONTAINER));
					}
					else if(tlvs.containsKey(T_PORT))
					{
						System.out.println("Adding connection between " + forwarderName + " and " + tlvs.get(T_CONTAINER));
						forwardersE.addConnection(forwarderName,tlvs.get(T_CONTAINER));
					}
					else
					{
						System.out.println("WARNING: Faulty table request");
					}
                }
				else if(type.equals(FORWARDER_LIST_REQ))
				{
					System.out.println(tlvs.get(T_CONTAINER) + " requesting relvent forwarders.");
					//taking ip address of endpoint and subtracting the last two digits to get network ip
					String ip = packet.getAddress().toString();
					ip = ip.substring(0,ip.length()-2);

					//getting all forwarders that have the same base ip
					ArrayList<String> forwarderOnIP = getForwardersOnIP(ip);
					System.out.println("Found " + forwarderOnIP.size() + " relavent forwarders, sending names.");

					DatagramPacket forwarderList;
					String val = "";
					for(int i = 0; i < forwarderOnIP.size(); i++)
					{
						val = val + T_CONTAINER + Integer.toString(forwarderOnIP.get(i).length()) + forwarderOnIP.get(i);
					}
                    forwarderList= new TLVPacket(FORWARDER_LIST, Integer.toString(forwarderOnIP.size()), val).toDatagramPacket();
                    forwarderList.setSocketAddress(packet.getSocketAddress());
                    socket.send(forwarderList);
				}
				else if(type.equals(FLOW_CONTROL_REQ))
                {
                    System.out.println("Looking for " + tlvs.get(T_DEST_NAME) + " in forwarding table.");
					ArrayList<String> hops = new ArrayList<String>();
					ArrayList<String> passed = new ArrayList<String>();
					getHops(tlvs.get(T_CONTAINER), tlvs.get(T_DEST_NAME), passed, hops);

					DatagramPacket flowRes;
					if(hops.size() < 1)
					{
						//todo ADD FUTURE CHECKS WHEN NEW EDNPOINTS ADDED
						System.out.println(tlvs.get(T_DEST_NAME) + " not in flow table. Packet Dropped.");
					}
					else
					{
						System.out.println(tlvs.get(T_DEST_NAME) + " found in flow table, sending hops.");
						int length = 1;
						String val = T_DEST_NAME + Integer.toString(tlvs.get(T_DEST_NAME).length()) + tlvs.get(T_DEST_NAME);
						for(int i = 0; i < hops.size(); i++)
						{
							String currentHop = hops.get(i);
							val = val + T_CONTAINER + Integer.toString(currentHop.length()) + currentHop;
							length++;
						}
						flowRes= new TLVPacket(FLOW_CONTROL_RES, Integer.toString(length), val).toDatagramPacket();
						flowRes.setSocketAddress(packet.getSocketAddress());
						socket.send(flowRes);
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

	public synchronized ArrayList<String> getForwardersOnIP(String epID)
	{
		int netIndex = networksF.contains(epID);

		if(netIndex == -1)
		{
			System.out.println("WARNING: " + epID + " had no forwarders.");
			return new ArrayList<String>();
		}

		return networksF.getAllByOrigin(epID);
	}

	//Returns 1 if found ep, 0 if not, true return is hops array though
	public synchronized int getHops(String forwarderOrigin, String destination, ArrayList<String> passed, ArrayList<String> hops)
	{
		int recursive = 0;
		ArrayList<String> futureChecks = new ArrayList<String>();

		for(int i = 0; i < networksF.size(); i++)
		{
			if(!passed.contains(Integer.toString(i)))
			{
				ArrayList<String> forwardersOnNetwork =  networksF.getAllByOrigin(i);
				if(forwardersOnNetwork.contains(forwarderOrigin))
				{
					forwardersOnNetwork.remove(forwarderOrigin);
					forwardersOnNetwork.removeAll(passed);

					for(int j = 0; j < forwardersOnNetwork.size(); j++)
					{
						String currentForwarder = forwardersOnNetwork.get(j);

						ArrayList<String> endpointsOnForwarders = forwardersE.getAllByOrigin(currentForwarder);
						int epIndex = endpointsOnForwarders.indexOf(destination);
						if(epIndex != -1)
						{
							hops.add(currentForwarder);
							return 1;
						}
						else
						{
							futureChecks.add(currentForwarder);
						}
					}
				}
			}
		}

		for(int i = 0; i < futureChecks.size(); i++)
		{
			passed.add(futureChecks.get(i));
			recursive = getHops(futureChecks.get(i), destination, passed, hops);
			if(recursive == 1)
			{
				hops.add(0, futureChecks.get(i));
				return recursive;
			}
		}
		return recursive;
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
