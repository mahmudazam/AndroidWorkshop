import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

class ChatRoomServer {
	
	ServerSocket server;
	InputStream in;
	OutputStream out;
	String command;
	String bufferName;
	SynchronizedFileBuffer mainBuffer;
	ConcurrentLinkedQueue<Client> clientList;
	Scanner omi;
	
	ChatRoomServer(int port, String n) {
		server = null;
		command = "";
		try {
			server = new ServerSocket(port);
		} catch(IOException e) {
			System.out.println("Server initiation failed. ");
			System.exit(0);
		}
		this.bufferName = n;
		mainBuffer = new SynchronizedFileBuffer(this.bufferName);
		clientList = new ConcurrentLinkedQueue<Client>();
		omi = new Scanner(System.in);
		in = null;
		out = null;
	}

	public void hostServer() {
		
		new Thread(new Runnable() { 
			public void run() {
				try {
					while(true) {
						Socket clientEndSender = server.accept();
						Socket clientEndReceiver = server.accept();
						if((clientEndSender == null) || (clientEndReceiver == null)) continue;
						String cesIP = clientEndSender.getInetAddress().toString();
						String cerIP = clientEndReceiver.getInetAddress().toString();
						if(!cesIP.equals(cerIP)) {
							clientEndSender.close();
							clientEndReceiver.close();
							continue;
						}
						in = clientEndSender.getInputStream();
						out = clientEndReceiver.getOutputStream();
						String iD = "";
						int temp = -1;
						boolean connected = false;
						while(!connected) {
							iD = "";
							temp = in.read();
							if((temp == -1) || (temp == (char)'\n')) continue;
							while(temp != (int)'\n') {
								iD += (char)temp;
								temp = in.read();
							}
							if(iD.contains("$exit")) {
								clientEndSender.close();
								clientEndSender.close();
								break;
							}
							// System.out.println(iD);
							if(!iDExists(iD)) {
								out.write("Server: Connection established! ".getBytes());
								out.write("\n".getBytes());
								connected = true;
								clientList.add(new Client(iD, clientEndSender, clientEndReceiver, mainBuffer));
								System.out.println("Number of Clients in the ChatRoom: " + clientList.size());
							} else {
								out.write("Server: ID already exists. ".getBytes());
								out.write("\n".getBytes());
								connected = false;
							}
						}
					}
				} catch(IOException e) {
					System.out.println("A client tried to connect but failed. ");
				}
			}
		}).start();
		
		System.out.println("Server is running.");
		System.out.println("Input '$exit' to quit server: ");
		
		new Thread(new Runnable() {
			public void run() {
				while(true) {
					Client temp = null;
					for(int i = 0; i < clientList.size(); i++) {
						temp = (Client)clientList.poll();
						if(temp == null) {
							System.out.println("null client in the clientList.");
							continue;
						}
						// System.out.println(temp.getID() + " checked");
						if(temp.isDisconnected() == false) {
								clientList.add(temp);
						} else {
							System.out.println(temp.getID() + " is not running and hence was removed from the list.");
							System.out.println("Number of Clients in the ChatRoom: " + clientList.size());
						}
					}
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) {}
				}
			}
		}).start();
		
		while(!command.equals("$exit")) {
			command = omi.nextLine();
			mainBuffer.append("Server Message: " + command + "\n");
		}
		System.out.println("Server shutting down... ");
		try {
			Thread.sleep(1000);
		} catch(InterruptedException e) {
			
		}
		System.exit(0);
		
	} // hostServer() ends
	
	public boolean iDExists(String id) {
		// System.out.println("Checking for ID " + id + " in clientList... ");
		// System.out.println("clientList size: " + clientList.size());
		Client temp = null;
		for(int i = 0; i < clientList.size(); i++) {
			// System.out.println("Loop entered. ");
			temp = (Client)clientList.poll();
			if(temp != null) {
				String iDInList = temp.getID();
				// System.out.println(iDInList);
				if(iDInList.equals(id)) {
					clientList.add(temp);
					return true;
				}
				clientList.add(temp);
			}
		}
		return false;
	}
	
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
			if(!this.fileBuffer.exists()) {
				this.fileBuffer.createNewFile();
				this.fileBuffer.deleteOnExit();
			}
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
	// private ServeClient service;
	private ReceiveThread receiver;
	private SendThread sender;
	private boolean running;
	private boolean disconnected;	

	// Constructor:
	// Builds the Client and starts the service thread:
	Client(String id, Socket s, Socket r, SynchronizedFileBuffer b) {
		
		try {
			
			this.iD = id;
			this.buffer = b;
			this.bufferFIStream = buffer.getFIStream();
			// System.out.println(this.bufferFIStream.read());
			this.clientEndSender = s;
			this.clientEndReceiver = r;
			this.socketIn = this.clientEndSender.getInputStream();
			this.socketOut = this.clientEndReceiver.getOutputStream();
			
			// this.service = new ServeClient();
			// new Thread(service).start();
			
			this.running = true;
			this.disconnected = false;
			this.receiver = new ReceiveThread();
			this.sender = new SendThread();
			this.buffer.append(this.iD + " joined chat. \n");
			System.out.println("Client: " + this.iD + " connected. ");
			
		} catch(IOException e) {
			System.out.println("Client: " + this.iD + " : Construction failed. ");
		}
	
	}
	
	public synchronized String getID() {
		return new String(this.iD);
	}
	
	public synchronized boolean isRunning() {
		return running;
	}
	
	public boolean isDisconnected() {
		return this.disconnected;
	}
	
	public synchronized void terminate() {
		this.running = false;
	}
	
	public synchronized void disconnect() {
		this.buffer.append((this.iD + " left chat. \n"));
		try {
			this.socketIn.close();
			this.socketOut.close();
			this.clientEndSender.close();
			this.clientEndReceiver.close();
		} catch(IOException e) {}
		System.out.println("Client: " + this.iD + " disconnected.");
		this.disconnected = true;
	}
	
	// Helper classes:
	class ReceiveThread implements Runnable {
		Thread t;
		
		ReceiveThread() {
			t = new Thread(this);
			t.start();
		}
		
		public void run() {
			while(isRunning()) {
				String message = iD + ": ";
				int socketTemp = -1;
				
				// Read client's message:
				try {
					socketTemp = socketIn.read();
					if(socketTemp == -1) continue;
					while(socketTemp != 3) {
						message += (char)socketTemp;
						socketTemp = socketIn.read();
					} // Reading from Socket ends
					message += '\n';
					if(message.equals(iD + ": $exit\n")) {
						break;
					}
					if(message != null) { 
						buffer.append(message);
						// System.out.print(message);
					}
				} catch(IOException e) {
					System.out.println("readMessage: IOException");
				}
			}
			try {
				Thread.sleep(100);
			} catch(InterruptedException e) {}
			terminate();
		}
	}
	
	class SendThread implements Runnable {
		Thread t;
		
		SendThread() {
			t = new Thread(this);
			t.start();
		}
		
		public void run() {
			while(isRunning()) {
				try {
					String message = "";
					int fileTemp = bufferFIStream.read();
					if(fileTemp == -1) continue;
					while(fileTemp != -1) {
						// System.out.print((char)fileTemp);
						message += (char)fileTemp;
						fileTemp = bufferFIStream.read();
					}
					// System.out.println();
					if(!message.equals("")) {
						socketOut.write((message + (char) 3).getBytes());
						socketOut.flush();
					}
					// if(message.contains(iD + ": $exit")) break;
				} catch(IOException e) {
					System.out.println("sendMessage: IOException");
				}
			}
			disconnect();
		}
	}
	
}
