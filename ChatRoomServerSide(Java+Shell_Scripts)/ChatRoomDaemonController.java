
// File: ChatRoomDaemonController.java

// The controller program for the daemon process of the ChatRoom server.

import java.util.*;
import java.io.*;
import java.net.*;

public class ChatRoomDaemonController {

	// This program takes in command line arguments:
	// 	args[0]:: the path/name of the server's log file
	//	args[1]:: the path/name of the daemon control file
	public static void main(String[] args) {
		// Set up file handler objects:
		FileInputStream logIn = null;
		BufferedWriter dcOut = null;
		try {
			logIn = new FileInputStream(args[0]);
			dcOut = new BufferedWriter(new FileWriter(args[1], true));
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		// Starting the server bufferfile
		new ChatRoomDaemonController().readBuffer(logIn);
		String command = "";
		Scanner omi = new Scanner(System.in);
		
		// Read commands from the user and write them to the daemon control file
		// from where the server program will read commands: 
		while(true) {
			command = omi.nextLine();
			try {
				dcOut.write((command + '\n'));
				dcOut.flush();
			} catch(Exception e) {
				e.printStackTrace();
			}
			if(command.equals("$controllerExit")) {
				System.exit(0);
			}
			if(command.equals("$exit")) {
				try {
					Thread.sleep(500);
				} catch(Exception e) {
					e.printStackTrace();
				}
				System.exit(0);
			}
		}
		
	}
	
	void readBuffer(FileInputStream logIn) {
		// Read the server log line by line in a new Thread continuously:
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true) {
					String file = "";
					try {
						int temp = logIn.read();
						if(temp == -1) continue;
						while(temp != '\n') {
							file += (char)temp;
							temp = logIn.read();
						}
						System.out.println(file);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}
}
