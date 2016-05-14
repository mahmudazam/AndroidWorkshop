package com.temporary.mahmud.chatroom;

import java.lang.ref.WeakReference;
import java.io.*;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Handler;
import java.net.Socket;


public class MainActivity extends AppCompatActivity {
    // Views:
    EditText input;
    TextView display;

    // Network objects:
    Connection connection;

    // Strings:
    String iP;
    String iD;

    // UI thread Handler:
    Handler mainHandler;

    // Counter for number of clicks on the "SEND" button:
    int clickCount;

    // Handler class definition for the UI thread:
    class MainHandler extends Handler {
        private final WeakReference<MainActivity> mainActivityWeakReference;
        MainHandler(MainActivity mainActivity) {
            // This is to avoid memory leaks during garbage collection:
            mainActivityWeakReference = new WeakReference<MainActivity>(mainActivity);
        }

        @Override
        public void handleMessage(Message message) {
            MainActivity mainActivity = mainActivityWeakReference.get();
            if(mainActivity != null) {
                mainActivity.displayString(message.getData().getString("message"));
            }
        }

    }

    // Activity creator:
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        input = (EditText) findViewById(R.id.text_input);
        input.setHint("Input text here...");
        input.setMovementMethod(new ScrollingMovementMethod()); // Making the EditText scrollable
        display = (TextView) findViewById(R.id.display);
        display.setMovementMethod(new ScrollingMovementMethod()); // Making the TextView scrollable
        mainHandler = (Handler) new MainHandler(this); // Creating a Handler for the UI thread
        clickCount = 0; // Initializing the click counter to 0
        displayString("Please enter the IP address of the server: ");
        // ContinuousText ct = new ContinuousText(mainHandler);
        // ct.start();

    }

    // onClick method for the "SEND" button:
    public void send(View view) {

        // Take the String in the EditText as an input:
        String message = input.getText().toString();
        input.setText("");

        // The first input is the server's IP address:
        if(this.clickCount == 0) {
            this.iP = message;
            this.clickCount++;
            displayString("Please enter your ID for the server: ");
            return;
        }

        // The second input is the ID the user chooses:
        if (this.clickCount == 1) {
            this.iD = message;
            displayString("Connecting to server...");
            this.connection = new Connection(this.iD, this.iP, 60000, (Handler) mainHandler);
            Thread connectionThread = new Thread(connection);
            connectionThread.start();
            displayString("Connection established! ");
            this.clickCount++;
            return;
        }

        // The next inputs are passed on to the server as messages:
        this.connection.sendMessage(message);

    }

    // Helper function for displaying Strings existing in the UI thread:
    public void displayString(String toDisplay) {
        display.append("\n");
        display.append(toDisplay);
    }

}

class Connection implements Runnable {
    // Fields:
    // Network objects:
    public Socket sender;
    public Socket receiver;
    public InputStream in;
    public OutputStream out;

    // Strings:
    String iD;
    String iP;
    String message;
    public final String TAG;

    // UI thread Handler:
    Handler mainHandler;

    // Elementary types:
    int port;

    // Constructor:
    Connection(String id, String ip, int p, Handler mh) {
        this.TAG = "Connection";
        this.iD = id;
        this.iP = ip;
        this.port = p;
        this.mainHandler = mh;
        this.message = null;
        // new Thread(this).start();
    }

    // Accessors:
    /*
    public OutputStream getOutputStream() {
        return this.out;
    }

    public InputStream getInputStream() {
        return this.in;
    }
    */

    // Start a thread for receiving messages from the server:
    public void startReception() {
        ReceiveThread receiveThread = new ReceiveThread(this.mainHandler ,this.iD, this.in);
        new Thread(receiveThread).start();
    }

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
                } catch(IOException e) {
                    Log.v(TAG, "sendMessage: IOException");
                }
            }
        }).start();
    }

    // Runnable method:
    public void run() {
        try {
            this.sender = new Socket(iP, port);
            this.receiver = new Socket(iP, port);
            this.out = sender.getOutputStream();
            this.in = receiver.getInputStream();
            this.out.write(this.iD.getBytes());
            this.out.write("\n".getBytes());
            if((sender == null) || (receiver == null) || (out == null) || (in == null)) {
                System.exit(-1);
            } else {
                this.startReception();
            }

        } catch(IOException e) {
            Log.v(TAG, "IOException while connecting to server...");
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }
    }

}

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

class ReceiveThread implements Runnable {
    // Fields:
    // Network objects:
    Socket receiveSocket;
    InputStream in;

    // Strings:
    String message;
    String iD;
    String TAG;

    // Handler for UI thread:
    Handler mainHandler;

    // Primaries:
    int temp;
    boolean running;

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

                // Close the program if the user enters an exit command and it passes through
                // the server back to the client program:
                if(message.contains(iD + ": $exit")) {
                    Log.v(this.TAG, "ChatRoom will close. ");
                    System.exit(0);
                }
            } catch(IOException e) {
                Log.v(this.TAG, "receiveMessage: IOException");
                break;
            }
        }
    }
}
