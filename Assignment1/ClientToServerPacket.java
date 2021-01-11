import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ClientToServerPacket {
	static final short DATA_SIZE = 1000, HEADER_SIZE = 5, CHUNK_SIZE = DATA_SIZE+HEADER_SIZE;
	private byte seqNo;
	private short CHKsum;
	private short size;
	private byte[] data;
	private DataOutputStream dataOut;
	private DataInputStream dataIn;
	
	
	public ClientToServerPacket(DataOutputStream dataOut) {
		this.dataOut = dataOut;
	}
	
	public ClientToServerPacket(DataInputStream dataIn) {
		this.dataIn = dataIn;
	}
	
	public void sendPacket(byte seqNo, short CHKsum, short size, byte[] data) throws IOException {
		this.seqNo = seqNo;
		this.CHKsum = CHKsum;
		this.size = size;
		this.data = data;
		
		ByteBuffer buff = ByteBuffer.allocate(CHUNK_SIZE);
		buff.clear();
		buff.put(seqNo);
		buff.putShort(CHKsum);
		
		buff.putShort((short)(size));
		buff.put(data);
		
		data = new byte[CHUNK_SIZE];
		buff.get(0, data);
		
		dataOut.write(data);
		dataOut.flush();
	}
	
	public void receivePacket() throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(CHUNK_SIZE);
		data = new byte[CHUNK_SIZE];
		buff.clear();
		
		dataIn.readFully(data);
		
		buff.put(data);
		
		seqNo = buff.get(0);
		CHKsum = buff.getShort(1);
		size = buff.getShort(3);
		
		int dataSize = size-(HEADER_SIZE);
		data = new byte[dataSize];
		buff.get(HEADER_SIZE, data, 0, dataSize);
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
}
