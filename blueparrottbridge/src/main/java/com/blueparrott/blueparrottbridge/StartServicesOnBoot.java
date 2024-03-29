package com.blueparrott.blueparrottbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;


/*
 *
 * Broadcast receiver that is fired when the phone boots (see manifest)
 * This starts the SDK Connection Service which monitors for Headset connections and triggers connection to the BP SDK
 *
 */
public class StartServicesOnBoot extends BroadcastReceiver {

    private static final String LOGTAG = StartServicesOnBoot.class.getName();

    /**
     * Broadcast receiver launched when phone has finished booting
     * Note that this will not work on some devices which have their own Startup manager (e.g. Huawei, Blackview etc.)
     * On those devices user will need to go to settings and manually allow the app to recieve startup instruction
     * @param c
     * @param arg1
     */
    public void onReceive(Context c, Intent arg1) {

        Log.d(LOGTAG, "Launching BlueParrott Bridge");

        //Start connection to SDK
        Intent intent = new Intent(c, SdkConnectionService.class);

        //Android OREO prohibits starting background services so start in the foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(LOGTAG, "about to start foreground service ");
            c.startForegroundService(intent);
        } else {
            Log.d(LOGTAG, "about to start background service");
            c.startService(intent);
        }

    }

}

