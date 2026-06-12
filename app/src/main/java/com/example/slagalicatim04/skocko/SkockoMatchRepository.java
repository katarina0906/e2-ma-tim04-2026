package com.example.slagalicatim04.skocko;

import com.example.slagalicatim04.services.SkockoGameService;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SkockoMatchRepository {
    public static final String DEFAULT_MATCH_ID = "skocko-test-room";
    public static final long ROUND_DURATION_MS = 30_000L;
    public static final long STEAL_DURATION_MS = 10_000L;

    public interface Listener {
        void onStateChanged(SkockoMatchState state);

        void onError(Exception error);
    }

    public interface ErrorCallback {
        void onError(Exception error);
    }

    private final DocumentReference matchRef;
    private final Random random = new Random();

    public SkockoMatchRepository(String roomId) {
        matchRef = FirebaseFirestore.getInstance()
                .collection("skockoMatches")
                .document(roomId);
    }

    public void joinRoom(StepByStepPlayerSession player, ErrorCallback onError) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                transaction.set(matchRef, newWaitingState(player));
                return null;
            }
            SkockoMatchState state = new SkockoMatchState(snapshot);
            if (needsFreshRoom(state, player.getId())) {
                transaction.set(matchRef, newWaitingState(player));
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (SkockoMatchState.isEmpty(state.getPlayer1Id())) {
                updates.put("player1Id", player.getId());
                updates.put("player1Name", player.getName());
                updates.put("player1Ready", false);
            } else if (!state.isParticipant(player.getId())
                    && SkockoMatchState.isEmpty(state.getPlayer2Id())) {
                updates.put("player2Id", player.getId());
                updates.put("player2Name", player.getName());
                updates.put("player2Ready", false);
                updates.put("statusMessage", "Oba igraca su u sobi. Potvrdite spremnost.");
            }
            if (!updates.isEmpty()) {
                transaction.set(matchRef, updates, SetOptions.merge());
            }
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public ListenerRegistration listen(Listener listener) {
        return matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                listener.onStateChanged(new SkockoMatchState(snapshot));
            }
        });
    }

    public void confirmReady(StepByStepPlayerSession player, ErrorCallback onError) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }
            SkockoMatchState state = new SkockoMatchState(snapshot);
            int myPlayer = state.playerNumber(player.getId());
            if (myPlayer == 0 || !state.hasSecondPlayer()) {
                return null;
            }

            boolean otherReady = myPlayer == 1 ? state.isPlayer2Ready() : state.isPlayer1Ready();
            Map<String, Object> updates = new HashMap<>();
            updates.put(myPlayer == 1 ? "player1Ready" : "player2Ready", true);
            updates.put("statusMessage", "Igrac " + myPlayer + " je spreman.");
            if (otherReady) {
                applyRoundStart(updates, 1);
                updates.put("player1Score", 0L);
                updates.put("player2Score", 0L);
                updates.put("finished", false);
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public void submitGuess(StepByStepPlayerSession player, int[] guess, ErrorCallback onError) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }
            SkockoMatchState state = new SkockoMatchState(snapshot);
            int myPlayer = state.playerNumber(player.getId());
            if (myPlayer == 0 || myPlayer != state.getActivePlayer() || state.isFinished()) {
                return null;
            }
            if (guess == null || guess.length != SkockoGameService.CODE_LENGTH) {
                return null;
            }

            long duration = SkockoMatchState.PHASE_STEAL.equals(state.getPhase())
                    ? STEAL_DURATION_MS : ROUND_DURATION_MS;
            if (state.getPhaseStartedAt() > 0 && state.secondsLeft(duration) <= 0) {
                Map<String, Object> timeoutUpdates = new HashMap<>();
                if (SkockoMatchState.PHASE_ROUND.equals(state.getPhase())) {
                    applyStealStart(timeoutUpdates, state.roundStarter());
                } else if (SkockoMatchState.PHASE_STEAL.equals(state.getPhase())) {
                    finishOrStartNextRound(
                            timeoutUpdates, state, "Ukradeni pokusaj je istekao.");
                }
                timeoutUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.set(matchRef, timeoutUpdates, SetOptions.merge());
                return null;
            }

            int[] secret = toArray(state.getSecret());
            SkockoGameService.Feedback moveFeedback =
                    SkockoGameService.calculateFeedback(secret, guess);
            Map<String, Object> updates = new HashMap<>();

            if (SkockoMatchState.PHASE_STEAL.equals(state.getPhase())) {
                boolean solved = moveFeedback.getExact() == SkockoGameService.CODE_LENGTH;
                if (solved) {
                    addScore(state, updates, myPlayer, 10);
                }
                finishOrStartNextRound(updates, state, solved
                        ? "Igrac " + myPlayer + " je ukrao rundu za 10 bodova."
                        : "Ukradeni pokusaj nije uspeo.");
            } else if (SkockoMatchState.PHASE_ROUND.equals(state.getPhase())
                    && state.getGuesses().size() < SkockoGameService.MAX_ATTEMPTS) {
                List<Map<String, Object>> attempts = createAttempts(state);
                Map<String, Object> attemptData = new HashMap<>();
                attemptData.put("symbols", toList(guess));
                attemptData.put("exact", (long) moveFeedback.getExact());
                attemptData.put("partial", (long) moveFeedback.getPartial());
                attempts.add(attemptData);
                updates.put("attempts", attempts);

                int attempt = attempts.size();
                if (moveFeedback.getExact() == SkockoGameService.CODE_LENGTH) {
                    int points = SkockoGameService.pointsForAttempt(attempt);
                    addScore(state, updates, myPlayer, points);
                    finishOrStartNextRound(updates, state, "Igrac " + myPlayer
                            + " je pogodio iz " + attempt + ". pokusaja za " + points + " bodova.");
                } else if (attempt >= SkockoGameService.MAX_ATTEMPTS) {
                    applyStealStart(updates, state.roundStarter());
                } else {
                    updates.put("statusMessage", "Pokusaj " + attempt + " nije tacan.");
                }
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public void expireIfNeeded(StepByStepPlayerSession player, SkockoMatchState state) {
        int myPlayer = state.playerNumber(player.getId());
        if (myPlayer == 0 || myPlayer != state.getActivePlayer() || state.getPhaseStartedAt() == 0) {
            return;
        }
        long duration = SkockoMatchState.PHASE_STEAL.equals(state.getPhase())
                ? STEAL_DURATION_MS : ROUND_DURATION_MS;
        if (state.secondsLeft(duration) > 0) {
            return;
        }
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot freshSnapshot = transaction.get(matchRef);
            if (!freshSnapshot.exists()) {
                return null;
            }
            SkockoMatchState fresh = new SkockoMatchState(freshSnapshot);
            if (fresh.getActivePlayer() != myPlayer
                    || !fresh.getPhase().equals(state.getPhase())
                    || fresh.getPhaseStartedAt() != state.getPhaseStartedAt()) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            if (SkockoMatchState.PHASE_ROUND.equals(fresh.getPhase())) {
                applyStealStart(updates, fresh.roundStarter());
            } else if (SkockoMatchState.PHASE_STEAL.equals(fresh.getPhase())) {
                finishOrStartNextRound(updates, fresh, "Ukradeni pokusaj je istekao.");
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void resetRoom(StepByStepPlayerSession player) {
        matchRef.set(newWaitingState(player));
    }

    private void finishOrStartNextRound(Map<String, Object> updates, SkockoMatchState state,
                                        String message) {
        if (state.getRound() >= 2) {
            updates.put("phase", SkockoMatchState.PHASE_FINISHED);
            updates.put("finished", true);
            updates.put("activePlayer", 0L);
            updates.put("phaseStartedAt", 0L);
            updates.put("statusMessage", message + " Igra je zavrsena.");
        } else {
            applyRoundStart(updates, 2);
            updates.put("statusMessage", message + " Pocinje runda 2, igra igrac 2.");
        }
    }

    private void applyStealStart(Map<String, Object> updates, int roundStarter) {
        int stealer = roundStarter == 1 ? 2 : 1;
        updates.put("phase", SkockoMatchState.PHASE_STEAL);
        updates.put("activePlayer", (long) stealer);
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        updates.put("statusMessage", "Igrac " + stealer
                + " ima jedan ukradeni pokusaj i 10 sekundi.");
    }

    private void applyRoundStart(Map<String, Object> updates, int round) {
        updates.put("round", (long) round);
        updates.put("phase", SkockoMatchState.PHASE_ROUND);
        updates.put("activePlayer", (long) round);
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        updates.put("secret", randomSecret());
        updates.put("attempts", new ArrayList<>());
        updates.put("statusMessage", "Runda " + round + " je na igracu " + round + ".");
    }

    private Map<String, Object> newWaitingState(StepByStepPlayerSession player) {
        Map<String, Object> state = new HashMap<>();
        state.put("player1Id", player.getId());
        state.put("player1Name", player.getName());
        state.put("player2Id", "");
        state.put("player2Name", "");
        state.put("player1Ready", false);
        state.put("player2Ready", false);
        state.put("player1Score", 0L);
        state.put("player2Score", 0L);
        state.put("round", 1L);
        state.put("phase", SkockoMatchState.PHASE_WAITING);
        state.put("activePlayer", 1L);
        state.put("phaseStartedAt", 0L);
        state.put("secret", new ArrayList<>());
        state.put("attempts", new ArrayList<>());
        state.put("finished", false);
        state.put("statusMessage", "Ceka se igrac 2.");
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private boolean needsFreshRoom(SkockoMatchState state, String playerId) {
        boolean fullForeignRoom = !state.isParticipant(playerId)
                && !SkockoMatchState.isEmpty(state.getPlayer1Id())
                && !SkockoMatchState.isEmpty(state.getPlayer2Id());
        return state.isFinished() || fullForeignRoom || state.getRound() < 1 || state.getRound() > 2;
    }

    private void addScore(SkockoMatchState state, Map<String, Object> updates,
                          int player, int points) {
        String key = player == 1 ? "player1Score" : "player2Score";
        long score = player == 1 ? state.getPlayer1Score() : state.getPlayer2Score();
        updates.put(key, score + points);
    }

    private List<Integer> randomSecret() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < SkockoGameService.CODE_LENGTH; i++) {
            result.add(random.nextInt(SkockoGameService.SYMBOL_COUNT));
        }
        return result;
    }

    private static int[] toArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private static List<Map<String, Object>> createAttempts(SkockoMatchState state) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < state.getGuesses().size(); i++) {
            Map<String, Object> attempt = new HashMap<>();
            attempt.put("symbols", new ArrayList<>(state.getGuesses().get(i)));
            List<Integer> feedback = i < state.getFeedback().size()
                    ? state.getFeedback().get(i) : new ArrayList<>();
            attempt.put("exact", (long) (feedback.size() > 0 ? feedback.get(0) : 0));
            attempt.put("partial", (long) (feedback.size() > 1 ? feedback.get(1) : 0));
            result.add(attempt);
        }
        return result;
    }
}
