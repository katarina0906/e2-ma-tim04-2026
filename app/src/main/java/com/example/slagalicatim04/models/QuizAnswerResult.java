package com.example.slagalicatim04.models;

public class QuizAnswerResult {
    private String playerId;
    private String answerId;
    private boolean correct;
    private long answeredAt;

    public QuizAnswerResult() {
    }

    public QuizAnswerResult(String playerId, String answerId, boolean correct, long answeredAt) {
        this.playerId = playerId;
        this.answerId = answerId;
        this.correct = correct;
        this.answeredAt = answeredAt;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getAnswerId() {
        return answerId;
    }

    public boolean isCorrect() {
        return correct;
    }

    public long getAnsweredAt() {
        return answeredAt;
    }
}