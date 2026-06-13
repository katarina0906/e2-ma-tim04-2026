package com.example.slagalicatim04.notifications;

import com.google.firebase.Timestamp;

import java.util.concurrent.TimeUnit;

public final class NotificationTimeFormatter {

    private NotificationTimeFormatter() {
    }

    public static String format(Timestamp timestamp) {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - timestamp.toDate().getTime());
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis);
        if (minutes < 1) {
            return "upravo sada";
        }
        if (minutes < 60) {
            return "pre " + minutes + " min";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis);
        if (hours < 24) {
            return "pre " + hours + " h";
        }
        long days = TimeUnit.MILLISECONDS.toDays(elapsedMillis);
        if (days == 1) {
            return "juce";
        }
        return "pre " + days + " dana";
    }
}
