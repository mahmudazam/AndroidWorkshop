
// File ChatRoomServer.java

//////////////////////////////////////////
// The API for hosting a ChatRoom server:


////////////////////////////////////////////////////////////////////////////////////////////////////
// Basic outline of protocol:
// 1. The client app makes two java.net.Socket connections to the server:
//                                                  1 for reception of messages and
//                                                  1 for sending messages.
// 2. The client app sends a '\n'-terminated message as the ID to be used in the server.
// 3. The server checks this ID against the ID's of all other clients in a list/queue and:
//          if the ID is unique,
//              sends a '\n'-terminated message: "Server: Connection established! \n"
//          else,
//              sends a '\n'-terminated message: "Server: ID already exists. \n"
// 4. The client app checks the server's message and:
//      if the message contains "Connection established",
//          it starts the normal reception of messages;
//      else if the message contains "ID already exists", it re-sends the ID('\n'-terminated),
//                                              or sends "$exit\n", which terminates the connection.
// 5. The messages sent between the server and the client app form this point onwards,
//      are all code-point-3-terminated Strings, which are continuously checked for and read from
//          the Sockets on all sides of the connection.
////////////////////////////////////////////////////////////////////////////////////////////////////


import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import java.time.LocalDateTime;


////////////////////////////////////////////////////
// Class for creating a multi-threaded chat server:
class ChatRoomServer {
	
	///////////
	// Fields:
	
	// Network objects:
	ServerSocket server;
	Socket clientEndSender;
	Socket clientEndReceiver;
	InputStream in;
	OutputStream out;
	
	// Strings:
	String command;
	String bufferName;
	
	// File Manager objects:
		// This is necessary because the server uses a plain text(.txt) file to store the
		//	messages of the clients. This file will be called the "file buffer". 
	SynchronizedFileBuffer mainBuffer;
	BufferedReader dcIn;
	
	// List/Queue object for managing clients:
	ConcurrentLinkedQueue<Client> clientList;
	
	// Scanner for taking inputs from the console:
	Scanner omi;
	
	// Constructor:
	// Pre: port::int :: the port to which the ServerSocket of this object will be bound
	//		sb::java.lang.String :: the name/path of the file buffer
	//		dc::java.lang.String :: the name/path of the daemon control file
	// Post: A new java.net.ServerSocket is created, bound to the given port.
	//		A new file is created as the file buffer with the given name/path.
	//		A new java.util.concurrent.ConcurrentLinkedQueue is created.
	//		A new Scanner object is allocated to take inputs from the console.
	// Return: a reference to the newly created ChatRoomServer object
	ChatRoomServer(int port, String sb, String dc) {
		server = null;
		command = "";
		try {
			server = new ServerSocket(port);
		} catch(IOException e) {
			Printer.println("Server initiation failed. ");
			System.exit(0);
		}
		clientEndSender = null;
		clientEndReceiver = null;
		this.bufferName = sb;
		mainBuffer = new SynchronizedFileBuffer(this.bufferName);
		try {
			dcIn = new BufferedReader(new InputStreamReader(new FileInputStream(dc)));
		} catch(Exception e) {
			e.printStackTrace();
		}
		clientList = new ConcurrentLinkedQueue<Client>();
		omi = new Scanner(System.in);
		in = null;
		out = null;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Algorithm: hostServer()
	// Starts the threads for the server's functions: for listening to incoming connections,
	//								for removing disconnected clients; and
	//								continuously takes inputs from the console as commands or messages to the clients.
	// Pre: nothing
	// Post: The two threads mentioned in the description are started and a starting message is printed
	//		to the console.
	// Return: void
	public void hostServer() {
		
	// Listener Thread:
		//	Listens for incoming connections and verifies that the ID's chosen by the clients are
		//		unique.
		new Thread(new Runnable() { 
			public void run() {
				// Listen for incoming java.net.Socket connections:
				while(true) {
					clientEndSender = null;
					clientEndReceiver = null;
					try {
						clientEndSender = server.accept();
						clientEndReceiver = server.accept();
						if((clientEndSender == null) || (clientEndReceiver == null)) continue;
						String cesIP = clientEndSender.getInetAddress().toString();
						String cerIP = clientEndReceiver.getInetAddress().toString();
						if(!cesIP.equals(cerIP)) {
							clientEndSender.close();
							clientEndReceiver.close();
							continue;
						}
					} catch(IOException e) {
						Printer.println("A client tried to connect but failed. ");
						continue;
					}
					
				// Receive and verify the ID of the client in a new Thread after the connection has been made:
					IDvalidation idValidation = new IDvalidation(clientEndSender, clientEndReceiver, clientList, mainBuffer);
					new Thread(idValidation).start();
				}
			}
		}).start();
		
	// Remover Thread:
		// Checks the List/Queue of clients for inactive clients and removes them.
		// Polls clients from the list/queue and checks their isDisconnected method.
		new Thread(new Runnable() {
			public void run() {
				while(true) {
					Client temp = null;
				// Iterate through the list/queue:
					for(int i = 0; i < clientList.size(); i++) {
				// Poll a Client from the list:
						temp = clientList.poll();
						if(temp == null) {
							Printer.println("null client in the clientList.");
							continue;
						}
				// Check the isDisconnected() method:
					// If isDisconnected() returns false, the Client is placed back into the list/queue.
						if(temp.isDisconnected() == false) {
								clientList.add(temp);
						} else {
				// Otherwise, the polled Client object is not added back to the list/queue:
							Printer.println("Number of Clients in the ChatRoom: " + clientList.size());
						}
					}
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) {}
				}
			}
		}).start();
		
	// Print the starting messages to the console: 
		
		System.out.println("////////////////////////////////////////////////////////////////");
		System.out.println("ChatRoomServer: ");
		Printer.println("Server is running.");
		Printer.println("Input '$exit' to quit server: ");
		Printer.println("(All other inputs will be sent to the Clients as regular messages.) ");
		
	// Continuously take inputs from the console:
		
		while(!command.equals("$exit")) {
			try{
				command = dcIn.readLine();
			} catch(Exception e) {
				e.printStackTrace();
				mainBuffer.append("Server Message: " + "$exit" + "\n");
				break;
			}
			if(command == null) {
				command = "";
			}
			if(!command.equals("")) { 
				mainBuffer.append("Server Message: " + command + "\n");
				Printer.println(command);
			}
		}
		Printer.println("Server shutting down... ");
		try {
			Thread.sleep(1000);
		} catch(InterruptedException e) {
			Printer.println("Shutdown: InterruptedException");
		}
		System.exit(0);
		
	} // hostServer() ends
		
} // ChatRoomServer class ends
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////////////////////////////////
// Class for managing the ID validation process:
class IDvalidation implements Runnable{
	
	//////////
	// Fields:

	// Network objects:
	Socket clientEndSender;
	Socket clientEndReceiver;
	InputStream in;
	OutputStream out;
	
	// List/Queue of Client objects:
	ConcurrentLinkedQueue<Client> clientList;
	
	// File buffer manager object:
	SynchronizedFileBuffer mainBuffer;
	
	// Strings:
	String iD;
	
	// Primitive types:
	int temp;
	boolean connected;
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////
	// Constructor:
	// Pre: ces::java.net.Socket:: the client end sender Socket
	//		cer::java.net.Socket:: the client end receiver Socket
	//		q::java.util.concurrent.ConcurrentLinkedQueue<Client> :: the list/queue of Client objects
	//		b::SynchronizedFileBuffer :: the file buffer for this server
	// Post: All fields of this object are properly initialized.
	// Return: a reference to the newly created IDvalidation object
	IDvalidation(Socket ces, Socket cer, ConcurrentLinkedQueue<Client> q, SynchronizedFileBuffer b) {
		clientEndSender = ces;
		clientEndReceiver = cer;
		clientList = q;
		mainBuffer = b;
		iD = "";
		temp = -1;
		connected = false;
		try {
			in = clientEndSender.getInputStream();
			out = clientEndReceiver.getOutputStream();
		} catch(IOException e) {
			Printer.println("A client tried to connect but failed. ");
		}
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Algorithm: run()
	// Runnable method to be executed in a Thread for this object
	// Pre: nothing
	// Post: A Client object is added to the list/queue of Client objects if the iD of the Client object is unique.
	// Return: void
	public void run() {
		try {	
		// Receive the first '\n'-terminated message which will be the ID of the client:
			while(!connected) {
				iD = "";
				temp = in.read();
				if((temp == -1) || (temp == '\n')) continue;
				while(temp != (int)'\n') {
					iD += (char)temp;
					temp = in.read();
				}
				if(iD.contains("$exit")) {
					clientEndSender.close();
					clientEndSender.close();
					Printer.println("A client tried to connect but quit before ID validation completed...");
					break;
				}
		
		// Verify that the ID is unique: 
				if(!iDExists(iD)) {
					out.write("Server: Connection established! ".getBytes());
					out.write("\n".getBytes());
					connected = true;
					clientList.add(new Client(iD, clientEndSender, clientEndReceiver, mainBuffer));
					Printer.println("Number of Clients in the ChatRoom: " + clientList.size());
				} else {
					out.write("Server: ID already exists. ".getBytes());
					out.write("\n".getBytes());
					connected = false;
				}
			}
		} catch(IOException e) {
			Printer.println("A client tried to connect but failed. ");
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Algorithm: iDExists(id)
	// Checks whether a given string exists as the iD field of any Client object in the list/queue:
	// Pre: id::java.lang.String :: the string to check for in the list/queue of clients
	// Post: The structure of the list/queue of clients is unaffected.
	// Return: boolean:: true, if id is a copy of the iD field of any Client object in the list/queue of clients
	//					false, otherwise
	public boolean iDExists(String id) {
		Client temp = null;
		for(int i = 0; i < clientList.size(); i++) {
			temp = clientList.poll();
			if(temp != null) {
				String iDInList = temp.getID();
				if(iDInList.equals(id)) {
					clientList.add(temp);
					return true;
				}
				clientList.add(temp);
			}
		}
		return false;
	} // idExists() ends

} // IDvalidation class ends

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////
// Class for managing the file buffer: 
class SynchronizedFileBuffer {
	
	// Fields:
	
	// File manipulation objects:
	private File fileBuffer;
	private FileWriter fWriter;
	private BufferedWriter bWriter;
	
	///////////////////////////////////////////////////////////////////////////////////////////////
	// Constructor:
	// Creates the file buffer for the server.
	// Pre: path::java.lang.String :: the path to the file to be used as the file buffer
	// Post: If the file does not exist, the file is created. All fields are properly initialized.
	// Return: a reference to the newly created SynchronizedFileBuffer object
	SynchronizedFileBuffer(String path) {
		try {
			this.fileBuffer = new File(path);
			if(!this.fileBuffer.exists()) {
				this.fileBuffer.createNewFile();
				this.fileBuffer.deleteOnExit();
			}
			this.fWriter = new FileWriter(this.fileBuffer, true);
			this.bWriter = new BufferedWriter(this.fWriter);
		} catch(IOException e) {
			Printer.println("SynchronizedFileBuffer: Server Buffer creation failed. ");
		}
	}
	
	//////////////
	// Functions:
	
	//////////////////////////////////////////////////////////////
	// Algorithm: append(toAppend)
	// Appends a given String to the end of the file buffer.
	// Pre: toAppend::java.lang.String :: the String to append
	// Post: toAppend is appended to the end of the file buffer.
	// Return: boolean:: true, if successful; false, otherwise
	synchronized boolean append(String toAppend) {
		if(toAppend == null) return false;
		toAppend = toAppend + (char)3;
		try {
			bWriter.write(toAppend);
			bWriter.flush();
			return true;
		} catch(IOException e) {
			Printer.println("SynchronizedFileBuffer: IOException in append method");
			return false;
		}
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////
	// Algorithm: getFIStream()
	// Returns a new java.io.FileInputStream connected to the beginning of the file buffer.
	// Pre: nothing
	// Post: A new FileInputStream connected to the beginning of the file buffer is created.
	// Return: java.io.FileInputStream:: a FileInputStream connected to the beginning of the file buffer
	synchronized FileInputStream getFIStream() {
		try {
			// Return a  FileInputStream to the server's buffer text file.
			FileInputStream fin = new FileInputStream(fileBuffer.getAbsolutePath());
			return fin;
		} catch(IOException e) {
			Printer.println("SynchronizedFileBuffer: getFIStream failure");
			return null;
		}
	}

} // SynchronizedFileBuffer class ends

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////
// A class for managing individual client connections:
class Client {
	
	// Fields:
	
	// Strings:
	private String iD;
	
	// File manipulation objects:
	private SynchronizedFileBuffer buffer;
	private FileInputStream bufferFIStream;
	
	// Network objects:
	private Socket clientEndSender;
	private Socket clientEndReceiver;
	private InputStream socketIn;
	private OutputStream socketOut;
	
	// Runnable objects:
	private ReceiveThread receiver;
	private SendThread sender;
	
	// Primitive types:
	private boolean running;
	private boolean disconnected;	
	
	////////////////////////////////////////////////////////////////////////////
	// Constructor:
	// Builds the Client object with proper field values and starts the service threads.
	// Pre: id::java.lang.String :: the ID to be used for this client on the server
	//		s::java.net.Socket :: the client end sender Socket
	//		r::java.net.Socket :: the client end receiver Socket
	//		b::SynchronizedFileBuffer :: the file buffer of this server
	// Post: All fields are properly initialized and the service threads are started.
	// Return: a reference to the newly created Client object
	Client(String id, Socket s, Socket r, SynchronizedFileBuffer b) {
		try {
			this.iD = id;
			this.buffer = b;
			this.bufferFIStream = buffer.getFIStream();
			this.clientEndSender = s;
			this.clientEndReceiver = r;
			this.socketIn = this.clientEndSender.getInputStream();
			this.socketOut = this.clientEndReceiver.getOutputStream();			
			this.running = true;
			this.disconnected = false;
			this.receiver = new ReceiveThread();
			this.sender = new SendThread();
			this.buffer.append(this.iD + " joined chat. \n");
			Printer.println("Client: " + this.iD + " connected. ");
		} catch(IOException e) {
			Printer.println("Client: " + this.iD + " : Construction failed. ");
		}
	}
	
	// Accessors:
	public synchronized String getID() {
		return new String(this.iD);
	}
	
	public synchronized boolean isRunning() {
		return running;
	}
	
	public boolean isDisconnected() {
		return disconnected;
	}
	
	public synchronized void terminate() {
		running = false;
	}
	
	public synchronized void disconnect() {
		buffer.append((this.iD + " left chat. \n"));
		try {
			socketIn.close();
			socketOut.close();
			clientEndSender.close();
			clientEndReceiver.close();
		} catch(IOException e) {}
		Printer.println("Client: " + this.iD + " disconnected.");
		this.disconnected = true;
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	// Nested classes for running the reception and sending of messages from and to the client side:
	
	class ReceiveThread implements Runnable {
		// Fields:
		
		// Thread objects:
		Thread t;
		
		// Constructor:
		// Starts the Thread for reception.
		ReceiveThread() {
			t = new Thread(this);
			t.start();
		}
		
		// Runnable method to be run in a separate thread:
		// Continuously checks for messages sent from the client side and appends them to the file buffer.
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
					if(message != null) { 
						buffer.append(message);
						Printer.print("# " + message);
					}
					if(message.equals(iD + ": $exit\n")) {
						break;
					}
				} catch(IOException e) {
					Printer.println("readMessage: IOException");
				}
			}
			try {
				Thread.sleep(100);
			} catch(InterruptedException e) {}
			terminate();
		}
	} // ReceiveThread class ends
	
	class SendThread implements Runnable {
		// Fields:

		// Thread objects:
		Thread t;
		
		// Constructor:
		// Starts a Thread for continuously sending updates in the file buffer to the client side.
		SendThread() {
			t = new Thread(this);
			t.start();
		}
		
		// Runnable method to be run in a separate Thread:
		// Continuously checks the file buffer for updates at the end and sends those updates to the client side.
		public void run() {
			while(isRunning()) {
				try {
					String message = "";
					int fileTemp = bufferFIStream.read();
					if(fileTemp == -1) continue;
					while(fileTemp != -1) {
						message += (char)fileTemp;
						fileTemp = bufferFIStream.read();
					}
					if(!message.equals("")) {
						socketOut.write((message + (char) 3).getBytes());
						socketOut.flush();
					}
				} catch(IOException e) {
					Printer.println("sendMessage: IOException");
				}
			}
			disconnect();
		}
	} // SendThread class ends 
	
} // Client class ends

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////////////////////////////
// Class for printing in a formatted manner:
final class Printer {
	public static void print(String message) {
		System.out.print(LocalDateTime.now().toString() + " : " + message);
	}
	
	public static void println(String message) {
		System.out.println(LocalDateTime.now().toString() + " : " + message);
	}
} // Printer class ends

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////
// File ends
/////////////
