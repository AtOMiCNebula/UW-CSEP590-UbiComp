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
import android.os.IBinder;
import android.util.Log;

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
            Log.w("Main","rfduinoReceiver called with " + action);
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
            }
        }
    };
/*
    final private static String DEVICE_FOUND = "com.rfduino.DEVICE_FOUND";
    private final BroadcastReceiver handlerServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.w("Main","handlerServiceReceiver called with " + action);
            if (DEVICE_FOUND.equals(action)) {
                final int rssi = intent.getIntExtra("rssi", 0);
                final byte[] scanRecord = intent.getByteArrayExtra("scanRecord");
                bluetoothDevice = service.getDevice();
                scanning = false;

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceInfoText.setText(
                                BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
                        updateUi();
                    }
                });

            } else if (false) {
            } else if (false) {
            }
        }
    };
*/
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

        // Find Device

        // Device Info

        // Connect Device

        // Disconnect Device

        // Send

        // Receive

        // refresh the ui if a restored fragment was found
        if (dataFragment != null) {
            updateUi();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.w("Main","onStart called");
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
        // Enable Bluetooth
        boolean on = state > STATE_BLUETOOTH_OFF;

        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
        }

        // Send

        Log.w("Main","Updated UI to state " + state);
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;
        scanning = false;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
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

