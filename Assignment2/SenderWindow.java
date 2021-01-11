import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class SenderWindow {
	static final long SENDER_TIMEOUT = 1000;
	private Timer timer;
	private ClientToServerPacket sendPacket;
	private boolean acked;
	
	class TimeoutTask extends TimerTask{
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				sendPacket.sendPacket(sendPacket.getSeqNo(), (short)0x0000, sendPacket.getSize(), sendPacket.getData(), false, false);
				System.out.print(sendPacket.getSeqNo() + " timed out & retransmitted ");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public SenderWindow(ClientToServerPacket sendPacket) {
		this.sendPacket = sendPacket;
		acked = false;
	}

	public void setTimer() {
		timer = new Timer();
		timer.schedule(new TimeoutTask(), SENDER_TIMEOUT);
	}
	
	public void stopTimer() {
		acked = true;
		timer.cancel();
	}

	public boolean isAcked() {
		return acked;
	}
}