package uk.ac.man.cs.COMP28512.lab4;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class ChatActivity extends Activity{

    private static final String LOGTAG = "Chat";
    private static final String BTNTAG = "ButtonPressed";

    SocketService mService;
    boolean mBound = false;
    String chatName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Log.i(LOGTAG, "onCreate entered");

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                chatName= null;
            } else {
                chatName= extras.getString(MainActivity.EXTRA_NAME);
            }
        } else {
            chatName= (String) savedInstanceState.getSerializable(MainActivity.EXTRA_NAME);
        }
        setTitle("Chat "+chatName);

        //Makes the receiving text area scrollable
        TextView tv = (TextView) findViewById(R.id.txtServerResponse);
        tv.setMovementMethod(new ScrollingMovementMethod());

        // Setting functionality to kill button
        Button killBtn = (Button) findViewById(R.id.btnKill);
        killBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(BTNTAG, "Closing program");
                Intent intent = new Intent("SocketService");
                stopService(intent);
                System.exit(0);
            }
        });

        // Setting functionality to send button
        // Sends message if it is connected and if the message is not empty.
        Button sendBtn = (Button) findViewById(R.id.btnSendCmd);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                EditText cmdText = (EditText) findViewById(R.id.cmdInput);
                String command = cmdText.getText().toString();

                if (command.equals("")) {
                    CharSequence text = "Type command to send";
                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(BTNTAG, "Sending message:" + command);
                    mService.send(command);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Log.d(LOGTAG,"Bind Service to chat");
        Intent intent = new Intent(this, SocketService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(LOGTAG, "ONSTART ENTERED");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOGTAG, "ON RESUME ENTERED");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void setupService(){
        mService.silentSend("INVITE "+chatName);
        mService.setCurrentActivity(this);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(LOGTAG,"Service connected to chat");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SocketService.LocalBinder binder = (SocketService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            setupService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(LOGTAG,"Service disconnected from chat");
            mBound = false;
        }
    };
}
