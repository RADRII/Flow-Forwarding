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
				if(type.equals("5")) //ack from forwarder
				{
					workerTerminal.println("From Forwarder: " + ((TLVPacket)content).getPacketEncoding());
					this.notify();
				}
				else if(type.equals("1")) //receiving message from somewhere
				{
					int length  = Integer.parseInt(((TLVPacket)content).getPacketLength());
					String encoding = ((TLVPacket)content).getPacketEncoding();
					HashMap<Integer,String> tlvs = readEncoding(length,encoding);

					workerTerminal.println("From " + tlvs.get(7) + ": " + tlvs.get(5));
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
				disconnect= new TLVPacket("1", "2", "43DIS64ENDA").toDatagramPacket();
				disconnect.setSocketAddress(dstAddress);
				socket.send(disconnect);
				this.wait();
				System.exit(0);
			}
			else if(userInput != null && userInput.equals("receive"))
			{
				workerTerminal.println("Connecting to this network's Forwarder");
				DatagramPacket connectReceive;
				connectReceive= new TLVPacket("1", "3", "43REC355000164ENDA").toDatagramPacket();
				connectReceive.setSocketAddress(dstAddress);
				socket.send(connectReceive);

				this.wait();
				this.wait();
			}
			else if(userInput != null && userInput.equals("send"))
			{
				workerTerminal.println("Connecting to this network's Forwarder");
				DatagramPacket connectSend;
				connectSend= new TLVPacket("1", "2", "43SEN64ENDA").toDatagramPacket();
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
				workerTerminal.println("Enter the message to send.");
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
				String val = "5" + messageLength + message + "6" + containerLength + containerName + "74ENDA";
				messageSend = new TLVPacket("1","3",val).toDatagramPacket();
				messageSend.setSocketAddress(dstAddress);
				socket.send(messageSend);

				this.wait(10000);
			}
			else
			{
				workerTerminal.println("Not a valid input.");
			}
		}
	}

	public static HashMap<Integer,String> readEncoding(int howMany, String encoding)
	{
		HashMap<Integer,String> toReturn = new HashMap<Integer,String>();

		for(int i = 0; i < howMany; i++)
		{
			Integer type =  Character.getNumericValue(encoding.charAt(0));
			Integer length =  Character.getNumericValue(encoding.charAt(1));
			String val = encoding.substring(2,2+length);

			toReturn.put(type,val);

			if(i != howMany-1)
				encoding = encoding.substring(2+length);
		}
		return toReturn;
	}

    public static void main(String[] args) {
		try {
			Terminal endpointTerminal = new Terminal("EndpointA");
			(new EndpointA(endpointTerminal, DEFAULT_DST_NODE, DEFAULT_DST_PORT, DEFAULT_SRC_PORT)).start();
			endpointTerminal.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
