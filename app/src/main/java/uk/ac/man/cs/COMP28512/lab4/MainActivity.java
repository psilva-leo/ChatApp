package uk.ac.man.cs.COMP28512.lab4;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * Created by psilv_000 on 21/03/2016.
 */
public class MainActivity extends Activity implements RegisterDialog.Communicator{
    //TAGs
    private static final String LOGTAG = "MainPage";
    public static final String EXTRA_NAME = "NAME";

    private boolean registered = false;
    private String clientName;
    private boolean serverOn = false;

    SocketService mService;
    boolean mBound = false;

    private int mInterval = 10000; // refresh the online users every 30 seconds
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(LOGTAG, "onCreate entered");
        Log.d(LOGTAG, "Reading config");

        loadConfig();

        loadServerStatus();

        if(!serverOn) {
            Log.d(LOGTAG, "STARTING SERVICE");
            // Start Client-side to communicate with server
            startService();

            TableLayout view = (TableLayout) findViewById(R.id.table);
            view.setVerticalScrollBarEnabled(true);
        }
        mHandler = new Handler();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOGTAG, "ONSTART ENTERED");
        // Bind to LocalService
        Log.d(LOGTAG, "Binding Service");
        Intent intent = new Intent(this, SocketService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LOGTAG, "ONSAVEINSTANCESTATE ENTERED ");
        outState.putBoolean("mBound", mBound);
        outState.putBoolean("registered", registered);
        outState.putString("clientName", clientName);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(LOGTAG, "ONRESTOREINSTANCESTATE ENTERED");
        mBound = savedInstanceState.getBoolean("mBound");
        clientName = savedInstanceState.getString("clientName");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOGTAG, "ONSTOP ENTERED");
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOGTAG, "ONRESUME ENTERED");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(LOGTAG, "ONRESTART ENTERED");
        mBound = true;
        loadConfig();
        startRepeatingTask();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "ONRDESTROY ENTERED");
        super.onDestroy();
        stopRepeatingTask();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(LOGTAG,"Service connected to Main");
            SocketService.LocalBinder binder = (SocketService.LocalBinder) service;
            mService = binder.getService();
            if(!mService.isBinded()){
                mBound = true;
                setupService();
            }else {
                restoreService();
                mBound = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(LOGTAG,"Service disconnected from Main");
            mBound = false;
        }
    };

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                setupChats(); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mStatusChecker, mInterval);
            }
        }
    };

    void startRepeatingTask() {
        mStatusChecker.run();
    }

    void stopRepeatingTask() {
        mHandler.removeCallbacks(mStatusChecker);
    }

    public void setupChats(){
        Log.d(LOGTAG, "Refresing who is online");
        mService.silentSend("WHO");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ArrayList<String> isOnline = mService.getOnline();
        TableLayout table = (TableLayout) findViewById(R.id.table);
        table.removeAllViews();


        for(final String s:isOnline){
            if(s.equals(clientName)) continue;
            TableRow row = new TableRow(this);
            TextView text = new TextView(this);
            text.setTextSize(20);
            text.setText(s);
            text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openChat(s);
                }
            });
            row.addView(text);
            table.addView(row);
        }
    }

    public void restoreService(){
        mService.setCurrentActivity(this);
        setupChats();
        startRepeatingTask();
    }
    public void setupService(){
        mService.setCurrentActivity(this);
        mService.startSocket();
        while (!mService.isBinded()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // If not registered prompt dialog to register
        // save clientname in config file
        if (registered) {
            Log.d(LOGTAG, "Registering client");
            mService.setClientName(clientName);
            mService.silentSend("REGISTER " + clientName);
            mService.silentSend("WHO");
        }else{
            Log.d(LOGTAG, "Writing config");
            final ViewGroup viewGroup = (ViewGroup) ((ViewGroup) this
                    .findViewById(android.R.id.content)).getChildAt(0);
            showDialog(viewGroup);
        }
        setupChats();
        startRepeatingTask();
    }

    public void openChat(String name){
        Log.v(LOGTAG, "Opening chat");
        stopRepeatingTask();
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(EXTRA_NAME, name);
        startActivity(intent);
    }

    private void startService() {
        Intent intent = new Intent(this, SocketService.class);
        startService(intent);
        Log.d(LOGTAG, "Service started");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void showDialog(View v){
        Log.d(LOGTAG, "ShowDialog");
        RegisterDialog registerDialog = new RegisterDialog();
        Log.d(LOGTAG, "ShowDialog");
        registerDialog.show(getFragmentManager(), "registerDialog");
    }

    public void loadConfig() {
        try {
            FileInputStream fileIn = openFileInput("config.txt");
            InputStreamReader InputRead = new InputStreamReader(fileIn);
            BufferedReader buffer = new BufferedReader(InputRead);
            String str;
            while ((str = buffer.readLine()) != null) {
                Log.d(LOGTAG, "Client name: "+str);
                clientName = str;
                registered = true;
            }

        } catch (Exception e) {
            Log.d(LOGTAG,"Error Reading config");
        }
    }

    public void loadServerStatus() {
        try {
            FileInputStream fileIn = openFileInput("serverStatus.txt");
            InputStreamReader InputRead = new InputStreamReader(fileIn);
            BufferedReader buffer = new BufferedReader(InputRead);
            String str;
            while ((str = buffer.readLine()) != null) {
                Log.d(LOGTAG, "Server status: "+str);
                if(str.equals("on")) {
                    serverOn = true;
                }else {
                    serverOn = false;
                }
            }

        } catch (Exception e) {
            Log.d(LOGTAG,"Error Reading server status");
        }
    }

    public void writeConfig(){
        try {
            FileOutputStream fileout = openFileOutput("config.txt", MODE_PRIVATE);
            OutputStreamWriter outputWriter=new OutputStreamWriter(fileout);
            outputWriter.write(clientName);
            outputWriter.close();

            //display file saved message
            Log.d(LOGTAG, "Config saved successfully");

        } catch (Exception e) {
            Log.d(LOGTAG, "Error writing config");
        }
    }

    @Override
    public void onDialogSignUp(String message) {
        clientName = message;
        Toast.makeText(getApplicationContext(),message, Toast.LENGTH_SHORT).show();
        mService.setClientName(clientName);
        mService.silentSend("REGISTER " + clientName);
        mService.silentSend("WHO");
        registered = true;
        writeConfig();
    }

    @Override
    public void onDialogCancel() {
        mService.disconnect();
        System.exit(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
