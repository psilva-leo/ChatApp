package uk.ac.man.cs.COMP28512.lab4;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by psilv_000 on 02/04/2016.
 */
public class SocketService extends Service {
    private static final String SERVICETAG = "Service";
    private ServerConnect socket;
    private final IBinder mBinder = new LocalBinder(); // Binder given to clients
    private Activity parentref;
    private int startId;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(SERVICETAG,"Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(SERVICETAG, "Service Started");
        this.startId = startId;
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(SERVICETAG, "Service destroyed");
        socket.disconnect();
        socket.active = false;
    }

    public void startSocket(){
        socket = new ServerConnect(startId,parentref);
        socket.start();
    }

    /** method for clients */
    public void send(String cmd){
        socket.send(cmd);
    }

    public void silentSend(String cmd){
        socket.silentSend(cmd);
    }

    public boolean isBinded(){
        if(socket == null)
            return false;
        else return socket.getBinded();
    }

    public void disconnect(){
        socket.disconnect();
    }

    public void setClientName(String name){
        socket.setClientName(name);
    }

    public ArrayList<String> getOnline(){
        return socket.getOnline();
    }

    public void setCurrentActivity(Activity parentRef){
        parentref = parentRef;
        if(socket != null)
            socket.setCurrentActivity(parentRef);
    }

    class ServerConnect extends Thread{
        private int startid;
        public boolean active = true;

        private Socket socket;
        private static final int SERVERPORT = 9999;         //This is the port that we are connecting to
        //private static final String SERVERIP = "uomlab4.ddns.net";  //This address is magically mapped to the host's loopback.
        private static final String SERVERIP = "10.0.2.2";  //This address is magically mapped to the host's loopback.
        private static final String LOGTAG = "SocketTester";

        private PrintWriter out;
        private BufferedReader in;

        private String clientName;
        private String chatName;
        private ArrayList<String> isOnline = new ArrayList();
        private boolean registered;
        private boolean terminated;
        Activity parentref;
        private boolean binded = false;

        public ServerConnect(int startid, Activity parentRef){
            this.startid = startid;
            clientName = "";
            registered = false;
            terminated = false;
            parentref  = parentRef;

            out = null;
            in  = null;
        }

        /**
         * Main thread loop that grabs incoming messages
         */
        public void run()
        {
            Log.i(SERVICETAG,"Running socket thread");

            Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR);
            int minutes = c.get(Calendar.MINUTE);
            final String date = hour+":"+minutes;

            try
            {
                InetAddress svrAddr = InetAddress.getByName(SERVERIP);
                socket = new Socket(svrAddr, SERVERPORT);

                //Setup i/o streams
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Log.d(SERVICETAG,"Socket binded");
                binded = true;
                serverOnFile();

                //Keep listening for messages from server
                while(!terminated)
                {
                    final String message = in.readLine();
                    if(message != null && message != ""){
                        Log.i(SERVICETAG, "MSG recv : " + message);
                        if(message.toUpperCase().contains("WHO")){
                            Log.d(SERVICETAG, "WHO MESSAGE");
                            whoMessage(message);
                        }else if(message.toUpperCase().contains("INVITE")){
                            Log.d(SERVICETAG, "INVITE MESSAGE");
                            silentSend("ACCEPT " + message.substring(7));
                        }else if(message.substring(0,3).toUpperCase().equals("MSG")){ //Update GUI with any server responses
                            int start = 4;
                            int end = message.indexOf(' ',4);
                            final String destinatary = message.substring(start,end);
                            Log.d(SERVICETAG, "Message to "+destinatary);
                            if(destinatary.equals(chatName)){
                                final EditText cmdText = (EditText) parentref.findViewById(R.id.cmdInput);
                                final TextView txtv = (TextView) parentref.findViewById(R.id.txtServerResponse);
                                parentref.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        int start = message.indexOf(' ', 4);
                                        txtv.append(Html.fromHtml("<font color=blue>"+chatName+"(" + date + "): </font>" + message.substring(start) + "<br>"));
                                        cmdText.setText("");
                                        writeLog("<font color=blue>"+chatName+"(" + date + "): </font>" + message.substring(start) + "<br>",chatName);

                                        final int scrollAmount = txtv.getLayout().getLineTop(txtv.getLineCount()) - txtv.getHeight();
                                        // if there is no need to scroll, scrollAmount will be <=0
                                        if (scrollAmount > 0)
                                            txtv.scrollTo(0, scrollAmount);
                                        else
                                            txtv.scrollTo(0, 0);
                                    }
                                });
                            }else{
                                Log.d(SERVICETAG, "Message received out of the chat view");
                                writeLog("<font color=blue>" + destinatary + "(" + date + "): </font>" + message.substring(end+1) + "<br>", destinatary);
                                notifyuser(destinatary);
                            }

                        }
                    }
                }
            } catch (UnknownHostException uhe){
                Log.e(SERVICETAG,"Unknownhost\n"+uhe.getStackTrace().toString());
            }
            catch (Exception e) {
                Log.e(SERVICETAG, "Socket failed\n"+e.getMessage());
                e.printStackTrace();
            }

            Log.i(SERVICETAG,"Could not connect");
            disconnect();
            stopSelf(startid);
            Log.i(SERVICETAG, "Thread now closing");
        }

        public void setCurrentActivity(Activity parentRef){
            Log.d(SERVICETAG, "Changing Acticity");
            this.parentref = parentRef;
            chatName = parentref.getTitle().toString().substring(5);
            if(parentref.getTitle().toString().contains("Chat")){
                loadLog();
            }
        }
        public void disconnect(){
            Log.i(LOGTAG, "Disconnecting from server");
            serverOffFile();
            try{
                out.print("DISCONNECT "+clientName);
                //in.close();
                out.close();
            }
            catch(Exception e){}

            try{
                socket.close();
            }
            catch(Exception e){}
        }

        private void whoMessage(String message){
            Pattern p = Pattern.compile("'([^']*)'");
            Matcher m = p.matcher(message);
            isOnline.clear();
            while (m.find()) {
                Log.d(SERVICETAG, "ISONLINE: " + m.group(1));
                isOnline.add(m.group(1).toString());
            }
        }

        private void loadLog() {
            try {
                final TextView txtv = (TextView) parentref.findViewById(R.id.txtServerResponse);
                FileInputStream fileIn = openFileInput("log"+chatName+".txt");
                InputStreamReader InputRead = new InputStreamReader(fileIn);
                BufferedReader buffer = new BufferedReader(InputRead);
                String str;
                txtv.setText("");
                while ((str = buffer.readLine()) != null) {
                    txtv.append(Html.fromHtml(str));
                }

            } catch (Exception e) {
                Log.d(LOGTAG,"Error Reading log");
            }

        }

        private void writeLog(String message, String chatName){
            try {
                FileOutputStream fileout = openFileOutput("log"+chatName+".txt", MODE_APPEND);
                OutputStreamWriter outputWriter=new OutputStreamWriter(fileout);
                outputWriter.write(message);
                outputWriter.close();

                //display file saved message
                Log.d(LOGTAG, "log saved successfully");

            } catch (Exception e) {
                Log.d(LOGTAG, "Error writing log");
            }
        }

        private void serverOnFile(){
            try {
                FileOutputStream fileout = openFileOutput("serverStatus.txt", MODE_PRIVATE);
                OutputStreamWriter outputWriter=new OutputStreamWriter(fileout);
                outputWriter.write("on");
                outputWriter.close();

                //display file saved message
                Log.d(LOGTAG, "Server Status saved successfully");

            } catch (Exception e) {
                Log.d(LOGTAG, "Error writing Server Status");
            }
        }

        private void serverOffFile(){
            try {
                FileOutputStream fileout = openFileOutput("serverStatus.txt", MODE_PRIVATE);
                OutputStreamWriter outputWriter=new OutputStreamWriter(fileout);
                outputWriter.write("off");
                outputWriter.close();

                //display file saved message
                Log.d(LOGTAG, "Server Status saved successfully");

            } catch (Exception e) {
                Log.d(LOGTAG, "Error writing Server Status");
            }
        }

        private void notifyuser(String chatName){
            int mId = 0;
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(parentref)
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setContentTitle(chatName)
                            .setContentText("new message")
                            .setAutoCancel(true);
            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(parentref, ChatActivity.class);
            resultIntent.putExtra(MainActivity.EXTRA_NAME, chatName);

            // The stack builder object will contain an artificial back stack for the
            // started Activity.
            // This ensures that navigating backward from the Activity leads out of
            // your application to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(parentref);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(ChatActivity.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                    stackBuilder.getPendingIntent(
                            0,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // mId allows you to update the notification later on.
            mNotificationManager.notify(mId, mBuilder.build());
        }

        /** method for clients */
        public boolean getBinded(){
            return binded;
        }

        public ArrayList<String> getOnline(){
            return isOnline;
        }

        public void setClientName(String name){
            clientName = name;
        }
        public void send(String cmd) {

            final EditText cmdText = (EditText) parentref.findViewById(R.id.cmdInput);
            final TextView txtv = (TextView) parentref.findViewById(R.id.txtServerResponse);

            Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR);
            int minutes = c.get(Calendar.MINUTE);
            String date = hour + ":" + minutes;
            try {
                Log.i(LOGTAG, "Sending command: " + cmd);
                out.print("MSG " + chatName + " " + cmd);
                out.flush();

                txtv.append(Html.fromHtml("<font color=green>" + clientName + " (" + date + "): </font>" + cmd + "<br>"));
                cmdText.setText("");
                writeLog("<font color=green>"+clientName+" (" + date + "): </font>" + cmd + "<br>",chatName);

                if (cmd.toUpperCase().contains("DISCONNECT") && registered) {
                    clientName = "";
                    registered = false;
                }

            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to send command : " + e);
                cmdText.setText("");
            }

        }

        public void silentSend(String cmd){
            try {
                Log.i(LOGTAG, "Sending command: " + cmd);
                out.print(cmd);
                out.flush();
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to send command : " + e);
            }
        }
    }

    public class LocalBinder extends Binder {
        SocketService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SocketService.this;
        }
    }
}
