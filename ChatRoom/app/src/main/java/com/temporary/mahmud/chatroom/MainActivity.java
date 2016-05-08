package com.temporary.mahmud.chatroom;

import java.lang.ref.WeakReference;
import java.util.*;
import java.io.*;
import java.net.*;

import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.os.Handler;

import java.net.Socket;
import java.util.logging.LogRecord;

public class MainActivity extends AppCompatActivity {
    EditText input;
    TextView display;
    String text;
    ContinuousText ct;
    Socket sender;
    Socket reciever;
    Handler mainHandler;

    class MainHandler extends Handler {
        private final WeakReference<MainActivity> mainActivityWeakReference;
        MainHandler(MainActivity mainActivity) {
            mainActivityWeakReference = new WeakReference<MainActivity>(mainActivity);
        }
        @Override
        public void handleMessage(Message message) {
            MainActivity mainActivity = mainActivityWeakReference.get();
            if(mainActivity != null) {
                display.append("\n");
                display.append("Text");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        input = (EditText) findViewById(R.id.text_input);
        input.setMovementMethod(new ScrollingMovementMethod());
        input.setHint("Input text here...");
        display = (TextView) findViewById(R.id.display);
        mainHandler = (Handler) new MainHandler(this);
        ct = new ContinuousText(mainHandler);
        ct.start();
    }

    public void send(View view) {
        String message = input.getText().toString();
        if(message.equals("$exit")) System.exit(1);
        if(message.equals("$pause")) this.ct.pause();
        if(message.equals("$resume")) this.ct.carryOn();
        this.displayString(message);
        input.setText("");
    }

    public void displayString(String toDisplay) {
        display.append("\n");
        display.append(toDisplay);
    }
    // connect();
    // sendID();
    // start ReceiveThread();

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
            mainHandler.sendEmptyMessage(0);
            i++;
            try {
                this.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


