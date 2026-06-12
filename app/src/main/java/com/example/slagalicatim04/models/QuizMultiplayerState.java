package com.example.slagalicatim04.models;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Collections;
import java.util.Map;

public class QuizMultiplayerState {
    private final String status;
    private final int currentQuestion;
    private final long deadlineAt;
    private final String player1Id;
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final Map<String, Long> scores;
    private final Map<String, Object> answers;

    public QuizMultiplayerState(DocumentSnapshot snapshot) {
        String phase = stringValue(snapshot.getString("phase"), "waiting");
        status = "koZnaZna".equals(snapshot.getString("currentGame"))
                && "koZnaZnaPlaying".equals(phase) ? "playing"
                : ("spojnice".equals(snapshot.getString("currentGame")) ? "next" : "waiting");
        currentQuestion = intValue(snapshot.getLong("kzzCurrentQuestion"));
        deadlineAt = 0L;
        player1Id = stringValue(snapshot.getString("player1Id"), "");
        player1Name = stringValue(snapshot.getString("player1Name"), "Igrac 1");
        player2Id = stringValue(snapshot.getString("player2Id"), "");
        player2Name = stringValue(snapshot.getString("player2Name"), "Igrac 2");
        scores = scoreMap(snapshot);
        answers = objectMapValue(snapshot.get("kzzAnswers"));
    }

    public String getStatus() {
        return status;
    }

    public int getCurrentQuestion() {
        return currentQuestion;
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

    public int getScore(String playerId) {
        Long score = scores.get(playerId);
        return score == null ? 0 : score.intValue();
    }

    public boolean hasAnswered(String playerId) {
        return answers.containsKey(playerId);
    }

    public int getAnswerCount() {
        return answers.size();
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }
}
