import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;


public class FTPClient {

	static String serverIP;
	static int ctrlPortNo, dataPortNo;
	static final String defaultIP = "127.0.0.1";
	static final int defaultCtrlPortNo = 2020, defaultDataPortNo = 2121;
	static short CHKsum = 0x0000;
	static final short DATA_SIZE = 1000, HEADER_SIZE = 5, CHUNK_SIZE = DATA_SIZE+HEADER_SIZE, S2C_SIZE = 3;
	
	private static void DROP(StringTokenizer st) {
		
	}
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		StringTokenizer st = null;
		String command, comToServer, result;
		int tmp = 0;
		
		serverIP = defaultIP;
		ctrlPortNo = defaultCtrlPortNo;
		dataPortNo = defaultDataPortNo;
		
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
					
					int loop = (int)Math.ceil((double)size/(double)DATA_SIZE);
					for (int i = 0; i < loop; i++) {
						ClientToServerPacket receivePacket = new ClientToServerPacket(dataInFromServer);
						receivePacket.receivePacket();
						
						byte seqNo = receivePacket.getSeqNo();
						short CHKsum = receivePacket.getCHKsum();
						byte[] data = receivePacket.getData();
						
						fos.write(data);
						fos.flush();
						
						// Client to Server Message send
						ServerToClientPacket sendPacket = new ServerToClientPacket(dataOutToServer);
						sendPacket.sendPacket(seqNo, CHKsum);
						
						System.out.print("#");
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
					byte[] data = null;
					
					System.out.println(filename + " transferred /" + file.length() + " bytes");
					short size;
					byte seqNo = 0; //TODO 
					while(true) {
						data = new byte[DATA_SIZE];
						size = (short) fis.read(data, 0, DATA_SIZE);
						if (size == -1) break;
						
						ClientToServerPacket sendPacket = new ClientToServerPacket(dataOutToServer);
						sendPacket.sendPacket(seqNo++, CHKsum, (short) (size + HEADER_SIZE), data);
						
						// Server to Client Message receive
						ServerToClientPacket receivePacket = new ServerToClientPacket(dataInFromServer);
						receivePacket.receivePacket();
						
						System.out.print("#");
					
					}
					
					System.out.println(" Completed...");
					dataSocket.close();
				} else {
					tmp = result.indexOf(' ');
					System.out.println(result.substring(tmp+1, result.length()));
					continue;
				}
			} else if (command.equals("QUIT")) break;
		}
		
		ctrlSocket.close();
		
		
	}

}