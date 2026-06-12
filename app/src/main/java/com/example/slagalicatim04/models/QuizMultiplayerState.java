package com.example.slagalicatim04.models;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Collections;
import java.util.Map;

public class QuizMultiplayerState {
    private final String status;
    private final int currentQuestion;
    private final long deadlineAt;
    private final String player1Id;
    private final String player2Id;
    private final Map<String, Long> scores;
    private final Map<String, Object> answers;

    public QuizMultiplayerState(DocumentSnapshot snapshot) {
        status = stringValue(snapshot.getString("status"), "waiting");
        currentQuestion = intValue(snapshot.getLong("currentQuestion"));
        deadlineAt = longValue(snapshot.getLong("deadlineAt"));
        player1Id = stringValue(snapshot.getString("player1Id"), "");
        player2Id = stringValue(snapshot.getString("player2Id"), "");
        scores = mapValue(snapshot.get("scores"));
        answers = objectMapValue(snapshot.get("answers"));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }
}
