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
				if(type.equals("5")) //ack from controller
				{
					System.out.println("From Controller: " + ((TLVPacket)content).getPacketEncoding());
				}
				else if(type.equals("1"))
				{
					int length  = Integer.parseInt(((TLVPacket)content).getPacketLength());
					String encoding = ((TLVPacket)content).getPacketEncoding();
					HashMap<Integer,String> tlvs = readEncoding(length,encoding);

                    if(tlvs.containsKey(4) && tlvs.get(4).equals("REC"))
                    {
                        System.out.println("Adding endpoint to forwarding table of forwarder");
                        forwardingTable.put(tlvs.get(6), tlvs.get(3));

                        System.out.println("Informing Controller");
                        DatagramPacket connectSend;
                        connectSend= new TLVPacket("8", Integer.toString(tlvs.get(6).length()),  tlvs.get(6)).toDatagramPacket();
                        connectSend.setSocketAddress(dstAddress);
                        socket.send(connectSend);

                        System.out.println("Sending ACK to endpoint");
                        DatagramPacket ack;
                        ack= new TLVPacket("5","3", "ACK").toDatagramPacket();
                        ack.setSocketAddress(packet.getSocketAddress());
                        socket.send(ack);
                    }
                    else if (tlvs.containsKey(4) && tlvs.get(4).equals("DIS"))
                    {
                        if(forwardingTable.containsKey(tlvs.get(6)))
                        {
                            forwardingTable.remove(tlvs.get(6));
                            System.out.println("Informing Controller");
                            DatagramPacket connectSend;
                            connectSend= new TLVPacket("6", Integer.toString(tlvs.get(6).length()),  tlvs.get(6)).toDatagramPacket();
                            connectSend.setSocketAddress(dstAddress);
                            socket.send(connectSend);
                        }

                        System.out.println("Sending ACK to endpoint");
                        DatagramPacket ack;
                        ack= new TLVPacket("5","3", "ACK").toDatagramPacket();
                        ack.setSocketAddress(packet.getSocketAddress());
                        socket.send(ack);
                    }
                    else if(tlvs.containsKey(4) && tlvs.get(4).equals("SEN"))
                    {
                        if(forwardingTable.containsKey(tlvs.get(6)))
                        {
                            forwardingTable.remove(tlvs.get(6));
                            System.out.println("Informing Controller");
                            DatagramPacket connectSend;
                            connectSend= new TLVPacket("6", Integer.toString(tlvs.get(6).length()),  tlvs.get(6)).toDatagramPacket();
                            connectSend.setSocketAddress(dstAddress);
                            socket.send(connectSend);
                        }

                        System.out.println("Sending ACK to endpoint");
                        DatagramPacket ack;
                        ack= new TLVPacket("5","3", "ACK").toDatagramPacket();
                        ack.setSocketAddress(packet.getSocketAddress());
                        socket.send(ack);
                    }
                    else if(tlvs.containsKey(5)) //Message to forward
                    {
                        if(forwardingTable.containsKey(tlvs.get(6)))
                        {
                            InetAddress ip = InetAddress.getByName(tlvs.get(6)); 	
                            System.out.println(ip);
                            InetSocketAddress currentDstAddress = new InetSocketAddress(ip, Integer.parseInt(forwardingTable.get(tlvs.get(6))));
                            packet.setSocketAddress(currentDstAddress);
                            socket.send(packet);

                            //REMOVE CONNECRTION AFTER SINCE ENDPOINT MIGHT STOP RECEIVING
                            System.out.println("Removing Connection");
                            forwardingTable.remove(tlvs.get(6));
                            DatagramPacket connectSend;
                            connectSend= new TLVPacket("6", Integer.toString(tlvs.get(6).length()),  tlvs.get(6)).toDatagramPacket();
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
                    else
                    {
                        System.out.println("Not a valid packet to receive.");
                    }
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
		//Connect to Controller
        System.out.println("Connecting to Controller");
        DatagramPacket connectSend;
        connectSend= new TLVPacket("7", "3", "CON").toDatagramPacket();
        connectSend.setSocketAddress(dstAddress);
        socket.send(connectSend);

        System.out.println("~Waiting for Contact~");
		this.wait();
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
			(new Forwarder(DEFAULT_DST_NODE, DEFAULT_DST_PORT, DEFAULT_SRC_PORT)).start();
			System.out.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
