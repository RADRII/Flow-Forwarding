import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * Forwarder class
 *
 * An instance accepts user input
 *
 */
public class Forwarder extends Node {
	static final int DEFAULT_SRC_PORT = 54321;
    static final int DEFAULT_DST_PORT = 50000;
	static final String DEFAULT_DST_NODE = "controller";
	InetSocketAddress dstAddress;

    DatagramSocket socketTwo;

    HashMap<String, String> forwardingTable = new HashMap<String, String>();
    ArrayList<DatagramPacket> droppedPackets = new ArrayList<DatagramPacket>();
    
	/**
	 * Constructor
	 *
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	Forwarder(String dstHost, int dstPort, int srcPort) {
		try {
			dstAddress= new InetSocketAddress(dstHost, dstPort);
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

			if (content.getType()==PacketContent.TLVPACKET) {

				String type = ((TLVPacket)content).getPacketT();

				if(type.equals(ACK_PACKET))
				{
					System.out.println("From Controller: " + ((TLVPacket)content).getPacketEncoding());
				}
                else if(type.equals(CON_ENDPOINT))
                {
                    HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();
                    if(tlvs.containsKey(T_PORT))
                    {
                        System.out.println("Adding endpoint to forwarding table of forwarder");
                        forwardingTable.put(tlvs.get(T_CONTAINER), tlvs.get(T_PORT));

                        System.out.println("Informing Controller");
                        packet.setSocketAddress(dstAddress);
                        socket.send(packet);

                        System.out.println("Sending ACK to endpoint");
                        DatagramPacket ack;
                        String val = T_MESSAGE + "3ACK";
                        ack= new TLVPacket(ACK_PACKET,"1", val).toDatagramPacket();
                        ack.setSocketAddress(packet.getSocketAddress());
                        socket.send(ack);
                    }
                    else
                    {
                        if(forwardingTable.containsKey(tlvs.get(T_CONTAINER)))
                        {
                            forwardingTable.remove(tlvs.get(T_CONTAINER));
                            
                            System.out.println("Informing Controller");
                            packet.setSocketAddress(dstAddress);
                            socket.send(packet);
                        }

                        System.out.println("Sending ACK to endpoint");
                        DatagramPacket ack;
                        String val = T_MESSAGE + "3ACK";
                        ack= new TLVPacket(ACK_PACKET,"1", val).toDatagramPacket();
                        ack.setSocketAddress(packet.getSocketAddress());
                        socket.send(ack);
                    }
                }
                else if(type.equals(MESSAGE_PACKET))
                {
                    HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();

                    if(forwardingTable.containsKey(tlvs.get(T_DEST_NAME)))
                        {
                            String containerNameEP = tlvs.get(T_DEST_NAME);
                            InetAddress ip = InetAddress.getByName(containerNameEP); 	
                            InetSocketAddress currentDstAddress = new InetSocketAddress(ip, Integer.parseInt(forwardingTable.get(containerNameEP)));
                            packet.setSocketAddress(currentDstAddress);
                            socket.send(packet);

                            //REMOVE CONNECRTION AFTER SINCE ENDPOINT MIGHT STOP RECEIVING
                            System.out.println("Removing Connection");
                            forwardingTable.remove(containerNameEP);

                            DatagramPacket connectSend;
                            String val = T_CONTAINER + Integer.toString(containerNameEP.length()) + containerNameEP;
                            connectSend= new TLVPacket(CON_ENDPOINT, "2",  val).toDatagramPacket();
                            connectSend.setSocketAddress(dstAddress);
                            socket.send(connectSend);
                        }
                        else
                        {
                            //PACKETS ARE DROPPED FOR NOW
                            //IMPLEMENT FLOWTABLE UPDATES LATER
                            droppedPackets.add(packet);  
                            System.out.println("NOT IN FORWARDING TABLE");
                        }
                }
            }
            else
            {
                System.out.println("WARNING: UNKNOWN PACKET TYPE");
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
		//Connect to Controller
        System.out.println("Connecting to Controller");
        DatagramPacket connectSend;
        String val =  T_MESSAGE + "3CON";
        connectSend= new TLVPacket(CON_FORWARDER, "1", val).toDatagramPacket();
        connectSend.setSocketAddress(dstAddress);
        socket.send(connectSend);

        System.out.println("~Waiting for Contact~");
		this.wait();
	}

	/**
	 * Test method
	 *
	 * Sends a packet to a given address
	 */
	public static void main(String[] args) {
		try {
			(new Forwarder(DEFAULT_DST_NODE, DEFAULT_DST_PORT, DEFAULT_SRC_PORT)).start();
			System.out.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
