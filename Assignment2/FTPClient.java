import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.StringTokenizer;


public class FTPClient {

	static String serverIP;
	static int ctrlPortNo, dataPortNo;
	static final String defaultIP = "127.0.0.1";
	static final int defaultCtrlPortNo = 2020, defaultDataPortNo = 2121;
	static short CHKsum = 0x0000;
	static final short DATA_SIZE = 1000, HEADER_SIZE = 5, CHUNK_SIZE = DATA_SIZE+HEADER_SIZE;
	static final byte WINDOW_SIZE = 5, MAX_SEQ_NO = 15;
	static HashMap<Byte, SenderWindow> senderWindow;
	static HashMap<Byte, ReceiverWindow> receiverWindow;
	static byte send_base = 0, nextSeqNo = 0, rcv_base = 0;
	static PriorityQueue<Integer> drop;
	static PriorityQueue<Integer> timeout;
	static PriorityQueue<Integer> biterror;
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		StringTokenizer st = null;
		String command, comToServer, result;
		int tmp = 0;
		send_base = 0; nextSeqNo = 0; rcv_base = 0;
		
		serverIP = defaultIP;
		ctrlPortNo = defaultCtrlPortNo;
		dataPortNo = defaultDataPortNo;
		senderWindow = new HashMap<>();
		receiverWindow = new HashMap<>();
		drop = new PriorityQueue<>();
		timeout = new PriorityQueue<>();
		biterror = new PriorityQueue<>();
		
		if (args.length == 3) {
			serverIP = args[0];
			ctrlPortNo = Integer.parseInt(args[1]);
			dataPortNo = Integer.parseInt(args[2]);
		}
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		Socket ctrlSocket = new Socket(serverIP, ctrlPortNo);
		DataOutputStream outToServer = new DataOutputStream(ctrlSocket.getOutputStream());
		BufferedReader inFromServer = new BufferedReader(new InputStreamReader(ctrlSocket.getInputStream()));
		
		while(true) {
			comToServer = br.readLine(); 
			st = new StringTokenizer(comToServer, " ");
			command = st.nextToken().toUpperCase();
			
			if (!command.equals("PUT")) {
				outToServer.writeBytes(comToServer + '\n');
			}
			
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
				result = inFromServer.readLine();
				tmp = result.indexOf(' ');
				int statusCode = Integer.parseInt(result.substring(0, tmp));
				
				if (statusCode == 200) {
					tmp = result.lastIndexOf(' ');
					result = result.substring(tmp+1, result.length());
					System.out.println(result);
				} else {
					result = result.substring(tmp+1, result.length());
					System.out.println(result);
				}
			} 
			
			
			else if (command.equals("LIST")) {
				result = inFromServer.readLine();
				tmp = result.indexOf(' ');
				int statusCode = Integer.parseInt(result.substring(0, tmp));
				if (statusCode == 200) {
					String list = inFromServer.readLine();
					int num = Integer.parseInt(result.substring(tmp).replaceAll("[^0-9]", ""));
					
					st = new StringTokenizer(list, ",");
					for (int i = 0; i < num; i++) {
						System.out.println(st.nextToken() + "," + st.nextToken());
					}
				} else {
					tmp = result.indexOf(' ');
					System.out.println(result.substring(tmp+1, result.length()));
				}
			} 
			
			
			else if (command.equals("GET")) {
				result = inFromServer.readLine();
				tmp = result.indexOf(' ');
				int statusCode = Integer.parseInt(result.substring(0, tmp));
				if (statusCode == 200) {
					int size = Integer.parseInt(result.substring(tmp).replaceAll("[^0-9]", ""));
					String filename = st.nextToken();
					System.out.println("Received " + filename + "\\ " + size + "  bytes");
					
					Socket dataSocket = new Socket(serverIP, dataPortNo);
					DataInputStream dataInFromServer = new DataInputStream(dataSocket.getInputStream());
					DataOutputStream dataOutToServer = new DataOutputStream(dataSocket.getOutputStream());
					
					File newFile = new File(filename);
					FileOutputStream fos = new FileOutputStream(newFile, false);
					
					int packet_num = (int)Math.ceil((double)size/(double)DATA_SIZE);
					while(packet_num != 0) {
						ClientToServerPacket receivePacket = new ClientToServerPacket(dataInFromServer, receiverWindow);
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
						
						// Client to Server Message send
						ServerToClientPacket sendPacket = new ServerToClientPacket(dataOutToServer);
						sendPacket.sendPacket(receivePacket.getSeqNo(), receivePacket.getCHKsum());
					}
					
					System.out.println("Completed...");
					fos.flush();
					fos.close();
					dataSocket.close();

				} else {
					tmp = result.indexOf(' ');
					System.out.println(result.substring(tmp+1, result.length()));
				}
			} 
			
			
			else if (command.equals("PUT")) {				
				String filename = st.nextToken();
				File file = new File(filename);
				
				if (!file.isFile() || !file.exists()) {
					System.out.println("ERROR : No such file exists");
					continue;
				}
				outToServer.writeBytes(comToServer + '\n');
				outToServer.writeBytes(Long.toString(file.length()) + '\n');
				
				result = inFromServer.readLine();
				tmp = result.indexOf(' ');
				int statusCode = Integer.parseInt(result.substring(0, tmp));
				
				if (statusCode == 200) {
					Socket dataSocket = new Socket(serverIP, dataPortNo);
					DataInputStream dataInFromServer = new DataInputStream(dataSocket.getInputStream());
					DataOutputStream dataOutToServer = new DataOutputStream(dataSocket.getOutputStream());
					FileInputStream fis = new FileInputStream(file);
					
					System.out.println(filename + " transferred /" + file.length() + " bytes");
					
					byte[] data = null;
					short size = 0;
					int sentNotAcked = 0, sendNo = 1;
					boolean isDrop = false, isTimeout = false;
					
					while(true) {
						if (dataSocket.isClosed()) break;
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
									CHKsum = (short) 0xFFFF;
									biterror.poll();
								}
							}
							
							ClientToServerPacket sendPacket = new ClientToServerPacket(dataOutToServer, senderWindow);
							sentNotAcked++; sendNo++;
							nextSeqNo %= (MAX_SEQ_NO+1);
			
							sendPacket.sendPacket(nextSeqNo++, CHKsum, (short) (size + HEADER_SIZE), data, isDrop, isTimeout);
						}
						if (sentNotAcked == 0) break;
						// Server to Client Message receive
						ServerToClientPacket receivePacket = new ServerToClientPacket(dataInFromServer, senderWindow);
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
					
					System.out.println(" Completed...");
					dataSocket.close();
					fis.close();
				} else {
					tmp = result.indexOf(' ');
					System.out.println(result.substring(tmp+1, result.length()));
					continue;
				}
			} else if (command.equals("QUIT")) {
				break;
			}
			
			drop.clear();
			timeout.clear();
			biterror.clear();
		}
		
		ctrlSocket.close();
		
	}

}