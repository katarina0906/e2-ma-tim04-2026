package com.example.slagalicatim04.services;

import com.example.slagalicatim04.models.SkockoGameResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SkockoGameService {
    public static final int CODE_LENGTH = 4;
    public static final int SYMBOL_COUNT = 6;
    public static final int MAX_ATTEMPTS = 6;
    public static final int ROUND_COUNT = 2;

    public enum Phase {
        PLAYING,
        STEAL,
        ROUND_FINISHED,
        GAME_FINISHED
    }

    private final Random random;
    private final int[] scores = new int[2];
    private final List<SkockoGameResult.RoundResult> completedRounds = new ArrayList<>();

    private int[] secret = new int[CODE_LENGTH];
    private int roundIndex;
    private int attempts;
    private int solvedAttempt;
    private boolean stealAttempted;
    private boolean stealSolved;
    private Phase phase;

    public SkockoGameService() {
        this(new Random());
    }

    SkockoGameService(Random random) {
        this.random = random;
    }

    public void startNewGame() {
        Arrays.fill(scores, 0);
        completedRounds.clear();
        roundIndex = 0;
        startRound();
    }

    public void startNextRound() {
        if (phase != Phase.ROUND_FINISHED || roundIndex + 1 >= ROUND_COUNT) {
            throw new IllegalStateException("Naredna runda trenutno nije dostupna.");
        }
        roundIndex++;
        startRound();
    }

    public MoveResult submitGuess(int[] guess) {
        validateGuess(guess);
        if (phase == Phase.PLAYING) {
            attempts++;
            Feedback feedback = calculateFeedback(secret, guess);
            if (feedback.getExact() == CODE_LENGTH) {
                solvedAttempt = attempts;
                int points = pointsForAttempt(attempts);
                scores[getRoundStarter()] += points;
                finishRound();
                return new MoveResult(feedback, attempts, points, true, phase);
            }
            if (attempts >= MAX_ATTEMPTS) {
                phase = Phase.STEAL;
            }
            return new MoveResult(feedback, attempts, 0, false, phase);
        }

        if (phase == Phase.STEAL) {
            stealAttempted = true;
            stealSolved = Arrays.equals(secret, guess);
            int points = stealSolved ? 10 : 0;
            if (stealSolved) {
                scores[getStealingPlayer()] += points;
            }
            finishRound();
            return new MoveResult(
                    calculateFeedback(secret, guess),
                    1,
                    points,
                    stealSolved,
                    phase
            );
        }

        throw new IllegalStateException("Potez nije dozvoljen u trenutnoj fazi.");
    }

    public Phase expireCurrentPhase() {
        if (phase == Phase.PLAYING) {
            phase = Phase.STEAL;
        } else if (phase == Phase.STEAL) {
            finishRound();
        }
        return phase;
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public int getRoundStarter() {
        return roundIndex % 2;
    }

    public int getStealingPlayer() {
        return 1 - getRoundStarter();
    }

    public int getAttempts() {
        return attempts;
    }

    public int getScore(int player) {
        if (player < 0 || player >= scores.length) {
            throw new IllegalArgumentException("Nepostojeci igrac.");
        }
        return scores[player];
    }

    public Phase getPhase() {
        return phase;
    }

    public SkockoGameResult getGameResult() {
        if (phase != Phase.GAME_FINISHED) {
            throw new IllegalStateException("Igra jos nije zavrsena.");
        }
        return new SkockoGameResult(scores[0], scores[1], completedRounds);
    }

    private void startRound() {
        attempts = 0;
        solvedAttempt = 0;
        stealAttempted = false;
        stealSolved = false;
        for (int i = 0; i < CODE_LENGTH; i++) {
            secret[i] = random.nextInt(SYMBOL_COUNT);
        }
        phase = Phase.PLAYING;
    }

    private void finishRound() {
        completedRounds.add(new SkockoGameResult.RoundResult(
                getRoundStarter(),
                solvedAttempt,
                stealAttempted,
                stealSolved
        ));
        phase = roundIndex + 1 >= ROUND_COUNT ? Phase.GAME_FINISHED : Phase.ROUND_FINISHED;
    }

    private static void validateGuess(int[] guess) {
        if (guess == null || guess.length != CODE_LENGTH) {
            throw new IllegalArgumentException("Kombinacija mora imati cetiri znaka.");
        }
        for (int symbol : guess) {
            if (symbol < 0 || symbol >= SYMBOL_COUNT) {
                throw new IllegalArgumentException("Kombinacija sadrzi nepostojeci znak.");
            }
        }
    }

    public static int pointsForAttempt(int attempt) {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Pokusaj mora biti izmedju 1 i 6.");
        }
        if (attempt <= 2) {
            return 20;
        }
        if (attempt <= 4) {
            return 15;
        }
        return 10;
    }

    public static Feedback calculateFeedback(int[] secret, int[] guess) {
        validateGuess(secret);
        validateGuess(guess);
        int exact = 0;
        int[] secretCounts = new int[SYMBOL_COUNT];
        int[] guessCounts = new int[SYMBOL_COUNT];

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (secret[i] == guess[i]) {
                exact++;
            } else {
                secretCounts[secret[i]]++;
                guessCounts[guess[i]]++;
            }
        }

        int partial = 0;
        for (int i = 0; i < SYMBOL_COUNT; i++) {
            partial += Math.min(secretCounts[i], guessCounts[i]);
        }
        return new Feedback(exact, partial);
    }

    public static class Feedback {
        private final int exact;
        private final int partial;

        public Feedback(int exact, int partial) {
            this.exact = exact;
            this.partial = partial;
        }

        public int getExact() {
            return exact;
        }

        public int getPartial() {
            return partial;
        }
    }

    public static class MoveResult {
        private final Feedback feedback;
        private final int attempt;
        private final int awardedPoints;
        private final boolean solved;
        private final Phase resultingPhase;

        public MoveResult(Feedback feedback, int attempt, int awardedPoints, boolean solved,
                          Phase resultingPhase) {
            this.feedback = feedback;
            this.attempt = attempt;
            this.awardedPoints = awardedPoints;
            this.solved = solved;
            this.resultingPhase = resultingPhase;
        }

        public Feedback getFeedback() {
            return feedback;
        }

        public int getAttempt() {
            return attempt;
        }

        public int getAwardedPoints() {
            return awardedPoints;
        }

        public boolean isSolved() {
            return solved;
        }

        public Phase getResultingPhase() {
            return resultingPhase;
        }
    }
}
