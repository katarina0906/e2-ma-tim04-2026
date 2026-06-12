package com.example.slagalicatim04.associations;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssociationMatchState {
    public static final String GAME = "associations";
    public static final String PHASE_ROUND = "associationRound";

    private final String currentGame;
    private final String phase;
    private final String player1Id;
    private final String player1Name;
    private final String player2Id;
    private final String player2Name;
    private final long player1Score;
    private final long player2Score;
    private final int round;
    private final int activePlayer;
    private final boolean openPhase;
    private final int secondsLeft;
    private final String puzzleId;
    private final List<Boolean> revealed;
    private final List<Boolean> solvedColumns;
    private final boolean finalSolved;
    private final int roundPlayer1Score;
    private final int roundPlayer2Score;
    private final String statusMessage;

    public AssociationMatchState(DocumentSnapshot snapshot) {
        currentGame = stringValue(snapshot, "currentGame");
        phase = stringValue(snapshot, "phase");
        player1Id = stringValue(snapshot, "player1Id");
        player1Name = stringValue(snapshot, "player1Name");
        player2Id = stringValue(snapshot, "player2Id");
        player2Name = stringValue(snapshot, "player2Name");
        player1Score = longValue(snapshot, "player1Score", 0);
        player2Score = longValue(snapshot, "player2Score", 0);
        round = (int) longValue(snapshot, "associationRound", 1);
        activePlayer = (int) longValue(snapshot, "associationActivePlayer", 1);
        openPhase = !Boolean.FALSE.equals(snapshot.getBoolean("associationOpenPhase"));
        secondsLeft = (int) longValue(snapshot, "associationSecondsLeft",
                AssociationGameService.ROUND_SECONDS);
        puzzleId = stringValue(snapshot, "associationPuzzleId");
        revealed = booleanList(snapshot.get("associationRevealed"),
                AssociationPuzzle.COLUMN_COUNT * AssociationPuzzle.CLUES_PER_COLUMN);
        solvedColumns = booleanList(snapshot.get("associationSolvedColumns"),
                AssociationPuzzle.COLUMN_COUNT);
        finalSolved = Boolean.TRUE.equals(snapshot.getBoolean("associationFinalSolved"));
        roundPlayer1Score = (int) longValue(snapshot, "associationRoundPlayer1Score", 0);
        roundPlayer2Score = (int) longValue(snapshot, "associationRoundPlayer2Score", 0);
        statusMessage = stringValue(snapshot, "statusMessage");
    }

    public String getCurrentGame() { return currentGame; }
    public String getPhase() { return phase; }
    public String getPlayer1Id() { return player1Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Id() { return player2Id; }
    public String getPlayer2Name() { return player2Name; }
    public long getPlayer1Score() { return player1Score; }
    public long getPlayer2Score() { return player2Score; }
    public int getRound() { return round; }
    public int getActivePlayer() { return activePlayer; }
    public boolean isOpenPhase() { return openPhase; }
    public int getSecondsLeft() { return secondsLeft; }
    public String getPuzzleId() { return puzzleId; }
    public List<Boolean> getRevealed() { return revealed; }
    public List<Boolean> getSolvedColumns() { return solvedColumns; }
    public boolean isFinalSolved() { return finalSolved; }
    public int getRoundPlayer1Score() { return roundPlayer1Score; }
    public int getRoundPlayer2Score() { return roundPlayer2Score; }
    public String getStatusMessage() { return statusMessage; }

    public boolean isAssociationGame() {
        return GAME.equals(currentGame) && PHASE_ROUND.equals(phase);
    }

    public boolean isRevealed(int column, int row) {
        return booleanAt(revealed, column * AssociationPuzzle.CLUES_PER_COLUMN + row);
    }

    public boolean isColumnSolved(int column) {
        return booleanAt(solvedColumns, column);
    }

    public int playerNumber(String playerId) {
        if (playerId != null && playerId.equals(player1Id)) return 1;
        if (playerId != null && playerId.equals(player2Id)) return 2;
        return 0;
    }

    private static boolean booleanAt(List<Boolean> values, int index) {
        return index >= 0 && index < values.size() && Boolean.TRUE.equals(values.get(index));
    }

    private static List<Boolean> booleanList(Object value, int size) {
        List<Boolean> result = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                result.add(Boolean.TRUE.equals(item));
            }
        }
        while (result.size() < size) {
            result.add(false);
        }
        if (result.size() > size) {
            result = new ArrayList<>(result.subList(0, size));
        }
        return Collections.unmodifiableList(result);
    }

    private static String stringValue(DocumentSnapshot snapshot, String key) {
        String value = snapshot.getString(key);
        return value == null ? "" : value;
    }

    private static long longValue(DocumentSnapshot snapshot, String key, long fallback) {
        Long value = snapshot.getLong(key);
        return value == null ? fallback : value;
    }
}
