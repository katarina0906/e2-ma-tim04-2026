package com.example.slagalicatim04.models;

public class QuizRoundState {
    private int currentQuestionIndex;
    private int player1Score;
    private int player2Score;
    private boolean finished;

    public QuizRoundState() {
    }

    public QuizRoundState(int currentQuestionIndex, int player1Score, int player2Score, boolean finished) {
        this.currentQuestionIndex = currentQuestionIndex;
        this.player1Score = player1Score;
        this.player2Score = player2Score;
        this.finished = finished;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public boolean isFinished() {
        return finished;
    }
}