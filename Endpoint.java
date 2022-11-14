import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.HashMap;

public class Endpoint extends Node
{
    static final int DEFAULT_SRC_PORT = 50001;
	static final int DEFAULT_DST_PORT = 54321;
    static final String DEFAULT_DST_NODE = "forwarderone";

    InetSocketAddress dstAddress;
	Terminal endpointTerminal;

    /**
	 * Constructor
	 *
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	Endpoint(Terminal t, String dstHost, int dstPort, int srcPort) {
		try {
			endpointTerminal = t;
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
			endpointTerminal.println("Received packet");

			PacketContent content= PacketContent.fromDatagramPacket(packet);

			if (content.getType()==PacketContent.TLVPACKET) {
				String type = ((TLVPacket)content).getPacketT();
				HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();

				if(type.equals(ACK_PACKET))
				{
					endpointTerminal.println("From Forwarder: " + tlvs.get(T_MESSAGE));
					this.notify();
				}
				else if(type.equals(MESSAGE_PACKET)) //receiving message from somewhere
				{

					endpointTerminal.println("From " + tlvs.get(T_SENDER_NAME) + ": " + tlvs.get(T_MESSAGE));
					this.notify();
				}
			}
		}
		catch(Exception e) {e.printStackTrace();}
	}

    public synchronized void start() throws Exception {
		endpointTerminal.println("Welcome!");
		while (true) 
		{
			endpointTerminal.println("Type 'receive' to receive packets, 'send' to send, 'quit' to quit.");

			String userInput = endpointTerminal.read("Type Here");
			if(userInput != null && userInput.equals("quit"))
			{
				endpointTerminal.println("Disconnecting from network");
				DatagramPacket disconnect;
				String val = T_MESSAGE + "3DIS" + T_CONTAINER + aliasLength + containerAlias;
				disconnect= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				disconnect.setSocketAddress(dstAddress);
				socket.send(disconnect);
				this.wait();
				System.exit(0);
			}
			else if(userInput != null && userInput.equals("receive"))
			{
				endpointTerminal.println("Connecting to this network's Forwarder");
				DatagramPacket connectReceive;
				String val = T_PORT + "550001" + T_CONTAINER + aliasLength + containerAlias;
				connectReceive= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				connectReceive.setSocketAddress(dstAddress);
				socket.send(connectReceive);

				this.wait();
				this.wait();
			}
			else if(userInput != null && userInput.equals("send"))
			{
				endpointTerminal.println("Connecting to this network's Forwarder");
				DatagramPacket connectSend;
				String val = T_MESSAGE + "3SEN" + T_CONTAINER + aliasLength + containerAlias;
				connectSend= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				connectSend.setSocketAddress(dstAddress);
				socket.send(connectSend);

				this.wait();

				endpointTerminal.println("Enter the name of the container the endpoint you want to send to is on.");
				String containerName;
				while(true)
				{
					containerName = endpointTerminal.read("Type Name Here");
					if(containerName == null || containerName.length() <= 3)
						endpointTerminal.println("Invalid Response");
					else
						break;
				}
				endpointTerminal.println("Enter the message to send. Message cannot begin with a number.");
				String message;
				while(true)
				{
					message = endpointTerminal.read("Type Message Here");
					if(message == null || message.length() < 1)
						endpointTerminal.println("Invalid Response");
					else
						break;
				}
				String containerLength = Integer.toString(containerName.length()); 
				String messageLength = Integer.toString(message.length()); 

				DatagramPacket messageSend;
				val = T_MESSAGE + messageLength + message + T_DEST_NAME + containerLength + containerName + T_SENDER_NAME + aliasLength + containerAlias;
				messageSend = new TLVPacket(MESSAGE_PACKET,"3",val).toDatagramPacket();
				messageSend.setSocketAddress(dstAddress);
				socket.send(messageSend);

				this.wait(5000);
			}
			else
			{
				endpointTerminal.println("Not a valid input.");
			}
		}
	}

    public static void main(String[] args) {
		try {
			Terminal endpointTerminal = new Terminal("Endpoint");
			(new Endpoint(endpointTerminal, DEFAULT_DST_NODE, DEFAULT_DST_PORT, DEFAULT_SRC_PORT)).start();
			endpointTerminal.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}