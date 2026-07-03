package com.example.slagalicatim04.friends;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class GameSessionRepository {
    private static final String MATCH_COLLECTION = "stepByStepMatches";

    private final FirebaseFirestore firestore;

    public GameSessionRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void abandonRoom(String roomId) {
        if (roomId == null || roomId.trim().isEmpty() || !roomId.startsWith("friend_")) {
            return;
        }
        String currentUserId = currentUserId();
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot room = transaction.get(roomRef);
            if (!room.exists() || Boolean.TRUE.equals(room.getBoolean("finished"))) {
                return null;
            }
            String phase = room.getString("phase");
            if ("matchFinished".equals(phase) || "abandoned".equals(phase)) {
                return null;
            }
            if (!isWaitingPhase(phase) && currentUserParticipates(room, currentUserId)) {
                applyForfeit(transaction, roomRef, room, currentUserId);
                return null;
            }

            Map<String, Object> roomUpdates = new HashMap<>();
            roomUpdates.put("phase", "abandoned");
            roomUpdates.put("currentGame", "abandoned");
            roomUpdates.put("inviteStatus", "abandoned");
            roomUpdates.put("finished", true);
            roomUpdates.put("statusMessage", "Partija je napustena.");
            roomUpdates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roomRef, roomUpdates, SetOptions.merge());

            clearPlayer(transaction, room.getString("player1Id"), roomId);
            clearPlayer(transaction, room.getString("player2Id"), roomId);
            return null;
        });
    }

    private void applyForfeit(Transaction transaction, DocumentReference roomRef,
                              DocumentSnapshot room, String playerId) {
        String player1Id = stringValue(room.getString("player1Id"));
        String player2Id = stringValue(room.getString("player2Id"));
        if (!playerId.equals(player1Id) && !playerId.equals(player2Id)) {
            return;
        }
        String forfeitedPlayerId = stringValue(room.getString("forfeitedPlayerId"));
        if (!forfeitedPlayerId.isEmpty() && !forfeitedPlayerId.equals(playerId)) {
            finishRoomForBothPlayers(transaction, roomRef, player1Id, player2Id);
            return;
        }
        String winnerId = playerId.equals(player1Id) ? player2Id : player1Id;
        Map<String, Object> roomUpdates = new HashMap<>();
        roomUpdates.put("forfeitedPlayerId", playerId);
        roomUpdates.put("winnerByForfeitId", winnerId);
        roomUpdates.put("statusMessage", "Igrac je napustio partiju. Protivnik nastavlja.");
        applyContinuationState(room, roomUpdates, playerId, player1Id, player2Id);
        if ("matchResult".equals(stringValue(room.getString("currentGame")))) {
            roomUpdates.put("matchRewardsApplied", true);
        }
        transaction.set(roomRef, roomUpdates, SetOptions.merge());
        clearPlayer(transaction, playerId, roomRef.getId());
    }

    private void finishRoomForBothPlayers(Transaction transaction, DocumentReference roomRef,
                                          String player1Id, String player2Id) {
        Map<String, Object> roomUpdates = new HashMap<>();
        roomUpdates.put("phase", "matchFinished");
        roomUpdates.put("currentGame", "matchResult");
        roomUpdates.put("inviteStatus", "finished");
        roomUpdates.put("finished", true);
        roomUpdates.put("statusMessage", "Partija je zavrsena jer su oba igraca napustila igru.");
        roomUpdates.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(roomRef, roomUpdates, SetOptions.merge());
        clearPlayer(transaction, player1Id, roomRef.getId());
        clearPlayer(transaction, player2Id, roomRef.getId());
    }

    private void clearPlayer(Transaction transaction, String userId, String roomId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        DocumentReference userRef = firestore.collection("users").document(userId);
        Map<String, Object> state = new HashMap<>();
        state.put("inGame", false);
        state.put("busy", false);
        state.put("isPlaying", false);
        state.put("currentRoomId", FieldValue.delete());
        state.put("currentMatchId", FieldValue.delete());
        state.put("currentOpponentId", FieldValue.delete());
        state.put("activeRoomId", FieldValue.delete());
        state.put("activeMatchId", FieldValue.delete());
        state.put("lastActiveAt", System.currentTimeMillis());
        transaction.set(userRef, state, SetOptions.merge());
    }

    private void applyContinuationState(DocumentSnapshot snapshot, Map<String, Object> updates,
                                        String forfeitedPlayerId,
                                        String player1Id, String player2Id) {
        String remainingPlayerId = forfeitedPlayerId.equals(player1Id) ? player2Id : player1Id;
        int remainingPlayerNumber = forfeitedPlayerId.equals(player1Id) ? 2 : 1;
        int forfeitedPlayerNumber = forfeitedPlayerId.equals(player1Id) ? 1 : 2;
        String currentGame = stringValue(snapshot.getString("currentGame"));
        String phase = stringValue(snapshot.getString("phase"));

        if ("koZnaZna".equals(currentGame) && "koZnaZnaPlaying".equals(phase)) {
            return;
        }
        if ("spojnice".equals(currentGame) && "spojnicePlaying".equals(phase)) {
            String currentPlayer = stringValue(snapshot.getString("spCurrentPlayer"));
            if (currentPlayer.equals(forfeitedPlayerId)) {
                updates.put("spCurrentPlayer", remainingPlayerId);
            }
            return;
        }
        if ("associations".equals(currentGame) && "associationRound".equals(phase)) {
            Long activePlayer = snapshot.getLong("associationActivePlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("associationActivePlayer", (long) remainingPlayerNumber);
            }
            return;
        }
        if ("skocko".equals(currentGame)) {
            Long activePlayer = snapshot.getLong("activePlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("activePlayer", (long) remainingPlayerNumber);
            }
            return;
        }
        if ("stepByStep".equals(currentGame)) {
            Long activePlayer = snapshot.getLong("activePlayer");
            Long stealPlayer = snapshot.getLong("stealPlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("activePlayer", (long) remainingPlayerNumber);
            }
            if (stealPlayer != null && stealPlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("stealPlayer", (long) remainingPlayerNumber);
            }
            return;
        }
        if ("myNumber".equals(currentGame)) {
            Long activePlayer = snapshot.getLong("myNumberActivePlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("myNumberActivePlayer", (long) remainingPlayerNumber);
            }
        }
    }

    private boolean isWaitingPhase(String phase) {
        return phase == null || phase.trim().isEmpty() || "waiting".equals(phase);
    }

    private boolean currentUserParticipates(DocumentSnapshot room, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return userId.equals(stringValue(room.getString("player1Id")))
                || userId.equals(stringValue(room.getString("player2Id")));
    }

    private String currentUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return "";
        }
        return stringValue(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    private String stringValue(String value) {
        return value == null ? "" : value;
    }
}
