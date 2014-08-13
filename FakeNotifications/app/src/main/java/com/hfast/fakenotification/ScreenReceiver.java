package com.hfast.fakenotification;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Created by Carl on 2014-08-11.
 */
public class ScreenReceiver extends BroadcastReceiver {

    private boolean screenOff;
    ConnectionService mService;
    boolean mBound = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, ConnectionService.class);
        context.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
            mService.logMessage("Screen turned off");
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)){
            mService.logMessage("Screen turned on");
        } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)){
            mService.logMessage("Screen unlocked");
        }
        context.unbindService(mConnection);
        mBound = false;
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.LocalBinder binder = (ConnectionService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };
}
