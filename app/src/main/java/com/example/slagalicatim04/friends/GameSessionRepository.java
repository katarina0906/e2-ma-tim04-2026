package com.example.slagalicatim04.friends;

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
}
