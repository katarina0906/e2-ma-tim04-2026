package com.example.slagalicatim04;

import android.app.Application;

import com.example.slagalicatim04.notifications.NotificationChannels;
import com.example.slagalicatim04.notifications.NotificationTokenManager;

public class SlagalicaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensureCreated(this);
        NotificationTokenManager.syncCurrentDevice();
    }
}
