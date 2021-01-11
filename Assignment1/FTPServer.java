import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;

public class FTPServer {
	private static String curDirectory;
	private static PrintWriter outToClient;
	static int ctrlPortNo, dataPortNo;
	static final int defaultCtrlPortNo = 2020, defaultDataPortNo = 2121;
	static short CHKsum = 0x0000;
	static final short DATA_SIZE = 1000, HEADER_SIZE = 5, CHUNK_SIZE = DATA_SIZE+HEADER_SIZE, S2C_SIZE = 3;
	
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		ctrlPortNo = defaultCtrlPortNo;
		dataPortNo = defaultDataPortNo;
		
		if (args.length == 2) {
			ctrlPortNo = Integer.parseInt(args[0]);
			dataPortNo = Integer.parseInt(args[1]);
		}
		
		String command, directory;
		ServerSocket welcomeSocket = new ServerSocket(ctrlPortNo);
		Socket ctrlSocket = welcomeSocket.accept();
		BufferedReader inFromClient = new BufferedReader(new InputStreamReader(ctrlSocket.getInputStream()));
		outToClient = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ctrlSocket.getOutputStream())));
		StringTokenizer st = null;
		
		curDirectory = System.getProperty("user.dir");
		
		while(true) {
			st = new StringTokenizer(inFromClient.readLine(), " ");
			command = st.nextToken().toUpperCase();
			
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
		
		ByteBuffer buff = null;
		ByteBuffer buff2 = null;
		
		int loop = (int)Math.ceil((double)size/(double)DATA_SIZE);
		for (int i = 0; i < loop; i++) {
			ClientToServerPacket receivePacket = new ClientToServerPacket(dataInFromClient);
			receivePacket.receivePacket();
			
			byte seqNo = receivePacket.getSeqNo();
			short CHKsum = receivePacket.getCHKsum();
			byte[] data = receivePacket.getData();

			fos.write(data);
			fos.flush();
			
			//Server To Client Message send
			ServerToClientPacket sendPacket = new ServerToClientPacket(dataOutToClient);
			sendPacket.sendPacket(seqNo, CHKsum);
			
		}
		
		fos.flush();
		fos.close();
		welcomeSocket2.close();
		dataSocket.close();
		
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
		ByteBuffer buff = null;
		byte[] data = null;
		byte seqNo = 0;
		int size;
		
		while(true) {
			data = new byte[DATA_SIZE];
			size = (short) fis.read(data, 0, DATA_SIZE);
			if (size == -1) break;
			
			ClientToServerPacket sendPacket = new ClientToServerPacket(dataOutToClient);
			sendPacket.sendPacket(seqNo++, CHKsum, (short) (size + HEADER_SIZE), data);			
			
			// Client to Server Message receive
			ServerToClientPacket receivePacket = new ServerToClientPacket(dataInFromClient);
			receivePacket.receivePacket();					
		
		}
		welcomeSocket2.close();
		dataSocket.close();
		fis.close();
		
	}
}
