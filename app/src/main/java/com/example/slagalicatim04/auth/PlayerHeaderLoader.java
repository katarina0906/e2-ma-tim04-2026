package com.example.slagalicatim04.auth;

import android.widget.ImageView;
import android.widget.TextView;

import com.example.slagalicatim04.leagues.LeagueInfo;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerHeaderLoader {
    private static final Map<String, String> AVATAR_CACHE = new HashMap<>();
    private static final Map<String, String> SUMMARY_CACHE = new HashMap<>();
    private static final Map<String, Long> SUMMARY_CACHE_TIME = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> SUMMARY_LOADING = new HashSet<>();
    private static final long SUMMARY_CACHE_MS = 30_000L;

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

    public static void loadProfileSummary(String playerId, TextView textView) {
        if (playerId == null || playerId.trim().isEmpty()) {
            textView.setText("");
            return;
        }

        long now = System.currentTimeMillis();
        Long cachedAt = SUMMARY_CACHE_TIME.get(playerId);
        if (SUMMARY_CACHE.containsKey(playerId)
                && cachedAt != null
                && now - cachedAt < SUMMARY_CACHE_MS) {
            textView.setText(SUMMARY_CACHE.get(playerId));
            return;
        }
        if (SUMMARY_LOADING.contains(playerId)) {
            return;
        }

        SUMMARY_LOADING.add(playerId);
        FirebaseFirestore.getInstance().collection("users").document(playerId).get()
                .addOnSuccessListener(snapshot -> {
                    long tokens = longValue(snapshot.getLong("tokens"));
                    long stars = firstLong(snapshot.getLong("totalStars"),
                            snapshot.getLong("overallStars"), snapshot.getLong("stars"));
                    String league = snapshot.getString("league");
                    if (league == null || league.trim().isEmpty()) {
                        league = LeagueInfo.forStars(stars).name;
                    }
                    String summary = tokens + " tokena • " + stars + " zvezda • " + league;
                    SUMMARY_CACHE.put(playerId, summary);
                    SUMMARY_CACHE_TIME.put(playerId, System.currentTimeMillis());
                    textView.setText(summary);
                })
                .addOnFailureListener(error -> textView.setText(""))
                .addOnCompleteListener(task -> SUMMARY_LOADING.remove(playerId));
    }

    private static long firstLong(Long first, Long second, Long third) {
        if (first != null) {
            return Math.max(0L, first);
        }
        if (second != null) {
            return Math.max(0L, second);
        }
        return longValue(third);
    }

    private static long longValue(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
