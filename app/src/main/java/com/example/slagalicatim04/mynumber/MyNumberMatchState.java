package com.example.slagalicatim04.mynumber;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MyNumberMatchState {
    private final String phase;
    private final String player1Id;
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final int round;
    private final int activePlayer;
    private final int target;
    private final List<Integer> numbers;
    private final int secondsLeft;
    private final int targetRevealLeft;
    private final int numbersRevealLeft;
    private final boolean targetShown;
    private final boolean numbersShown;
    private final boolean p1Submitted;
    private final boolean p2Submitted;
    private final String p1Expression;
    private final String p2Expression;
    private final Integer p1Result;
    private final Integer p2Result;
    private final long player1Score;
    private final long player2Score;
    private final String statusMessage;

    public MyNumberMatchState(DocumentSnapshot snapshot) {
        phase = stringValue(snapshot, "phase");
        player1Id = stringValue(snapshot, "player1Id");
        player1Name = stringValue(snapshot, "player1Name");
        player2Id = stringValue(snapshot, "player2Id");
        player2Name = stringValue(snapshot, "player2Name");
        round = (int) longValue(snapshot, "myNumberRound", 1);
        activePlayer = (int) longValue(snapshot, "myNumberActivePlayer", 1);
        target = (int) longValue(snapshot, "myNumberTarget", 0);
        numbers = intList(snapshot.get("myNumberNumbers"));
        secondsLeft = (int) longValue(snapshot, "myNumberSecondsLeft", 60);
        targetRevealLeft = (int) longValue(snapshot, "myNumberTargetRevealLeft", 5);
        numbersRevealLeft = (int) longValue(snapshot, "myNumberNumbersRevealLeft", 5);
        targetShown = Boolean.TRUE.equals(snapshot.getBoolean("myNumberTargetShown"));
        numbersShown = Boolean.TRUE.equals(snapshot.getBoolean("myNumberNumbersShown"));
        p1Submitted = Boolean.TRUE.equals(snapshot.getBoolean("myNumberP1Submitted"));
        p2Submitted = Boolean.TRUE.equals(snapshot.getBoolean("myNumberP2Submitted"));
        p1Expression = stringValue(snapshot, "myNumberP1Expression");
        p2Expression = stringValue(snapshot, "myNumberP2Expression");
        p1Result = nullableInt(snapshot, "myNumberP1Result");
        p2Result = nullableInt(snapshot, "myNumberP2Result");
        player1Score = longValue(snapshot, "player1Score", 0);
        player2Score = longValue(snapshot, "player2Score", 0);
        statusMessage = stringValue(snapshot, "myNumberStatusMessage");
    }

    public String getPhase() { return phase; }
    public String getPlayer1Id() { return player1Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Id() { return player2Id; }
    public String getPlayer2Name() { return player2Name; }
    public int getRound() { return round; }
    public int getActivePlayer() { return activePlayer; }
    public int getTarget() { return target; }
    public List<Integer> getNumbers() { return new ArrayList<>(numbers); }
    public int getSecondsLeft() { return secondsLeft; }
    public int getTargetRevealLeft() { return targetRevealLeft; }
    public int getNumbersRevealLeft() { return numbersRevealLeft; }
    public boolean isTargetShown() { return targetShown; }
    public boolean isNumbersShown() { return numbersShown; }
    public boolean isP1Submitted() { return p1Submitted; }
    public boolean isP2Submitted() { return p2Submitted; }
    public String getP1Expression() { return p1Expression; }
    public String getP2Expression() { return p2Expression; }
    public Integer getP1Result() { return p1Result; }
    public Integer getP2Result() { return p2Result; }
    public long getPlayer1Score() { return player1Score; }
    public long getPlayer2Score() { return player2Score; }
    public String getStatusMessage() { return statusMessage; }

    public boolean isSubmitted(int player) {
        if (player == 1) return p1Submitted;
        if (player == 2) return p2Submitted;
        return false;
    }

    private static String stringValue(DocumentSnapshot snapshot, String key) {
        String value = snapshot.getString(key);
        return value == null ? "" : value;
    }

    private static long longValue(DocumentSnapshot snapshot, String key, long fallback) {
        Long value = snapshot.getLong(key);
        return value == null ? fallback : value;
    }

    private static Integer nullableInt(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? null : value.intValue();
    }

    private static List<Integer> intList(Object value) {
        List<Integer> out = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item instanceof Number) out.add(((Number) item).intValue());
            }
        }
        return out;
    }
}
