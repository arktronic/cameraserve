package com.arkconcepts.cameraserve;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) && shouldRunOnBoot()) {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    private boolean shouldRunOnBoot() {
        Context ctx = AndroidApplication.getInstance().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ctx);

        return preferences.getBoolean("run_on_boot", true);
    }
}
