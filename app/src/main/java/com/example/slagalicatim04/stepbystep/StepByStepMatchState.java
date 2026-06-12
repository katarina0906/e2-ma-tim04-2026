package com.example.slagalicatim04.stepbystep;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class StepByStepMatchState {
    public static final String PHASE_WAITING = "waiting";
    public static final String PHASE_PLAYING = "playing";
    public static final String PHASE_STEAL = "steal";
    public static final String PHASE_FINISHED = "finished";

    private final String player1Id;
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final long player1Score;
    private final long player2Score;
    private final int round;
    private final String phase;
    private final int activePlayer;
    private final int stealPlayer;
    private final long roundStartedAt;
    private final long stealStartedAt;
    private final boolean finished;
    private final String statusMessage;
    private final boolean player1Ready;
    private final boolean player2Ready;
    private final int visibleStepCount;
    private final int secondsLeft;

    public StepByStepMatchState(DocumentSnapshot snapshot) {
        player1Id = stringValue(snapshot, "player1Id");
        player1Name = stringValue(snapshot, "player1Name");
        player2Id = stringValue(snapshot, "player2Id");
        player2Name = stringValue(snapshot, "player2Name");
        player1Score = longValue(snapshot, "player1Score", 0);
        player2Score = longValue(snapshot, "player2Score", 0);
        round = (int) longValue(snapshot, "round", 1);
        phase = stringValue(snapshot, "phase", PHASE_WAITING);
        activePlayer = (int) longValue(snapshot, "activePlayer", 1);
        stealPlayer = (int) longValue(snapshot, "stealPlayer", 0);
        roundStartedAt = timeMillis(snapshot, "roundStartedAt", 0);
        stealStartedAt = timeMillis(snapshot, "stealStartedAt", 0);
        finished = Boolean.TRUE.equals(snapshot.getBoolean("finished"));
        statusMessage = stringValue(snapshot, "statusMessage");
        player1Ready = Boolean.TRUE.equals(snapshot.getBoolean("player1Ready"));
        player2Ready = Boolean.TRUE.equals(snapshot.getBoolean("player2Ready"));
        visibleStepCount = (int) longValue(snapshot, "visibleStepCount", 0);
        secondsLeft = (int) longValue(snapshot, "secondsLeft", 0);
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

    public int getActivePlayer() {
        return activePlayer;
    }

    public int getStealPlayer() {
        return stealPlayer;
    }

    public long getRoundStartedAt() {
        return roundStartedAt;
    }

    public long getStealStartedAt() {
        return stealStartedAt;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isPlayer1Ready() {
        return player1Ready;
    }

    public boolean isPlayer2Ready() {
        return player2Ready;
    }

    public boolean isReady(int playerNumber) {
        if (playerNumber == 1) {
            return player1Ready;
        }
        if (playerNumber == 2) {
            return player2Ready;
        }
        return false;
    }

    public int getVisibleStepCount() {
        return visibleStepCount;
    }

    public int getSecondsLeft() {
        return secondsLeft;
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

    public String effectivePhase() {
        if (!finished && !hasSecondPlayer()) {
            return PHASE_WAITING;
        }
        return phase;
    }

    static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String stringValue(DocumentSnapshot snapshot, String key) {
        return stringValue(snapshot, key, "");
    }

    private static String stringValue(DocumentSnapshot snapshot, String key, String fallback) {
        String value = snapshot.getString(key);
        return value == null ? fallback : value;
    }

    private static long longValue(DocumentSnapshot snapshot, String key, long fallback) {
        Long value = snapshot.getLong(key);
        return value == null ? fallback : value;
    }

    private static long timeMillis(DocumentSnapshot snapshot, String key, long fallback) {
        Object value = snapshot.get(key);
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return fallback;
    }
}
