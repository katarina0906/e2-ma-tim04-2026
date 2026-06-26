package com.example.slagalicatim04.regions;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RegionChatTimeFormatter {

    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT);

    private RegionChatTimeFormatter() {
    }

    public static String format(Timestamp timestamp) {
        Date date = timestamp == null ? new Date() : timestamp.toDate();
        return FORMAT.format(date);
    }
}
