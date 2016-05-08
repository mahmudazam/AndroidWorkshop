import java.util.*;
import java.io.*;
import java.net.*;

public class ChatRoom1 {
	public static void main(String[] args) {
		ChatRoomServer server1 = new ChatRoomServer(60000);
		server1.hostServer();
	}
}
