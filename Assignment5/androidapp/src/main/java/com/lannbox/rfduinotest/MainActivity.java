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
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.UUID;
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

    private Button enableBluetoothButton;
    private TextView scanStatusText;
    private Button scanButton;
    private TextView deviceInfoText;
    private TextView connectionStatusText;
    private Button connectButton;
    private Button disconnectButton;
    private EditData valueEdit;
    private Button sendZeroButton;
    private Button sendValueButton;
    private Button clearButton;
    private LinearLayout dataLayout;

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
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
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

        // Bluetooth
        enableBluetoothButton = (Button) findViewById(R.id.enableBluetooth);
        enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBluetoothButton.setEnabled(false);
                enableBluetoothButton.setText(
                        bluetoothAdapter.enable() ? "Enabling bluetooth..." : "Enable failed!");
            }
        });

        // Find Device
        scanStatusText = (TextView) findViewById(R.id.scanStatus);

        scanButton = (Button) findViewById(R.id.scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStarted = true;
                bluetoothAdapter.startLeScan(
                        new UUID[]{ RFduinoService.UUID_SERVICE },
                        MainActivity.this);
            }
        });

        // Device Info
        deviceInfoText = (TextView) findViewById(R.id.deviceInfo);

        // Connect Device
        connectionStatusText = (TextView) findViewById(R.id.connectionStatus);

        connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                connectionStatusText.setText("Connecting...");
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
            }
        });

        // Disconnect Device
        disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);

                disconnect();
            }
        });

        // Send
        valueEdit = (EditData) findViewById(R.id.value);
        valueEdit.setImeOptions(EditorInfo.IME_ACTION_SEND);
        valueEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendValueButton.callOnClick();
                    return true;
                }
                return false;
            }
        });

        sendZeroButton = (Button) findViewById(R.id.sendZero);
        sendZeroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w("Main","Send to " + rfduinoService.toString());
                rfduinoService.send(new byte[]{0});
            }
        });

        sendValueButton = (Button) findViewById(R.id.sendValue);
        sendValueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w("Main","Send to " + rfduinoService.toString());
                rfduinoService.send(valueEdit.getData());
            }
        });

        // Receive
        clearButton = (Button) findViewById(R.id.clearData);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataLayout.removeAllViews();
            }
        });

        dataLayout = (LinearLayout) findViewById(R.id.dataLayout);

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
        enableBluetoothButton.setEnabled(!on);
        enableBluetoothButton.setText(on ? "Bluetooth enabled" : "Enable Bluetooth");
        scanButton.setEnabled(on);

        // Scan
        if (scanStarted && scanning) {
            scanStatusText.setText("Scanning...");
            scanButton.setText("Stop Scan");
            scanButton.setEnabled(true);
        } else if (scanStarted) {
            scanStatusText.setText("Scan started...");
            scanButton.setEnabled(false);
        } else {
            scanStatusText.setText("");
            scanButton.setText("Scan");
            scanButton.setEnabled(true);
        }

        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
        }
        connectionStatusText.setText(connectionText);
        connectButton.setEnabled(bluetoothDevice != null && state == STATE_DISCONNECTED);
        disconnectButton.setEnabled(bluetoothDevice != null && state == STATE_CONNECTED);

        // Send
        sendZeroButton.setEnabled(connected);
        sendValueButton.setEnabled(connected);

        Log.w("Main","Updated UI to state " + state);
    }

    private void addData(byte[] data) {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, dataLayout, false);

        TextView text1 = (TextView) view.findViewById(android.R.id.text1);
        text1.setText(HexAsciiHelper.bytesToHex(data));

        String ascii = HexAsciiHelper.bytesToAsciiMaybe(data);
        if (ascii != null) {
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);
            text2.setText(ascii);
        }

        dataLayout.addView(
                view, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;
        scanning = false;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceInfoText.setText(
                        BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
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

