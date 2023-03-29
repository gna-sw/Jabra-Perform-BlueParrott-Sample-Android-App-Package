package com.blueparrott.blueparrottbridge;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.blueparrott.blueparrottsdk.BPHeadset;
import com.blueparrott.blueparrottsdk.BPSdk;

import com.blueparrott.blueparrottsdk.IBPHeadsetListener;

/*
 * Service that maintains connection to the SDK/BlueParrott Button
 * Monitors for Headset Connection Changes and automatically connects to headset SDK
 * Sends OS Broadcasts on button events
 *
 */
public class SdkConnectionService extends Service implements IBPHeadsetListener {
    public static final int BUTTON_STATE_DISCONNECTED = 0;
    public static final int BUTTON_STATE_CONNECTING = 1;
    public static final int BUTTON_STATE_CONNECTED = 2;
    public static final int BUTTON_STATE_DOWN = 3;
    public static final int BUTTON_STATE_UP = 4;
    public static final String EXTRA_MESSENGER = "com.blueparrott.bplink.EXTRA_MESSENGER";
    private static final String LOGTAG = SdkConnectionService.class.getName();
    private static final int HEADSET_NOTIFICATION_ID = 100;
    private final SDKBinder localBinder = new SDKBinder();//create binder
    Messenger messenger;
    //handle to the BPHeadet sdk instance
    private BPHeadset headsetSdk;

    /*
     * Detect Bluetooth Headset State change, so we can trigger connection to the BlueParrott Headset SDK when a headset connects
     */
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
            switch (state) {

                case BluetoothAdapter.STATE_CONNECTED:
                    if (headsetSdk.getConnectedState() == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.v(LOGTAG, "BluetoothAdapter.STATE_CONNECTED Connecting to headsetSDK");
                        headsetSdk.connect();
                    }

                case BluetoothAdapter.STATE_DISCONNECTED:
                    if ((headsetSdk.connected())) {
                        Log.v(LOGTAG, "Disconnecting from headsetSDK");
                        headsetSdk.disconnect();
                    }

                default:
                    break;
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGTAG, "in onStartCommand");

        //Is there a bluetooth headset connected ? if so consider doing SDK connect.
        if (Utils.isBluetoothHeadsetConnected()) {
            //when services launches if the SDK is not already connected, connect
            if (!(headsetSdk.connected())) {
                Log.d(LOGTAG, "in onStartCommand");
                headsetSdk.connect();
            }
        } else {
            Log.v(LOGTAG, "No Headset is connected.");
        }

        //this is only set when starting from the activity not the onboot service
        messenger=null;
        if (intent.getExtras()!=null){
            messenger = (Messenger) intent.getExtras().get(EXTRA_MESSENGER);
        }


        return Service.START_STICKY;
    }

    //when service is created (should only be once)
    @Override
    public void onCreate() {
        super.onCreate();

        //get headsetsdk reference
        headsetSdk = (BPHeadset) BPSdk.getBPHeadset(this);

        //send analytics - in dev mode
        BPSdk.setSendAnalytics(true);

        //listen for headset events
        headsetSdk.addListener(SdkConnectionService.this);

        //listen for bluetooth state changes (when headset is connected, disconnected)
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(btReceiver, intentFilter);

        //ensure this service is started in Foreground (required for Android OREO and above, otherwise service gets taken down
        Notification n = Utils.buildNotification(this, BUTTON_STATE_DISCONNECTED);
        startForeground(HEADSET_NOTIFICATION_ID, n);

    }

    @Override
    public void onDestroy() {
        //tidy up - remove listener
        headsetSdk.removeListener(SdkConnectionService.this);

        //remove the receiver for bluetooth headset connect state changes
        unregisterReceiver(btReceiver);
    }

    @Override
    public void onConnectProgress(int progressCode) {
        //during connect cycle (e.g. steps in BLE connect or Classic Connect, optionally update the user with progress
        Log.v(LOGTAG, Utils.getStatusDescription(progressCode));
        //Utils.createNotification(this, BUTTON_STATE_CONNECTING);
    }

    @Override
    public void onConnect() {
        //when successfully connected to SDK, ensure we are in SDK mode so we are notified of SDK button press events
        headsetSdk.enableSDKMode();

        //we are connected so show that in the notification bar
        Utils.createNotification(this, BUTTON_STATE_CONNECTED);
    }

    @Override
    public void onConnectFailure(int reasonCode) {
        //connection has failed, ensure we clear the notification bar
        Log.d(LOGTAG, Utils.getConnectErrorDescription(reasonCode));
        Utils.cancelHeadsetStatusNotification(this);
    }

    @Override
    public void onDisconnect() {
        Utils.createNotification(this, BUTTON_STATE_DISCONNECTED);
    }

    @Override
    public void onModeUpdate() {
    }

    @Override
    public void onModeUpdateFailure(int reasonCode) {
    }

    @Override
    public void onButtonDown(int buttonId) {

        //Let User know button has been pressed by updating the notification bar
        Utils.createNotification(this, BUTTON_STATE_DOWN);

        //broadcast intent from prefs (optional, if applicable)
        sendButtonIntentFromPref(getString(R.string.key_pref_button_down_intent), getString(R.string.value_pref_button_down_intent));

    }

    @Override
    public void onButtonUp(int buttonId) {

        Utils.createNotification(this, BUTTON_STATE_UP);

        sendButtonIntentFromPref(getString(R.string.key_pref_button_up_intent), getString(R.string.value_pref_button_up_intent));
    }


    @Override
    public void onTap(int buttonId) {
            sendButtonIntentFromPref(getString(R.string.key_pref_button_tap_intent), getString(R.string.value_pref_button_tap_intent));
    }

    @Override
    public void onDoubleTap(int buttonId) {
            sendButtonIntentFromPref(getString(R.string.key_pref_button_double_tap_intent), getString(R.string.value_pref_button_double_tap_intent));
    }

    @Override
    public void onLongPress(int buttonId) {
            sendButtonIntentFromPref(getString(R.string.key_pref_button_long_press_intent), getString(R.string.value_pref_button_long_press_intent));
    }

    @Override
    public void onProximityChange(int status) {

    }

    @Override
    public void onValuesRead() {

    }

    @Override
    public void onEnterpriseValuesRead() {

    }


    //broadcast intent - use either the default blueparrott intent or custom intent stored in prefs
    private void sendButtonIntentFromPref(String prefKey, String defaultIntentValue) {

        String intentString = PrefHelper.getStringPref(this, prefKey, defaultIntentValue);

        Log.d(LOGTAG, "Broadcasting:" + intentString);

        if (intentString.equals("")) {
            Log.d(LOGTAG, "Blank intent found - nothing will be broadcast");
            return;
        }

        Intent i = new Intent(intentString);
        sendBroadcast(i);

        //We may not have a messenger object if we have not been launched (via StartCommand) from the Activity (which passes in the message handler)
        if (messenger != null) {
            //update the UI also
            Message message = new Message();
            message.obj = intentString;

            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }


    public class SDKBinder extends Binder {
        public SdkConnectionService getService() {
            return SdkConnectionService.this;
        }
    }


}
