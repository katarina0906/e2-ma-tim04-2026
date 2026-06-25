package com.example.slagalicatim04.associations;

import com.example.slagalicatim04.services.SkockoGameService;
import com.example.slagalicatim04.skocko.SkockoMatchRepository;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AssociationMatchRepository {
    public interface Listener {
        void onState(AssociationMatchState state);
        void onError(Exception error);
    }

    private final DocumentReference matchRef;
    private final AssociationGameService service = new AssociationGameService();
    private final Random random = new Random();

    public AssociationMatchRepository(String roomId) {
        matchRef = FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(roomId);
    }

    public ListenerRegistration listen(Listener listener) {
        return matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
            } else if (snapshot != null && snapshot.exists()) {
                listener.onState(new AssociationMatchState(snapshot));
            }
        });
    }

    public void openCell(StepByStepPlayerSession player, int column, int row) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            AssociationMatchState state = state(snapshot);
            int myPlayer = playerNumber(state, player);
            int index = column * AssociationPuzzle.CLUES_PER_COLUMN + row;
            if (!canAct(state, myPlayer)
                    || (!state.isOpenPhase() && !state.canContinueAfterCorrect())
                    || column < 0 || column >= AssociationPuzzle.COLUMN_COUNT
                    || row < 0 || row >= AssociationPuzzle.CLUES_PER_COLUMN
                    || state.isRevealed(column, row)
                    || state.isColumnSolved(column)) {
                return null;
            }
            List<Boolean> revealed = mutableBooleans(state.getRevealed());
            revealed.set(index, true);
            Map<String, Object> updates = new HashMap<>();
            updates.put("associationRevealed", revealed);
            updates.put("associationOpenPhase", false);
            updates.put("associationCanContinueAfterCorrect", false);
            updates.put("statusMessage", "Igrac " + myPlayer
                    + " je otvorio polje. Moze da pogadja ili preda potez.");
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void submitColumnGuess(StepByStepPlayerSession player, AssociationPuzzle puzzle,
                                  int column, String guess) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            AssociationMatchState state = state(snapshot);
            int myPlayer = playerNumber(state, player);
            if (!canGuess(state, puzzle, myPlayer)
                    || column < 0 || column >= AssociationPuzzle.COLUMN_COUNT
                    || state.isColumnSolved(column)) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            if (service.matches(guess, puzzle.getColumnAnswer(column))) {
                int points = service.columnPoints(state.getRevealed(), column);
                List<Boolean> solved = mutableBooleans(state.getSolvedColumns());
                solved.set(column, true);
                List<Boolean> revealed = mutableBooleans(state.getRevealed());
                int start = column * AssociationPuzzle.CLUES_PER_COLUMN;
                for (int row = 0; row < AssociationPuzzle.CLUES_PER_COLUMN; row++) {
                    revealed.set(start + row, true);
                }
                updates.put("associationSolvedColumns", solved);
                updates.put("associationRevealed", revealed);
                updates.put("associationCanContinueAfterCorrect", hasOpenableCell(revealed, solved));
                addScore(state, updates, myPlayer, points);
                addRoundScore(state, updates, myPlayer, points);
                updates.put("statusMessage", "Igrac " + myPlayer + " je resio kolonu "
                        + (char) ('A' + column) + " za " + points
                        + " bodova i nastavlja da pogadja.");
            } else {
                switchTurn(updates, state, myPlayer, "Netacno resenje kolone.");
            }
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void submitFinalGuess(StepByStepPlayerSession player, AssociationPuzzle puzzle,
                                 String guess) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            AssociationMatchState state = state(snapshot);
            int myPlayer = playerNumber(state, player);
            if (!canGuess(state, puzzle, myPlayer) || state.isFinalSolved()) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            if (service.matches(guess, puzzle.getFinalAnswer())) {
                int points = service.finalPoints(
                        state.getRevealed(), state.getSolvedColumns());
                addScore(state, updates, myPlayer, points);
                addRoundScore(state, updates, myPlayer, points);
                updates.put("associationFinalSolved", true);
                advanceRound(updates, state, "Igrac " + myPlayer
                        + " je pogodio konacno resenje za " + points + " bodova.");
            } else {
                switchTurn(updates, state, myPlayer, "Netacno konacno resenje.");
            }
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void pass(StepByStepPlayerSession player) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            AssociationMatchState state = state(snapshot);
            int myPlayer = playerNumber(state, player);
            if (!canAct(state, myPlayer) || state.isOpenPhase()) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            switchTurn(updates, state, myPlayer,
                    "Igrac " + myPlayer + " je predao potez.");
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void tick(StepByStepPlayerSession player) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            AssociationMatchState state = state(snapshot);
            int myPlayer = playerNumber(state, player);
            if (!canAct(state, myPlayer)) {
                return null;
            }
            int secondsLeft = Math.max(0, state.getSecondsLeft() - 1);
            Map<String, Object> updates = new HashMap<>();
            updates.put("associationSecondsLeft", secondsLeft);
            if (secondsLeft == 0) {
                advanceRound(updates, state, "Vreme u rundi je isteklo.");
            }
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void resolveForfeitTurn(AssociationMatchState state) {
        String forfeitedPlayerId = state == null ? "" : state.getForfeitedPlayerId();
        if (state == null || !state.isAssociationGame()
                || forfeitedPlayerId == null || forfeitedPlayerId.trim().isEmpty()) {
            return;
        }
        int forfeitedPlayer = state.playerNumber(forfeitedPlayerId);
        if (forfeitedPlayer == 0 || forfeitedPlayer != state.getActivePlayer()) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        switchTurn(updates, state, forfeitedPlayer, "Igrac je napustio partiju.");
        matchRef.set(updates, SetOptions.merge());
    }

    private AssociationMatchState state(DocumentSnapshot snapshot) {
        return snapshot.exists() ? new AssociationMatchState(snapshot) : null;
    }

    private int playerNumber(AssociationMatchState state, StepByStepPlayerSession player) {
        return state == null ? 0 : state.playerNumber(player.getId());
    }

    private boolean canAct(AssociationMatchState state, int player) {
        return state != null && state.isAssociationGame()
                && player != 0 && player == state.getActivePlayer()
                && state.getSecondsLeft() > 0;
    }

    private boolean canGuess(AssociationMatchState state, AssociationPuzzle puzzle, int player) {
        return canAct(state, player)
                && !state.isOpenPhase()
                && puzzle != null
                && puzzle.getId().equals(state.getPuzzleId());
    }

    private void switchTurn(Map<String, Object> updates, AssociationMatchState state,
                            int currentPlayer, String message) {
        int nextPlayer = currentPlayer == 1 ? 2 : 1;
        updates.put("associationActivePlayer", nextPlayer);
        updates.put("associationOpenPhase", hasOpenableCell(state));
        updates.put("associationCanContinueAfterCorrect", false);
        updates.put("statusMessage", message + " Na potezu je igrac " + nextPlayer + ".");
    }

    private boolean hasOpenableCell(AssociationMatchState state) {
        return hasOpenableCell(state.getRevealed(), state.getSolvedColumns());
    }

    private boolean hasOpenableCell(List<Boolean> revealed, List<Boolean> solvedColumns) {
        for (int column = 0; column < AssociationPuzzle.COLUMN_COUNT; column++) {
            if (Boolean.TRUE.equals(solvedColumns.get(column))) {
                continue;
            }
            for (int row = 0; row < AssociationPuzzle.CLUES_PER_COLUMN; row++) {
                int index = column * AssociationPuzzle.CLUES_PER_COLUMN + row;
                if (!Boolean.TRUE.equals(revealed.get(index))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addScore(AssociationMatchState state, Map<String, Object> updates,
                          int player, int points) {
        updates.put(player == 1 ? "player1Score" : "player2Score",
                (player == 1 ? state.getPlayer1Score() : state.getPlayer2Score()) + points);
    }

    private void addRoundScore(AssociationMatchState state, Map<String, Object> updates,
                               int player, int points) {
        updates.put(player == 1
                        ? "associationRoundPlayer1Score" : "associationRoundPlayer2Score",
                (player == 1
                        ? state.getRoundPlayer1Score() : state.getRoundPlayer2Score()) + points);
    }

    private void advanceRound(Map<String, Object> updates, AssociationMatchState state,
                              String message) {
        if (state.getRound() < AssociationGameService.ROUND_COUNT) {
            updates.putAll(roundState(2));
            updates.put("statusMessage", message + " Pocinje druga runda, igrac 2 otvara.");
            return;
        }
        updates.putAll(skockoState());
        updates.put("statusMessage", message + " Asocijacije su zavrsene. Pokrece se Skocko.");
    }

    private Map<String, Object> roundState(int round) {
        Map<String, Object> state = new HashMap<>();
        state.put("currentGame", AssociationMatchState.GAME);
        state.put("phase", AssociationMatchState.PHASE_ROUND);
        state.put("associationRound", round);
        state.put("associationActivePlayer", round);
        state.put("associationOpenPhase", true);
        state.put("associationCanContinueAfterCorrect", false);
        state.put("associationSecondsLeft", AssociationGameService.ROUND_SECONDS);
        state.put("associationPuzzleId", "association-" + round);
        state.put("associationRevealed", falseList(
                AssociationPuzzle.COLUMN_COUNT * AssociationPuzzle.CLUES_PER_COLUMN));
        state.put("associationSolvedColumns", falseList(AssociationPuzzle.COLUMN_COUNT));
        state.put("associationFinalSolved", false);
        state.put("associationRoundPlayer1Score", 0);
        state.put("associationRoundPlayer2Score", 0);
        state.put("finished", false);
        return state;
    }

    private Map<String, Object> skockoState() {
        Map<String, Object> state = new HashMap<>();
        state.put("currentGame", "skocko");
        state.put("phase", "round");
        state.put("phaseToken", System.currentTimeMillis() + "-" + random.nextLong());
        state.put("round", 1L);
        state.put("activePlayer", 1L);
        state.put("phaseStartedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        state.put("phaseDeadlineAt",
                System.currentTimeMillis() + SkockoMatchRepository.ROUND_DURATION_MS);
        state.put("secret", randomSkockoSecret());
        state.put("attempts", new ArrayList<>());
        state.put("finished", false);
        return state;
    }

    private List<Integer> randomSkockoSecret() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < SkockoGameService.CODE_LENGTH; i++) {
            result.add(random.nextInt(SkockoGameService.SYMBOL_COUNT));
        }
        return result;
    }

    private static List<Boolean> falseList(int size) {
        List<Boolean> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(false);
        }
        return result;
    }

    private static List<Boolean> mutableBooleans(List<Boolean> values) {
        return new ArrayList<>(values);
    }
}
