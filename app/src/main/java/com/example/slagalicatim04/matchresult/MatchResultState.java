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
    private final String challengeId;
    private final String tournamentId;
    private final String tournamentRound;
    private final boolean soloChallenge;
    private final long player1Score;
    private final long player2Score;
    private final long player1StarDelta;
    private final long player2StarDelta;
    private final long player1Stars;
    private final long player2Stars;
    private final long player1EarnedTokens;
    private final long player2EarnedTokens;

    public MatchResultState(DocumentSnapshot snapshot) {
        currentGame = stringValue(snapshot, "currentGame");
        player1Id = stringValue(snapshot, "player1Id");
        player1Name = stringValue(snapshot, "player1Name");
        player2Id = stringValue(snapshot, "player2Id");
        player2Name = stringValue(snapshot, "player2Name");
        challengeId = stringValue(snapshot, "challengeId");
        tournamentId = stringValue(snapshot, "tournamentId");
        tournamentRound = stringValue(snapshot, "tournamentRound");
        soloChallenge = Boolean.TRUE.equals(snapshot.getBoolean("soloChallenge"));
        player1Score = longValue(snapshot, "player1Score");
        player2Score = longValue(snapshot, "player2Score");
        player1StarDelta = longValue(snapshot, "player1StarDelta");
        player2StarDelta = longValue(snapshot, "player2StarDelta");
        player1Stars = longValue(snapshot, "player1Stars");
        player2Stars = longValue(snapshot, "player2Stars");
        player1EarnedTokens = longValue(snapshot, "player1EarnedTokens");
        player2EarnedTokens = longValue(snapshot, "player2EarnedTokens");
    }

    public boolean isMatchResult() { return GAME.equals(currentGame); }
    public String getPlayer1Id() { return player1Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Id() { return player2Id; }
    public String getPlayer2Name() { return player2Name; }
    public String getChallengeId() { return challengeId; }
    public String getTournamentId() { return tournamentId; }
    public String getTournamentRound() { return tournamentRound; }
    public boolean isSoloChallenge() { return soloChallenge; }
    public long getPlayer1Score() { return player1Score; }
    public long getPlayer2Score() { return player2Score; }
    public long getPlayer1StarDelta() { return player1StarDelta; }
    public long getPlayer2StarDelta() { return player2StarDelta; }
    public long getPlayer1Stars() { return player1Stars; }
    public long getPlayer2Stars() { return player2Stars; }
    public long getPlayer1EarnedTokens() { return player1EarnedTokens; }
    public long getPlayer2EarnedTokens() { return player2EarnedTokens; }

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
