import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class ServerToClientPacket {
	static final short S2C_SIZE = 3;
	byte seqNo;
	short CHKsum;
	DataOutputStream dataOut;
	DataInputStream dataIn;
	HashMap<Byte, SenderWindow> senderWindow;
	
	public ServerToClientPacket(DataOutputStream dataOut) {
		this.dataOut = dataOut;
	}
	
	public ServerToClientPacket(DataInputStream dataIn, HashMap<Byte, SenderWindow> senderWindow) {
		this.dataIn = dataIn;
		this.senderWindow = senderWindow;
	}
	
	public void sendPacket(byte seqNo, short CHKsum) throws IOException {
		this.seqNo = seqNo;
		this.CHKsum = CHKsum;
		
		ByteBuffer buff2 = ByteBuffer.allocate(S2C_SIZE);
		byte[] data = null;
		buff2.clear();
		
		buff2.put(seqNo);
		buff2.putShort(CHKsum);
		data = new byte[S2C_SIZE];
		buff2.get(0, data);
		
		dataOut.write(data);
		dataOut.flush();
	}
	
	public boolean receivePacket() throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(S2C_SIZE);
		byte[] response = new byte[S2C_SIZE];
		dataIn.readFully(response);
		buff.put(response);
		
		seqNo = buff.get(0);
		CHKsum = buff.getShort(1);
		
		if (CHKsum != 0x0000) return false;
		
		SenderWindow window = senderWindow.get(seqNo);
		if (!window.isAcked()) {
			window.stopTimer();
			System.out.print("<" + seqNo + " acked> ");
		}
		
		return true;
	}
	
	public byte getSeqNo() {
		return seqNo;
	}

	public short getCHKsum() {
		return CHKsum;
	}
}
