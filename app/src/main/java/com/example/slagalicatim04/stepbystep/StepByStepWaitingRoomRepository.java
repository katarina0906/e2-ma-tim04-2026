package com.example.slagalicatim04.stepbystep;

import com.example.slagalicatim04.auth.TokenService;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class StepByStepWaitingRoomRepository {
    public interface RoomListener {
        void onStateChanged(StepByStepMatchState state);

        void onError(Exception error);
    }

    public interface ErrorCallback {
        void onError(Exception error);
    }

    private static final String COLLECTION = "stepByStepMatches";

    private final DocumentReference roomRef;
    private final TokenService tokenService;

    public StepByStepWaitingRoomRepository(String roomId) {
        roomRef = FirebaseFirestore.getInstance().collection(COLLECTION).document(roomId);
        tokenService = new TokenService(roomRef.getFirestore());
    }

    public void joinRoom(StepByStepPlayerSession player, ErrorCallback onError) {
        roomRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(roomRef);
            if (!snapshot.exists()) {
                transaction.set(roomRef, newWaitingState(player));
                return null;
            }

            StepByStepMatchState state = new StepByStepMatchState(snapshot);
            if (needsFreshRoom(state, player.getId())) {
                transaction.set(roomRef, newWaitingState(player));
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (StepByStepMatchState.isEmpty(state.getPlayer1Id())) {
                updates.put("player1Id", player.getId());
                updates.put("player1Name", player.getName());
                updates.put("player1Ready", false);
            } else if (!state.isParticipant(player.getId())
                    && StepByStepMatchState.isEmpty(state.getPlayer2Id())) {
                updates.put("player2Id", player.getId());
                updates.put("player2Name", player.getName());
                updates.put("player2Ready", false);
                updates.put("statusMessage", "Oba igraca su u sobi. Potvrdite spremnost.");
            }

            if (!updates.isEmpty()) {
                transaction.set(roomRef, updates, SetOptions.merge());
            }
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public ListenerRegistration listen(RoomListener listener) {
        return roomRef.addSnapshotListener((snapshot, error) -> {
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

    public void confirmReady(StepByStepPlayerSession player, ErrorCallback onError) {
        roomRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(roomRef);
            if (!snapshot.exists()) {
                return null;
            }
            StepByStepMatchState state = new StepByStepMatchState(snapshot);
            int myPlayer = state.playerNumber(player.getId());
            if (myPlayer == 0 || !state.hasSecondPlayer()) {
                return null;
            }

            boolean otherReady = myPlayer == 1 ? state.isPlayer2Ready() : state.isPlayer1Ready();
            Map<String, Object> updates = new HashMap<>();
            updates.put(myPlayer == 1 ? "player1Ready" : "player2Ready", true);
            updates.put("statusMessage", "Igrac " + myPlayer + " je spreman.");

            if (otherReady) {
                DocumentSnapshot player1Snapshot = transaction.get(
                        roomRef.getFirestore().collection("users").document(state.getPlayer1Id()));
                DocumentSnapshot player2Snapshot = transaction.get(
                        roomRef.getFirestore().collection("users").document(state.getPlayer2Id()));
                tokenService.consumeSingleToken(transaction, state.getPlayer1Id(), player1Snapshot);
                tokenService.consumeSingleToken(transaction, state.getPlayer2Id(), player2Snapshot);
                updates.put("phase", "koZnaZnaPlaying");
                updates.put("currentGame", "koZnaZna");
                updates.put("finished", false);
                updates.put("player1Score", 0L);
                updates.put("player2Score", 0L);
                updates.put("kzzCurrentQuestion", 0L);
                updates.put("kzzAnswers", new HashMap<>());
                updates.put("statusMessage", "Oba igraca su spremna. Pokrece se Ko zna zna.");
                transaction.set(roomRef.getFirestore().collection("users")
                                .document(state.getPlayer1Id()),
                        busyState(roomRef.getId(), state.getPlayer2Id()),
                        SetOptions.merge());
                transaction.set(roomRef.getFirestore().collection("users")
                                .document(state.getPlayer2Id()),
                        busyState(roomRef.getId(), state.getPlayer1Id()),
                        SetOptions.merge());
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roomRef, updates, SetOptions.merge());
            return null;
        }).addOnFailureListener(onError::onError);
    }

    public void resetRoom(StepByStepPlayerSession player) {
        roomRef.set(newWaitingState(player));
    }

    private boolean needsFreshRoom(StepByStepMatchState state, String playerId) {
        if (state.isParticipant(playerId)) {
            return state.getRound() < 1 || state.getRound() > 2;
        }
        boolean gameAlreadyStarted = StepByStepMatchState.PHASE_PLAYING.equals(state.getPhase())
                || "koZnaZnaPlaying".equals(state.getPhase())
                || "spojnicePlaying".equals(state.getPhase())
                || StepByStepMatchState.PHASE_STEAL.equals(state.getPhase())
                || StepByStepMatchState.PHASE_ROUND1.equals(state.getPhase())
                || StepByStepMatchState.PHASE_STEAL1.equals(state.getPhase())
                || StepByStepMatchState.PHASE_ROUND2.equals(state.getPhase())
                || StepByStepMatchState.PHASE_STEAL2.equals(state.getPhase())
                || StepByStepMatchState.PHASE_FINISHED.equals(state.getPhase())
                || state.isFinished();
        boolean fullForeignRoom = !state.isParticipant(playerId)
                && !StepByStepMatchState.isEmpty(state.getPlayer1Id())
                && !StepByStepMatchState.isEmpty(state.getPlayer2Id());
        return gameAlreadyStarted || fullForeignRoom || state.getRound() < 1 || state.getRound() > 2;
    }

    private Map<String, Object> newWaitingState(StepByStepPlayerSession player) {
        Map<String, Object> state = new HashMap<>();
        state.put("player1Id", player.getId());
        state.put("player1Name", player.getName());
        state.put("player2Id", "");
        state.put("player2Name", "");
        state.put("player1Score", 0L);
        state.put("player2Score", 0L);
        state.put("player1Ready", false);
        state.put("player2Ready", false);
        state.put("round", 1L);
        state.put("phase", StepByStepMatchState.PHASE_WAITING);
        state.put("currentGame", "waiting");
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
        state.put("statusMessage", "Ceka se igrac 2.");
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private Map<String, Object> busyState(String roomId, String opponentId) {
        Map<String, Object> state = new HashMap<>();
        state.put("active", true);
        state.put("lastActiveAt", System.currentTimeMillis());
        state.put("inGame", true);
        state.put("currentRoomId", roomId);
        state.put("currentMatchId", roomId);
        state.put("currentOpponentId", opponentId);
        return state;
    }
}
