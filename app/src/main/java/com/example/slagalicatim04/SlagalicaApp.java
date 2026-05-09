package com.example.slagalicatim04;

import android.app.Application;

import com.example.slagalicatim04.notifications.NotificationChannels;

public class SlagalicaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensureCreated(this);
    }
}
