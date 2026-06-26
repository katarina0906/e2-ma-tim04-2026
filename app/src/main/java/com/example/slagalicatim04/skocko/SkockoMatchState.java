package com.example.slagalicatim04.skocko;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SkockoMatchState {
    public static final String PHASE_WAITING = "waiting";
    public static final String PHASE_ROUND = "round";
    public static final String PHASE_STEAL = "steal";
    public static final String PHASE_FINISHED = "finished";

    private final String player1Id;
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final String forfeitedPlayerId;
    private final boolean player1Ready;
    private final boolean player2Ready;
    private final long player1Score;
    private final long player2Score;
    private final int round;
    private final String phase;
    private final String phaseToken;
    private final int activePlayer;
    private final long phaseStartedAt;
    private final long phaseDeadlineAt;
    private final List<Integer> secret;
    private final List<List<Integer>> guesses;
    private final List<List<Integer>> feedback;
    private final boolean finished;
    private final String statusMessage;
    private final String currentGame;

    public SkockoMatchState(DocumentSnapshot snapshot) {
        player1Id = stringValue(snapshot, "player1Id");
        player1Name = stringValue(snapshot, "player1Name");
        player2Id = stringValue(snapshot, "player2Id");
        player2Name = stringValue(snapshot, "player2Name");
        forfeitedPlayerId = stringValue(snapshot, "forfeitedPlayerId");
        player1Ready = Boolean.TRUE.equals(snapshot.getBoolean("player1Ready"));
        player2Ready = Boolean.TRUE.equals(snapshot.getBoolean("player2Ready"));
        player1Score = longValue(snapshot, "player1Score", 0);
        player2Score = longValue(snapshot, "player2Score", 0);
        round = (int) longValue(snapshot, "round", 1);
        phase = stringValue(snapshot, "phase", PHASE_WAITING);
        phaseToken = stringValue(snapshot, "phaseToken");
        activePlayer = (int) longValue(snapshot, "activePlayer", 1);
        phaseStartedAt = timeMillis(snapshot, "phaseStartedAt");
        phaseDeadlineAt = timeMillis(snapshot, "phaseDeadlineAt");
        secret = intList(snapshot.get("secret"));
        List<List<Integer>> parsedGuesses = new ArrayList<>();
        List<List<Integer>> parsedFeedback = new ArrayList<>();
        parseAttempts(snapshot.get("attempts"), parsedGuesses, parsedFeedback);
        guesses = Collections.unmodifiableList(parsedGuesses);
        feedback = Collections.unmodifiableList(parsedFeedback);
        finished = Boolean.TRUE.equals(snapshot.getBoolean("finished"));
        statusMessage = stringValue(snapshot, "statusMessage");
        currentGame = stringValue(snapshot, "currentGame");
    }

    public String getPlayer1Id() {
        return player1Id;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public String getPlayer2Id() {
        return player2Id;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public String getForfeitedPlayerId() {
        return forfeitedPlayerId;
    }

    public boolean isPlayer1Ready() {
        return player1Ready;
    }

    public boolean isPlayer2Ready() {
        return player2Ready;
    }

    public boolean isReady(int player) {
        return player == 1 ? player1Ready : player == 2 && player2Ready;
    }

    public long getPlayer1Score() {
        return player1Score;
    }

    public long getPlayer2Score() {
        return player2Score;
    }

    public int getRound() {
        return round;
    }

    public String getPhase() {
        return phase;
    }

    public String getPhaseToken() {
        return phaseToken;
    }

    public int getActivePlayer() {
        return activePlayer;
    }

    public long getPhaseStartedAt() {
        return phaseStartedAt;
    }

    public long getPhaseDeadlineAt() {
        return phaseDeadlineAt;
    }

    public List<Integer> getSecret() {
        return secret;
    }

    public List<List<Integer>> getGuesses() {
        return guesses;
    }

    public List<List<Integer>> getFeedback() {
        return feedback;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getCurrentGame() {
        return currentGame;
    }

    public boolean hasSecondPlayer() {
        return !isEmpty(player2Id);
    }

    public int playerNumber(String playerId) {
        if (playerId.equals(player1Id)) {
            return 1;
        }
        if (playerId.equals(player2Id)) {
            return 2;
        }
        return 0;
    }

    public boolean isParticipant(String playerId) {
        return playerNumber(playerId) != 0;
    }

    public boolean isForfeited(String playerId) {
        return playerId != null && playerId.equals(forfeitedPlayerId);
    }

    public boolean hasForfeit() {
        return !isEmpty(forfeitedPlayerId);
    }

    public int roundStarter() {
        return round == 1 ? 1 : 2;
    }

    public int secondsLeft(long durationMs) {
        if (phaseDeadlineAt > 0) {
            long remaining = phaseDeadlineAt - System.currentTimeMillis();
            return (int) Math.max(0, (remaining + 999) / 1000);
        }
        if (phaseStartedAt <= 0) {
            return 0;
        }
        long elapsed = Math.max(0, System.currentTimeMillis() - phaseStartedAt);
        return (int) Math.max(0, (durationMs - elapsed + 999) / 1000);
    }

    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String stringValue(DocumentSnapshot snapshot, String key) {
        String value = snapshot.getString(key);
        return value == null ? "" : value;
    }

    private static String stringValue(DocumentSnapshot snapshot, String key, String fallback) {
        String value = snapshot.getString(key);
        return value == null ? fallback : value;
    }

    private static long longValue(DocumentSnapshot snapshot, String key, long fallback) {
        Long value = snapshot.getLong(key);
        return value == null ? fallback : value;
    }

    private static long timeMillis(DocumentSnapshot snapshot, String key) {
        Object value = snapshot.get(key);
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    private static List<Integer> intList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Number) {
                result.add(((Number) item).intValue());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static void parseAttempts(Object value, List<List<Integer>> guesses,
                                      List<List<Integer>> feedback) {
        if (!(value instanceof List<?>)) {
            return;
        }
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> attempt = (Map<?, ?>) item;
            List<Integer> symbols = intList(attempt.get("symbols"));
            if (symbols.size() != 4) {
                continue;
            }
            guesses.add(symbols);
            List<Integer> moveFeedback = new ArrayList<>();
            moveFeedback.add(intValue(attempt.get("exact")));
            moveFeedback.add(intValue(attempt.get("partial")));
            feedback.add(Collections.unmodifiableList(moveFeedback));
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
