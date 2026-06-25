package com.example.slagalicatim04.stepbystep;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class StepByStepMatchRepository {
    public interface MatchListener {
        void onStateChanged(StepByStepMatchState state);

        void onError(Exception error);
    }

    private static final String COLLECTION = "stepByStepMatches";
    public static final String DEFAULT_MATCH_ID = "step-by-step-test-room";

    private final FirebaseFirestore firestore;
    private final DocumentReference matchRef;
    private final StepByStepGameService gameService;

    public StepByStepMatchRepository(StepByStepGameService gameService) {
        this(gameService, DEFAULT_MATCH_ID);
    }

    public StepByStepMatchRepository(StepByStepGameService gameService, String matchId) {
        this.firestore = FirebaseFirestore.getInstance();
        this.matchRef = firestore.collection(COLLECTION).document(matchId);
        this.gameService = gameService;
    }

    public void joinMatch(StepByStepPlayerSession player, Runnable onSuccess, ErrorCallback onError) {
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                transaction.set(matchRef, newMatchState(player));
                return null;
            }

            StepByStepMatchState state = new StepByStepMatchState(snapshot);
            if (needsFreshMatch(state, player.getId())) {
                transaction.set(matchRef, newMatchState(player));
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (StepByStepMatchState.isEmpty(state.getPlayer1Id())) {
                updates.put("player1Id", player.getId());
                updates.put("player1Name", player.getName());
            } else if (!state.isParticipant(player.getId())
                    && StepByStepMatchState.isEmpty(state.getPlayer2Id())) {
                updates.put("player2Id", player.getId());
                updates.put("player2Name", player.getName());
                updates.put("phase", StepByStepMatchState.PHASE_ROUND1);
                updates.put("roundStartedAt", FieldValue.serverTimestamp());
                updates.put("statusMessage", "Igrac 2 se pridruzio. Runda 1 je na igracu 1.");
            }

            if (!updates.isEmpty()) {
                transaction.set(matchRef, updates, SetOptions.merge());
            }
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::onError);
    }

    public ListenerRegistration listen(MatchListener listener) {
        return matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            listener.onStateChanged(new StepByStepMatchState(snapshot));
        });
    }

    public void ensureParticipant(StepByStepPlayerSession player, Runnable onSuccess, ErrorCallback onError) {
        matchRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && new StepByStepMatchState(snapshot).isParticipant(player.getId())) {
                        onSuccess.run();
                    } else {
                        joinMatch(player, onSuccess, onError);
                    }
                })
                .addOnFailureListener(onError::onError);
    }

    public void submitAnswer(StepByStepPlayerSession player, StepByStepRound roundData,
                             String answer, ErrorCallback onError) {
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }
            StepByStepMatchState state = new StepByStepMatchState(snapshot);
            int myPlayer = state.playerNumber(player.getId());
            if (!state.isStepByStepGame() || !gameService.isMyTurn(state, myPlayer)) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (gameService.matches(answer, roundData.getAnswer())) {
                applyCorrectAnswer(state, updates);
            } else {
                updates.put("statusMessage", "Odgovor nije tacan. Pokusaj ponovo dok imas vremena.");
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public void giveUpRound(StepByStepPlayerSession player, ErrorCallback onError) {
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }
            StepByStepMatchState state = new StepByStepMatchState(snapshot);
            int myPlayer = state.playerNumber(player.getId());
            if (!state.isStepByStepGame()
                    || !gameService.isRoundPhase(state.effectivePhase())
                    || state.getActivePlayer() != myPlayer) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            startStealUpdates(updates, state.getActivePlayer());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public void expireIfNeeded(StepByStepPlayerSession player, StepByStepMatchState state) {
        if (!state.isStepByStepGame()
                || !state.hasSecondPlayer()
                || gameService.waitingForServerTime(state)) {
            return;
        }
        if (!isClockOwner(player, state)) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        if (gameService.shouldStartSteal(state)) {
            startStealUpdates(updates, state.getActivePlayer());
        } else if (gameService.shouldFinishSteal(state)) {
            updates.put("statusMessage", "Ukradeni pokusaj je istekao bez bodova.");
            putRoundResult(updates, state.getRound(), "Runda je zavrsena bez osvojenih bodova.");
            applyNextRound(updates, state.getRound());
            updates.put("updatedAt", FieldValue.serverTimestamp());
        }
        if (!updates.isEmpty()) {
            matchRef.set(updates, SetOptions.merge());
        }
    }

    public void publishClockDisplay(StepByStepPlayerSession player, StepByStepMatchState state,
                                    int visibleStepCount, int secondsLeft) {
        if (!state.isStepByStepGame() || !isClockOwner(player, state) || state.isFinished()) {
            return;
        }
        if (state.getVisibleStepCount() == visibleStepCount && state.getSecondsLeft() == secondsLeft) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("visibleStepCount", visibleStepCount);
        updates.put("secondsLeft", secondsLeft);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        matchRef.set(updates, SetOptions.merge());
    }

    public void tickClock(StepByStepPlayerSession player, StepByStepMatchState state) {
        if (!state.isStepByStepGame() || !isClockOwner(player, state) || state.isFinished()) {
            return;
        }
        int nextSecondsLeft = gameService.nextSecondsLeft(state);
        int nextVisibleStepCount = gameService.nextVisibleStepCount(state, nextSecondsLeft);
        publishClockDisplay(player, state, nextVisibleStepCount, nextSecondsLeft);
    }

    public void resetMatch(StepByStepPlayerSession player) {
        matchRef.set(newMatchState(player));
    }

    public void resolveForfeitTurn(StepByStepMatchState state) {
        String forfeitedPlayerId = state.getForfeitedPlayerId();
        if (StepByStepMatchState.isEmpty(forfeitedPlayerId) || !state.isStepByStepGame()) {
            return;
        }
        int forfeitedPlayer = state.playerNumber(forfeitedPlayerId);
        if (forfeitedPlayer == 0) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        String phase = state.effectivePhase();
        if (gameService.isRoundPhase(phase) && state.getActivePlayer() == forfeitedPlayer) {
            startStealUpdates(updates, state.getActivePlayer());
        } else if (gameService.isStealPhase(phase) && state.getStealPlayer() == forfeitedPlayer) {
            updates.put("statusMessage", "Igrac je napustio partiju. Runda se zavrsava bez bodova.");
            putRoundResult(updates, state.getRound(), "Protivnik je napustio partiju tokom runde.");
            applyNextRound(updates, state.getRound());
            updates.put("updatedAt", FieldValue.serverTimestamp());
        }
        if (!updates.isEmpty()) {
            matchRef.set(updates, SetOptions.merge());
        }
    }

    public interface ErrorCallback {
        void onError(Exception error);
    }

    private boolean needsFreshMatch(StepByStepMatchState state, String playerId) {
        boolean invalidRound = state.getRound() < 1 || state.getRound() > 2;
        boolean finished = state.isFinished() || StepByStepMatchState.PHASE_FINISHED.equals(state.getPhase());
        boolean fullForeignMatch = !state.isParticipant(playerId)
                && !StepByStepMatchState.isEmpty(state.getPlayer1Id())
                && !StepByStepMatchState.isEmpty(state.getPlayer2Id());
        return finished || invalidRound || fullForeignMatch;
    }

    private Map<String, Object> newMatchState(StepByStepPlayerSession player) {
        Map<String, Object> state = new HashMap<>();
        state.put("player1Id", player.getId());
        state.put("player1Name", player.getName());
        state.put("player2Id", "");
        state.put("player2Name", "");
        state.put("player1Score", 0L);
        state.put("player2Score", 0L);
        state.put("player1Ready", false);
        state.put("player2Ready", false);
        state.put("currentGame", "stepByStep");
        state.put("round", 1L);
        state.put("phase", StepByStepMatchState.PHASE_WAITING);
        state.put("activePlayer", 1L);
        state.put("stealPlayer", 0L);
        state.put("roundStartedAt", 0L);
        state.put("stealStartedAt", 0L);
        state.put("visibleStepCount", 0L);
        state.put("secondsLeft", 0L);
        state.put("round1Result", "");
        state.put("round2Result", "");
        state.put("finalResult", "");
        state.put("finished", false);
        state.put("statusMessage", "Ceka se igrac 2. Runda 1 je na igracu 1.");
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private void applyCorrectAnswer(StepByStepMatchState state, Map<String, Object> updates) {
        if (gameService.isStealPhase(state.effectivePhase())) {
            addScore(state, updates, state.getStealPlayer(), 5);
            putRoundResult(updates, state.getRound(), "Igrac " + state.getStealPlayer()
                    + " je ukrao rundu za 5 bodova.");
            updates.put("statusMessage", "Igrac " + state.getStealPlayer()
                    + " je ukrao rundu i osvojio 5 bodova.");
        } else {
            int openedSteps = gameService.openedSteps(state);
            int points = gameService.pointsForStep(openedSteps);
            addScore(state, updates, state.getActivePlayer(), points);
            putRoundResult(updates, state.getRound(), "Igrac " + state.getActivePlayer()
                    + " je pogodio u " + openedSteps + ". koraku za " + points + " bodova.");
            updates.put("statusMessage", "Igrac " + state.getActivePlayer() + " je pogodio u "
                    + openedSteps + ". koraku i osvojio " + points + " bodova.");
        }
        applyNextRound(updates, state.getRound());
    }

    private void startStealUpdates(Map<String, Object> updates, int activePlayer) {
        int opponent = activePlayer == 1 ? 2 : 1;
        updates.put("phase", activePlayer == 1
                ? StepByStepMatchState.PHASE_STEAL1
                : StepByStepMatchState.PHASE_STEAL2);
        updates.put("stealPlayer", opponent);
        updates.put("stealStartedAt", FieldValue.serverTimestamp());
        updates.put("visibleStepCount", 7L);
        updates.put("secondsLeft", StepByStepGameService.STEAL_DURATION_MS / 1000);
        updates.put("statusMessage", "Igrac " + activePlayer + " nije pogodio. Igrac "
                + opponent + " ima 10 sekundi za 5 bodova.");
        updates.put("updatedAt", FieldValue.serverTimestamp());
    }

    private void applyNextRound(Map<String, Object> updates, int currentRound) {
        if (currentRound >= 2) {
            updates.put("finished", false);
            updates.put("stepByStepFinished", true);
            updates.put("currentGame", "myNumber");
            updates.put("phase", "myNumberSetup");
            updates.put("activePlayer", 0);
            updates.put("stealPlayer", 0);
            updates.put("visibleStepCount", 7L);
            updates.put("secondsLeft", 0L);
            updates.put("finalResult", "Korak po korak je zavrsen. Pokrece se Moj broj.");
            updates.put("statusMessage", "Korak po korak je zavrsen. Pokrece se Moj broj.");
            return;
        }
        updates.put("round", currentRound + 1);
        updates.put("phase", StepByStepMatchState.PHASE_ROUND2);
        updates.put("activePlayer", 2);
        updates.put("stealPlayer", 0);
        updates.put("roundStartedAt", FieldValue.serverTimestamp());
        updates.put("stealStartedAt", 0L);
        updates.put("visibleStepCount", 1L);
        updates.put("secondsLeft", StepByStepGameService.ROUND_DURATION_MS / 1000);
        updates.put("statusMessage", "Runda 2 je pocela. Na potezu je igrac 2.");
    }

    private void addScore(StepByStepMatchState state, Map<String, Object> updates,
                          int player, int points) {
        String key = player == 1 ? "player1Score" : "player2Score";
        long current = player == 1 ? state.getPlayer1Score() : state.getPlayer2Score();
        updates.put(key, current + points);
    }

    private void putRoundResult(Map<String, Object> updates, int round, String result) {
        updates.put(round == 1 ? "round1Result" : "round2Result", result);
    }

    private boolean isClockOwner(StepByStepPlayerSession player, StepByStepMatchState state) {
        int myPlayer = state.playerNumber(player.getId());
        String phase = state.effectivePhase();
        if (gameService.isRoundPhase(phase)) {
            return myPlayer == state.getActivePlayer();
        }
        if (gameService.isStealPhase(phase)) {
            return myPlayer == state.getStealPlayer();
        }
        return false;
    }
}
