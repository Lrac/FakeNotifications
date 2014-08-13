package com.hfast.fakenotification;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.hfast.fakenotification.R;

import java.util.List;

public class DisplayNewMessage extends Activity {

    public static final String TEXT_STAT = "TextStatus";

    private String sender;
    private List<String> messages;
    MessageAdapter theAdapter;
    EditText editMessage;
    ListView theListView;

    ConnectionService mService;
    boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        messages = intent.getStringArrayListExtra(Main.EXTRA_MESSAGE);
        sender = intent.getStringExtra(Main.EXTRA_SENDER);
        setTitle(sender);
        IntentFilter intentFilter = new IntentFilter("androidtext");
        intentFilter.setPriority(9001);
        this.registerReceiver(newIncomingMessage, intentFilter);
        setContentView(R.layout.message_display_list);
        editMessage = (EditText) findViewById(R.id.editMessage);
        theAdapter = new MessageAdapter(this, messages);
        theListView = (ListView) findViewById(R.id.messageList);
        theListView.setAdapter(theAdapter);
//        SharedPreferences textStat = getSharedPreferences(TEXT_STAT, 0);
//        SharedPreferences.Editor editor = textStat.edit();
//        editor.putBoolean("isThereText", false);
//        editor.commit();

//                ((TextView) findViewById(R.id.incoming_textview)).setText(message);
        System.out.println("creating text");

        editMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.logMessage("Editing message");
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }


    private BroadcastReceiver newIncomingMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newSender = intent.getStringExtra("sender");
            if(newSender.contentEquals(sender)){
                //add on new message
                messages.add(intent.getStringExtra("content"));
                theAdapter.notifyDataSetChanged();
                // Creates the vibrate, color flash, and tone
                Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(500);

                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
                setResultData("already received");
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.LocalBinder binder = (ConnectionService.LocalBinder) service;
            mService = binder.getService();
            mService.logMessage("Start:Displaying " + sender + "'s messages");
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    public void default_message(View view){
        Button button = (Button) view;
        String message = button.getHint().toString();
        editMessage.setText(message);
        mService.logMessage("Selected default \"" + button.getText() + "\" message");

    }

    public void sendMessage(View view){
        String message = editMessage.getText().toString();
        if(!message.isEmpty()) {
            messages.add("Self:" + message);
            theAdapter.notifyDataSetChanged();
            Intent intent = new Intent("selfmessage");
            intent.putExtra("sender", sender);
            intent.putExtra("message", message);
            this.sendBroadcast(intent, null);
            editMessage.setText("");
            mService.logMessage("User sent message: \"" + message + "\"");
        }
    }


    public void editMessageClick(View view){
//        mService.logMessage("Editing message");
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("resuming text");

    }

    @Override
    protected void onStop() {
        super.onStop();
        System.out.println("stopping text");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        mService.logMessage("Closing " + sender + "'s messages activity");
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.out.println("destroying text");
        this.unregisterReceiver(newIncomingMessage);

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        System.out.println("starting text");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        System.out.println("restarting text");
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("pausing text");

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.display_new_message, menu);
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
