package com.example.slagalicatim04.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkockoGameResult {
    private final int player1Score;
    private final int player2Score;
    private final List<RoundResult> rounds;

    public SkockoGameResult(int player1Score, int player2Score, List<RoundResult> rounds) {
        this.player1Score = player1Score;
        this.player2Score = player2Score;
        this.rounds = Collections.unmodifiableList(new ArrayList<>(rounds));
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public List<RoundResult> getRounds() {
        return rounds;
    }

    public static class RoundResult {
        private final int starter;
        private final int solvedAttempt;
        private final boolean stealAttempted;
        private final boolean stealSolved;

        public RoundResult(int starter, int solvedAttempt, boolean stealAttempted,
                           boolean stealSolved) {
            this.starter = starter;
            this.solvedAttempt = solvedAttempt;
            this.stealAttempted = stealAttempted;
            this.stealSolved = stealSolved;
        }

        public int getStarter() {
            return starter;
        }

        public int getSolvedAttempt() {
            return solvedAttempt;
        }

        public boolean isStealAttempted() {
            return stealAttempted;
        }

        public boolean isStealSolved() {
            return stealSolved;
        }
    }
}
