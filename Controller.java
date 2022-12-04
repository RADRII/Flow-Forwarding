import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.io.IOException;
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
	static final int DEFAULT_DST_PORT = 54321;

	Graph networksToF = new Graph();
	Graph routingTable = new Graph();
	Graph droppedPackets = new Graph();
    
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

					System.out.println(forwarderName + " is on " + (length-1) + " networks:");

					for(int i = 0; i < length-1; i++)
					{
						Integer l =  Character.getNumericValue(encoding.charAt(1));
						int beginIndex = 2;

						if(l == 0)
							break;

						String network = encoding.substring(beginIndex, beginIndex+l);
						networksToF.addLink(network, forwarderName);

						System.out.println(network);

						if(i != length-1)
							encoding = encoding.substring(beginIndex+l);
					}

					updateRoutingTable(forwarderName);

					System.out.println("Sending ACK to " + forwarderName);

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

					if(!tlvs.containsKey(T_PORT))
					{
						routingTable.removeAllByEnd(tlvs.get(T_CONTAINER));
					}
					else if(tlvs.containsKey(T_PORT))
					{
						System.out.println("Adding connection between " + forwarderName + " and " + tlvs.get(T_CONTAINER));
						routingTable.addLink(forwarderName, tlvs.get(T_CONTAINER));

						//Check if theres a dropped packet with the new connected endpoint as a destination (but not the forwarder who sent the message)
						ArrayList<String> forwardersForUpdate = droppedPackets.getAllByEnd(tlvs.get(T_CONTAINER));
						if(forwardersForUpdate.size() > 0) 
						{
							System.out.println("Checking if a path for previously dropped packets exists now.");
							String dest = tlvs.get(T_CONTAINER);
							for(int i = 0; i < forwardersForUpdate.size(); i++)
							{
								String forwarder = forwardersForUpdate.get(i);

								if(forwarder.equals(forwarderName))
								{
									droppedPackets.removeLink(forwarder, dest);
								}
								else
								{
									ArrayList<String> traverse = new ArrayList<String>();
									ArrayList<String> passed = new ArrayList<String>();
									ArrayList<String> hops = getHops(forwarder, dest, passed, traverse);

									if(hops == null)
									{
										System.out.println("Path from " + forwarder + " and " + dest + " still not found. Continue storing.");
									}
									else
									{
										System.out.println(dest + " to " + forwarder + " found in flow table, sending hops and removing from dropped packets list.");
										int length = 1;
										String val = T_DEST_NAME + Integer.toString(dest.length()) + dest;
										for(int j = 0; j < hops.size(); j++)
										{
											String currentHop = hops.get(j);
											val = val + T_CONTAINER + Integer.toString(currentHop.length()) + currentHop;
											length++;
										}

										DatagramPacket flowRes= new TLVPacket(FLOW_CONTROL_RES, Integer.toString(length), val).toDatagramPacket();
										InetSocketAddress forwarderAddress = new InetSocketAddress(forwarder, DEFAULT_DST_PORT);
										flowRes.setSocketAddress(forwarderAddress);
										try {
											socket.send(flowRes);
										} catch (IOException e) {
											e.printStackTrace();
										}
										droppedPackets.removeLink(forwarder, dest);
									}
								}
							}
						}
						System.out.println("Finished checking dropped packets, sending Ack to Connecting Forwarder.");
						this.wait(3000); //waiting three seconds for backlogged messages to be transferred 

						DatagramPacket ack;
						String val = T_MESSAGE + "3ACK";
						ack= new TLVPacket(ACK_PACKET,"1", val).toDatagramPacket();
						ack.setSocketAddress(packet.getSocketAddress());
						socket.send(ack);
					}
					else
					{
						System.out.println("WARNING: Faulty table request");
					}
                }
				else if(type.equals(FORWARDER_LIST_REQ))
				{
					System.out.println(tlvs.get(T_CONTAINER) + " requesting relavent forwarders.");
					//taking ip address of endpoint and subtracting the last two digits to get network ip
					String ip = packet.getAddress().toString();
					ip = ip.substring(0,ip.length()-2);

					//getting all forwarders that have the same base ip
					ArrayList<String> forwarderOnIP = getForwardersOnIP(ip);
					System.out.println("Found " + forwarderOnIP.size() + " relavent forwarders: ");

					DatagramPacket forwarderList;
					String val = "";
					for(int i = 0; i < forwarderOnIP.size(); i++)
					{
						val = val + T_CONTAINER + Integer.toString(forwarderOnIP.get(i).length()) + forwarderOnIP.get(i);
						System.out.println(forwarderOnIP.get(i));
					}
					System.out.println("Sending list of forwarders to " + tlvs.get(T_CONTAINER));
					
                    forwarderList= new TLVPacket(FORWARDER_LIST, Integer.toString(forwarderOnIP.size()), val).toDatagramPacket();
                    forwarderList.setSocketAddress(packet.getSocketAddress());
                    socket.send(forwarderList);
				}
				else if(type.equals(FLOW_CONTROL_REQ))
                {
                    System.out.println("Looking for " + tlvs.get(T_DEST_NAME) + " in forwarding table.");
					ArrayList<String> traverse = new ArrayList<String>();
					ArrayList<String> passed = new ArrayList<String>();
					ArrayList<String> hops = getHops(tlvs.get(T_CONTAINER), tlvs.get(T_DEST_NAME), passed,traverse);

					DatagramPacket flowRes;
					if(hops == null)
					{
						System.out.println("Path from " + tlvs.get(T_CONTAINER) + " and " + tlvs.get(T_DEST_NAME) + " not found. Storing until path is found.");
						droppedPackets.addLink(tlvs.get(T_CONTAINER), tlvs.get(T_DEST_NAME));
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
		ArrayList<String> toReturn = networksToF.getAllByEnd(epID);

		if(toReturn.size() < 1)
		{
			System.out.println("WARNING: " + epID + " had no forwarders.");
			return new ArrayList<String>();
		}

		return toReturn;
	}

	//Returns 1 if found ep, 0 if not, true return is path array though. DFS
	public synchronized ArrayList<String> getHops(String forwarderOrigin, String destination, ArrayList<String> passed,  ArrayList<String> hops)
	{
		passed.add(forwarderOrigin);
		if(forwarderOrigin.equals(destination))
		{
			hops.remove(forwarderOrigin);
			return new ArrayList<String>(hops);
		}
		
		ArrayList<String> neighbors = routingTable.getAllByEnd(forwarderOrigin);
		neighbors.removeAll(passed);

		ArrayList<String> toReturn = null;
		for(int i = 0; i < neighbors.size(); i++)
		{
			if(!passed.contains(neighbors.get(i)))
			{
				hops.add(neighbors.get(i));
				ArrayList<String> newRecursion = getHops(neighbors.get(i), destination, passed, hops);
				if(toReturn == null)
					toReturn = newRecursion;
				else if(toReturn != null && newRecursion != null && toReturn.size() > newRecursion.size())
					toReturn = new ArrayList<String>(newRecursion);
				hops.remove(neighbors.get(i));

				if(toReturn != null)
					passed.remove(neighbors.get(i));
			}
		}
		return toReturn;
	}

	public void updateRoutingTable(String fAdded)
	{
		ArrayList<String> networksFOn = networksToF.getAllByEnd(fAdded);
		for(int i = 0; i < networksFOn.size(); i++)
		{
			ArrayList<String> forwardersOnNetwork = networksToF.getAllByEnd(networksFOn.get(i));
			for(int j = 0; j < forwardersOnNetwork.size(); j++)
			{
				if(!forwardersOnNetwork.get(j).equals(fAdded))
					routingTable.addLink(fAdded, forwardersOnNetwork.get(j));
			}
		}
		
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
