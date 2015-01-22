package com.hfast.fakenotification;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.support.v4.app.NotificationCompat.WearableExtender;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.NotificationCompat.Action;

import com.hfast.fakenotification.util.PebbleDictionary;

import org.jivesoftware.smack.SmackAndroid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


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

    public final static UUID PEBBLE_APP_UUID = UUID.fromString("13c15dd2-7e2c-4712-8e88-d707d2912093");
    public final static UUID PEBBLE_TEXT_UUID = UUID.fromString("0f415f6f-2b01-480a-966b-594b2d820ad2");

    public static final int STATE_CONNECTION_STARTED = 0;
    public static final int STATE_CONNECTION_LOST = 1;
    public static final int READY_TO_CONN = 2;
    public static final int MESSAGE_READ = 3;


    private List<TextMessage> textList = new ArrayList<TextMessage>();
    private List<String> senders = new ArrayList<String>();

    private MyAdapter listAdapter;
    private int id = 0;

    private int[] messageCount = new int[4];
    private PebbleKit.PebbleDataReceiver mReceiver;
    private PebbleKit.PebbleDataReceiver textReceiver;

    PowerManager.WakeLock TempWakeLock;
    ListView listView;
    NotificationManagerCompat mNotificationManager;
    ConnectionService mService;
    ScreenReceiver mScreenReceiver;
    boolean mBound = false;

    private boolean threadConnected = false;
    private boolean startDisconnecting = false;

    //NotificationCompat.WearableExtender wearableExtender;

    // our last connection
    ConnectedThread mConnectedThread;// = new ConnectedThread(socket);
    // track our connections
    ArrayList<ConnectedThread> mConnThreads;
    // bt adapter for all your bt needs (where we get all our bluetooth powers)
    BluetoothAdapter myBt;
    // list of sockets we have running (for multiple connections)
    ArrayList<BluetoothSocket> mSockets = new ArrayList<BluetoothSocket>();
    // list of addresses for devices we've connected to
    ArrayList<String> mDeviceAddresses = new ArrayList<String>();
    // just a name, nothing more...
    String NAME="G6BITCHES";
    // We can handle up to 7 connections... or something...
    UUID[] uuids = new UUID[2];
    // some uuid's we like to use..
    String uuid1 = "05f2934c-1e81-4554-bb08-44aa761afbfb";
    String uuid2 = "c2911cd0-5c3c-11e3-949a-0800200c9a66";
    // just a tag..
    String TAG = "G6 Bluetooth Host Activity";
    // constant we define and pass to startActForResult (must be >0), that the system passes back to you in your onActivityResult()
    // implementation as the requestCode parameter.
    int REQUEST_ENABLE_BT = 1;
    AcceptThread accThread;
    Handler handle;
    BroadcastReceiver receiver;


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

        IntentFilter screenIntents = new IntentFilter(intent.ACTION_SCREEN_ON);
        screenIntents.addAction(intent.ACTION_SCREEN_OFF);
        screenIntents.addAction(intent.ACTION_USER_PRESENT);

        this.registerReceiver(displayAndroidCall, new IntentFilter("androidcall"));
        this.registerReceiver(displayAndroidText, new IntentFilter("androidtext"));
        this.registerReceiver(displayPebbleCall, new IntentFilter("pebblecall"));
        this.registerReceiver(displayPebbleText, new IntentFilter("pebbletext"));
        this.registerReceiver(displayGlassText, new IntentFilter("glasstext"));
        this.registerReceiver(displayGlassCall, new IntentFilter("glasscall"));
        this.registerReceiver(addSelfMessage, new IntentFilter("selfmessage"));
        this.registerReceiver(declineWithMessage, new IntentFilter("declineWithMessage"));
        this.registerReceiver(mScreenReceiver, screenIntents);

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
                    mService.logMessage("Android: Selected " + text.getSender() + "'s messages");
                    startActivity(resultIntent);
                }

            }
        });


        mReceiver = new PebbleKit.PebbleDataReceiver(PEBBLE_APP_UUID) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                //ACK the message
                PebbleKit.sendAckToPebble(context, transactionId);
                System.out.println("receiving data from pebble");
                //Check the key exists
                if(data.getUnsignedInteger(KEY_BUTTON_EVENT) != null) {
                    int button = data.getUnsignedInteger(KEY_BUTTON_EVENT).intValue();
                    System.out.println("received key button event");
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
                if(data.getUnsignedInteger(KEY_BUTTON_REPLY) != null){
                    int button = data.getUnsignedInteger(KEY_BUTTON_REPLY).intValue();
                    System.out.println("received message reply");
                    switch (button){
                        case REPLY_BUSY:
                            mService.logMessage("Pebble: Replied \"Busy\" message");
                            break;
                        case REPLY_CALLBACK:
                            mService.logMessage("Pebble: Replied \"Callback\" message");
                            break;
                        case REPLY_LOL:
                            mService.logMessage("Pebble: Replied \"Lol\" message");
                            break;
                    }
                }

                if(data.getUnsignedInteger(LOG_MESSAGE) != null) {
                    int message = data.getUnsignedInteger(LOG_MESSAGE).intValue();
                    System.out.println("received message to log");
                        mService.logMessage("Pebble: " + message);


                }
                PebbleKit.registerReceivedAckHandler(getApplicationContext(), new PebbleKit.PebbleAckReceiver(PEBBLE_APP_UUID) {
                    @Override
                    public void receiveAck(Context context, int transactionId) {
                            System.out.println("Received ack for transaction " + transactionId);
                    }
                });

                PebbleKit.registerReceivedNackHandler(getApplicationContext(), new PebbleKit.PebbleNackReceiver(PEBBLE_APP_UUID) {
                    @Override
                    public void receiveNack(Context context, int transactionId) {
                        System.out.println("Received nack for transaction " + transactionId);
                    }
                });
            }
        };
        textReceiver = new PebbleKit.PebbleDataReceiver(PEBBLE_TEXT_UUID) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                //ACK the message
                PebbleKit.sendAckToPebble(context, transactionId);
                System.out.println("receiving data from pebble");
                //Check the key exists
                if(data.getUnsignedInteger(KEY_BUTTON_EVENT) != null) {
                    int button = data.getUnsignedInteger(KEY_BUTTON_EVENT).intValue();
                    System.out.println("received key button event");
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
                        case BUTTON_GO_BACK:
                            mService.logMessage("Pebble: Pressed back from messages screen");
                            break;

                    }
                }
                if(data.getUnsignedInteger(KEY_BUTTON_REPLY) != null){
                    int button = data.getUnsignedInteger(KEY_BUTTON_REPLY).intValue();
                    System.out.println("received message reply");
                    switch (button){
                        case REPLY_BUSY:
                            mService.logMessage("Pebble: Replied \"Busy\" message");
                            break;
                        case REPLY_CALLBACK:
                            mService.logMessage("Pebble: Replied \"Callback\" message");
                            break;
                        case REPLY_LOL:
                            mService.logMessage("Pebble: Replied \"Lol\" message");
                            break;
                        case BUTTON_GO_BACK:
                            mService.logMessage("Pebble: Pressed back from reply screen");
                            break;
                    }
                }
                if(data.getUnsignedInteger(KEY_BUTTON_PERSON) != null){
                    int button = data.getUnsignedInteger(KEY_BUTTON_PERSON).intValue();
                    System.out.println("pebble selected someone's messages");
                    switch (button){
                        case BUTTON_FIRST_PERSON:
                            mService.logMessage("Pebble: User selected " + senders.get(0) + "'s messages");
                            break;
                    }
                }
                if(data.getUnsignedInteger(LOG_MESSAGE) != null) {
                    int message = data.getUnsignedInteger(LOG_MESSAGE).intValue();
                    System.out.println("received message to log");
                    mService.logMessage("Pebble: " + message);


                }
                PebbleKit.registerReceivedAckHandler(getApplicationContext(), new PebbleKit.PebbleAckReceiver(PEBBLE_TEXT_UUID) {
                    @Override
                    public void receiveAck(Context context, int transactionId) {
                        System.out.println("Received ack for transaction " + transactionId);
                    }
                });

                PebbleKit.registerReceivedNackHandler(getApplicationContext(), new PebbleKit.PebbleNackReceiver(PEBBLE_TEXT_UUID) {
                    @Override
                    public void receiveNack(Context context, int transactionId) {
                        System.out.println("Received nack for transaction " + transactionId);
                    }
                });
            }
        };
        PebbleKit.registerReceivedDataHandler(this, mReceiver);
        PebbleKit.registerReceivedDataHandler(this, textReceiver);


        uuids[0] = UUID.fromString(uuid1);
        uuids[1] = UUID.fromString(uuid2);
        handle = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case STATE_CONNECTION_STARTED:
                        Log.i(TAG,"Connected to: " + msg.getData().getString("NAMES"));
                        break;
                    case STATE_CONNECTION_LOST:
                        System.out.println("exited");
                        startListening();
                        break;
                    case READY_TO_CONN:
                        startListening();
                        break;
                    case MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;

                        String readMessage = new String(readBuf, 0, msg.arg1);
                        if (readMessage.length() > 0){
                            mService.logMessage("Glass: " + readMessage);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        // ....
        myBt = BluetoothAdapter.getDefaultAdapter();
        // run the "go get em" thread..
        accThread = new AcceptThread();
        accThread.start();


        mNotificationManager = NotificationManagerCompat.from(getApplicationContext());
//        int notificationId = 001;
//// Build intent for notification content
//        Intent viewIntent = new Intent(this, Main.class);
//        PendingIntent viewPendingIntent =
//                PendingIntent.getActivity(this, 0, viewIntent, 0);
//
//        NotificationCompat.Builder notificationBuilder =
//                new NotificationCompat.Builder(this)
//                        .setSmallIcon(R.drawable.ic_launcher)
//                        .setContentTitle("hey")
//                        .setContentText("testing")
//                        .setContentIntent(viewPendingIntent);
//
//// Get an instance of the NotificationManager service
//        NotificationManagerCompat notificationManager =
//                NotificationManagerCompat.from(this);
//
//// Build the notification and issues it with notification manager.
//        notificationManager.notify(notificationId, notificationBuilder.build());

        // Create a WearableExtender to add functionality for wearables
        /*wearableExtender =
                new NotificationCompat.WearableExtender()
                        .setHintHideIcon(true);

*/


    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.LocalBinder binder = (ConnectionService.LocalBinder) service;
            mService = binder.getService();
            mService.logMessage("Start:Android: Application started");
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };



    ///////////////////////////////////////////ANDROID////////////////////////////////////////////////////

    private BroadcastReceiver displayAndroidCall = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("received call broadcast");
            String caller = intent.getStringExtra("sender");
            String number = intent.getStringExtra("content");
            String audiofile = intent.getStringExtra("audiofile");
            String audiolength = intent.getStringExtra("audiolength");

            Intent resultIntent = new Intent(getApplicationContext(), PhoneCall.class);
            resultIntent.putExtra(EXTRA_CALLER, caller);
            resultIntent.putExtra(EXTRA_NUMBER, number);
            resultIntent.putExtra(EXTRA_FILENAME, audiofile);
            resultIntent.putExtra(EXTRA_FILELENGTH, audiolength);
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
                // Create an intent for the reply action
                Intent actionIntent = new Intent(getApplicationContext(), Main.class);
                PendingIntent actionPendingIntent =
                        PendingIntent.getActivity(getApplicationContext(), 0, actionIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT);

                NotificationCompat.Action action =
                        new NotificationCompat.Action.Builder(R.drawable.ic_launcher,
                                "other option", actionPendingIntent)
                                .build();

                NotificationCompat.Action action2 =
                        new NotificationCompat.Action.Builder(R.drawable.ic_launcher,
                                "other option", actionPendingIntent)
                                .build();


                WearableExtender wearableExtender = new WearableExtender().clearActions();

                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(sender)
                        .setContentText(message)
                        .extend(wearableExtender);


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
                            resultIntent.putExtra(EXTRA_MESSAGE,(ArrayList) text.getAllMessages());
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


                PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), textMessage.getId(),
                        resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                builder.setContentIntent(resultPendingIntent);

                mNotificationManager.notify(sender,1, builder.build());
                mService.logMessage("Android: Created text message notification from " + sender);

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


    /////////////////////////////////////////////////PEBBLE/////////////////////////////////////////////

    private static final int
            KEY_BUTTON_EVENT = 0,
            BUTTON_EVENT_UP = 1,
            BUTTON_EVENT_DOWN = 2,
            BUTTON_EVENT_SELECT = 3,
            DISPLAY_SENDER = 4,
            DISPLAY_CONTENT = 5,
            LOG_MESSAGE = 6,
            STOP_VIBRATE = 7,
            REPLY_BUSY = 8,
            REPLY_CALLBACK = 9,
            REPLY_LOL = 10,
            KEY_BUTTON_REPLY = 11,

            BUTTON_GO_BACK = 12,

            BUTTON_FIRST_PERSON = 13,

            KEY_BUTTON_PERSON = 17,
            NAME_FIRST_PERSON = 18,

            MESSAGE_FIRST_PERSON = 22,
            NEXT_MESSAGE_FIRST_PERSON = 23;



    private BroadcastReceiver displayPebbleCall = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String caller = intent.getStringExtra("sender");
            String number = intent.getStringExtra("content");
            String vibratelength = intent.getStringExtra("vibratelength");

            PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_APP_UUID);

            //start a new thread that will handle starting a stopping the call
            String params[] = new String[]{caller, number, vibratelength};
            new NewPebbleCall().execute(params);

        }
    };

    private class NewPebbleCall extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... params) {

            try {
                Thread.sleep(1000);
                PebbleDictionary dict = new PebbleDictionary();
                dict.addString(DISPLAY_SENDER, params[0]);
                dict.addString(DISPLAY_CONTENT, params[1]);
                dict.addInt32(STOP_VIBRATE, Integer.parseInt(params[2]));
                PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, dict);
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            PebbleDictionary dict2 = new PebbleDictionary();
            dict2.addString(STOP_VIBRATE, "STOP");
            PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, dict2);
            PebbleKit.closeAppOnPebble(getApplicationContext(), UUID.fromString("13c15dd2-7e2c-4712-8e88-d707d2912093"));
            super.onPostExecute(result);
        }
    }


    private BroadcastReceiver displayPebbleText = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String sender = intent.getStringExtra("sender");
            String message = intent.getStringExtra("content");
            String number = intent.getStringExtra("number");

            PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLE_TEXT_UUID);

            System.out.println("PEBBLETEXT SENDER: " + sender + " MESSAGE: " + message + "  NUMBER: " + number);
            PebbleDictionary dict = new PebbleDictionary();
            if (senders.contains(sender)){
                int index = senders.indexOf(sender);
                messageCount[index]++;
                dict.addString(NAME_FIRST_PERSON, sender);
                dict.addString(NEXT_MESSAGE_FIRST_PERSON, message);
                try {
                    Thread.sleep(1000);
                    PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_TEXT_UUID, dict);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }

                System.out.println("old sender");
            } else {
                String[] params;
                senders.add(sender);
                int index = senders.indexOf(sender);
                messageCount[index] = 1;
                switch (index){
                    case 0:
                        params = new String[]{sender, message, ""+ NAME_FIRST_PERSON, "" + MESSAGE_FIRST_PERSON};
                        new NewPebbleText().execute(params);
                        System.out.println(sender + " " + message);
                        break;
                }
                System.out.println("new sender");
            }


        }

    };
    private class NewPebbleText extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {

            try {
                Thread.sleep(1000);
                PebbleDictionary dict = new PebbleDictionary();
                dict.addString(Integer.parseInt(params[2]), params[0]);
                dict.addString(Integer.parseInt(params[3]), params[1]);
                PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_TEXT_UUID, dict);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }



    /////////////////////////////////////////////////GLASS/////////////////////////////////////////////////

    private BroadcastReceiver displayGlassText =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String sender = intent.getStringExtra("sender");
            String message = intent.getStringExtra("content");
            if(threadConnected) {
                mConnectedThread.write(("text<sender>" + sender + "</sender><message>" + message + "</message>").getBytes());
            }
        }
    };

    private BroadcastReceiver displayGlassCall = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String caller = intent.getStringExtra("sender");
            String number = intent.getStringExtra("content");
            String audiofile = intent.getStringExtra("audiofile");
            String audiolength = intent.getStringExtra("audiolength");
            if(threadConnected) {
                mConnectedThread.write(("call<caller>" + caller + "</caller><number>" + number + "</number><audiofile>"
                        + audiofile + "</audiofile><audiolength>" + audiolength + "</audiolength>").getBytes());
            }
        }
    };

    public void startListening() {
        if (mConnectedThread!= null){
            System.out.println("Cancelling connectedThread");
            mConnectedThread.cancel();
            mConnectedThread = null;
            threadConnected = false;
        }else if(accThread!=null) {
            System.out.println("Cancelling old thread");
            accThread.cancel();
            accThread = null;
        } else{
            System.out.println("Restarting accept thread");
            accThread = new AcceptThread();
            accThread.start();
        }
    }

    private class AcceptThread extends Thread {
        private BluetoothServerSocket mmServerSocket;
        BluetoothServerSocket tmp;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = myBt.listenUsingInsecureRfcommWithServiceRecord(NAME, uuids[0]);

            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.e(TAG,"running?");
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {

                try {

                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                // If a connection was accepted

                if (socket != null) {
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG,"error closing server socket");
                        e.printStackTrace();
                    }
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);

                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
                Message msg = handle.obtainMessage(READY_TO_CONN);
                handle.sendMessage(msg);

            } catch (IOException e) { }
        }
    }


    private void manageConnectedSocket(BluetoothSocket socket) {
        // start our connection thread
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        // so the HH can show you it's working and stuff...
        String devs="";
//        for(BluetoothSocket sock: mSockets) {
//            devs+=sock.getRemoteDevice().getName()+"\n";
//        }
        devs = socket.getRemoteDevice().getName()+"\n";
        // pass it to the UI....
        Message msg = handle.obtainMessage(STATE_CONNECTION_STARTED);
        Bundle bundle = new Bundle();
        bundle.putString("NAMES", devs);
        msg.setData(bundle);

        handle.sendMessage(msg);
    }
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThreadTwo");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            threadConnected = true;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThreadTwo");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    //                  byte[] blah = ("System Time:" +System.currentTimeMillis()).getBytes();
                    //                  write(blah);
                    //                  Thread.sleep(1000);
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    handle.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();

                    //                  .sendToTarget();
                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }
        public void connectionLost() {
            Message msg = handle.obtainMessage(STATE_CONNECTION_LOST);
            //          Bundle bundle = new Bundle();
            //          bundle.putString("NAMES", devs);
            //          msg.setData(bundle);

            handle.sendMessage(msg);

        }
        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                //              mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer)
                //              .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
                mmOutStream.close();
                mmInStream.close();
                Message msg = handle.obtainMessage(READY_TO_CONN);
                handle.sendMessage(msg);
                Log.i(TAG, "closing socket");
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }






    @Override
    protected void onStart() {
        super.onStart();
        new Thread (new Runnable() {
            @Override
            public void run() {
                while (!mBound){}
                mService.logMessage("Start:Android: Displaying main Messages inbox");
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
        mService.logMessage("Android: Closing main Messages inbox");
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
            mService.logMessage("Android: Clicked back button from main Messages activity");
            this.moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.out.println("Destroying Main");
        mService.logMessage("Start:Android: Application stopped");
        TempWakeLock.release();
        unregisterReceiver(displayAndroidText);
        unregisterReceiver(displayAndroidCall);
        unregisterReceiver(displayPebbleCall);
        unregisterReceiver(displayPebbleText);
        unregisterReceiver(displayGlassText);
        unregisterReceiver(addSelfMessage);
        unregisterReceiver(declineWithMessage);
        unregisterReceiver(mReceiver);
        unregisterReceiver(textReceiver);
        unregisterReceiver(mScreenReceiver);

        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
       stopService(new Intent(Main.this,ConnectionService.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
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
