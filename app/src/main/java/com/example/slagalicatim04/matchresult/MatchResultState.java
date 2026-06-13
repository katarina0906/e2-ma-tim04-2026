package com.example.slagalicatim04.matchresult;

import com.google.firebase.firestore.DocumentSnapshot;

public class MatchResultState {
    public static final String GAME = "matchResult";
    public static final String PHASE = "matchFinished";

    private final String currentGame;
    private final String player1Id;
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final long player1Score;
    private final long player2Score;

    public MatchResultState(DocumentSnapshot snapshot) {
        currentGame = stringValue(snapshot, "currentGame");
        player1Id = stringValue(snapshot, "player1Id");
        player1Name = stringValue(snapshot, "player1Name");
        player2Id = stringValue(snapshot, "player2Id");
        player2Name = stringValue(snapshot, "player2Name");
        player1Score = longValue(snapshot, "player1Score");
        player2Score = longValue(snapshot, "player2Score");
    }

    public boolean isMatchResult() { return GAME.equals(currentGame); }
    public String getPlayer1Id() { return player1Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Id() { return player2Id; }
    public String getPlayer2Name() { return player2Name; }
    public long getPlayer1Score() { return player1Score; }
    public long getPlayer2Score() { return player2Score; }

    public int winner() {
        if (player1Score > player2Score) return 1;
        if (player2Score > player1Score) return 2;
        return 0;
    }

    private static String stringValue(DocumentSnapshot snapshot, String key) {
        String value = snapshot.getString(key);
        return value == null ? "" : value;
    }

    private static long longValue(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? 0 : value;
    }
}
