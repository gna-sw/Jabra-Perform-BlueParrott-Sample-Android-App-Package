package com.blueparrott.blueparrottbridge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.blueparrott.blueparrottsdk.BPHeadset;
import com.blueparrott.blueparrottsdk.BPHeadsetListener;
import com.blueparrott.blueparrottsdk.BPSdk;
import com.blueparrott.blueparrottsdk.IBPHeadsetListener;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;


/**
 * Blueparrott Bridge
 * Sample Application that automatically connects to the Blueparrott SDK when a new headset is detected
 * Maintains connection when app is in foreground or background
 * Broadcasts intents for button events
 * intents can be customised in preferences screen
 */
public class BPBridgeActivity extends AppCompatActivity implements IBPHeadsetListener {
    static final public String LOGTAG = BPBridgeActivity.class.getName();
    TextView tvLog;
    ScrollView scScrollView;
    private TextView tvStatus;
    private TextView tvFriendlyName;
    private BPHeadset headsetSdk;

    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    boolean isScanPermissionGranted=false; //required for Android 12 if using BLE
    boolean isFineLocationGranted=false; // required for android 11 and before if using BLE
    boolean isConnectPermissionGranted=false; // required for Android 12 for both connection methods


    /*
    Display message coming from service
     */
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            logStatus((msg.obj.toString()));
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set up the UI elements
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvSDKStatus);
        tvFriendlyName=findViewById(R.id.tvHeadsetFriendlyName);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        scScrollView = (ScrollView) this.findViewById(R.id.scScrollView);

        //Set Up Permission Launcher
        mPermissionResultLauncher=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if (result.get(Manifest.permission.BLUETOOTH_SCAN)!=null){
                    isScanPermissionGranted=result.get(Manifest.permission.BLUETOOTH_SCAN); //Only required for BLE connection
                };
                if (result.get(Manifest.permission.BLUETOOTH_CONNECT)!=null){
                    isConnectPermissionGranted=result.get(Manifest.permission.BLUETOOTH_CONNECT);
                };
                if (result.get(Manifest.permission.ACCESS_FINE_LOCATION)!=null){
                    isFineLocationGranted=result.get(Manifest.permission.ACCESS_FINE_LOCATION);//Only required for BLE connection on Pre Android 12 handsets
                }
            }
        });

        //Get handle to the blueparrott SDK and create a listener for headset events
        headsetSdk = (BPHeadset) BPSdk.getBPHeadset(this);
        headsetSdk.addListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (BuildConfig.USE_PREF_INTENTS) { //use build flag if you want to prevent user from making changes to the intents
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent myIntent = new Intent(this, PrefActivity.class);
                this.startActivityForResult(myIntent, 1001);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        //update the UI - it may have changed while we are in the background
        if (headsetSdk.connected()) {
            tvStatus.setText(R.string.button_state_connected);
        }
    }

    @Override
    protected void onDestroy() {
        //stop listening for headset events
        headsetSdk.removeListener(this);
        super.onDestroy();
    }

    /*
     * Bind to the SDK
     */
    @Override
    public void onResume() {
        super.onResume();

        if (checkPermissions(false)){
            //start sdk connection service and bind to it
            Intent headsetServiceIntent = new Intent(this, SdkConnectionService.class);
            headsetServiceIntent.putExtra(SdkConnectionService.EXTRA_MESSENGER, new Messenger(handler));
            startService(headsetServiceIntent);
        }

        boolean showIntents = PrefHelper.getBooleanPref(this, getString(R.string.key_show_intents), false);
        if (scScrollView != null) {
            scScrollView.setVisibility(showIntents ? View.VISIBLE : View.INVISIBLE);
        }

    }


    @Override
    public void onConnectProgress(int progressCode) {
        tvStatus.setText(R.string.button_state_connecting);
    }

    @Override
    public void onConnect() {
        tvStatus.setText(R.string.button_state_connected);
        logStatus("Parrott Button Connected");
    }

    @Override
    public void onConnectFailure(int reasonCode) {
        if (reasonCode == BPHeadsetListener.CONNECT_ERROR_BLE_REQUIRES_PERMISSION) {
            Log.d(LOGTAG, "ble permission required");
            logStatus("BLE permission required");
            getBLEPermission();//and resume which will kick things off
        } else {
            logStatus(Utils.getConnectErrorDescription(reasonCode));

        }
    }

    @Override
    public void onDisconnect() {
        tvStatus.setText(R.string.button_state_disconnected);
        tvFriendlyName.setText("");
        logStatus("Parrott Button Disconnected");
    }

    @Override
    public void onModeUpdate() {

    }

    @Override
    public void onModeUpdateFailure(int reasonCode) {

    }

    @Override
    public void onButtonDown(int buttonId) {
        tvStatus.setText(R.string.button_state_down);
    }

    @Override
    public void onButtonUp(int buttonId) {
        tvStatus.setText(R.string.button_state_up);
    }

    @Override
    public void onTap(int buttonId) {

    }

    @Override
    public void onDoubleTap(int buttonId) {

    }

    @Override
    public void onLongPress(int buttonId) {

    }

    @Override
    public void onProximityChange(int status) {
        logStatus("Proximity Change =" + status);

    }

    @Override
    public void onValuesRead() {
        tvFriendlyName.setText(headsetSdk.getFriendlyName());
        logStatus("Headset:"+headsetSdk.getFriendlyName());
        logStatus("Firmware:"+headsetSdk.getFirmwareVersion());

    }

    @Override
    public void onEnterpriseValuesRead() {

    }


    /*
     * Log progress to the to Screen
     */
    public void logStatus(String s) {
        String time = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
        tvLog.append(time + " " + s + "\n");
        scScrollView.post(new Runnable() {
            @Override
            public void run() {
                scScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }


    /*
     * If supporting older BLE only headsets (B350, B450, C400), you must include a request for ACCESS_FINE_LOCATION
     * For newer headsets supporting the AT commands firmware, you do NOT need any special permissions here
     */
    public boolean getBLEPermission() {
        // Send a request to request permissions from the user and return in the Activity's onRequestPermissionsResult()
        //ONLY required for older BP headsets that connect via BLE - remove if only targetting headsets that connect over the classic connection (AT commands)
        //Note that in this demonstration app, it tries to connect without the permission (using AT commands) and only if it fails, will it automatically prompt the user
        String[] BLE_PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION};

        boolean showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        if (showRationale) {
            Toast.makeText(this, "Permission was denied already, leaving you alone", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, BLE_PERMISSIONS, 1);
            return false;
        }
        return true;
    }


    public boolean checkPermissions(boolean justCheck) {
        Log.d("IVAN","in checkPermissions "+justCheck);
        List<String> permissionRequest=new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            isScanPermissionGranted= ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED;
            isConnectPermissionGranted= ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;

            if (!isScanPermissionGranted) {
                //ONLY REQUIRED IF USING FORCE BLE OR HEADSET THAT DOES NOT SUPPORT AT COMMANDS
                permissionRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }

            if (!isConnectPermissionGranted) {
                permissionRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            //only required for BLE on 11
            isFineLocationGranted= ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if (!isFineLocationGranted) {
                permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionRequest.isEmpty() & !justCheck){
            Log.d("IVAN","launching launcher");
            mPermissionResultLauncher.launch(permissionRequest.toArray(new String[0]));
            return false; //we don't have the necessary permissions
        } else {
            return true;//we have the necessary permissions
        }

    }


}
