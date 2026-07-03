package com.example.slagalicatim04.tournament;

import java.util.Map;

public class TournamentParticipant {
    public final String userId;
    public final String username;
    public final String league;
    public final String avatarData;

    public TournamentParticipant(String userId, String username, String league, String avatarData) {
        this.userId = userId;
        this.username = username;
        this.league = league;
        this.avatarData = avatarData;
    }

    public static TournamentParticipant fromMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return new TournamentParticipant("", "", "", "");
        }
        Map<?, ?> map = (Map<?, ?>) value;
        return new TournamentParticipant(
                string(map.get("userId")),
                string(map.get("username")),
                string(map.get("league")),
                string(map.get("avatarData")));
    }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("userId", userId);
        map.put("username", username);
        map.put("league", league);
        map.put("avatarData", avatarData);
        return map;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
