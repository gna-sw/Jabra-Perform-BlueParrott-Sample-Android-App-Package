package com.blueparrott.batterysample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Sample Application that reads battery life when the application is launched and monitors for further battery level changes
 */
public class MainActivity extends AppCompatActivity {
    static final public String LOGTAG = "BPBatterySample";
    private TextView textView;

    TextView tvLog;
    ScrollView scScrollView;

    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    boolean isScanPermissionGranted = false; //required for Android 12 if using BLE
    boolean isFineLocationGranted = false; // required for android 11 and before if using BLE
    boolean isConnectPermissionGranted = false; // required for Android 12 for both connection methods


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set up the UI elements
        setContentView(R.layout.activity_main);

        //Set Up Permission Launcher
        mPermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if (result.get(Manifest.permission.BLUETOOTH_SCAN) != null) {
                    isScanPermissionGranted = result.get(Manifest.permission.BLUETOOTH_SCAN); //Only required for BLE connection
                }
                ;
                if (result.get(Manifest.permission.BLUETOOTH_CONNECT) != null) {
                    isConnectPermissionGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
                }
                ;
                if (result.get(Manifest.permission.ACCESS_FINE_LOCATION) != null) {
                    isFineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);//Only required for BLE connection on Pre Android 12 handsets
                }

            }
        });

        checkPermissions();

        textView = findViewById(R.id.btnTalk);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        scScrollView = (ScrollView) this.findViewById(R.id.scScrollView);

        //non SDK methods - example on how to read and monitor headset battery level
        getCurrentBatteryLevel();
        monitorBatteryLevel();
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


    public void checkPermissions() {

        List<String> permissionRequest = new ArrayList<String>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            isConnectPermissionGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            if (!isConnectPermissionGranted) {
                permissionRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (!permissionRequest.isEmpty()) {
            mPermissionResultLauncher.launch(permissionRequest.toArray(new String[0]));
        }

    }


    /**
     * Sample code to monitor read battery Level - note this uses reflection and may not be officially supported in the future
     */
    @SuppressLint("MissingPermission")
    //omitted for simplicity - assuming developer will already have asked for permissions
    private void getCurrentBatteryLevel() {

        // Do we have bluetooth turned on?
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if ((mBluetoothAdapter == null) || (!mBluetoothAdapter.isEnabled())) {
            return;
        }

        // Do we have a classic headset connection ?
        mBluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @SuppressLint("MissingPermission")
//omitted for simplicity - assuming developer will already have asked for permissions
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                //take the first HEADSET device
                for (BluetoothDevice foundDevice : proxy.getConnectedDevices()) {
                    if (profile == BluetoothProfile.HEADSET) {
                        Log.d(LOGTAG, "Selecting device" + foundDevice.getName() + foundDevice.getAddress());
                        BluetoothDevice btd = foundDevice;
                        try {
                            //read the battery level
                            java.lang.reflect.Method method;
                            method = btd.getClass().getMethod("getBatteryLevel");
                            int value = (int) method.invoke(btd);
                            logStatus("Battery is :" + value);
                        } catch (Exception ex) {
                            Log.d(LOGTAG, "Could not read battery " + ex.getMessage());
                        }
                    }
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.HEADSET);
    }

    /**
     * Sample code to monitor Battery Level
     */
    private void monitorBatteryLevel() {
        this.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", 999);
                logStatus("Battery Update:" + level);
            }
        }, new IntentFilter("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"));
    }

}
