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
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final String forfeitedPlayerId;
    private final boolean soloChallenge;
    private final String statusMessage;
    private final List<Long> matchedPairs;
    private final List<Long> attemptedPairs;
    private final Map<String, Long> scores;

    public MatchingMultiplayerState(DocumentSnapshot snapshot) {
        String phase = stringValue(snapshot.getString("phase"), "waiting");
        status = "spojnice".equals(snapshot.getString("currentGame"))
                && "spojnicePlaying".equals(phase) ? "playing"
                : ("associations".equals(snapshot.getString("currentGame")) ? "next" : "waiting");
        currentRound = intValue(snapshot.getLong("spCurrentRound"));
        currentPlayer = stringValue(snapshot.getString("spCurrentPlayer"), "");
        secondChance = Boolean.TRUE.equals(snapshot.getBoolean("spSecondChance"));
        deadlineAt = 0L;
        player1Id = stringValue(snapshot.getString("player1Id"), "");
        player1Name = stringValue(snapshot.getString("player1Name"), "");
        player2Id = stringValue(snapshot.getString("player2Id"), "");
        player2Name = stringValue(snapshot.getString("player2Name"), "");
        forfeitedPlayerId = stringValue(snapshot.getString("forfeitedPlayerId"), "");
        soloChallenge = Boolean.TRUE.equals(snapshot.getBoolean("soloChallenge"));
        statusMessage = stringValue(snapshot.getString("statusMessage"), "");
        matchedPairs = listValue(snapshot.get("spMatchedPairs"));
        attemptedPairs = listValue(snapshot.get("spAttemptedPairs"));
        scores = scoreMap(snapshot);
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

    public String getPlayer1Name() {
        return player1Name;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public String getForfeitedPlayerId() {
        return forfeitedPlayerId;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isSoloChallenge() {
        return soloChallenge;
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

    public boolean isForfeited(String playerId) {
        return playerId != null && playerId.equals(forfeitedPlayerId);
    }

    public boolean hasForfeit() {
        return forfeitedPlayerId != null && !forfeitedPlayerId.trim().isEmpty();
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

    private static Map<String, Long> scoreMap(DocumentSnapshot snapshot) {
        Map<String, Long> result = new java.util.HashMap<>();
        result.put(stringValue(snapshot.getString("player1Id"), ""),
                longValue(snapshot.getLong("player1Score")));
        result.put(stringValue(snapshot.getString("player2Id"), ""),
                longValue(snapshot.getLong("player2Score")));
        return result;
    }
}
