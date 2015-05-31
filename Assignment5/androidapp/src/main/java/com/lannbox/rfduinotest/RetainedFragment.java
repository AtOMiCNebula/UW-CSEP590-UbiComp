package com.lannbox.rfduinotest;

import android.app.Fragment;
import android.content.ServiceConnection;
import android.os.Bundle;

public class RetainedFragment extends Fragment {

    // data object we want to retain
    private BTLEBundle btleBundle;

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setData(BTLEBundle bundle) {
        this.btleBundle = bundle;
    }

    public BTLEBundle getData() {
        return btleBundle;
    }
}

