import java.util.*;
import java.io.*;
import java.net.*;

public class ChatRoomClient {
	public static void main(String[] args) {
		String iD;
		Scanner omi = new Scanner(System.in);
		
		// Read ID from user:
		
		System.out.println("Chat App Starts: \nPlease Enter ID: ");
		iD = omi.nextLine();
		
		// Read Server IP from user:
		
		// System.out.println("Please enter server IP: ");
		final String ip = "172.16.1.71";
		final int port = 60000;
		
		// Connect to Server:
		Socket sender = null;
		Socket receiver = null;
		OutputStream out = null;
		InputStream in = null;
				
		try {
			
			sender = new Socket(ip, port);
			receiver = new Socket(ip, port);
			out = sender.getOutputStream();
			in = receiver.getInputStream();
			out.write(iD.getBytes());
			out.write("\n".getBytes());
			
		} catch(IOException e) {
			
			System.out.println("connect: IOException");
			
		}
		

		// Run service:
		ReceiveThread r = new ReceiveThread(in, iD);
		Thread tReceive = new Thread(r);
		SendThread s = new SendThread(out, iD);
		Thread tSend = new Thread(s);
		tReceive.start();
		tSend.start();
	}		
}

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
		while(running) {
			message = "";
			
			// Read and send client's message to server: 
			message = omi.nextLine();
			
			try {
				out.write(message.getBytes());
				// out.write("\n".getBytes());
				out.write(3);
			} catch(IOException e) {
				System.out.println("sendMessage: IOException");
			}
		}
	}
}

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
	
	public synchronized void terminate() {
		this.running = false;
		try { 
			Thread.sleep(1000);
		} catch(InterruptedException e) {
			System.out.println("ReceiveThread: Interrupted Exception");
		}
		System.exit(0);
	}
	
	public void run() {
		while(this.running) {
			this.message = "";
			try {
				temp = in.read();
				if(temp == -1) continue;
				while(temp != 3) {
					// System.out.print(temp);
					message += (char) temp;
					temp = in.read();
				}
				System.out.print(message);
				if(message.contains(iD + ": $exit")) {
					System.out.println("ChatRoom will close. ");
					System.exit(0);
				}
			} catch(IOException e) {
				System.out.println("receiveMessage: IOException");
				break;
			}
		}
	}
}

