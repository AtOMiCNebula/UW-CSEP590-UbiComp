/*
 * Copyright (C) 2013 Lann Martin
 *
 * Licensed under the Apache License, Version 2.0 as below
 *
 * This file incorporates work covered by the following notice:
 *
 *   Copyright (C) 2013 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.lannbox.rfduinotest;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.UUID;

/*
 * Adapted from:
 * http://developer.android.com/samples/BluetoothLeGatt/src/com.example.android.bluetoothlegatt/BluetoothLeService.html
 */
public class RFduinoService extends Service {
    private final static String TAG = RFduinoService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothGattService;
    private BTLEBundle dataBundle;

    public final static String ACTION_CONNECTED =
            "com.rfduino.ACTION_CONNECTED";
    public final static String ACTION_DISCONNECTED =
            "com.rfduino.ACTION_DISCONNECTED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.rfduino.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.rfduino.EXTRA_DATA";

    public final static UUID UUID_SERVICE = BluetoothHelper.sixteenBitUuid(0x2220);
    public final static UUID UUID_RECEIVE = BluetoothHelper.sixteenBitUuid(0x2221);
    public final static UUID UUID_SEND = BluetoothHelper.sixteenBitUuid(0x2222);
    public final static UUID UUID_DISCONNECT = BluetoothHelper.sixteenBitUuid(0x2223);
    public final static UUID UUID_CLIENT_CONFIGURATION = BluetoothHelper.sixteenBitUuid(0x2902);


    // Stuff for handling the connection in the service:
    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    private BluetoothDevice bluetoothDevice;

    private NotificationManager mNotificationManager;

    boolean receiverRegistered = false;

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

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to RFduino.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from RFduino.");
                broadcastUpdate(ACTION_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mBluetoothGattService = gatt.getService(UUID_SERVICE);
                if (mBluetoothGattService == null) {
                    Log.e(TAG, "RFduino GATT service not found!");
                    return;
                }

                BluetoothGattCharacteristic receiveCharacteristic =
                        mBluetoothGattService.getCharacteristic(UUID_RECEIVE);
                if (receiveCharacteristic != null) {
                    BluetoothGattDescriptor receiveConfigDescriptor =
                            receiveCharacteristic.getDescriptor(UUID_CLIENT_CONFIGURATION);
                    if (receiveConfigDescriptor != null) {
                        gatt.setCharacteristicNotification(receiveCharacteristic, true);

                        receiveConfigDescriptor.setValue(
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(receiveConfigDescriptor);
                    } else {
                        Log.e(TAG, "RFduino receive config descriptor not found!");
                    }

                } else {
                    Log.e(TAG, "RFduino receive characteristic not found!");
                }

                broadcastUpdate(ACTION_CONNECTED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent, Manifest.permission.BLUETOOTH);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        if (UUID_RECEIVE.equals(characteristic.getUuid())) {
            final Intent intent = new Intent(action);
            intent.putExtra(EXTRA_DATA, characteristic.getValue());
            sendBroadcast(intent, Manifest.permission.BLUETOOTH);
            Log.w(TAG,"BTLE Data received and broadcasted");

            // Create notification
            Intent notificationIntent = new Intent(RFduinoService.this, MainActivity.class);
            notificationIntent.setAction("RFduinoTest_CallToMain");
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(RFduinoService.this, 0, notificationIntent, 0);
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(RFduinoService.this)
                    .setContentTitle("Bluetooth Data")
                    .setTicker("New Bluetooth Data Received")
                    .setContentText("Data:" + HexAsciiHelper.bytesToAsciiMaybe(characteristic.getValue()) + "\nOr: " + HexAsciiHelper.bytesToHex(characteristic.getValue()))
                    .setSmallIcon(R.drawable.ic_launcher)
//                    .setLargeIcon(
                            //                          Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            mNotificationManager.notify(110, mBuilder.build());
        }
    }

    public BTLEBundle restoreData() {
        return dataBundle;
    }

    public void setData(BTLEBundle btleBundle) {
        dataBundle = btleBundle;
    }

    public class LocalBinder extends Binder {
        RFduinoService getService() {
            return RFduinoService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.w(TAG, "onBind called");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.w(TAG, "onBebind called");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(TAG, "onUnbind called");

        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        disconnectRFduino();

        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void read() {
        if (mBluetoothGatt == null || mBluetoothGattService == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return;
        }

        BluetoothGattCharacteristic characteristic =
                mBluetoothGattService.getCharacteristic(UUID_RECEIVE);

        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public boolean send(byte[] data) {
        if (mBluetoothGatt == null || mBluetoothGattService == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return false;
        }

        BluetoothGattCharacteristic characteristic =
                mBluetoothGattService.getCharacteristic(UUID_SEND);

        if (characteristic == null) {
            Log.w(TAG, "Send characteristic not found");
            return false;
        }

        characteristic.setValue(data);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public boolean disconnectRFduino() {
        if (mBluetoothGatt == null || mBluetoothGattService == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return false;
        }

        BluetoothGattCharacteristic characteristic =
                mBluetoothGattService.getCharacteristic(UUID_DISCONNECT);

        if (characteristic == null) {
            Log.w(TAG, "Disconnect characteristic not found");
            return false;
        }

        characteristic.setValue("");
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CONNECTED);
        filter.addAction(ACTION_DISCONNECTED);
        filter.addAction(ACTION_DATA_AVAILABLE);
        return filter;
    }


    // Extension stuff to handle btle connection here

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    // preparing for stand alone action without activity
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.i(TAG, "onStartCommand()");

        if(intent.getAction().equals("RFduinoService_StartForeground")) {
            //TODO: use proper notification ID here!
            startForeground(101, buildServiceNotification().build());
        }
        else if(intent.getAction().equals("ACTION_DISCONNECT")) {
            Log.i(TAG,"disconnect clicked");
            if(dataBundle.state_ == 4) {
                disconnect();
                close();
                //TODO: States need to be handled generally
                dataBundle.state_ = 2; // Disconnected state
                NotificationCompat.Builder mBuilder = buildServiceNotification();
                mBuilder.setContentText("RFDuino disconnected");
                //TODO: use proper notification ID here!
                mNotificationManager.notify(101, mBuilder.build());
            }
        }
        else if(intent.getAction().equals("ACTION_CONNECT")) {
            Log.i(TAG,"connect clicked");
            if(dataBundle.state_ == 2) {
                connect(dataBundle.device.getAddress());
                dataBundle.state_ = 4;
                //TODO: use proper notification ID here!
                mNotificationManager.notify(101, buildServiceNotification().build());
            }
        }
        else if(intent.getAction().equals("RFduinoService_Stop")) {
            Log.i(TAG, "Background service stop received");
            if (dataBundle != null) {
                if (dataBundle.state_ == 4) {
                    disconnect();
                }
            }
            // Saving to sharedPreferences that the service is not running in foreground anymore
            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                    getString(R.string.preference_file_key), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("foregroundServiceRunning", false);
            editor.commit();

            stopForeground(true);
            stopSelf();
        }
        else if(intent.getAction().equals("RFduinoService_StopForeground")) {
            Log.i(TAG, "service stop foreground received");
            stopForeground(true);
        }

        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        receiverRegistered = true;

        return START_STICKY;
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
        //updateUi();
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy()");

        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();

        if(receiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver);
        }
        super.onDestroy();
    }

    private NotificationCompat.Builder buildServiceNotification() {
        Intent notificationIntent = new Intent(RFduinoService.this, MainActivity.class);
        notificationIntent.setAction("RFduinoTest_CallToMain");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(RFduinoService.this, 0, notificationIntent, 0);

        Intent discoIntent = new Intent(RFduinoService.this, RFduinoService.class);
        discoIntent.setAction("ACTION_DISCONNECT");
        PendingIntent pDiscoIntent = PendingIntent.getService(RFduinoService.this, 0, discoIntent, 0);

        Intent connIntent = new Intent(RFduinoService.this, RFduinoService.class);
        connIntent.setAction("ACTION_CONNECT");
        PendingIntent pConnIntent = PendingIntent.getService(RFduinoService.this, 0, connIntent, 0);

        Intent stopIntent = new Intent(RFduinoService.this, RFduinoService.class);
        stopIntent.setAction("RFduinoService_Stop");
        PendingIntent pStopIntent = PendingIntent.getService(RFduinoService.this, 0, stopIntent, 0);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(RFduinoService.this)
                .setContentTitle("Bluetooth Connection running")
                .setTicker("BTLE Ticker")
                .setContentText("RFDuino connected")
                .setSmallIcon(R.drawable.ic_launcher)
//                    .setLargeIcon(
                        //                          Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true) // maybe disable to allow closing with x-button?
                .addAction(android.R.drawable.ic_media_pause, "Disconnect", pDiscoIntent)
                .addAction(android.R.drawable.ic_media_play, "Connect", pConnIntent)
                .addAction(android.R.drawable.ic_delete, "Stop", pStopIntent);
        return mBuilder;
    }
}
