package com.lannbox.rfduinotest;

import android.app.Activity;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.androidplot.xy.XYPlot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//Test
public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    private boolean scanStarted;
    private boolean scanning;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;
    private ServiceConnection rfduinoServiceConnection;

    private RetainedFragment dataFragment;
    private boolean serviceBound;
    private boolean connectionIsOld = false;
    private boolean fromNotification = false;
    private boolean serviceInForeground = false;

    private HeartRateMonitor monitor = null;
    private TextView textView = null;
    private Date lastPing = null;
    private Handler intervalHandler;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            updateUi();
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            //Log.w("Main","rfduinoReceiver called with " + action);
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] rawData = intent.getByteArrayExtra(RFduinoService.EXTRA_DATA);
                monitor.newBeatData(ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer(), 5);
                lastPing = new Date();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        serviceInForeground = sharedPref.getBoolean("foregroundServiceRunning", false);

        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        dataFragment = (RetainedFragment) fm.findFragmentByTag("data");

        // create the fragment and data the first time
        if (dataFragment == null) {
            // add the fragment
            dataFragment = new RetainedFragment();
            fm.beginTransaction().add(dataFragment, "data").commit();
        }
        else
        {
            BTLEBundle btleBundle = dataFragment.getData();
            if(btleBundle != null)
            {
                bluetoothDevice = btleBundle.device;
                serviceBound = btleBundle.isBound;
                scanStarted = btleBundle.scanStarted;
                scanning = btleBundle.scanning;
                if(serviceBound) {
                    // only restore the connection if there has been one
                    rfduinoServiceConnection = btleBundle.connection;
                    rfduinoService = btleBundle.service;
                    connectionIsOld = true; // setting this flag to true to indicate a rotation
                }
                state = btleBundle.state_;

                Log.w("Main", "Bundle restored from fragment, state is " + String.valueOf(state));
            }
        }


        Intent inti = getIntent();
        int flags = inti.getFlags();
        if((inti.getAction().equals("RFduinoTest_CallToMain")) || (serviceInForeground))//&& ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0))
        {
            Log.w("Main", "Return from notifictation");
            Intent stopForegroundIntent = new Intent(getApplicationContext(), RFduinoService.class);
            stopForegroundIntent.setAction("RFduinoService_StopForeground");
            getApplicationContext().startService(stopForegroundIntent);
            serviceInForeground = false;
            // Saving to sharedPreferences that the service is running in foreground now
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("foregroundServiceRunning", serviceInForeground);
            editor.commit();
            fromNotification = true;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // rebind to service if it currently isn't
        if(!serviceBound) {
            rfduinoServiceConnection = genServiceConnection();
        }

        if(fromNotification) {
            Intent rfduinoIntent = new Intent(getApplicationContext(), RFduinoService.class);
            getApplicationContext().bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
        }

        if (monitor == null) {
            monitor = new HeartRateMonitor((XYPlot)findViewById(R.id.xyPlot));
        }
        textView = (TextView)findViewById(R.id.textView);

        intervalHandler = new Handler();
        postUpdateUiInterval();

        // Begin scanning!
        scanStarted = true;
        bluetoothAdapter.startLeScan(new UUID[]{RFduinoService.UUID_SERVICE}, MainActivity.this);

        // refresh the ui if a restored fragment was found
        if (dataFragment != null) {
            updateUi();
        }
    }

    private void postUpdateUiInterval() {
        intervalHandler.postDelayed(new Runnable() {
            public void run() {
                updateUi();
            }
        }, 1000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.w("Main", "onStart called");
        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        if(state <= STATE_DISCONNECTED) {
            updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    @Override
    protected  void onDestroy()
    {
        if(isFinishing() && serviceBound)
        {
            // shut down service if background action is not wanted
            //if(!backgroundService) {
            if(!true) {
                Log.w("Main", "Service is unbound");
                //Intent stopBackgroundIntent = new Intent(getApplicationContext(), RFduinoService.class);
                //stopBackgroundIntent.setAction("RFduinoService_Stop");
                getApplicationContext().unbindService(rfduinoServiceConnection);
                //getApplicationContext().stopService(stopBackgroundIntent);
            }
            else {
                // store the data in the fragment
                BTLEBundle btleBundle = new BTLEBundle();
                btleBundle.device = bluetoothDevice;
                btleBundle.state_ = state;
                btleBundle.isBound = serviceBound;
                btleBundle.scanStarted = scanStarted;
                btleBundle.scanning = scanning;
                if(serviceBound) {
                    // only save the connection if there is one
                    btleBundle.connection = rfduinoServiceConnection;
                    btleBundle.service = rfduinoService;
                }
/*                if(dataFragment != null) {
                    Log.w("Main","Bundle saved to fragment");
                    dataFragment.setData(btleBundle);
                }
*/
                if(rfduinoService != null) {
                    Log.w("Main","Bundle saved to service");
                    rfduinoService.setData(btleBundle);
                }
                // Saving to sharedPreferences that the service is running in foreground now
                SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                        getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("foregroundServiceRunning", true);
                editor.commit();

                Log.w("Main","Service pushed into foreground");
                Intent startBackgroundIntent = new Intent(getApplicationContext(), RFduinoService.class);
                startBackgroundIntent.setAction("RFduinoService_StartForeground");
                getApplicationContext().startService(startBackgroundIntent);

                if(rfduinoServiceConnection != null) {
                    getApplicationContext().unbindService(rfduinoServiceConnection);
                }
            }
        }

        // rotating behaviour is handled below
        else if(!isFinishing()) {
            // store the data in the fragment
            BTLEBundle btleBundle = new BTLEBundle();
            btleBundle.device = bluetoothDevice;
            btleBundle.state_ = state;
            btleBundle.isBound = serviceBound;
            btleBundle.scanStarted = scanStarted;
            btleBundle.scanning = scanning;
            if(serviceBound) {
                // only save the connection if there is one
                btleBundle.connection = rfduinoServiceConnection;
                btleBundle.service = rfduinoService;
            }

            if(dataFragment != null) {
                Log.w("Main","Bundle saved to fragment");
                dataFragment.setData(btleBundle);
            }
        }

        super.onDestroy();
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {
        String lastUpdate;
        if (state == STATE_BLUETOOTH_OFF) {
            lastUpdate = "(bluetooth off)";
        }
        else if (state == STATE_DISCONNECTED) {
            lastUpdate = "(disconnected)";
        }
        else if (state == STATE_CONNECTING) {
            lastUpdate = "(connecting)";
        }
        else {
            if (lastPing != null) {
                lastUpdate = String.format("%.3fs ago", (new Date().getTime() - lastPing.getTime()) / 1000.f);
            }
            else {
                lastUpdate = "(no data)";
            }
        }

        int rate = (int) Math.round(monitor.getRate());
        if (rate == -1) {
            rate = 0;
        }

        textView.setText(String.format("Rate: %03d\nLast Update: %s", rate, lastUpdate));

        //Log.w("Main", "Updated UI to state " + state);
        postUpdateUiInterval();
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        Log.w("Main", String.format("onLeScan: name='%s', scanRecord='%s'", device.getName(), BluetoothHelper.parseScanRecord(scanRecord)));
        if (!device.getName().equals("UWCSEP590-A5") || !BluetoothHelper.parseScanRecord(scanRecord).contains("\"jdw\"")) {
            return;
        }
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;
        scanning = false;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // if device was rotated we need to set up a new service connection with this activity
                if (connectionIsOld) {
                    Log.w("Main", "Rebuilding connection after rotation");
                    connectionIsOld = false;
                    rfduinoServiceConnection = genServiceConnection();
                }
                if (serviceBound) {
                    if (rfduinoService.initialize()) {
                        if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                            upgradeState(STATE_CONNECTING);
                        }
                    }
                } else {
                    Intent rfduinoIntent = new Intent(getApplicationContext(), RFduinoService.class);
                    getApplicationContext().bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
                }

                updateUi();
            }
        });
    }

    private ServiceConnection genServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceBound = true;
                rfduinoService = ((RFduinoService.LocalBinder) service).getService();
                Log.w("Main","onServiceConnected called, service = "+ rfduinoService.toString());
                if(fromNotification) {
                    BTLEBundle bundle = rfduinoService.restoreData();
                    if(bundle != null) {
                        state = bundle.state_;
                        bluetoothDevice = bundle.device;
                        scanStarted = bundle.scanStarted;
                        scanning = bundle.scanning;
                        Log.w("Main","State restored from service, state: "+ state);
                    }
                    Log.w("Main","Stopping service before unbinding");
                    Intent stopIntent = new Intent(getApplicationContext(),RFduinoService.class);
                    getApplicationContext().stopService(stopIntent);
                    fromNotification = false;
                    if(state<STATE_CONNECTED) {
                        disconnect();
                    }
                    updateUi();
                }
                else{
                    if (rfduinoService.initialize()) {
                        if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                            upgradeState(STATE_CONNECTING);
                        }
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w("Main","onServiceDisconnected called");
                rfduinoService = null;
                downgradeState(STATE_DISCONNECTED);
            }
        };
    }

    private void disconnect(){
        if(rfduinoService != null) {
            rfduinoService.disconnect();
            rfduinoService = null;
        }
        else {Log.w("Main","Service empty");}
        if(rfduinoServiceConnection != null) {
            getApplicationContext().unbindService(rfduinoServiceConnection);
            serviceBound = false;
        }
        else{ Log.w("Main","ServiceConnection empty");}
    }

    @Override
    public void  onNewIntent(Intent intent) {
        Log.w("Main", "onNewintent called");
    }
}

