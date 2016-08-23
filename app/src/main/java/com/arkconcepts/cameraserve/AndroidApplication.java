package com.arkconcepts.cameraserve;

import android.app.Application;

public class AndroidApplication extends Application {
    private static AndroidApplication instance;

    public static AndroidApplication getInstance(){
        return instance;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        instance = this;
    }
}
