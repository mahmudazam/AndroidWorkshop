import java.util.*;
import java.io.*;
import java.net.*;

public class ChatRoom1 {
	public static void main(String[] args) {
		final String serverBufferName = args[0];
		final String daemonControlName = args[1];
		File daemonControl = new File(daemonControlName);
		try {
			daemonControl.createNewFile();
		} catch(Exception e) {
			e.printStackTrace();
		}
		ChatRoomServer server1 = new ChatRoomServer(60000, serverBufferName, daemonControlName);
		server1.hostServer();
	}
}
