package com.hfast.fakenotification;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.hfast.fakenotification.R;

public class PhoneCall extends Activity {

    private Vibrator vibrator;
    private MediaPlayer player;
    private MediaPlayer new_player;
    private String filename;
    private int filelength;

    private String caller;
    private String number;
    ConnectionService mService;
    boolean mBound = false;
    Button button1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        caller = intent.getStringExtra(Main.EXTRA_CALLER);
        number = intent.getStringExtra(Main.EXTRA_NUMBER);
        filename = intent.getStringExtra(Main.EXTRA_FILENAME);
        filelength = Integer.parseInt(intent.getStringExtra(Main.EXTRA_FILELENGTH));

        setContentView(R.layout.activity_phone_call);
        button1 = (Button) findViewById(R.id.declineWithMessage);
        ((TextView)findViewById(R.id.incoming_caller)).setText(caller);
        ((TextView)findViewById(R.id.incoming_number)).setText(number);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        call();

        new TakingPhoneCall().execute("");
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.LocalBinder binder = (ConnectionService.LocalBinder) service;
            mService = binder.getService();
            mService.logMessage("Start:Displaying phone call from " + caller);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.phone_call, menu);
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

    public void call() {
        // Creates the vibrate
        long[] pattern = {500, 500, 500};
        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(pattern, 0);

        // Ringtone
        player = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI);
        player.start();
    }

    public void end_activity(View view) {
        vibrator.cancel();
        player.release();
        new_player.stop();
        mService.logMessage("User declined call");
        finish();
    }


    public void accept_call(View view) {
        player.release();
        new_player = MediaPlayer.create(this, Uri.parse(Environment.getExternalStorageDirectory().getPath()+ "/Music/" + filename + ".mp3"));
	    setContentView(R.layout.active_phone_call);
        vibrator.cancel();
        mService.logMessage("User clicked accept call");
        new_player.start();
        new AnswerPhoneCall().execute("");
    }

    public void declineWithMessage(View view){
        PopupMenu popup = new PopupMenu(PhoneCall.this, button1);
        popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String message = item.getTitle().toString();
                Intent intent = new Intent("declineWithMessage");
                intent.putExtra("message", message);
                intent.putExtra("caller", caller);
                getApplicationContext().sendBroadcast(intent);
                vibrator.cancel();
                player.release();
                new_player.stop();
                mService.logMessage("User declined with \"" + item.getTitleCondensed() + "\" default message");
                finish();
                return true;
            }
        });
        popup.show();
        mService.logMessage("User selected \"Decline with Message\"");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
        vibrator.cancel();

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mService.logMessage("Call ended");
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    private class TakingPhoneCall extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... params) {

            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if(new_player.isPlaying()) {
                new_player.stop();
            }

            finish();
            super.onPostExecute(result);
        }
    }

    private class AnswerPhoneCall extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... params) {

            try {
                Thread.sleep(filelength);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if(new_player.isPlaying()) {
                new_player.stop();
            }

            finish();
            super.onPostExecute(result);
        }
    }
}
