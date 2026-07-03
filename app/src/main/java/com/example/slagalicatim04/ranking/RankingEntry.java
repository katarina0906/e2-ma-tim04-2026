package com.example.slagalicatim04.ranking;

import com.google.firebase.firestore.DocumentSnapshot;

public class RankingEntry {
    public final String userId;
    public final String username;
    public final String leagueIcon;
    public final long stars;
    public final int rank;

    public RankingEntry(DocumentSnapshot snapshot, int rank) {
        userId = snapshot.getId();
        username = text(snapshot, "username", "Igrac");
        leagueIcon = text(snapshot, "leagueIcon", "🏆");
        Long value = snapshot.getLong("stars");
        stars = value == null ? 0 : value;
        this.rank = rank;
    }

    private static String text(DocumentSnapshot snapshot, String key, String fallback) {
        String value = snapshot.getString(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
