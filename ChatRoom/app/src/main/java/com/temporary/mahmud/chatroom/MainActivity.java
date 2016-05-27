package com.temporary.mahmud.chatroom;

import java.lang.ref.WeakReference;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.text.DateFormat;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Handler;

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

public class MainActivity extends AppCompatActivity {
    // Views:
    EditText input;
    TextView display;

    // Network objects:
    Connection connection;

    // UI thread Handler:
    Handler mainHandler;

    // String:
    String TAG;

    // Algorithm: onCreate(savedInstanceState)
    // Activity creator:
    // Pre: savedInstanceState::android.os.Bundle
    // Post: The MainActivity is created with the UI specifications in
    //          activity_main.xml
    // Return: void
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        input = (EditText) findViewById(R.id.text_input);
        input.setHint("Input text here...");
        input.setMovementMethod(new ScrollingMovementMethod()); // Making the EditText scrollable
        display = (TextView) findViewById(R.id.display);
        display.setMovementMethod(new ScrollingMovementMethod()); // Making the TextView scrollable
        mainHandler = new MainHandler(this); // Creating a Handler for the UI thread
        connection = new Connection(60000, mainHandler);
        TAG = "UIThread";
        displayStringf("Please enter the IP address of the server: ");
        // ContinuousText ct = new ContinuousText(mainHandler);
        // ct.start();

    }

    // Algorithm: send(view)
    // onClick method for the "SEND" button
    // Pre: view::android.view.View
    // Post: Depending on the sequence of clicks, a connection to the Server is established,
    //          or a String is displayed to the UI's TextView.
    // Return: void
    public void send(View view) {
        // Take the String in the EditText as an input:
        String message = input.getText().toString();
        input.setText("");
        if(message.equals("")) return;
        if(connection.getConnectionStatus() == -4) {
            if(!message.equals("$exit")) {
                displayStringf("Connecting to server(with IP: " + message + ")... ");
                connection.setIP(message);
                connection.connect();
            }
        } else if(connection.getConnectionStatus() == -3) {
            displayStringf("Still connecting to server. Please wait...");
        } else if(connection.getConnectionStatus() == -2) {
            if(!message.equals("$exit")) {
                displayStringf("Logging in to server with ID: " + message);
                connection.setID(message);
                connection.sendID();
            }
        } else if(connection.getConnectionStatus() == -1) {
            displayStringf("Still validating your ID. Please wait...");
        } else if(connection.getConnectionStatus() == 0) {
            displayStringf("Still validating your ID. Please wait...");
        } else if(connection.getConnectionStatus() == 1) {
            connection.sendMessage(message);
        } else {
            displayStringf("Connection lost. ");
            connection.close();
            connection = new Connection(60000, mainHandler);
        }

        if(message.equals("$exit")) {
            displayStringf("ChatRoom will close. ");
            try {
                if(connection != null) connection.close();
            } catch(Exception e) {
                Log.v(TAG, e.toString());
            }
            finish();
        }

    }

    // Algorithm: displayString(toDisplay)
    // Helper function for displaying Strings existing in the UI thread:
    // Pre: toDisplay::java.lang.String :: the String to be displayed in the UI's TextView
    // Post: toDisplay is displayed to the UI's TextView.
    // Return: void
    public void displayString(String toDisplay) {
        display.append(toDisplay);
        display.append("\n");
    }

    // Algorithm: displayStringf(toDisplay)
    // Helper function for displaying Strings existing in the UI thread in a formatted manner:
    // Pre: toDisplay::java.lang.String :: the String to be displayed in the UI's TextView
    // Post: toDisplay is displayed to the UI's TextView in a formatted manner.
    // Return: void
    public void displayStringf(String toDisplay) {
        display.append(DateFormat.getDateTimeInstance().format(new Date()) + " : " + toDisplay);
        if(toDisplay != null) {
            if( (toDisplay.charAt(toDisplay.length() - 1)) != '\n') display.append("\n");
        }
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
// An android.os.Handler definition for the UI thread:
class MainHandler extends Handler {
    private final WeakReference<MainActivity> mainActivityWeakReference;

    // Constructor:
    MainHandler(MainActivity mainActivity) {
        // This is to avoid memory leaks during garbage collection:
        mainActivityWeakReference = new WeakReference<>(mainActivity);
    }

    // Algorithm: handleMessage(message)
    // This is a method for displaying Strings on the UI thread's TextView by other
    //      threads.
    // Pre: message::android.os.Message
    // Post: The String with key "message" in the android.os.Bundle within message
    //          is displayed to the TextView
    // Return: void
    @Override
    public void handleMessage(Message message) {
        MainActivity mainActivity = mainActivityWeakReference.get();
        if(mainActivity != null) {
            if(message.getData().getString("message").contains("Server Message: $exit")) {
                mainActivity.displayStringf("Server went offline. \nPlease enter the IP address " +
                        "of a different server (or the same server to try again): ");
                mainActivity.connection.close();
                mainActivity.connection = new Connection(60000, this);
                return;
            }
            mainActivity.displayStringf(message.getData().getString("message"));
        }
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
// A class for managing the connection to and the communication with the server:
class Connection {
    // Fields:
    // Network objects:
    Socket sender;
    Socket receiver;
    InputStream in;
    OutputStream out;

    // Thread object for receiveing messages from the server:
    ReceiveThread receiveThread;

    // Strings:
    String iD;
    String iP;
    String message;
    String serverMessage;
    public final String TAG;

    // UI thread Handler:
    Handler mainHandler;

    // Primitive types:
    int port;
    int connectionStatus;
        // connectionStatus:
        // -4 :: No Socket connections are open.
        // -3 :: Socket connections are being attempted.
        // -2 :: Socket connections have been made to the server but the iD has not yet been sent.
        // -1 :: The iD is being sent to the server.
        //  0 :: The server's response to the sent iD is being received and checked.
        //  1 :: The server has validated the iD and the connection has been consolidated for
        //          communication.

    // Constructor:
    // Algorithm: Connection(id, ip, p, mh)
    // Constructs a new Connection Object for communicating with the server.
    // Pre: id::java.lang.String :: the ID the user chooses to use on the Server
    //      ip::java.lang.String :: the IP address of the server
    //      p::int :: the port used by the server
    //      mh::android.os.Handler :: Handler for the communicating with the UI thread
    // Post: All variables are initialized.
    // Return: a reference to a new Connection object with the passed in information
    Connection(int p, Handler mh) {
        sender = null;
        receiver = null;
        receiveThread = null;
        TAG = "Connection";
        port = p;
        mainHandler = mh;
        message = null;
        connectionStatus = -4;
    }

    // Accessors:

    // Algorithm: getServerMessage()
    // Synchronized method for accessing the serverMessage field of this object.
    // Pre: nothing
    // Post: nothing/All fields are unchanged.
    // Return: java.lang.String:: a reference to the serverMessage field of this object
    public synchronized String getServerMessage() {
        return serverMessage;
    }

    // Algorithm: getConnectionStatus()
    // Synchronized method for  accessing the connectionStatus field of this object.
    // Pre: nothing
    // Post: nothing/All fields are unchanged.
    // Return: int:: the connectionStatus field of this object.
    public synchronized int getConnectionStatus() {
        return connectionStatus;
    }

    // Mutators:

    // Algorithm: setID(id)
    // Sets the iD field of this object to a given String.
    // Pre: id::java.lang.String :: the String to set as the iD field of this object
    // Post: The iD field of this object is set to the passed in String.
    // Return: void
    public void setID(String id) {
        iD = new String(id);
    }

    // Algorithm: setIP(ip)
    // Sets the iP field of this object to a given String.
    // Pre: ip::java.lang.String :: the String to set as the iP field of this object
    // Post: The iP field of this object is set to the passed in String.
    // Return: void
    public void setIP(String ip) {
        iP = new String(ip);
    }

    // Helper functions:

    // Algorithm: displayOnUI(String toDisplay)
    // Method for displaying a given String on the UI thread.
    // Pre: toDisplay::java.lang.String :: the String to display on the UI thread
    // Post: nothing
    // Return: void
    void displayOnUI(String toDisplay) {
        Message stringMsg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("message", toDisplay);
        stringMsg.setData(bundle);
        mainHandler.sendMessage(stringMsg);
    }

    // Network operations:

    // Algorithm: startReception()
    // Starts a thread for receiving messages from the server.
    // Pre: nothing
    // Post: A new ReceiveThread object is created and the run() function of the ReceiveThread
    //          is run in a new Thread.
    // Return: void
    public void startReception() {
        receiveThread = new ReceiveThread(mainHandler, iD, in);
        new Thread(receiveThread).start();
    }

    // Algorithm: sendMessage(msg)
    // Sends a String to the server, maintaining protocol.
    // Pre: msg::java.lang.String :: the String message to send to the server
    // Post: msg is sent to the server maintaining protocol in a new Thread.
    // Return: void
    public void sendMessage(String msg) {
        this.message = msg;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    out.write(message.getBytes());
                    // out.write("\n".getBytes());
                    out.write(3);
                    message = "";
                } catch (IOException e) {
                    Log.v(TAG, "sendMessage: IOException");
                }
            }
        }).start();
    }

    // Algorithm: connect()
    // Method for starting communication with the server:
    // Tries to connect to the server and start reception from the server.
    // Pre: nothing
    // Post: java.net.Socket connections are made to the server if possible and
    //          a message is displayed on the UI's TextView to report
    //          success or failure.
    // Return: void
    public void connect() {
        if(getConnectionStatus() == -4) {
            // Connect to the server:
            connectionStatus = -3;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sender = new Socket();
                        sender.connect(new InetSocketAddress(iP, 60000), 2000);
                        receiver = new Socket();
                        receiver.connect(new InetSocketAddress(iP, 60000), 2000);
                        out = sender.getOutputStream();
                        in = receiver.getInputStream();
                        connectionStatus = -2;
                        displayOnUI("Connected to the server. \nPlease enter the ID you wish to " +
                                "use: ");
                    } catch (Exception e) {
                        displayOnUI("Error connecting to server. " +
                                "Please enter the IP address of the server again: ");
                        connectionStatus = -4;
                        return;
                    }
                }
            }).start();
        }
    }

    // Algorithm: sendID()
    // Method for sending the ID to the server for validation:
    // Pre: nothing
    // Post: The iD field of this object is sent to the server and the message of the server is
    //          recorded and checked. The connectionStatus field of this object is updated
    //          according to the message of the server.
    // Return: void
    public void sendID() {
        if(connectionStatus == -2) {
            connectionStatus = -1;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Send the ID to the server:
                        out.write(iD.getBytes());
                        out.write("\n".getBytes());

                        // Read the server's message:
                        connectionStatus = 0;
                        serverMessage = "";
                        int temp = -1;
                        while(getServerMessage().equals("")) {
                            temp = in.read();
                            if((temp == -1) || (temp == (int)'\n')) continue;
                            while(temp != (int)'\n') {
                                serverMessage += (char)temp;
                                temp = in.read();
                            }
                        }

                        // Check the server's response and update connectionStatus accordingly:
                        displayOnUI(serverMessage + '\n');
                        if(serverMessage.contains("Connection established")) {
                            connectionStatus = 1;
                            startReception();
                        } else if(serverMessage.contains("ID already exists")){
                            displayOnUI("Please enter a different ID: ");
                            connectionStatus = -2;
                        } else {
                            out.write("$exit".getBytes());
                            out.write("\n".getBytes());
                            connectionStatus = -4;
                            displayOnUI("Error in logging: please enter the IP address " +
                                    "of the server again: ");
                        }
                    } catch(Exception e) {
                        Log.v(TAG, "Error in sending/receiving messages. ");
                        connectionStatus = -1;
                    }
                }
            }).start();
        }
    }

    // Algorithm: close()
    // Stops the reception thread and closes the connection.
    // Pre: nothing
    // Post: All java.net.Socket connections are closed.
    // Return: void
    public void close() {
        if(receiveThread != null) receiveThread.terminate();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            if(sender != null) sender.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if(receiver != null) receiver.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
// A class for managing the reception of messages from the server:
class ReceiveThread implements Runnable {

    // Fields:
    // Network objects:
    InputStream in;

    // Strings:
    String message;
    String iD;
    String TAG;

    // Handler for UI thread:
    Handler mainHandler;

    // Primitives:
    int temp;
    boolean running;

    // Constructor:
    // Algorithm: ReceiveThread(mh, id, i)
    // Creates a ReceiveThread object with the appropriate information.
    // Pre: mh::android.os.Handler :: a Handler for the UI thread
    //      id::java.lang.String :: the ID the user chose to use in the server
    //      i::InputStream :: the InputStream of the receiver Socket of the Connection object
    //                          creating the ReceiveThread object.
    ReceiveThread(Handler mh, String id, InputStream i) {

        // Set up the UI thread Handler:
        this.mainHandler = mh;

        // Set up the InputStream:
        this.in = i;

        // Initialize all other variables:
        this.message = "";
        this.iD = id;
        this.TAG = "ReceiveThread";
        this.temp = -1;
        this.running = true;

    }

    /*
    // Algorithm: displayOnUI(String toDisplay)
    // Method for displaying a given String on the UI thread.
    // Pre: toDisplay::java.lang.String :: the String to display on the UI thread
    // Post: nothing
    // Return: void
    void displayOnUI(String toDisplay) {
        Message stringMsg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("message", toDisplay);
        stringMsg.setData(bundle);
        this.mainHandler.sendMessage(stringMsg);
    }
    */

    // Algorithm: terminate()
    // Breaks the loop in the run() method.
    // Pre: nothing
    // Post: The running field of this object is set to false.
    // Return: void
    public void terminate() {
        running = false;
    }

    // Algorithm: run()
    // Runnable method:
    // Continuously listens for and receives messages from the server:
    // Pre: nothing
    // Post: Messages from the server are received and sent to the UI thread for displaying.
    // Return: void
    public void run() {
        while(this.running) {
            // Initialize message as an empty String:
            this.message = "";

            try {
                // Read bytes from the Socket's InputStream and concatenate as characters
                // to message:
                temp = in.read();
                if(temp == -1) continue;
                while(temp != 3) {
                    // System.out.print(temp);
                    message += (char) temp;
                    temp = in.read();
                }

                // Set up an android.os.Message with the message String to send to the UI thread
                // for displaying:
                if(!message.equals("")) {
                    Message stringMsg = new Message();
                    Bundle bundle = new Bundle();
                    bundle.putString("message", message);
                    stringMsg.setData(bundle);
                    mainHandler.sendMessage(stringMsg);
                }

                if(message.contains("Server Message: $exit")) {
                    running = false;
                }

            } catch(IOException e) {
                Log.v(this.TAG, "receiveMessage: IOException");
                break;
            } catch(Exception e) {
                System.exit(-1);
            }
        }
    }
}

/*
////////////////////////////////////////////////////////////////////////////////////////////////////
// Exercise Class: Threading and Handler exercise in Android:
class ContinuousText extends Thread {
    Handler mainHandler;
    boolean running;
    boolean terminate;

    ContinuousText(Handler handler) {
        this.mainHandler = handler;
        this.running = true;
        this.terminate = false;
    }

    public synchronized void pause() {
        this.running = false;
    }

    public synchronized void carryOn() {
        this.running = true;
    }

    public synchronized void terminate() {
        this.terminate = true;
    }

    @Override
    public void run() {
        int i = 1;
        while(!terminate) {
            if(!this.running) continue;
            Bundle bundle = new Bundle();
            bundle.putString("message", new String("Test" + i));
            Message message = new Message();
            message.setData(bundle);
            mainHandler.sendMessage(message);
            i++;
            try {
                this.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
*/
