package com.example.slagalicatim04.multiplayer;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;

public class TestRoomPlayerProvider {
    private static final String PREFS = "test_room_player";
    private static final String KEY_GUEST_ID = "guest_id";

    private final Context context;

    public TestRoomPlayerProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getPlayerId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return user.getUid();
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String guestId = preferences.getString(KEY_GUEST_ID, null);
        if (guestId == null) {
            guestId = "guest-" + UUID.randomUUID();
            preferences.edit().putString(KEY_GUEST_ID, guestId).apply();
        }
        return guestId;
    }
}
