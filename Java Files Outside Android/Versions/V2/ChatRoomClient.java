
// File: ChatRoomClient.java

//////////////////////////////
// Command line ChatRoom app:


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
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;


public class ChatRoomClient {
	public static void main(String[] args) {
		String iD = "";
		Scanner omi = new Scanner(System.in);
		
		// Print a starting message to the console:
		System.out.println("///////////////////////");
		System.out.println("ChatRoom Command Line: ");
		
		// Read Server IP from user:
		
		PrinterOnClientSide.print("Please enter the IP address of the server: ");
		String ip = omi.nextLine();
		if(ip.equals("$exit")) {
			PrinterOnClientSide.println("ChatRoom will close. ");
			System.exit(0);
		}
		final int port = 60000;
		
		Socket sender = null;
		Socket receiver = null;
		OutputStream out = null;
		InputStream in = null;
		
		// Make Socket connections to the server:
		while(true) {
			PrinterOnClientSide.println("Connecting to server...");
			try {
				sender = new Socket();
				sender.connect(new InetSocketAddress(ip, port), 2000);
				receiver = new Socket();
				receiver.connect(new InetSocketAddress(ip, port), 2000);
				out = sender.getOutputStream();
				in = receiver.getInputStream();
				PrinterOnClientSide.println("Connected to the server. ");
				break;
			} catch(IOException e) {
				PrinterOnClientSide.println("Error connecting to the server. ");
				PrinterOnClientSide.print("Please enter the IP address of the server again: ");
				ip = omi.nextLine();
				if(ip.equals("$exit")) {
					PrinterOnClientSide.println("ChatRoom will close. ");
					System.exit(0);
				}
			}
		}
		
		// Send the ID to the server and repeat if not validated:
		boolean connected = false;
		while(connected != true) {
			PrinterOnClientSide.print("Please enter another ID: ");
			iD = omi.nextLine();
			try { 
				out.write(iD.getBytes());
				out.write("\n".getBytes());
				if(iD.contains("$exit")) System.exit(0);
				String serverMessage = "";
				int temp = -1;
				innerLoop:
				while(serverMessage.equals("")) {
					temp = in.read();
					if((temp == -1) || (temp == '\n')) continue innerLoop;
					while(temp != (int)'\n') {
						serverMessage += (char)temp;
						temp = in.read();
					}
					PrinterOnClientSide.println(serverMessage);
					if(serverMessage.contains("ID already exists")) {
						serverMessage = "";
						break;
					}
					if(serverMessage.contains("Connection established")) {
						connected = true;
						break;
					}
					serverMessage = "";
				}
			} catch(IOException e) {
				PrinterOnClientSide.println("IOException while connecting. ");
			}
		}

		// Start separate Threads for receiving and sending messages from and to the server:
		ReceiveThread r = new ReceiveThread(in, iD);
		Thread tReceive = new Thread(r);
		SendThread s = new SendThread(out, iD);
		Thread tSend = new Thread(s);
		tReceive.start();
		tSend.start();
		while(true) {
			if(!((s.getState() == true) && (r.getState() == true))) {
				try {
					sender.close();
					receiver.close();
				} catch(IOException e) {}
				System.exit(0);
			}
		}
	}		
}

////////////////////////////////////////////////////////////////////////////////////
// Class for continuously taking inputs and sending them as messages to the server:
class SendThread implements Runnable {
	OutputStream out;
	boolean running;
	String message;
	Scanner omi;
	String iD;
	
	public synchronized boolean getState() {
		return this.running;
	}
	
	SendThread(OutputStream o, String id) {
		this.out = o;
		this.running = true;
		this.message = "";
		this.omi = new Scanner(System.in);
		this.iD = id;
	}
	
	public void run() {
		while(running == true) {
			message = "";
			
			// Read and send client's message to server: 
			message = omi.nextLine();
			
			try {
				out.write(message.getBytes());
				out.write(3);
				if(message.equals("$exit")) { 
					try {
						Thread.sleep(250);
					} catch(InterruptedException e) {}
					running = false;
				}
			} catch(IOException e) {
				PrinterOnClientSide.println("sendMessage: IOException");
			}
		}
	}
}

//////////////////////////////////////////////////////////////////////////////////////////////////
// Class for continuously checking for messages from the server and printing them to the console:
class ReceiveThread implements Runnable {
	InputStream in;
	int temp;
	boolean running;
	String message;
	String iD;
	
	ReceiveThread(InputStream i, String id) {
		this.temp = -1;
		this.in = i;
		this.running = true;
		this.message = "";
		this.iD = id;
	}
	
	public synchronized boolean getState() {
		return this.running;
	}
	
	public void run() {
		while(running == true) {
			this.message = "";
			try {
				temp = in.read();
				if(temp == -1) continue;
				while(temp != 3) {
					// System.out.print(temp);
					message += (char) temp;
					temp = in.read();
				}
				if(message.contains(iD + ": $exit")) {
					PrinterOnClientSide.println("ChatRoom will close. ");
					running = false;
				} else if(!message.equals("")){
					PrinterOnClientSide.print(message);
				}
				if(message.contains("Server Message: $exit")) {
					PrinterOnClientSide.println("Server shut down. ChatRoom will close. ");
					running = false;
				}
			} catch(IOException e) {
				PrinterOnClientSide.println("receiveMessage: IOException");
				break;
			}
		}
	}
}

/////////////////////////////////////////////
// Class for printing in a formatted manner:
final class PrinterOnClientSide {
	public static void print(String message) {
		System.out.print(LocalDateTime.now().toString() + " : " + message);
	}
	
	public static void println(String message) {
		System.out.println(LocalDateTime.now().toString() + " : " + message);
	}
}


