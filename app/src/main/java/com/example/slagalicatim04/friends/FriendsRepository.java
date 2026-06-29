package com.example.slagalicatim04.friends;

import com.example.slagalicatim04.leagues.LeagueInfo;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class FriendsRepository {
    private static final long ACTIVE_WINDOW_MS = 30L * 60L * 1000L;
    public static final long GAME_INVITE_TIMEOUT_MS = 10L * 1000L;
    private static final long ACCEPTED_WAITING_GRACE_MS = 30L * 1000L;
    private static final long STALE_ACTIVE_ROOM_MS = 45L * 1000L;
    private static final String MATCH_COLLECTION = "stepByStepMatches";

    private final FirebaseFirestore firestore;

    public FriendsRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public List<FriendItem> loadFriends(String userId)
            throws ExecutionException, InterruptedException {
        if (isEmpty(userId)) {
            return new ArrayList<>();
        }
        clearInvalidBusyState(userId);
        DocumentSnapshot user = Tasks.await(firestore.collection("users").document(userId).get());
        Set<String> friendIds = new LinkedHashSet<>();
        addStringList(friendIds, user.get("friends"));
        addStringList(friendIds, user.get("friendIds"));

        QuerySnapshot friendDocs = Tasks.await(firestore.collection("users")
                .document(userId)
                .collection("friends")
                .get());
        for (DocumentSnapshot friendDoc : friendDocs.getDocuments()) {
            String friendId = friendDoc.getId();
            if (isEmpty(friendId)) {
                friendId = friendDoc.getString("userId");
            }
            if (!isEmpty(friendId) && !friendId.equals(userId)) {
                friendIds.add(friendId);
            }
        }

        Map<String, Integer> monthlyRanks = monthlyRanks();
        List<FriendItem> friends = new ArrayList<>();
        for (String friendId : friendIds) {
            if (isEmpty(friendId) || friendId.equals(userId)) {
                continue;
            }
            DocumentSnapshot friend = Tasks.await(firestore.collection("users")
                    .document(friendId)
                    .get());
            if (friend.exists()) {
                clearInvalidBusyState(friendId);
                friend = Tasks.await(firestore.collection("users").document(friendId).get());
                friends.add(toFriendItem(friendId, friend, monthlyRanks));
            }
        }
        return friends;
    }

    public void clearGameStatus(String userId) throws ExecutionException, InterruptedException {
        if (isEmpty(userId)) {
            return;
        }
        Tasks.await(firestore.collection("users").document(userId)
                .set(clearBusyState(), SetOptions.merge()));
    }

    public FriendItem findByUsername(String username)
            throws ExecutionException, InterruptedException {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return null;
        }
        DocumentSnapshot usernameDoc = Tasks.await(firestore.collection("usernames")
                .document(normalized)
                .get());
        String userId = usernameDoc.getString("uid");
        if (isEmpty(userId)) {
            return null;
        }
        return loadUser(userId);
    }

    public FriendItem addFriend(String currentUserId, String friendUserId)
            throws ExecutionException, InterruptedException {
        if (isEmpty(currentUserId) || isEmpty(friendUserId)) {
            throw new IllegalArgumentException("Korisnik nije pronadjen.");
        }
        if (currentUserId.equals(friendUserId)) {
            throw new IllegalArgumentException("Ne mozes dodati sebe kao prijatelja.");
        }
        FriendItem friend = loadUser(friendUserId);
        if (friend == null) {
            throw new IllegalArgumentException("Korisnik nije pronadjen.");
        }

        WriteBatch batch = firestore.batch();
        batch.set(firestore.collection("users").document(currentUserId)
                        .collection("friends").document(friendUserId),
                friendDocData(friendUserId),
                SetOptions.merge());
        batch.set(firestore.collection("users").document(friendUserId)
                        .collection("friends").document(currentUserId),
                friendDocData(currentUserId),
                SetOptions.merge());
        batch.update(firestore.collection("users").document(currentUserId),
                "friendIds", FieldValue.arrayUnion(friendUserId));
        batch.update(firestore.collection("users").document(friendUserId),
                "friendIds", FieldValue.arrayUnion(currentUserId));
        Tasks.await(batch.commit());
        return friend;
    }

    public GameInviteResult startGameWithFriend(String currentUserId, String currentUsername,
                                                FriendItem friend)
            throws ExecutionException, InterruptedException {
        if (isEmpty(currentUserId)) {
            throw new IllegalArgumentException("Korisnik nije prijavljen.");
        }
        if (friend == null || isEmpty(friend.id)) {
            throw new IllegalArgumentException("Prijatelj nije pronadjen.");
        }
        if (currentUserId.equals(friend.id)) {
            throw new IllegalArgumentException("Ne mozes poslati zahtev za partiju samom sebi.");
        }
        clearInvalidBusyState(currentUserId);
        clearInvalidBusyState(friend.id);

        String roomId = "friend_" + currentUserId + "_" + friend.id + "_" + System.currentTimeMillis();
        String notificationId = "game_invite_" + roomId;
        long now = System.currentTimeMillis();
        long expiresAt = now + GAME_INVITE_TIMEOUT_MS;
        DocumentReference currentRef = firestore.collection("users").document(currentUserId);
        DocumentReference friendRef = firestore.collection("users").document(friend.id);
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);
        DocumentReference notificationRef = friendRef.collection("notifications").document(notificationId);

        Tasks.await(firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot currentSnapshot = transaction.get(currentRef);
            DocumentSnapshot friendSnapshot = transaction.get(friendRef);
            if (!friendSnapshot.exists()) {
                throw new IllegalArgumentException("Prijatelj nije pronadjen.");
            }
            if (currentSnapshot.exists() && isInGame(transaction, currentRef, currentSnapshot)) {
                throw new IllegalStateException("Vec ucestvujes u partiji.");
            }
            if (isInGame(transaction, friendRef, friendSnapshot)) {
                throw new IllegalStateException("Prijatelj trenutno ucestvuje u drugoj partiji.");
            }

            transaction.set(roomRef, newFriendInviteState(
                    currentUserId,
                    displayName(currentUsername, "Igrac 1"),
                    friend.id,
                    displayName(friend.username, "Igrac 2"),
                    expiresAt,
                    notificationId));
            transaction.set(notificationRef, newGameInviteNotification(
                    notificationId,
                    roomId,
                    currentUserId,
                    displayName(currentUsername, "Prijatelj"),
                    expiresAt));
            return null;
        }));
        return new GameInviteResult(roomId, notificationId);
    }

    public void expireGameInvite(String roomId, String notificationId)
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
            if (expiresAt != null && System.currentTimeMillis() < expiresAt) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("inviteStatus", "expired");
            updates.put("phase", "inviteExpired");
            updates.put("statusMessage", "Poziv za partiju je istekao.");
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roomRef, updates, SetOptions.merge());

            String player2Id = room.getString("player2Id");
            String notifId = isEmpty(notificationId) ? room.getString("inviteNotificationId") : notificationId;
            if (!isEmpty(player2Id) && !isEmpty(notifId)) {
                transaction.set(firestore.collection("users").document(player2Id)
                                .collection("notifications").document(notifId),
                        inviteNotificationState("expired"),
                        SetOptions.merge());
            }
            return null;
        }));
    }

    public void cancelGameInvite(String currentUserId, String roomId, String notificationId)
            throws ExecutionException, InterruptedException {
        if (isEmpty(currentUserId) || isEmpty(roomId)) {
            throw new IllegalArgumentException("Poziv nije pronadjen.");
        }
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);
        Tasks.await(firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot room = transaction.get(roomRef);
            if (!room.exists()) {
                return null;
            }
            if (!currentUserId.equals(room.getString("player1Id"))) {
                throw new IllegalStateException("Samo igrac koji je poslao zahtev moze da ga prekine.");
            }
            if (!"pending".equals(room.getString("inviteStatus"))) {
                throw new IllegalStateException("Zahtev vise nije aktivan.");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("inviteStatus", "cancelled");
            updates.put("phase", "inviteCancelled");
            updates.put("currentGame", "inviteCancelled");
            updates.put("statusMessage", "Poziv za partiju je prekinut.");
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roomRef, updates, SetOptions.merge());

            String player2Id = room.getString("player2Id");
            String notifId = isEmpty(notificationId) ? room.getString("inviteNotificationId") : notificationId;
            if (!isEmpty(player2Id) && !isEmpty(notifId)) {
                transaction.set(firestore.collection("users").document(player2Id)
                                .collection("notifications").document(notifId),
                        inviteNotificationState("cancelled"),
                        SetOptions.merge());
            }
            return null;
        }));
    }

    @SuppressWarnings("unchecked")
    private void addStringList(Set<String> output, Object value) {
        if (!(value instanceof List<?>)) {
            return;
        }
        for (Object item : (List<Object>) value) {
            if (item instanceof String) {
                output.add((String) item);
            }
        }
    }

    private FriendItem loadUser(String userId) throws ExecutionException, InterruptedException {
        DocumentSnapshot user = Tasks.await(firestore.collection("users")
                .document(userId)
                .get());
        if (!user.exists()) {
            return null;
        }
        return toFriendItem(userId, user, monthlyRanks());
    }

    private FriendItem toFriendItem(String userId, DocumentSnapshot user,
                                    Map<String, Integer> monthlyRanks) {
        long totalStars = firstLongValue(user, "totalStars", "overallStars", "stars");
        return new FriendItem(
                userId,
                stringValue(user, "username", "Nepoznat igrac"),
                stringValue(user, "email", ""),
                stringValue(user, "region", ""),
                stringValue(user, "avatarData", ""),
                (int) longValue(user, "avatarFramePlace"),
                monthlyRanks.containsKey(userId) ? monthlyRanks.get(userId) : 0,
                totalStars,
                LeagueInfo.forStars(totalStars).name,
                isOnline(user),
                isInGame(user)
        );
    }

    private Map<String, Integer> monthlyRanks() throws ExecutionException, InterruptedException {
        String cycle = currentCycle();
        QuerySnapshot users = Tasks.await(firestore.collection("users").get());
        List<UserStars> ranking = new ArrayList<>();
        for (DocumentSnapshot user : users.getDocuments()) {
            long stars = cycle.equals(user.getString("monthlyStarsCycle"))
                    ? longValue(user, "monthlyStars") : 0L;
            ranking.add(new UserStars(user.getId(), stars));
        }
        Collections.sort(ranking, (left, right) -> Long.compare(right.stars, left.stars));
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            ranks.put(ranking.get(i).userId, i + 1);
        }
        return ranks;
    }

    private Map<String, Object> newFriendInviteState(String currentUserId, String currentUsername,
                                                     String friendId, String friendUsername,
                                                     long expiresAt, String notificationId) {
        Map<String, Object> state = new HashMap<>();
        state.put("player1Id", currentUserId);
        state.put("player1Name", currentUsername);
        state.put("player2Id", friendId);
        state.put("player2Name", friendUsername);
        state.put("player1Score", 0L);
        state.put("player2Score", 0L);
        state.put("player1Ready", false);
        state.put("player2Ready", false);
        state.put("round", 1L);
        state.put("phase", "invitePending");
        state.put("currentGame", "invitePending");
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
        state.put("inviteType", "friend");
        state.put("inviteStatus", "pending");
        state.put("inviteExpiresAt", expiresAt);
        state.put("inviteNotificationId", notificationId);
        state.put("statusMessage", "Ceka se odgovor prijatelja.");
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private Map<String, Object> newGameInviteNotification(String notificationId, String roomId,
                                                          String inviterId, String inviterName,
                                                          long expiresAt) {
        Map<String, String> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("inviterId", inviterId);
        data.put("expiresAt", String.valueOf(expiresAt));

        Map<String, Object> notification = new HashMap<>();
        notification.put("category", "other");
        notification.put("title", "Novi poziv za partiju");
        notification.put("message", inviterName + " te poziva na partiju. Imas 10 sekundi da prihvatis.");
        notification.put("action", "game_invite");
        notification.put("targetId", roomId);
        notification.put("data", data);
        notification.put("source", "friend_invite");
        notification.put("inviteStatus", "pending");
        notification.put("read", false);
        notification.put("readAt", null);
        notification.put("actionedAt", null);
        notification.put("createdAt", FieldValue.serverTimestamp());
        notification.put("expiresAtMillis", expiresAt);
        return notification;
    }

    private Map<String, Object> inviteNotificationState(String status) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("inviteStatus", status);
        updates.put("read", true);
        updates.put("readAt", FieldValue.serverTimestamp());
        updates.put("actionedAt", FieldValue.serverTimestamp());
        return updates;
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

    private Map<String, Object> friendDocData(String userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private String stringValue(DocumentSnapshot snapshot, String field, String fallback) {
        String value = snapshot.getString(field);
        return isEmpty(value) ? fallback : value;
    }

    private long longValue(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value == null ? 0L : value;
    }

    private long firstLongValue(DocumentSnapshot snapshot, String... fields) {
        for (String field : fields) {
            Long value = snapshot.getLong(field);
            if (value != null) {
                return value;
            }
        }
        return 0L;
    }

    private String firstStringValue(DocumentSnapshot snapshot, String firstField,
                                    String secondField, String fallback) {
        String first = snapshot.getString(firstField);
        if (!isEmpty(first)) {
            return first;
        }
        String second = snapshot.getString(secondField);
        return isEmpty(second) ? fallback : second;
    }

    private boolean isOnline(DocumentSnapshot user) {
        Boolean active = user.getBoolean("active");
        if (Boolean.TRUE.equals(active)) {
            return true;
        }
        Long lastActiveAt = user.getLong("lastActiveAt");
        return lastActiveAt != null && System.currentTimeMillis() - lastActiveAt <= ACTIVE_WINDOW_MS;
    }

    private boolean isInGame(DocumentSnapshot user) {
        String roomId = activeRoomId(user);
        if (isEmpty(roomId)) {
            return false;
        }
        try {
            DocumentSnapshot room = Tasks.await(firestore.collection(MATCH_COLLECTION)
                    .document(roomId)
                    .get());
            boolean activeRoom = isActiveRoom(room);
            if (!activeRoom) {
                Tasks.await(firestore.collection("users").document(user.getId())
                        .set(clearBusyState(), SetOptions.merge()));
            }
            return activeRoom;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void clearInvalidBusyState(String userId) throws ExecutionException, InterruptedException {
        if (isEmpty(userId)) {
            return;
        }
        DocumentReference userRef = firestore.collection("users").document(userId);
        DocumentSnapshot user = Tasks.await(userRef.get());
        if (!user.exists()) {
            return;
        }
        String roomId = activeRoomId(user);
        boolean hasBusyFlag = Boolean.TRUE.equals(user.getBoolean("inGame"))
                || Boolean.TRUE.equals(user.getBoolean("busy"))
                || Boolean.TRUE.equals(user.getBoolean("isPlaying"));
        if (isEmpty(roomId)) {
            if (hasBusyFlag) {
                Tasks.await(userRef.set(clearBusyState(), SetOptions.merge()));
            }
            return;
        }
        DocumentSnapshot room = Tasks.await(firestore.collection(MATCH_COLLECTION)
                .document(roomId)
                .get());
        if (!isActiveRoom(room)) {
            Tasks.await(userRef.set(clearBusyState(), SetOptions.merge()));
        }
    }

    private boolean isInGame(Transaction transaction, DocumentReference userRef, DocumentSnapshot user)
            throws FirebaseFirestoreException {
        String roomId = activeRoomId(user);
        if (isEmpty(roomId)) {
            return false;
        }
        DocumentSnapshot room = transaction.get(firestore.collection(MATCH_COLLECTION).document(roomId));
        boolean activeRoom = isActiveRoom(room);
        if (!activeRoom) {
            transaction.set(userRef, clearBusyState(), SetOptions.merge());
        }
        return activeRoom;
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
                && !"inviteDeclined".equals(phase)
                && !"inviteExpired".equals(phase)
                && !"inviteCancelled".equals(phase)
                && !"matchFinished".equals(phase);
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

    private String displayName(String value, String fallback) {
        return isEmpty(value) ? fallback : value;
    }

    private String currentCycle() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM", Locale.ROOT);
        return format.format(new Date());
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class UserStars {
        final String userId;
        final long stars;

        UserStars(String userId, long stars) {
            this.userId = userId;
            this.stars = stars;
        }
    }
}
