package com.hfast.fakenotification;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.hfast.fakenotification.R;

import org.jivesoftware.smack.ConnectionConfiguration;
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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import com.hfast.fakenotification.PebbleKit;
import com.hfast.fakenotification.util.PebbleDictionary;


/**
 * Messaging format: <tag></tag>
 * There are four tags: device, type, sender, and content
 * device is either "android" or "pebble" (case sensitive)
 * type is either "call" or "text"
 * e.g. <device>android</device><type>text</type><sender>Carl</sender><content>Hello Android</content>
 */

public class Main extends Activity {

    public final static String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
    public final static String EXTRA_NUMBER = "com.example.myfirstapp.NUMBER";
    public final static String EXTRA_CALLER = "com.example.myfirstapp.CALLER";
    public final static String EXTRA_SENDER = "com.example.myfirstapp.SENDER";
    public final static String EXTRA_FILENAME = "com.example.myfirstapp.FILENAME";
    public final static String EXTRA_FILELENGTH = "com.example.myfirstapp.FILELENGTH";
    public static final String TEXT_STAT = "TextStatus";


    private List<TextMessage> textList = new ArrayList<TextMessage>();
    private MyAdapter listAdapter;
    private int id = 0;

    private PebbleKit.PebbleDataReceiver mReceiver;

    PowerManager.WakeLock TempWakeLock;
    ListView listView;
    NotificationManager mNotificationManager;
    ConnectionService mService;
    boolean mBound = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println("Creating Main");

        PowerManager TempPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        TempWakeLock = TempPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TempWakeLock");
        if (!TempWakeLock.isHeld()) {
            TempWakeLock.acquire();
        }
        //initiates Smack client and calls up connect function on a separate thread
        SmackAndroid.init(getApplicationContext());
        Intent intent = new Intent(this, ConnectionService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        this.registerReceiver(displayAndroidCall, new IntentFilter("androidcall"));
        this.registerReceiver(displayAndroidText, new IntentFilter("androidtext"));
        this.registerReceiver(displayPebbleCall, new IntentFilter("pebblecall"));
        this.registerReceiver(displayPebbleText, new IntentFilter("pebbletext"));
        this.registerReceiver(addSelfMessage, new IntentFilter("selfmessage"));
        this.registerReceiver(declineWithMessage, new IntentFilter("declineWithMessage"));

        mReceiver = new PebbleKit.PebbleDataReceiver(UUID.fromString("13c15dd2-7e2c-4712-8e88-d707d2912093")) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                //ACK the message
                PebbleKit.sendAckToPebble(context, transactionId);

                //Check the key exists
                if(data.getUnsignedInteger(KEY_BUTTON_EVENT) != null) {
                    int button = data.getUnsignedInteger(KEY_BUTTON_EVENT).intValue();

                    switch (button) {
                        case BUTTON_EVENT_UP:
                            //The UP button was pressed
                           mService.logMessage("Pebble: Call Accepted");
                            break;
                        case BUTTON_EVENT_DOWN:
                            //The DOWN button was pressed
                            mService.logMessage("Pebble: Call Declined");
                            break;
                        case BUTTON_EVENT_SELECT:
                            //The SELECT button was pressed
                           mService.logMessage("Pebble: Preset User Message Played");
                            break;
                    }
                }
            }
        };
        PebbleKit.registerReceivedDataHandler(this, mReceiver);

        listAdapter = new MyAdapter(this, textList);
        listView = (ListView) findViewById(R.id.inboxList);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent resultIntent = new Intent(getApplicationContext(), DisplayNewMessage.class);
                TextMessage text = (TextMessage) parent.getItemAtPosition(position);
                resultIntent.putExtra(EXTRA_SENDER, text.getSender());
                resultIntent.putExtra(EXTRA_MESSAGE,(ArrayList) text.getAllMessages());
                try {
                    mNotificationManager.cancel(text.getSender(), 1);
                } catch (NullPointerException e){
                    System.out.println("nothing wrong here");
                } finally {
                    mService.logMessage("Selected " + text.getSender() + "'s messages");
                    startActivity(resultIntent);
                }

            }
        });


//        SharedPreferences textStat = getSharedPreferences(TEXT_STAT, 0);
//        boolean isThereText = textStat.getBoolean("isThereText", false);
//        if(isThereText){
//            String message = textStat.getString("message", "");
//            String sender = textStat.getString("sender", "");
//
//            // Creates an explicit intent for an Activity in your app
//            Intent resultIntent = new Intent(this, DisplayNewMessage.class);
//            resultIntent.putExtra(EXTRA_MESSAGE, message);
//            resultIntent.putExtra(EXTRA_SENDER, sender);
//
//            startActivity(resultIntent);
//        }

    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.LocalBinder binder = (ConnectionService.LocalBinder) service;
            mService = binder.getService();
            mService.logMessage("Start:Android app started");
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private BroadcastReceiver declineWithMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            String caller = intent.getStringExtra("caller");

            Intent resultIntent = new Intent(getApplicationContext(), DisplayNewMessage.class);
            resultIntent.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

            boolean contains = false;
            TextMessage textMessage = null;

            if (textList.isEmpty()) {
                textMessage = new TextMessage(caller, "Self:" + message, id);
                textList.add(0, textMessage);
                id++;
                resultIntent.putExtra(EXTRA_MESSAGE, (ArrayList) textMessage.getAllMessages());
                resultIntent.putExtra(EXTRA_SENDER, caller);
                listAdapter.notifyDataSetChanged();
            } else {

                for (TextMessage text : textList) {
                    if (caller.contentEquals(text.getSender())) {
                        text.addSelfMessage(message);
                        resultIntent.putExtra(EXTRA_MESSAGE,(ArrayList) text.getAllMessages()); //change to getAllMessages() once we figure out how to display more than one message
                        resultIntent.putExtra(EXTRA_SENDER, text.getSender());
                        textMessage = text;
                        contains = true;
                    }
                }
                if (contains) {
                    textList.remove(textMessage);
                    textList.add(0, textMessage);
                    listAdapter.notifyDataSetChanged();
                } else {
                    textMessage = new TextMessage(caller, "Self" + message, id);
                    textList.add(0, textMessage);
                    id++;
                    resultIntent.putExtra(EXTRA_MESSAGE,(ArrayList)textMessage.getAllMessages());
                    resultIntent.putExtra(EXTRA_SENDER, caller);
                    listAdapter.notifyDataSetChanged();
                }

            }

            startActivity(resultIntent);

        }
    };

    private BroadcastReceiver addSelfMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            String sender = intent.getStringExtra("sender");
            TextMessage textMessage = null;



            for (TextMessage text : textList) {
                if (sender.contentEquals(text.getSender())) {
                    text.addSelfMessage(message);
                    textMessage = text;
                }
            }
            textList.remove(textMessage);
            textList.add(0, textMessage);
            listAdapter.notifyDataSetChanged();
        }
    };

    private BroadcastReceiver displayAndroidCall = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("received call broadcast");
            String caller = intent.getStringExtra("sender");
            String number = intent.getStringExtra("content");
            String filename = intent.getStringExtra("filename");
            String filelength = intent.getStringExtra("filelength");

            Intent resultIntent = new Intent(getApplicationContext(), PhoneCall.class);
            resultIntent.putExtra(EXTRA_CALLER, caller);
            resultIntent.putExtra(EXTRA_NUMBER, number);
            resultIntent.putExtra(EXTRA_FILENAME, filename);
            resultIntent.putExtra(EXTRA_FILELENGTH, filelength);
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            startActivity(resultIntent);

        }
    };

    private BroadcastReceiver displayAndroidText = new BroadcastReceiver() {
        @Override

        public void onReceive(Context context, Intent intent) {
            String sender = intent.getStringExtra("sender");
            String message = intent.getStringExtra("content");

            if(getResultData() == null) {
                Notification.Builder builder = new Notification.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(sender)
                        .setContentText(message);


                // Creates an explicit intent for an Activity in your app
                Intent resultIntent = new Intent(getApplicationContext(), DisplayNewMessage.class);
                TextMessage textMessage = null;
                boolean contains = false;
                if (textList.isEmpty()) {
                    textMessage = new TextMessage(sender, message, id);
                    textList.add(0, textMessage);
                    id++;
                    resultIntent.putExtra(EXTRA_MESSAGE, (ArrayList) textMessage.getAllMessages());
                    resultIntent.putExtra(EXTRA_SENDER, sender);
                    listAdapter.notifyDataSetChanged();
                } else {

                    for (TextMessage text : textList) {
                        if (sender.contentEquals(text.getSender())) {
                            text.addMessage(message);
                            resultIntent.putExtra(EXTRA_MESSAGE,(ArrayList) text.getAllMessages()); //change to getAllMessages() once we figure out how to display more than one message
                            resultIntent.putExtra(EXTRA_SENDER, text.getSender());
                            textMessage = text;
                            contains = true;
                        }
                    }
                    if (contains) {
                        textList.remove(textMessage);
                        textList.add(0, textMessage);
                        listAdapter.notifyDataSetChanged();
                    } else {
                        textMessage = new TextMessage(sender, message, id);
                        textList.add(0, textMessage);
                        id++;
                        resultIntent.putExtra(EXTRA_MESSAGE,(ArrayList)textMessage.getAllMessages());
                        resultIntent.putExtra(EXTRA_SENDER, sender);
                        listAdapter.notifyDataSetChanged();
                    }

                }

                // Creates the vibrate, color flash, and tone
                Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(500);

                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
                builder.setAutoCancel(true);
                builder.setLights(Color.BLUE, 500, 500);


                PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), textMessage.getId(), resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                builder.setContentIntent(resultPendingIntent);
                mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.notify(sender, 1, builder.build());
                mService.logMessage("Created text message notification from " + sender);

//        SharedPreferences settings = getSharedPreferences(TEXT_STAT, 0);
//        SharedPreferences.Editor editor = settings.edit();
//        editor.putBoolean("isThereText", true);
//        editor.putString("message", message);
//        editor.putString("sender", sender);
//        editor.commit();
//        startActivity(resultIntent);
            } else{

                TextMessage textMessage = null;
                boolean contains = false;
                if (textList.isEmpty()) {
                    textMessage = new TextMessage(sender, message, id);
                    textList.add(0, textMessage);
                    id++;
                    listAdapter.notifyDataSetChanged();
                } else {

                    for (TextMessage text : textList) {
                        if (sender.contentEquals(text.getSender())) {
                            text.addMessage(message);
                            textMessage = text;
                            contains = true;
                        }
                    }
                    if (contains) {
                        textList.remove(textMessage);
                        textList.add(0, textMessage);
                        listAdapter.notifyDataSetChanged();
                    } else {
                        textMessage = new TextMessage(sender, message, id);
                        textList.add(0, textMessage);
                        id++;
                        listAdapter.notifyDataSetChanged();
                    }

                }
            }

        }
    };

    private static final int
            KEY_BUTTON_EVENT = 0,
            BUTTON_EVENT_UP = 1,
            BUTTON_EVENT_DOWN = 2,
            BUTTON_EVENT_SELECT = 3,
            DISPLAY_CALL_CALLER = 4,
            DISPLAY_CALL_NUMBER = 5;


    private BroadcastReceiver displayPebbleCall = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String caller = intent.getStringExtra("sender");
            String number = intent.getStringExtra("content");

            //Make the watch vibrate
            PebbleDictionary dict = new PebbleDictionary();
            dict.addString(DISPLAY_CALL_CALLER, caller);
            dict.addString(DISPLAY_CALL_NUMBER, number);
            PebbleKit.sendDataToPebble(context, UUID.fromString("13c15dd2-7e2c-4712-8e88-d707d2912093"), dict);


        }
    };

    private BroadcastReceiver displayPebbleText = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String sender = intent.getStringExtra("sender");
            String message = intent.getStringExtra("content");

            boolean connected = PebbleKit.isWatchConnected(getApplicationContext());
            Log.i(getLocalClassName(), "Pebble is " + (connected ? "connected" : "not connected"));

            final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

            final Map<String, String> data = new HashMap<String, String>();
            data.put("title", sender);
            data.put("body", message);

            final JSONObject jsonData = new JSONObject(data);
            final String notificationData = new JSONArray().put(jsonData).toString();
            i.putExtra("messageType", "PEBBLE_ALERT");
            i.putExtra("sender", "Test");
            i.putExtra("notificationData", notificationData);

            Log.d("Test", "Sending to Pebble: " + notificationData);
            sendBroadcast(i);


        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        new Thread (new Runnable() {
            @Override
            public void run() {
                while (!mBound){}
                mService.logMessage("Displaying main Messages inbox");
            }
        }).start();

        System.out.println("Starting Main");

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        System.out.println("Restarting Main");
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("Pausing Main");
    }

    @Override
    protected void onStop() {
        super.onStop();
        mService.logMessage("Closing main Messages inbox");
        System.out.println("Stopping Main");
    }


    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("Resuming Main");
    }



    //Prevent app from calling onDestroy() when back button is pressed.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {

        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)
        {
            mService.logMessage("Clicked back button from main Messages activity");
            this.moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.out.println("Destroying Main");
        mService.logMessage("Android app stopped");
        TempWakeLock.release();
        unregisterReceiver(displayAndroidText);
        unregisterReceiver(displayAndroidCall);
        unregisterReceiver(displayPebbleCall);
        unregisterReceiver(displayPebbleText);
        unregisterReceiver(addSelfMessage);
        unregisterReceiver(declineWithMessage);
        unregisterReceiver(mReceiver);
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        stopService(new Intent(Main.this,ConnectionService.class    ));
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
