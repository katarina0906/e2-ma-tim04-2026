package com.example.slagalicatim04.friends;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class GameInviteRepository {
    private static final String MATCH_COLLECTION = "stepByStepMatches";
    private static final long ACCEPTED_WAITING_GRACE_MS = 30L * 1000L;
    private static final long STALE_ACTIVE_ROOM_MS = 45L * 1000L;

    private final FirebaseFirestore firestore;

    public GameInviteRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public String acceptInvite(String currentUserId, String roomId, String notificationId)
            throws ExecutionException, InterruptedException {
        if (isEmpty(currentUserId) || isEmpty(roomId)) {
            throw new IllegalArgumentException("Poziv nije pronadjen.");
        }
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);
        Tasks.await(firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot room = transaction.get(roomRef);
            if (!room.exists()) {
                throw new IllegalArgumentException("Poziv vise ne postoji.");
            }
            if (!currentUserId.equals(room.getString("player2Id"))) {
                throw new IllegalStateException("Ovaj poziv nije namenjen tvom nalogu.");
            }
            String status = room.getString("inviteStatus");
            if (!"pending".equals(status)) {
                throw new IllegalStateException("Poziv vise nije aktivan.");
            }
            Long expiresAt = room.getLong("inviteExpiresAt");
            if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                expireRoom(transaction, roomRef, room, notificationId);
                throw new IllegalStateException("Poziv je istekao.");
            }

            String player1Id = room.getString("player1Id");
            DocumentReference player1Ref = firestore.collection("users").document(player1Id);
            DocumentReference player2Ref = firestore.collection("users").document(currentUserId);
            DocumentSnapshot player1 = transaction.get(player1Ref);
            DocumentSnapshot player2 = transaction.get(player2Ref);
            if (isInGame(transaction, player1Ref, player1, roomId)
                    || isInGame(transaction, player2Ref, player2, roomId)) {
                throw new IllegalStateException("Jedan od igraca je vec u partiji.");
            }

            Map<String, Object> roomUpdates = new HashMap<>();
            roomUpdates.put("inviteStatus", "accepted");
            roomUpdates.put("phase", "waiting");
            roomUpdates.put("currentGame", "waiting");
            roomUpdates.put("statusMessage", "Poziv je prihvacen. Potvrdite spremnost.");
            roomUpdates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roomRef, roomUpdates, SetOptions.merge());
            updateNotification(transaction, currentUserId, notificationId, "accepted");
            return null;
        }));
        return roomId;
    }

    public void declineInvite(String currentUserId, String roomId, String notificationId)
            throws ExecutionException, InterruptedException {
        if (isEmpty(currentUserId) || isEmpty(roomId)) {
            throw new IllegalArgumentException("Poziv nije pronadjen.");
        }
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);
        Tasks.await(firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot room = transaction.get(roomRef);
            if (!room.exists()) {
                updateNotification(transaction, currentUserId, notificationId, "declined");
                return null;
            }
            if (!currentUserId.equals(room.getString("player2Id"))) {
                throw new IllegalStateException("Ovaj poziv nije namenjen tvom nalogu.");
            }
            if ("pending".equals(room.getString("inviteStatus"))) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("inviteStatus", "declined");
                updates.put("phase", "inviteDeclined");
                updates.put("statusMessage", "Poziv za partiju je odbijen.");
                updates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.set(roomRef, updates, SetOptions.merge());
            }
            updateNotification(transaction, currentUserId, notificationId, "declined");
            return null;
        }));
    }

    public void expireInviteIfNeeded(String currentUserId, String roomId, String notificationId)
            throws ExecutionException, InterruptedException {
        if (isEmpty(roomId)) {
            return;
        }
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);
        Tasks.await(firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot room = transaction.get(roomRef);
            if (!room.exists() || !"pending".equals(room.getString("inviteStatus"))) {
                return null;
            }
            Long expiresAt = room.getLong("inviteExpiresAt");
            if (expiresAt != null && System.currentTimeMillis() >= expiresAt) {
                expireRoom(transaction, roomRef, room, notificationId);
                String player2Id = isEmpty(currentUserId) ? room.getString("player2Id") : currentUserId;
                updateNotification(transaction, player2Id, notificationId, "expired");
            }
            return null;
        }));
    }

    private void expireRoom(Transaction transaction, DocumentReference roomRef,
                            DocumentSnapshot room, String notificationId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("inviteStatus", "expired");
        updates.put("phase", "inviteExpired");
        updates.put("statusMessage", "Poziv za partiju je istekao.");
        updates.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(roomRef, updates, SetOptions.merge());
    }

    private void updateNotification(Transaction transaction, String userId,
                                    String notificationId, String status) {
        if (isEmpty(userId) || isEmpty(notificationId)) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("inviteStatus", status);
        updates.put("read", true);
        updates.put("readAt", FieldValue.serverTimestamp());
        updates.put("actionedAt", FieldValue.serverTimestamp());
        transaction.set(firestore.collection("users").document(userId)
                        .collection("notifications").document(notificationId),
                updates,
                SetOptions.merge());
    }

    private boolean isInGame(Transaction transaction, DocumentReference userRef,
                             DocumentSnapshot user, String invitedRoomId)
            throws FirebaseFirestoreException {
        if (user == null || !user.exists()) {
            return false;
        }
        String roomId = activeRoomId(user);
        boolean hasBusyFlag = Boolean.TRUE.equals(user.getBoolean("inGame"))
                || Boolean.TRUE.equals(user.getBoolean("busy"))
                || Boolean.TRUE.equals(user.getBoolean("isPlaying"));
        if (isEmpty(roomId) || roomId.equals(invitedRoomId)) {
            if (isEmpty(roomId) && hasBusyFlag) {
                transaction.set(userRef, clearBusyState(), SetOptions.merge());
            }
            return false;
        }
        DocumentSnapshot room = transaction.get(firestore.collection(MATCH_COLLECTION).document(roomId));
        boolean activeRoom = isActiveRoom(room) && roomContainsActivePlayer(room, user.getId());
        if (!activeRoom) {
            transaction.set(userRef, clearBusyState(), SetOptions.merge());
        }
        return activeRoom;
    }

    private boolean roomContainsActivePlayer(DocumentSnapshot room, String userId) {
        if (room == null || !room.exists() || isEmpty(userId)) {
            return false;
        }
        String player1Id = room.getString("player1Id");
        String player2Id = room.getString("player2Id");
        if (!userId.equals(player1Id) && !userId.equals(player2Id)) {
            return false;
        }
        return !userId.equals(room.getString("forfeitedPlayerId"));
    }

    private boolean isActiveRoom(DocumentSnapshot room) {
        if (room == null || !room.exists()) {
            return false;
        }
        if (Boolean.TRUE.equals(room.getBoolean("finished"))) {
            return false;
        }
        String inviteStatus = room.getString("inviteStatus");
        if ("declined".equals(inviteStatus)
                || "expired".equals(inviteStatus)
                || "cancelled".equals(inviteStatus)) {
            return false;
        }
        String phase = room.getString("phase");
        String currentGame = room.getString("currentGame");
        if ("accepted".equals(inviteStatus)
                && "waiting".equals(phase)
                && "waiting".equals(currentGame)
                && isOlderThan(room, ACCEPTED_WAITING_GRACE_MS)) {
            return false;
        }
        if (isRunningGamePhase(phase) && isOlderThan(room, STALE_ACTIVE_ROOM_MS)) {
            return false;
        }
        return !"finished".equals(phase)
                && !"abandoned".equals(phase)
                && !"inviteDeclined".equals(phase)
                && !"inviteExpired".equals(phase)
                && !"inviteCancelled".equals(phase)
                && !"matchFinished".equals(phase)
                && !"abandoned".equals(currentGame)
                && !"abandoned".equals(inviteStatus);
    }

    private boolean isRunningGamePhase(String phase) {
        return "koZnaZnaPlaying".equals(phase)
                || "spojnicePlaying".equals(phase)
                || "round1".equals(phase)
                || "round2".equals(phase)
                || "steal".equals(phase)
                || "steal1".equals(phase)
                || "steal2".equals(phase);
    }

    private boolean isOlderThan(DocumentSnapshot snapshot, long maxAgeMs) {
        Object updatedAt = snapshot.get("updatedAt");
        long updatedAtMillis;
        if (updatedAt instanceof Timestamp) {
            updatedAtMillis = ((Timestamp) updatedAt).toDate().getTime();
        } else if (updatedAt instanceof Number) {
            updatedAtMillis = ((Number) updatedAt).longValue();
        } else {
            return true;
        }
        return System.currentTimeMillis() - updatedAtMillis > maxAgeMs;
    }

    private Map<String, Object> clearBusyState() {
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
        return state;
    }

    private String activeRoomId(DocumentSnapshot user) {
        String currentRoomId = user.getString("currentRoomId");
        if (!isEmpty(currentRoomId)) {
            return currentRoomId;
        }
        String currentMatchId = user.getString("currentMatchId");
        if (!isEmpty(currentMatchId)) {
            return currentMatchId;
        }
        String activeRoomId = user.getString("activeRoomId");
        if (!isEmpty(activeRoomId)) {
            return activeRoomId;
        }
        String activeMatchId = user.getString("activeMatchId");
        return isEmpty(activeMatchId) ? "" : activeMatchId;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
