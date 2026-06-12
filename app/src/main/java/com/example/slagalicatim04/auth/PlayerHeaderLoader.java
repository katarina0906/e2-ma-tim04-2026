package com.example.slagalicatim04.auth;

import android.widget.ImageView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerHeaderLoader {
    private static final Map<String, String> AVATAR_CACHE = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();

    private PlayerHeaderLoader() {
    }

    public static void loadAvatar(String playerId, ImageView imageView) {
        if (playerId == null || playerId.trim().isEmpty()) {
            AvatarImageLoader.load(imageView, "");
            return;
        }

        if (AVATAR_CACHE.containsKey(playerId)) {
            AvatarImageLoader.load(imageView, AVATAR_CACHE.get(playerId));
            return;
        }
        if (LOADING.contains(playerId)) {
            return;
        }

        LOADING.add(playerId);
        FirebaseFirestore.getInstance().collection("users").document(playerId).get()
                .addOnSuccessListener(snapshot -> {
                    String avatarData = snapshot.getString("avatarData");
                    AVATAR_CACHE.put(playerId, avatarData == null ? "" : avatarData);
                    AvatarImageLoader.load(imageView, avatarData);
                })
                .addOnFailureListener(error -> AvatarImageLoader.load(imageView, ""))
                .addOnCompleteListener(task -> LOADING.remove(playerId));
    }
}
