
public class ReceiverWindow {
	private ClientToServerPacket receivePacket;
	private boolean acked;
	
	public ReceiverWindow(ClientToServerPacket receivePacket) {
		this.receivePacket = receivePacket;
		acked = false;
	}
	
	public byte[] getData() {
		return receivePacket.getData();
	}
	
	public void setAcked(boolean acked) {
		this.acked = acked;
	}

	public boolean isAcked() {
		return acked;
	}
}
