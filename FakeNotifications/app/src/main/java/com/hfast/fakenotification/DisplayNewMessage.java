package com.hfast.fakenotification;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.List;

public class DisplayNewMessage extends Activity {

    public static final String TEXT_STAT = "TextStatus";

    private String sender;
    private List<String> messages;
    MessageAdapter theAdapter;
    EditText editMessage;
    ListView theListView;
    Button button1, button2, button3;

    ConnectionService mService;
    boolean mBound = false;
    boolean screenOn = false;

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
        theAdapter = new MessageAdapter(this, messages);
        theListView = (ListView) findViewById(R.id.messageList);
        theListView.setAdapter(theAdapter);
        scrollMyListViewToBottom();

        editMessage = (EditText) findViewById(R.id.editMessage);
        button1 = (Button) findViewById(R.id.button1);
        button2 = (Button) findViewById(R.id.button2);
        button3 = (Button) findViewById(R.id.button3);

        editMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.logMessage("Android: Editing message");
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }


    private void scrollMyListViewToBottom() {
        theListView.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
                theListView.setSelection(theAdapter.getCount() - 1);
            }
        });
    }


    private BroadcastReceiver newIncomingMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (screenOn) {
                String newSender = intent.getStringExtra("sender");
                if (newSender.contentEquals(sender)) {
                    //add on new message
                    messages.add(intent.getStringExtra("content"));

                    theAdapter.notifyDataSetChanged();

                    setResultData("already received");
                }
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.LocalBinder binder = (ConnectionService.LocalBinder) service;
            mService = binder.getService();
            mService.logMessage("Android: Displaying " + sender + "'s messages");
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    //function that's called whenever one of the three default message buttons are pressed
    public void default_message(View view){
        Button button = (Button) view;
        String message = button.getHint().toString();
        editMessage.setText(message);
        mService.logMessage("Android: Selected default \"" + button.getText() + "\" message");

    }

    //function that's called when a user presses send
    public void sendMessage(View view){
        String message = editMessage.getText().toString();
        if(!message.isEmpty()) {
            messages.add("Self:" + message);
            theAdapter.notifyDataSetChanged();
            scrollMyListViewToBottom();
            Intent intent = new Intent("selfmessage");
            intent.putExtra("sender", sender);
            intent.putExtra("message", message);
            this.sendBroadcast(intent, null);
            editMessage.setText("");
            mService.logMessage("Android: User sent message: \"" + message + "\"");
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
        screenOn = false;

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.out.println("destroying text");
        this.unregisterReceiver(newIncomingMessage);
        mService.logMessage("Android: Closing " + sender + "'s messages activity");
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        screenOn = true;
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
