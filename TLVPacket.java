import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

/**
 * Class for packet content that represents acknowledgements
 *
 */
public class TLVPacket extends PacketContent {
    String t;
    String length;
    String encoding;

	/**
	 * Constructor that takes in information about a file.
	 * @param filename Initial filename.
	 * @param size Size of filename.
	 */
	TLVPacket(String t, String length, String value) {
		type= TLVPACKET;
		this.t = t;
        this.length = length;
        this.encoding = value;
	}

	/**
	 * Constructs an object out of a datagram packet.
	 * @param packet Packet that contains information about a file.
	 */
	protected TLVPacket(ObjectInputStream oin) {
		try {
			type= TLVPACKET;
            t= oin.readUTF();
            length= oin.readUTF();
			encoding= oin.readUTF();
		}
		catch(Exception e) {e.printStackTrace();}
	}

	/**
	 * Writes the content into an ObjectOutputStream
	 *
	 */
	protected void toObjectOutputStream(ObjectOutputStream oout) {
		try {
            oout.writeUTF(t);
            oout.writeUTF(length);
			oout.writeUTF(encoding);
		}
		catch(Exception e) {e.printStackTrace();}
	}

	/**
	 * Returns the content of the packet as String.
	 *
	 * @return Returns the content of the packet as String.
	 */
	public String toString() {
		return t + length + encoding;
	}

    /**
	 * Returns the type contained in the packet.
	 *
	 * @return Returns the info contained in the packet.
	 */
	public String getPacketT() {
		return t;
	}

    /**
	 * Returns the length of packet.
	 *
	 * @return Returns the info contained in the packet.
	 */
	public String getPacketLength() {
		return length;
	}

	/**
	 * Returns the info contained in the packet.
	 *
	 * @return Returns the info contained in the packet.
	 */
	public String getPacketEncoding() {
		return encoding;
	}

	public HashMap<String,String> readEncoding()
	{
		HashMap<String,String> toReturn = new HashMap<String,String>();
		int howMany = Integer.parseInt(this.length);
		String encoding = this.encoding;

		for(int i = 0; i < howMany; i++)
		{
			Integer type =  Character.getNumericValue(encoding.charAt(0));
			Integer length =  Character.getNumericValue(encoding.charAt(1));
			int beginIndex = 2;

			if(type == 5)
			{
				int nextChar = Character.getNumericValue(encoding.charAt(2));
				if(nextChar >= 0 && nextChar < 10)
				{
					length = (length * 10) + nextChar;
					beginIndex++;
				}
			}

			String val = encoding.substring(beginIndex, beginIndex+length);
			toReturn.put(Integer.toString(type),val);

			if(i != howMany-1)
				encoding = encoding.substring(beginIndex+length);
		}
		return toReturn;
	}
}
