import java.util.*;
import java.net.*;
import java.io.*;

class ChatRoomServer {
	
	ServerSocket server;
	String command;
	
	ChatRoomServer(int port) {
		server = null;
		command = "";
		try {
			server = new ServerSocket(port);
		} catch(IOException e) {
			System.out.println("Server initiation failed. ");
			System.exit(0);
		}
	}

	public void hostServer() {
		
		Scanner omi = new Scanner(System.in);
		Queue<Client> clientList = new SynchronizedQueue<Client>();
		SynchronizedFileBuffer mainBuffer = new SynchronizedFileBuffer("ServerBuffer.txt");
		
		/*Thread listenThread =*/ new Thread(new Runnable() { 
			public void run() {
				try {
					while(true) {
						Socket clientEndSender = server.accept();
						Socket clientEndReceiver = server.accept();
						// if((clientEndSender == null) || (clientEndReceiver == null)) continue;
						// if(clientEndSender.getRemoteAddress().getHostAddress() != clientEndSender.getInetAddress().getHostAddress()) continue;
						clientList.addLast(new Client(clientEndSender, clientEndReceiver, mainBuffer));
						ListIterator<Client> iterator = clientList.listIterator(0);
						Client temp = null;
						while(iterator.hasNext()) {
							temp = (Client)iterator.next();
							if(temp == null) {
								System.out.println("null client in the clientList.");
								clientList.remove(temp);
								continue;
							}
							System.out.println(temp.getID() + " checked");
							if(temp.isRunning() == false) {
								System.out.println("Removed ");
								try {
									Thread.sleep(100);
									clientList.remove(temp);
								} catch(InterruptedException e) {
									System.out.println("InterruptedException in main(). ");
								} catch(ConcurrentModificationException ce) {
									continue;
								} 
							}
						}

					}
				} catch(IOException e) {
					System.out.println("ChatRoom: Room creation failed. ");
				}
			}
		}).start();
		
		//listenThread.start();
		
		System.out.println("Server is running.");
		System.out.println("Input '$exit' to quit server: ");
		
		/*Thread monitorThread =*/ new Thread(new Runnable() {
			public void run() {
				while(true) {
				}
			}
		}).start();
		
		// monitorThread.start();
		
		while(!command.equals("$exit")) {
			command = omi.nextLine();
		}
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
			// this.service = new ServeClient();
			// new Thread(service).start();
			
			this.running = true;
			this.receiver = new ReceiveThread();
			this.sender = new SendThread();
			this.buffer.append(this.iD + " joined chat. \n");
			System.out.println("Client: " + this.iD + " connected. ");
			
		} catch(IOException e) {
			System.out.println("Client: " + this.iD + " : Construction failed. ");
		}
	
	}
	
	public synchronized String getID() {
		return this.iD;
	}
	
	public synchronized boolean isRunning() {
		return running;
	}
	
	public synchronized void disconnect() {
		this.running = false;
		this.buffer.append((this.iD + "left chat. \n"));
		try {
			this.socketIn.close();
			this.socketOut.close();
			this.clientEndSender.close();
			this.clientEndReceiver.close();
		} catch(IOException e) {
			
		}
		System.out.println("Client: " + this.iD + " disconnected.");
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
					while(socketTemp != /* (int)'\n' */ 3) {
						message += (char)socketTemp;
						socketTemp = socketIn.read();
					} // Reading from Socket ends
					message += '\n';
					if(message != null) { 
						buffer.append(message);
						// System.out.print(message);
					}
					if(message.contains(iD + ": $exit")) {
						break;
					}
				} catch(IOException e) {
					System.out.println("readMessage: IOException");
				}
			}
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
					if(message.contains(iD +": $exit")) break;
					// System.out.println();
					if(!message.equals("")) {
						socketOut.write((message + (char) 3).getBytes());
						socketOut.flush();
					}
				} catch(IOException e) {
					System.out.println("sendMessage: IOException");
				}
			}
			disconnect();
		}
	}
	
}
