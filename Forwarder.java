import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
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
    static boolean waitingForBacklog;
    SocketAddress waiting;

    HashMap<String, String> forwardingTable = new HashMap<String, String>();
    HashMap<DatagramPacket, String> droppedPackets = new HashMap<DatagramPacket, String>();
    
	/**
	 * Constructor
	 *
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	Forwarder(String dstHost, int dstPort, int srcPort) {
		try {
            waitingForBacklog = false;
            waiting = null;

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
                HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();

				if(type.equals(ACK_PACKET))
				{
					System.out.println("From Controller: " + tlvs.get(T_MESSAGE));
                    if(tlvs.get(T_MESSAGE).equals("FIN"))
                    {
                        String waitingEndpoint = ((InetSocketAddress) waiting).getHostName();
                        System.out.println("Sending message backlog (if any) to " + waitingEndpoint);
                        sendBackLogs(0,waitingEndpoint, "", waitingEndpoint);

                        DatagramPacket ack;
                        String val = T_MESSAGE + "3ACK";
                        ack= new TLVPacket(ACK_PACKET,"1", val).toDatagramPacket();
                        ack.setSocketAddress(waiting);

                        System.out.println("Sending ACK to " + waitingEndpoint);
                        socket.send(ack);

                        waiting = null;
                        waitingForBacklog = false;
                    }
				}
                else if(type.equals(CON_ENDPOINT))
                {
                    if(tlvs.containsKey(T_PORT))
                    {
                        System.out.println("Adding " + tlvs.get(T_CONTAINER) + " to forwarding table of forwarder");
                        forwardingTable.put(tlvs.get(T_CONTAINER), tlvs.get(T_PORT));

                        System.out.println("Informing Controller, waiting for backlogged messages to come through.");
                        packet.setSocketAddress(dstAddress);
                        socket.send(packet);

                        waitingForBacklog = true;
                        waiting = packet.getSocketAddress();
                    }
                    else
                    {
                        if(forwardingTable.containsKey(tlvs.get(T_CONTAINER)))
                        {
                            forwardingTable.remove(tlvs.get(T_CONTAINER));
                            
                            System.out.println("Informing Controller of removal.");
                            packet.setSocketAddress(dstAddress);
                            socket.send(packet);
                        }

                        System.out.println("Sending ACK to " + tlvs.get(T_CONTAINER));
                        DatagramPacket ack;
                        String val = T_MESSAGE + "3ACK";
                        ack= new TLVPacket(ACK_PACKET,"1", val).toDatagramPacket();
                        ack.setSocketAddress(packet.getSocketAddress());
                        socket.send(ack);
                    }
                }
                else if(type.equals(MESSAGE_PACKET))
                {
                    if(forwardingTable.containsKey(tlvs.get(T_DEST_NAME)))
                        {
                            if(waitingForBacklog == false)
                                System.out.println(tlvs.get(T_DEST_NAME) + " found in forwarding table - Sending packet.");
                            else
                                System.out.println("Backlog message received for " + tlvs.get(T_DEST_NAME) + ", sending on.");

                            String containerNameEP = tlvs.get(T_DEST_NAME);
                            InetAddress ip = InetAddress.getByName(containerNameEP); 	
                            InetSocketAddress currentDstAddress = new InetSocketAddress(ip, Integer.parseInt(forwardingTable.get(containerNameEP)));
                            
                            packet.setSocketAddress(currentDstAddress);
                            socket.send(packet);

                            if(waitingForBacklog == false)
                            {
                                System.out.println("Sending ACK to " + tlvs.get(T_DEST_NAME));
                                DatagramPacket ack;
                                String val = T_MESSAGE + "3FIN";
                                ack= new TLVPacket(ACK_PACKET,"1", val).toDatagramPacket();
                                ack.setSocketAddress(currentDstAddress);
                                socket.send(ack);

                                //REMOVE CONNECRTION AFTER SINCE ENDPOINT WILL STOP RECEIVING
                                System.out.println("Removing Connection");
                                forwardingTable.remove(containerNameEP);

                                DatagramPacket connectSend;
                                val = T_MESSAGE + "3DIS" + T_CONTAINER + Integer.toString(containerNameEP.length()) + containerNameEP;
                                connectSend= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
                                connectSend.setSocketAddress(dstAddress);
                                socket.send(connectSend);
                            }
                        }
                    else if(tlvs.containsKey(T_CONTAINER))
                    {
                        ArrayList<String> encodings = ((TLVPacket)content).readEncodingList();
                        String hopName = encodings.get(3);

                        //Creating new val which is the same encoding but minus the next hop 
                        String val = "";
                        for(int i = 0; i < encodings.size(); i++)
                        {
                            String currentV = encodings.get(i);
                            if(i == 0)
                            {
                                val = val + T_MESSAGE + Integer.toString(currentV.length()) + currentV;
                            }
                            else if(i == 1)
                            {
                                val = val + T_DEST_NAME + Integer.toString(currentV.length()) + currentV;
                            }
                            else if(i == 2)
                            {
                                val = val + T_SENDER_NAME + Integer.toString(currentV.length()) + currentV;
                            }
                            else if(i > 3)
                            {
                                val = val + T_CONTAINER + Integer.toString(currentV.length()) + currentV;
                            }
                        }

                        System.out.println("Hop encoded in message, forwarding packet via " + hopName);

                        DatagramPacket forwardMessage;
                        Integer l = Integer.parseInt(((TLVPacket)content).getPacketLength()) - 1;
                        forwardMessage = new TLVPacket(MESSAGE_PACKET, Integer.toString(l), val).toDatagramPacket();

                        InetSocketAddress forwarderAddress = new InetSocketAddress(hopName, DEFAULT_SRC_PORT);
                        forwardMessage.setSocketAddress(forwarderAddress);
                    }
                    else
                    {
                        droppedPackets.put(packet, tlvs.get(T_DEST_NAME));  

                        System.out.println(tlvs.get(T_DEST_NAME) + " not in this forwarders forwarding table, informing controller of need for path.");
                        int destNameLength = tlvs.get(T_DEST_NAME).length();
                        String val = T_DEST_NAME + destNameLength + tlvs.get(T_DEST_NAME) + T_CONTAINER + aliasLength + containerAlias;
                        DatagramPacket flowRequest= new TLVPacket(FLOW_CONTROL_REQ, "2", val).toDatagramPacket();
                        flowRequest.setSocketAddress(dstAddress);
                        socket.send(flowRequest);
                    }
                }
                else if(type.equals(FLOW_CONTROL_RES))
                {
                    System.out.println("Received path for " + tlvs.get(T_DEST_NAME));

                    String hops = ((TLVPacket)content).getPacketEncoding(); 
                    Integer length =  Character.getNumericValue(hops.charAt(1));
                    //removing destination name from hops encoding
                    hops = hops.substring(2+length);
                    //Getting name of next hop and removing from encoding
                    Integer hopNameL = Character.getNumericValue(hops.charAt(1));
                    String hopName;
                    if(2+hopNameL > hops.length())
                    {
                        hopName = hops.substring(2);
                        hops = "";
                    }
                    else
                    {
                        hopName = hops.substring(2, 2+hopNameL);
                        hops = hops.substring(2+hopNameL);
                    }

                    //need to declare final because java was complaining
                    final String trueHops = hops;

                    sendBackLogs(Integer.parseInt(((TLVPacket)content).getPacketLength())-2, tlvs.get(T_DEST_NAME), trueHops, hopName);
                }
                else
                {
                    System.out.println("WARNING: PACKET TYPE NOT HANDLED BY FORWARDER");
                }
            }
            else
            {
                System.out.println("WARNING: UNKNOWN PACKET TYPE");
            }
		}
		catch(Exception e) {e.printStackTrace();}
	}

    public synchronized void sendBackLogs(int hopL, String destName, String trueHops, String hopName)
    {
        ArrayList<DatagramPacket> toRemove = new ArrayList<DatagramPacket>();
        droppedPackets.forEach((datagram, dest) -> 
        {
            if(dest.equals(destName))
            {
                PacketContent toModify = PacketContent.fromDatagramPacket(datagram);
                HashMap<String,String> mod = ((TLVPacket)toModify).readEncoding();

                DatagramPacket forwardMessage;
                String val = ((TLVPacket)toModify).getPacketEncoding() + trueHops;
                Integer l = Integer.parseInt(((TLVPacket)toModify).getPacketLength()) + hopL;
                forwardMessage = new TLVPacket(MESSAGE_PACKET, Integer.toString(l), val).toDatagramPacket();

                System.out.println("Forwarding message " + mod.get(T_MESSAGE) + " from " + mod.get(T_SENDER_NAME) + " via " + hopName);

                InetSocketAddress forwardAddress;
                if(forwardingTable.get(hopName) != null)
                {
                    forwardAddress = new InetSocketAddress(hopName, Integer.parseInt(forwardingTable.get(hopName)));
                }
                else
                    forwardAddress = new InetSocketAddress(hopName, DEFAULT_SRC_PORT);
                forwardMessage.setSocketAddress(forwardAddress);

                toRemove.add(datagram);
                try {
                    socket.send(forwardMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //removing previously dropped packets that were sent
        for(int i = 0; i < toRemove.size(); i++)
        {
            droppedPackets.remove(toRemove.get(i));
        }
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
        String val =  T_CONTAINER + aliasLength + containerAlias;
        int length = 1;

        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets))
        {
            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                String address = inetAddress.toString();
                address = address.substring(0, address.length()-2);

                if(!address.equals(BRIDGE_NET_IP) && !address.equals(HOST_NET_IP))
                {
                    val = val + T_NETWORK + Integer.toString(address.length()) + address;
                    length++;
                }
            }
        }

        connectSend= new TLVPacket(CON_FORWARDER, Integer.toString(length), val).toDatagramPacket();
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
