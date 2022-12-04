import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class Endpoint extends Node
{
	static final int CONTROLLER_DST_PORT = 50000;
    static final int FORWARDER_DST_PORT = 54321;
    static final String DEFAULT_DST_NODE = "controller";
    static final String DEFAULT_FORWARDER_NODE = "forwarderone";
    static int DEFAULT_SRC_PORT;

    InetSocketAddress controllerAddress;
    InetSocketAddress defaultForwarderAddress = null;
	Terminal endpointTerminal;
    ArrayList<String> forwarders = new ArrayList<String>();

    /**
	 * Constructor
	 *
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	Endpoint(Terminal t, String dstHost, int dstPort) {
		try {
            int lastChar = containerAlias.charAt((containerAlias.length()-1));;
            if(lastChar == 'A')
                DEFAULT_SRC_PORT = 50001;
            else if(lastChar == 'B')
                DEFAULT_SRC_PORT = 50002;
			else if(lastChar == 'C')
				DEFAULT_SRC_PORT = 50003;
            else
                DEFAULT_SRC_PORT = 50004;

			endpointTerminal = t;
			controllerAddress= new InetSocketAddress(dstHost, dstPort);
			socket= new DatagramSocket(DEFAULT_SRC_PORT);
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
					endpointTerminal.println("From " + packet.getAddress().getHostName() + " : " + tlvs.get(T_MESSAGE));
					this.notify();
				}
				else if(type.equals(MESSAGE_PACKET)) //receiving message from somewhere
				{

					endpointTerminal.println("From " + tlvs.get(T_SENDER_NAME) + ": " + tlvs.get(T_MESSAGE));
				}
                else if(type.equals(FORWARDER_LIST))
                {
                    endpointTerminal.println("Updating Forwarding List");
                    ArrayList<String> forwardersReceived = ((TLVPacket)content).readEncodingList();
                    forwarders = forwardersReceived;

					this.notify();
                }
				else
				{
					endpointTerminal.println("ERROR: Not expected Receive.");
				}
			}
		}
		catch(Exception e) {e.printStackTrace();}
	}

    public synchronized void start() throws Exception {
		endpointTerminal.println("Welcome!");
        //Setup Forwarders
        endpointTerminal.println("Asking controller for forwarders on this network.");
        DatagramPacket reqForwarders;
        String value = T_CONTAINER + aliasLength + containerAlias;
        reqForwarders= new TLVPacket(FORWARDER_LIST_REQ, "1", value).toDatagramPacket();
        reqForwarders.setSocketAddress(controllerAddress);
        socket.send(reqForwarders);
        this.wait();

        endpointTerminal.println("Setup complete, ready for User");

        if(forwarders.size() < 1)
		{
            System.out.println("ERROR: no forwarders on this endpoints network, please run all forwarders before endpoints.");
			System.exit(0);
		}
        else
            defaultForwarderAddress = new InetSocketAddress(forwarders.get(0), FORWARDER_DST_PORT);
        
        //Start User Interaction
		while (true) 
		{
			endpointTerminal.println("Type 'receive' to receive packets or 'send' to send.");

			String userInput = endpointTerminal.read("Type Here");
			if(userInput != null && userInput.equals("receive"))
			{
				endpointTerminal.println("Connecting to this network's Forwarder/s");
				endpointTerminal.println("May receive a backlog of messages.");

				DatagramPacket connectReceive;
				String val = T_PORT + "5" + Integer.toString(DEFAULT_SRC_PORT) + T_CONTAINER + aliasLength + containerAlias;
				connectReceive= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();

                for(int i = 0; i < forwarders.size(); i++)
                {
                    InetSocketAddress sendTo = new InetSocketAddress(forwarders.get(i), FORWARDER_DST_PORT);
                    connectReceive.setSocketAddress(sendTo);
                    socket.send(connectReceive);

					this.wait();
                }
				endpointTerminal.println("Connected, waiting for message.");
				this.wait();
			}
			else if(userInput != null && userInput.equals("send"))
			{
				endpointTerminal.println("Connecting to a Forwarder");
				DatagramPacket connectSend;
				String val = T_MESSAGE + "3SEN" + T_CONTAINER + aliasLength + containerAlias;
				connectSend= new TLVPacket(CON_ENDPOINT, "2", val).toDatagramPacket();
				connectSend.setSocketAddress(defaultForwarderAddress);
				socket.send(connectSend);

				this.wait();

				endpointTerminal.println("Enter the name of the container the endpoint you want to send to is on.");
				String containerName;
				while(true)
				{
					containerName = endpointTerminal.read("Type Name Here");
					if(containerName == null)
						endpointTerminal.println("Invalid Response");
					else if(containerName.equals(containerAlias))
						endpointTerminal.println("Invalid Response: Cannot send message to self.");
					else
						break;
				}
				endpointTerminal.println("Enter the message to send. Message cannot begin with a number.");
				String message;
				while(true)
				{
					message = endpointTerminal.read("Type Message Here");
					if(message == null || message.length() < 1 || Character.isDigit(message.charAt(0)))
						endpointTerminal.println("Invalid Response");
					else
						break;
				}
				String containerLength = Integer.toString(containerName.length()); 
				String messageLength = Integer.toString(message.length()); 

				DatagramPacket messageSend;
				val = T_MESSAGE + messageLength + message + T_DEST_NAME + containerLength + containerName + T_SENDER_NAME + aliasLength + containerAlias;
				messageSend = new TLVPacket(MESSAGE_PACKET,"3",val).toDatagramPacket();
				messageSend.setSocketAddress(defaultForwarderAddress);
				socket.send(messageSend);

				this.wait(2000);
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
			(new Endpoint(endpointTerminal, DEFAULT_DST_NODE, CONTROLLER_DST_PORT)).start();
			endpointTerminal.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
