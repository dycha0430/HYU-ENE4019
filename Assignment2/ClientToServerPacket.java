import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;


public class ClientToServerPacket {
	static final short DATA_SIZE = 1000, HEADER_SIZE = 5, CHUNK_SIZE = DATA_SIZE+HEADER_SIZE;
	static final byte WINDOW_SIZE = 5;
	private byte seqNo;
	private short CHKsum;
	private short size;
	private byte[] data;
	private DataOutputStream dataOut;
	private DataInputStream dataIn;
	HashMap<Byte, SenderWindow> senderWindow;
	HashMap<Byte, ReceiverWindow> receiverWindow;
	
	
	public ClientToServerPacket(DataOutputStream dataOut, HashMap<Byte, SenderWindow> senderWindow) {
		this.dataOut = dataOut;
		this.senderWindow = senderWindow;
	}
	
	public ClientToServerPacket(DataInputStream dataIn,  HashMap<Byte, ReceiverWindow> receiverWindow) {
		this.dataIn = dataIn;
		this.receiverWindow = receiverWindow;
	}
	
	public void sendPacket(byte seqNo, short CHKsum, short size, byte[] data, boolean isDrop, boolean isTimeout) throws IOException {
		this.seqNo = seqNo;
		this.CHKsum = CHKsum;
		this.size = size;
		this.data = data;
		
		SenderWindow window = new SenderWindow(this);
		ByteBuffer buff = ByteBuffer.allocate(CHUNK_SIZE);
		buff.clear();
		buff.put(seqNo);
		buff.putShort(CHKsum);
		
		buff.putShort((short)(size));
		buff.put(data);
		
		data = new byte[CHUNK_SIZE];
		buff.get(0, data);
		
		senderWindow.put(seqNo, window);
		window.setTimer();
		
		if (isTimeout) {
			Timeout timeout = new Timeout(this);
			timeout.setTimer();
		} else {
			if (!isDrop) {
				dataOut.write(data);
				dataOut.flush();
			}
		}
		System.out.print(seqNo + " ");
	}
	
	public boolean receivePacket(byte rcv_base) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(CHUNK_SIZE);
		data = new byte[CHUNK_SIZE];
		buff.clear();
		
		dataIn.readFully(data);
		
		buff.put(data);
		
		seqNo = buff.get(0);
		CHKsum = buff.getShort(1);
		
		if (CHKsum != 0x0000) return false;
		
		size = buff.getShort(3);
		int dataSize = size-(HEADER_SIZE);
		data = new byte[dataSize];
		buff.get(HEADER_SIZE, data, 0, dataSize);
		
		if (receiverWindow.containsKey(seqNo)) return true;
		
		if (inWindow(seqNo, rcv_base)) {
			ReceiverWindow window = new ReceiverWindow(this);
			window.setAcked(true);
			receiverWindow.put(seqNo, window);
		}
		
		System.out.print(seqNo + " ");
		return true;
	}

	public byte getSeqNo() {
		return seqNo;
	}

	public short getCHKsum() {
		return CHKsum;
	}

	public short getSize() {
		return size;
	}

	public byte[] getData() {
		return data;
	}
	
	private boolean inWindow(byte seqNo, byte rcv_base) {
		int diff = seqNo - rcv_base;
		if (seqNo < rcv_base) diff += (WINDOW_SIZE+1);
		
		return diff < WINDOW_SIZE;
	}
	
	public HashMap<Byte, SenderWindow> getSenderWindow() {
		return senderWindow;
	}

}
