package com.example.slagalicatim04.notifications;

import com.example.slagalicatim04.leagues.LeagueInfo;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public final class LeagueNotificationData {
    private LeagueNotificationData() {
    }

    public static Map<String, Object> create(String oldLeague, LeagueInfo newLeague,
                                             long totalStars, boolean promoted) {
        Map<String, String> data = new HashMap<>();
        data.put("oldLeague", oldLeague == null ? "" : oldLeague);
        data.put("newLeague", newLeague.name);
        data.put("leagueLevel", String.valueOf(newLeague.level));
        data.put("totalStars", String.valueOf(totalStars));
        data.put("change", promoted ? "promoted" : "demoted");

        Map<String, Object> notification = new HashMap<>();
        notification.put("category", "ranking");
        notification.put("title", promoted ? "Nova liga!" : "Promena lige");
        notification.put("message", promoted
                ? "Presao/la si u " + newLeague.name + ". Ukupno zvezda: " + totalStars + "."
                : "Pao/la si u " + newLeague.name + ". Ukupno zvezda: " + totalStars + ".");
        notification.put("action", NotificationRouter.ACTION_LEAGUE);
        notification.put("targetId", "league");
        notification.put("data", data);
        notification.put("source", "league_change");
        notification.put("read", false);
        notification.put("readAt", null);
        notification.put("actionedAt", null);
        notification.put("createdAt", FieldValue.serverTimestamp());
        return notification;
    }
}
