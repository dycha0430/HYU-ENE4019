import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class FTPServer {
	private static String curDirectory;
	private static PrintWriter outToClient;
	static int ctrlPortNo, dataPortNo;
	static final int defaultCtrlPortNo = 2020, defaultDataPortNo = 2121;
	static short CHKsum = 0x0000;
	static final short DATA_SIZE = 1000, HEADER_SIZE = 5, CHUNK_SIZE = DATA_SIZE+HEADER_SIZE;
	static final byte WINDOW_SIZE = 5, MAX_SEQ_NO = 15;
	public static HashMap<Byte, SenderWindow> senderWindow;
	public static HashMap<Byte, ReceiverWindow> receiverWindow;
	static byte rcv_base = 0, send_base = 0, nextSeqNo = 0;
	static PriorityQueue<Integer> drop;
	static PriorityQueue<Integer> timeout;
	static PriorityQueue<Integer> biterror;
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		ctrlPortNo = defaultCtrlPortNo;
		dataPortNo = defaultDataPortNo;
		send_base = 0; nextSeqNo = 0; rcv_base = 0;
		
		if (args.length == 2) {
			ctrlPortNo = Integer.parseInt(args[0]);
			dataPortNo = Integer.parseInt(args[1]);
		}
		
		drop = new PriorityQueue<>();
		timeout = new PriorityQueue<>();
		biterror = new PriorityQueue<>();
		
		String command, directory;
		ServerSocket welcomeSocket = new ServerSocket(ctrlPortNo);
		Socket ctrlSocket = welcomeSocket.accept();
		BufferedReader inFromClient = new BufferedReader(new InputStreamReader(ctrlSocket.getInputStream()));
		outToClient = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ctrlSocket.getOutputStream())));
		receiverWindow = new HashMap<>();
		senderWindow = new HashMap<>();
		StringTokenizer st = null;
		curDirectory = System.getProperty("user.dir");
		
		while(true) {
			st = new StringTokenizer(inFromClient.readLine(), " ");
			command = st.nextToken().toUpperCase();
			
			int packetNum;
			if (command.equals("DROP")) {
				while (st.hasMoreTokens()) {
					packetNum = Integer.parseInt(st.nextToken(",").replaceAll("[^0-9]", ""));
					drop.add(packetNum);
				}
				
				continue;
			} else if (command.equals("TIMEOUT")) {
				while (st.hasMoreTokens()) {
					packetNum = Integer.parseInt(st.nextToken(",").replaceAll("[^0-9]", ""));
					timeout.add(packetNum);
				}
				
				continue;
			} else if (command.equals("BITERROR")) {
				while (st.hasMoreTokens()) {
					packetNum = Integer.parseInt(st.nextToken(",").replaceAll("[^0-9]", ""));
					biterror.add(packetNum);
				}
				
				continue;
			}
			
			if (command.equals("CD")) {
				directory = curDirectory;
				if (st.countTokens() != 0) directory = st.nextToken();
				cd(directory);
			} else if (command.equals("QUIT")) {
				break;
			} else if (command.equals("LIST")) {
				directory = curDirectory;
				if (st.countTokens() != 0) directory = st.nextToken();
				list(directory);
			} else if (command.equals("GET")) {
				directory = curDirectory;
				if (st.countTokens() != 0) directory = st.nextToken();
				get(directory);
			} else if (command.equals("PUT")) {
				directory = st.nextToken();
				int size = Integer.parseInt(inFromClient.readLine());
				put(directory, size);
			}
			
			drop.clear();
			timeout.clear();
			biterror.clear();
		}
		
		welcomeSocket.close();
		ctrlSocket.close();
	}

	// CD
	private static void cd(String directory) throws IOException{
		File curDir = new File(curDirectory);
		
		File newDir = new File(directory);
		if (!newDir.isAbsolute()) directory = curDirectory + '\\' + directory;
		newDir = new File(directory);
		
		if (!newDir.isDirectory() || !curDir.exists()) {
			outToClient.write("550 Failed - Requested action not taken. File unavailable\n");
		} else {
			curDirectory = newDir.getCanonicalPath();
			outToClient.write("200 Moved to " + curDirectory + '\n');
		}
		
		outToClient.flush();
		return;
	}
	
	// LIST
	private static void list(String directory) throws IOException {
		File dir = new File(directory);
		if (!dir.isAbsolute()) directory = curDirectory + '\\' + directory;
		dir = new File(directory);
		
		if (!dir.isDirectory() || !dir.exists()) {
			outToClient.write("550 Failed - Requested action not taken. File unavailable\n");
			outToClient.flush();
			return;
		}

		File files[] = dir.listFiles();
		outToClient.write("200 Comprising " + files.length + " entries\n");
		outToClient.flush();
		
		String name;
		int i;
		for (i = 0; i < files.length-1; i++) {
			name = files[i].getName();
			
			if (files[i].isDirectory()) {
				outToClient.write(name + ",-");
			} else {
				outToClient.write(name + "," + files[i].length());
			}
			outToClient.write(",");
		}
		
		name = files[i].getName();
		if (files[i].isDirectory()) {
			outToClient.write(name + ",-");
		} else {
			outToClient.write(name + "," + files[i].length());
		}
		
		outToClient.write('\n');
		outToClient.flush();
		
		return;
	}
	
	// GET
	private static void get(String directory) throws IOException {
		File file = new File(directory);
		if (!file.isAbsolute()) directory = curDirectory + '\\' + directory;
		file = new File(directory);
		
		if (!file.isFile() || !file.exists()) {
			outToClient.write("550 Failed - Requested action not taken. File unavailable\n");
			outToClient.flush();
			return;
		}
		outToClient.write("200 Containing " + file.length() + " bytes in total\n");
		outToClient.flush();
		
		ServerSocket welcomeSocket2 = new ServerSocket(dataPortNo);
		Socket dataSocket = welcomeSocket2.accept();
		DataInputStream dataInFromClient = new DataInputStream(dataSocket.getInputStream());
		DataOutputStream dataOutToClient = new DataOutputStream(dataSocket.getOutputStream());
		FileInputStream fis = new FileInputStream(directory);
		byte[] data = null;
		short size = 0;
		int sentNotAcked = 0, sendNo = 1;
		boolean isDrop = false, isTimeout = false;
		
		while(true) {
			while (size != -1) {
				CHKsum = 0x0000;
				isDrop = false; isTimeout = false;
				
				if (senderWindow.size() == WINDOW_SIZE) break;
				// Client to Server Message send
				data = new byte[DATA_SIZE];
				size = (short) fis.read(data, 0, DATA_SIZE);
				if (size == -1) break;
				
				if (!drop.isEmpty()) {
					if (sendNo == drop.peek()) {
						isDrop = true;
						drop.poll();
					}
				}
				
				if (!timeout.isEmpty()) {
					if (sendNo == timeout.peek()) {
						isTimeout = true;
						timeout.poll();
					}
				}
				
				if (!biterror.isEmpty()) {
					if (sendNo == biterror.peek()) {
						biterror.poll();
						CHKsum = (short) 0xFFFF;
					}
				}
				
				ClientToServerPacket sendPacket = new ClientToServerPacket(dataOutToClient, senderWindow);
				sentNotAcked++; sendNo++;
				nextSeqNo %= (MAX_SEQ_NO+1);
				sendPacket.sendPacket(nextSeqNo++, CHKsum, (short) (size + HEADER_SIZE), data, isDrop, isTimeout);
			}
			
			if (sentNotAcked == 0) break;
			// Server to Client Message receive
			ServerToClientPacket receivePacket = new ServerToClientPacket(dataInFromClient, senderWindow);
			if (!receivePacket.receivePacket()) continue;
			sentNotAcked--;
			
			if (receivePacket.getSeqNo() == send_base) {
				while (!senderWindow.isEmpty() && senderWindow.get(send_base).isAcked()) {
					senderWindow.remove(send_base);
					send_base++;
					send_base %= (MAX_SEQ_NO+1);
				}
			}
		}
		System.out.println("Completed...");
		
		welcomeSocket2.close();
		dataSocket.close();
		fis.close();
		
	}
	
	// PUT
	private static void put(String directory, int size) throws IOException {
		File file = new File(directory);
		if (!file.isAbsolute()) directory = curDirectory + '\\' + directory;
		file = new File(directory);
		
		outToClient.write("200 Ready to receive\n");
		outToClient.flush();
		
		ServerSocket welcomeSocket2 = new ServerSocket(dataPortNo);
		Socket dataSocket = welcomeSocket2.accept();
		DataInputStream dataInFromClient = new DataInputStream(dataSocket.getInputStream());
		DataOutputStream dataOutToClient = new DataOutputStream(dataSocket.getOutputStream());
		FileOutputStream fos = new FileOutputStream(file, false);
		
		int packet_num = (int)Math.ceil((double)size/(double)DATA_SIZE);
		while(packet_num != 0) {
			// Client to Server Message receive
			ClientToServerPacket receivePacket = new ClientToServerPacket(dataInFromClient, receiverWindow);
				
			if (!receivePacket.receivePacket(rcv_base)) continue;
				
			if (rcv_base == receivePacket.getSeqNo()) {
				while (!receiverWindow.isEmpty() && (receiverWindow.get(rcv_base) != null) && receiverWindow.get(rcv_base).isAcked()) {
					fos.write(receiverWindow.get(rcv_base).getData());
					fos.flush();
					
					receiverWindow.remove(rcv_base);
					rcv_base++;
					rcv_base %= (MAX_SEQ_NO+1);
					packet_num--;
				}
			}
			
			// Server To Client Message send
			ServerToClientPacket sendPacket = new ServerToClientPacket(dataOutToClient);
			sendPacket.sendPacket(receivePacket.getSeqNo(), receivePacket.getCHKsum());
		}
			
		System.out.println("Completed...");
		fos.flush();
		fos.close();
		welcomeSocket2.close();
		dataSocket.close();
		
		return;	
	}
}
