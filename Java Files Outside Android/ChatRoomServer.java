import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.file.Files;

class ChatRoomServer {

	public static void main(String[] args) {
		
		Scanner omi = new Scanner(System.in);
		String command = "";
		ServerSocket server = null;
		LinkedList<Client> clientList = new LinkedList<Client>();
		SynchronizedFileBuffer mainBuffer = new SynchronizedFileBuffer("ServerBuffer.txt");
		try {
			
			server = new ServerSocket(60000);
			command = "";
			for(int i = 1; i <= 3; i++) {
				
				Socket clientEndSender = server.accept();
				Socket clientEndReceiver = server.accept();
				clientList.addLast(new Client(clientEndSender, clientEndReceiver, mainBuffer));
				
			}
			
		} catch(IOException e) {
			
			System.out.println("ChatRoom: Room creation failed. ");
			
		}
		
		System.out.println("Input 'exit' to quit server: ");
		while(!command.equals("exit")) {
			command = omi.nextLine();
		}
		clientList.getLast().disconnect();
		System.exit(0);
		
	} // main method ends
	
} // ChatRoomServer class ends

class SynchronizedFileBuffer {
	
	// Fields:
	private File fileBuffer;
	private FileWriter fWriter;
	private BufferedWriter bWriter;
	
	// Constructor:
	SynchronizedFileBuffer(String path) {
		// Creates the server's buffer text file.

		try {
			
			this.fileBuffer = new File(path);
			if(!this.fileBuffer.exists()) this.fileBuffer.createNewFile();
			this.fWriter = new FileWriter(this.fileBuffer, true);
			this.bWriter = new BufferedWriter(this.fWriter);
			
		} catch(IOException e) {
			
			System.out.println("SynchronizedFileBuffer: Server Buffer creation failed. ");
			
		}

	}
	
	// Methods:

	synchronized boolean append(String toAppend) {
		
		// Append to the end of the file buffer.
		if(toAppend == null) return false;
		toAppend = toAppend + (char)3;
		try {
			
			bWriter.write(toAppend);
			bWriter.flush();
			return true;
			
		} catch(IOException e) {
			
			System.out.println("SynchronizedFileBuffer: IOException in append method");
			return false;
			
		}
		
	}
	
	synchronized FileInputStream getFIStream() {
		
		try {
			
			// Return a  FileInputStream to the server's buffer text file.
			FileInputStream fin = new FileInputStream(fileBuffer.getAbsolutePath());
			return fin;
			
		} catch(IOException e) {
			
			System.out.println("SynchronizedFileBuffer: getFIStream failure");
			return null;
			
		}
		
	}

}

class Client {
	
	// Fields:
	private String iD;
	private SynchronizedFileBuffer buffer;
	private FileInputStream bufferFIStream;
	private Socket clientEndSender;
	private Socket clientEndReceiver;
	private InputStream socketIn;
	private OutputStream socketOut;
	private ServeClient service;
	
	// Constructor:
	// Builds the Client and starts the service thread:
	Client(Socket s, Socket r, SynchronizedFileBuffer b) {
		
		try {
			
			this.iD = "";
			this.buffer = b;
			this.bufferFIStream = buffer.getFIStream();
			// System.out.println(this.bufferFIStream.read());
			this.clientEndSender = s;
			this.clientEndReceiver = r;
			this.socketIn = this.clientEndSender.getInputStream();
			this.socketOut = this.clientEndReceiver.getOutputStream();
			int temp = this.socketIn.read();
			while(temp != (int) '\n') {
				
				iD += (char) temp;
				temp = this.socketIn.read();
				
			}
			this.service = new ServeClient();
			new Thread(service).start();
			
		} catch(IOException e) {
			
			System.out.println("Client: " + this.iD + ": Construction failed. ");
			
		}
		
	}
	
	// Client Disconnector:
	public void disconnect() {
		
		this.service.terminate();
		try {
			this.socketOut.flush();
			this.clientEndSender.close();
			this.clientEndReceiver.close();
		} catch(IOException e) {
			
			System.out.println("disconnect: IOException");
			
		}
		
	}
	
	
	// Helper functions:
	private String readMessage() {
		
		String message = this.iD + ": ";
		int socketTemp = -1;
		
		// Read client's message:
		try {
			
			socketTemp = this.socketIn.read();
			if(socketTemp == -1) return null;;
			while(socketTemp != /* (int)'\n' */ 3) {
				message += (char)socketTemp;
				socketTemp = this.socketIn.read();
			} // Reading from Socket ends
			if(message.equals("$exit")) this.disconnect();
			message += '\n';
			return message;
			
		} catch(IOException e) {
			System.out.println("readMessage: IOException");
			return null;
		}
	
	}
	
	private void sendMessage() {
		
		try {
			String message = "";
			int fileTemp = this.bufferFIStream.read();
			if(fileTemp == -1) return;
			while(fileTemp != -1) {
				// System.out.print((char)fileTemp);
				message += (char)fileTemp;
				fileTemp = this.bufferFIStream.read();
			}
			// System.out.println();
			if(!message.equals("")) {
				this.socketOut.write((message + (char) 3).getBytes());
				this.socketOut.flush();
			}
		} catch(IOException e) {
			System.out.println("sendMessage: IOException");
		}
		
	}
	
	// Service thread execution:
	class ServeClient implements Runnable {
		private boolean running;
		
		ServeClient() {
			this.running = true;
		}
		
		public synchronized void terminate() {
			this.running = false;
		}
		public void run() {
			
			while(this.running == true) {
				
				// Read message from client:
				String message = readMessage();
				
				// Write/append message to the server's file buffer:
				if(message != null) { 
					buffer.append(message);
					// System.out.print(message);
				}
				
				// Send changes in the updated buffer to the client:
				sendMessage();
			
			} // while(running) ends
		
		} // run ends
		
	} // serveClient class ends
	
}
