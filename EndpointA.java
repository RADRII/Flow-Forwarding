import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.HashMap;

public class EndpointA extends Node
{
    static final int DEFAULT_SRC_PORT = 50001;
	static final int DEFAULT_DST_PORT = 54321;
    static final String DEFAULT_DST_NODE = "forwarderone";
    InetSocketAddress dstAddress;

	Terminal workerTerminal;

    /**
	 * Constructor
	 *
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	EndpointA(Terminal t, String dstHost, int dstPort, int srcPort) {
		try {
			workerTerminal = t;
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
			workerTerminal.println("Received packet");

			PacketContent content= PacketContent.fromDatagramPacket(packet);

			if (content.getType()==PacketContent.TLVPACKET) {

				String type = ((TLVPacket)content).getPacketT();
				if(type.equals(ACK_PACKET))
				{
					workerTerminal.println("From Forwarder: " + ((TLVPacket)content).getPacketEncoding());
					this.notify();
				}
				else if(type.equals(MESSAGE_PACKET)) //receiving message from somewhere
				{
					HashMap<String,String> tlvs = ((TLVPacket)content).readEncoding();

					workerTerminal.println("From " + tlvs.get(T_SENDER_NAME) + ": " + tlvs.get(T_MESSAGE));
					this.notify();
				}
			}
		}
		catch(Exception e) {e.printStackTrace();}
	}

    public synchronized void start() throws Exception {
		workerTerminal.println("Welcome!");
		while (true) 
		{
			workerTerminal.println("Type 'receive' to receive packets, 'send' to send, 'quit' to quit.");

			String userInput = workerTerminal.read("Type Here");
			if(userInput != null && userInput.equals("quit"))
			{
				workerTerminal.println("Disconnecting from network");
				DatagramPacket disconnect;
				String val = T_MESSAGE + "3DIS" + T_CONTAINER + "4ENDA";
				disconnect= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				disconnect.setSocketAddress(dstAddress);
				socket.send(disconnect);
				this.wait();
				System.exit(0);
			}
			else if(userInput != null && userInput.equals("receive"))
			{
				workerTerminal.println("Connecting to this network's Forwarder");
				DatagramPacket connectReceive;
				String val = T_PORT + "550001" + T_CONTAINER + "4ENDA";
				connectReceive= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				connectReceive.setSocketAddress(dstAddress);
				socket.send(connectReceive);

				this.wait();
				this.wait();
			}
			else if(userInput != null && userInput.equals("send"))
			{
				workerTerminal.println("Connecting to this network's Forwarder");
				DatagramPacket connectSend;
				String val = T_MESSAGE + "3SEN" + T_CONTAINER + "4ENDA";
				connectSend= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				connectSend.setSocketAddress(dstAddress);
				socket.send(connectSend);

				this.wait();

				workerTerminal.println("Enter the name of the container the endpoint you want to send to is on.");
				String containerName;
				while(true)
				{
					containerName = workerTerminal.read("Type Name Here");
					if(containerName == null || containerName.length() <= 3)
						workerTerminal.println("Invalid Response");
					else
						break;
				}
				workerTerminal.println("Enter the message to send. Message cannot begin with a number.");
				String message;
				while(true)
				{
					message = workerTerminal.read("Type Message Here");
					if(message == null || message.length() < 1)
						workerTerminal.println("Invalid Response");
					else
						break;
				}
				String containerLength = Integer.toString(containerName.length()); 
				String messageLength = Integer.toString(message.length()); 

				DatagramPacket messageSend;
				val = T_MESSAGE + messageLength + message + T_DEST_NAME + containerLength + containerName + T_SENDER_NAME + "4ENDA";
				messageSend = new TLVPacket(MESSAGE_PACKET,"3",val).toDatagramPacket();
				messageSend.setSocketAddress(dstAddress);
				socket.send(messageSend);

				this.wait(5000);
			}
			else
			{
				workerTerminal.println("Not a valid input.");
			}
		}
	}

    public static void main(String[] args) {
		try {
			Terminal endpointTerminal = new Terminal("EndpointA");
			(new EndpointA(endpointTerminal, DEFAULT_DST_NODE, DEFAULT_DST_PORT, DEFAULT_SRC_PORT)).start();
			endpointTerminal.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
