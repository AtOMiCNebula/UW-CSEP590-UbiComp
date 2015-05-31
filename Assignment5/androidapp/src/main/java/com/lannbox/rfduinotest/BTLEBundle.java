package com.lannbox.rfduinotest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ServiceConnection;

public class BTLEBundle
{
    public ServiceConnection connection;
    public RFduinoService service;
    public BluetoothDevice device;
    public int state_;
    public boolean isBound, scanStarted, scanning;

    public BTLEBundle(){
        connection = null;
        service = null;
        device = null;
        state_ = 0;
        isBound = false;
        scanStarted = false;
        scanning = false;
    }

}
