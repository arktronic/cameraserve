package com.arkconcepts.cameraserve;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectivityChangeReceiver extends BroadcastReceiver {
    public static volatile boolean Changed = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        Changed = true;
    }
}
