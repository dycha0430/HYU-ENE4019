import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class Timeout {
	static final long TIMEOUT = 2000;
	private Timer timer;
	private ClientToServerPacket sendPacket;

	class TimeoutTask extends TimerTask{
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				sendPacket.sendPacket(sendPacket.getSeqNo(), (short)0x0000, sendPacket.getSize(), sendPacket.getData(), false, false);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			timer.cancel();
		}
	}
	
	public Timeout(ClientToServerPacket sendPacket) {
		this.sendPacket = sendPacket;
	}

	public void setTimer() {
		timer = new Timer();
		timer.schedule(new TimeoutTask(), TIMEOUT);
	}
}
