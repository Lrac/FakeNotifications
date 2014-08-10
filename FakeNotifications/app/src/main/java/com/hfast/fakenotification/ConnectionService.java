package com.hfast.fakenotification;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.view.KeyEvent;


import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.ping.PingManager;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Date;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import javax.net.ssl.SSLContext;

/**
 * Created by Carl on 2014-08-01.
 */
public class ConnectionService extends Service {
    //These will need to be changed depending on server and user info
    public static final String HOST = "10.0.2.2"; //wireless IPv4 address, 192.168.0.184 is wayne's address
    public static final int PORT = 5222; //default port
    public static final String USERNAME = "android";
    public static final String PASSWORD = "12345";

    private double sendTime;
    private XMPPConnection conn;
    private Chat chat;
    private final IBinder mBinder = new LocalBinder();
    private boolean chatInitiated = false;
    private long startTime;


    @Override
    public void onCreate() {
        super.onCreate();
        SmackAndroid.init(getApplicationContext());
        new Thread(new Runnable() {
            @Override
            public void run() {
                //set up connection configurations

                ConnectionConfiguration configuration = new ConnectionConfiguration(HOST, PORT, "Carl");
                configuration.setDebuggerEnabled(true);
                configuration.setCompressionEnabled(true);
                try {
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, MemorizingTrustManager.getInstanceList(getApplicationContext()), new SecureRandom());
                    configuration.setCustomSSLContext(sc);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                } catch (KeyManagementException e) {
                    throw new IllegalStateException(e);
                }


                conn = new XMPPTCPConnection(configuration);


                //attempts to connect and login to XMPP server
                try {
                    conn.connect();
                    conn.login(USERNAME, PASSWORD);
                } catch (XMPPException e) {
                    conn = null;
                    e.printStackTrace();
                } catch (SmackException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                PingManager mPingManager = PingManager.getInstanceFor(conn);
                mPingManager.setPingInterval(15);
                System.out.println("Ping Interval: " + mPingManager.getPingInterval() + "seconds");
                try {
                    if (mPingManager.pingMyServer()) {
                        System.out.println("pinged successfully");
                    } else {
                        System.out.println("not pinged successfully");
                    }
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }

                if (conn.isConnected()) {
                    System.out.println("isConnected");
                } else {
                    System.out.println("notConnected");
                }

                if (conn.isAuthenticated()) {
                    System.out.println("isLoggedIn");
                } else {
                    System.out.println("notLoggedIn");
                }

                //set status to available
                Presence presence = new Presence(Presence.Type.available);
                try {
                    conn.sendPacket(presence);
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }

                //Sets up a Listener to receive any incoming messages
                PacketFilter filter = new MessageTypeFilter(Message.Type.chat);
                conn.addPacketListener(new PacketListener() {

                    public void processPacket(Packet packet) {
                        final Message message = (Message) packet;
                        System.out.println("Received message:" + message.getBody());
                        logMessage("Received message: " + message.getBody());
                        long time = (System.currentTimeMillis());
                        sortMessage(message.getBody());
//                        double receiveTime = time;
//                        try {
//                            receiveTime += SntpClient.main("tick.utoronto.ca");
//                        } catch (IOException e){
//                            e.printStackTrace();
//                        }
//                        String sendTimeS[] = extract(message.getBody(),new String[]{"sendTime"});
//                        sendTime = Double.parseDouble(sendTimeS[0]);
//                        Double timeDelay = receiveTime - sendTime;
//                        System.out.println("Time Delay: " + new DecimalFormat("0.00").format(timeDelay) + " ms");
                    }
                }, filter);

                chat = ChatManager.getInstanceFor(conn).createChat("lrac@carl/Smack", new MessageListener() {
                    @Override
                    public void processMessage(Chat chat, Message message) {

                    }
                });
                chatInitiated = true;

            }
        }).start();
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Messages")
                .setContentText("connected to XMPP service")
                .build();

        Intent i=new Intent(this, Main.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi= PendingIntent.getActivity(this, 0,
                i, 0);
        startForeground(1337, notification);

        return super.onStartCommand(intent, flags, startId);
    }


    //Parses and sorts messages passed from the Listener
    public void sortMessage(String message){
        String[] tags = new String[]{"device", "type", "sender", "content"};
        String[] parsedMessage = extract(message, tags);
        String device = parsedMessage[0].toLowerCase();
        String type = parsedMessage[1].toLowerCase();
        String sender = parsedMessage[2];
        String content = parsedMessage[3];
        if(sender == null){
            System.out.println("Error: incorrect sender tag");
            return;
        }
        if(content == null){
            System.out.println("Error: incorrect content tag");
            return;
        }

        Intent intent = new Intent(device+type);
        intent.putExtra("sender", sender);
        intent.putExtra("content", content);
        System.out.println("Sending broadcast message: " + device+type);
        this.sendOrderedBroadcast(intent, null);


    }

    //extracts the content from the tags into an array of strings
    public String[] extract(String message, String tags[]){
        int length = tags.length;
        String[] content = new String[length];
        for (int i = 0; i < length; i++){
            int index1 = message.indexOf("<" + tags[i] + ">");
            if (index1<0) break;
            index1 += (2 + tags[i].length());
            int index2 = message.indexOf("</" + tags[i] + ">");
            if (index2<0) break;
            content[i] = message.substring(index1, index2);
        }
        return content;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        startTime = intent.getLongExtra("startTime", System.currentTimeMillis());
        return mBinder;
    }

    public void logMessage(String message){
        DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
        String date = df.format(new Date(System.currentTimeMillis()));
        if(message.startsWith("Start:")){
            try {
                date = df.format(new Date(startTime));
                while (!chatInitiated){}
                chat.sendMessage(date + ": " + message.substring(6));
            } catch (XMPPException e) {
                e.printStackTrace();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        } else {
            try {
                chat.sendMessage(date + ": " + message);
            } catch (XMPPException e) {
                e.printStackTrace();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }
    }

    public class LocalBinder extends Binder {
        ConnectionService getService(){
            return ConnectionService.this;
        }
    }
}
