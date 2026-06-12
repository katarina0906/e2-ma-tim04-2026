package com.example.slagalicatim04.models;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MatchingMultiplayerState {
    private final String status;
    private final int currentRound;
    private final String currentPlayer;
    private final boolean secondChance;
    private final long deadlineAt;
    private final String player1Id;
    private final String player2Id;
    private final List<Long> matchedPairs;
    private final List<Long> attemptedPairs;
    private final Map<String, Long> scores;

    public MatchingMultiplayerState(DocumentSnapshot snapshot) {
        status = stringValue(snapshot.getString("status"), "waiting");
        currentRound = intValue(snapshot.getLong("currentRound"));
        currentPlayer = stringValue(snapshot.getString("currentPlayer"), "");
        secondChance = Boolean.TRUE.equals(snapshot.getBoolean("secondChance"));
        deadlineAt = longValue(snapshot.getLong("deadlineAt"));
        player1Id = stringValue(snapshot.getString("player1Id"), "");
        player2Id = stringValue(snapshot.getString("player2Id"), "");
        matchedPairs = listValue(snapshot.get("matchedPairs"));
        attemptedPairs = listValue(snapshot.get("attemptedPairs"));
        scores = mapValue(snapshot.get("scores"));
    }

    public String getStatus() {
        return status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public String getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isSecondChance() {
        return secondChance;
    }

    public long getDeadlineAt() {
        return deadlineAt;
    }

    public String getPlayer1Id() {
        return player1Id;
    }

    public String getPlayer2Id() {
        return player2Id;
    }

    public boolean isMatched(int pairIndex) {
        return matchedPairs.contains((long) pairIndex);
    }

    public boolean isAttempted(int pairIndex) {
        return attemptedPairs.contains((long) pairIndex);
    }

    public int getScore(String playerId) {
        Long score = scores.get(playerId);
        return score == null ? 0 : score.intValue();
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private static String stringValue(String value, String fallback) {
        return value == null ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> listValue(Object value) {
        return value instanceof List ? new ArrayList<>((List<Long>) value) : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Long>) value : Collections.emptyMap();
    }
}
