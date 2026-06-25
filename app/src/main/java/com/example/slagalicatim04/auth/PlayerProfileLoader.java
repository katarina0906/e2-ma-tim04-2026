package com.example.slagalicatim04.auth;

import com.google.firebase.firestore.FirebaseFirestore;

public final class PlayerProfileLoader {
    public interface Callback {
        void onLoaded(String username);
    }

    private PlayerProfileLoader() {
    }

    public static void loadUsername(String playerId, String fallback, Callback callback) {
        if (playerId == null || playerId.trim().isEmpty()) {
            callback.onLoaded(fallback);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(playerId).get()
                .addOnSuccessListener(snapshot -> {
                    String username = snapshot.getString("username");
                    callback.onLoaded(username == null || username.trim().isEmpty()
                            ? fallback : username);
                })
                .addOnFailureListener(error -> callback.onLoaded(fallback));
    }
}
