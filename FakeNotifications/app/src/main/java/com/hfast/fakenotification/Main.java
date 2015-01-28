package com.hfast.fakenotification;

import android.app.Activity;
import android.app.PendingIntent;
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
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.WearableExtender;
import android.support.v4.app.NotificationManagerCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;

import org.jivesoftware.smack.SmackAndroid;

import java.util.ArrayList;
import java.util.List;


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

    private List<TextMessage> textList = new ArrayList<TextMessage>();

    private MyAdapter listAdapter;
    private int id = 0;

    PowerManager.WakeLock TempWakeLock;
    ListView listView;
    NotificationManagerCompat mNotificationManager;
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

        //setup listeners to receive broadcasts from ConnectionService whenever new messages arrive
        this.registerReceiver(displayAndroidCall, new IntentFilter("androidcall"));
        this.registerReceiver(displayAndroidText, new IntentFilter("androidtext"));
        this.registerReceiver(addSelfMessage, new IntentFilter("selfmessage"));
        this.registerReceiver(declineWithMessage, new IntentFilter("declineWithMessage"));

        mNotificationManager = NotificationManagerCompat.from(getApplicationContext());

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

    }

    //Sets up the logging service for this activity, make sure to unbind this service when the activity is destroyed
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


            //occasionally, a new message will arrive while the user is already viewing messages from the same sender
            //in that case, DisplayNewMessage activity will set ResultData  and we'll still vibrate but we don't need to send out a notification
            if(getResultData() == null) {

                // Create an intent and action for the Pebble notifications
                Intent actionIntent = new Intent(getApplicationContext(), Main.class); //set what activity you want to open when an action is pressed
                PendingIntent actionPendingIntent =
                        PendingIntent.getActivity(getApplicationContext(), 0, actionIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT);

                NotificationCompat.Action action =
                        new NotificationCompat.Action.Builder(R.drawable.ic_launcher,
                                "action 1", actionPendingIntent)
                                .build();

                NotificationCompat.Action action2 =
                        new NotificationCompat.Action.Builder(R.drawable.ic_launcher,
                                "action 2", actionPendingIntent)
                                .build();


                WearableExtender wearableExtender = new WearableExtender().addAction(action).addAction(action2);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(sender)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setLights(Color.BLUE, 500, 500)
                        .extend(wearableExtender);


                PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), textMessage.getId(),
                        resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                builder.setContentIntent(resultPendingIntent);


                mNotificationManager.notify(sender,1, builder.build());
                mService.logMessage("Android: Created text message notification from " + sender);

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
        unregisterReceiver(addSelfMessage);
        unregisterReceiver(declineWithMessage);

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
