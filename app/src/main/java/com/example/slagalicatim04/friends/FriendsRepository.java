package com.example.slagalicatim04.friends;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

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
        DocumentSnapshot user = Tasks.await(firestore.collection("users").document(userId).get());
        Set<String> friendIds = new LinkedHashSet<>();
        addStringList(friendIds, user.get("friends"));
        addStringList(friendIds, user.get("friendIds"));

        QuerySnapshot friendDocs = Tasks.await(firestore.collection("users")
                .document(userId)
                .collection("friends")
                .get());
        for (DocumentSnapshot friendDoc : friendDocs.getDocuments()) {
            String friendId = friendDoc.getString("userId");
            friendIds.add(isEmpty(friendId) ? friendDoc.getId() : friendId);
        }

        Map<String, Integer> monthlyRanks = monthlyRanks();
        List<FriendItem> friends = new ArrayList<>();
        for (String friendId : friendIds) {
            if (isEmpty(friendId)) {
                continue;
            }
            DocumentSnapshot friend = Tasks.await(firestore.collection("users")
                    .document(friendId)
                    .get());
            if (friend.exists()) {
                friends.add(toFriendItem(friendId, friend, monthlyRanks));
            }
        }
        return friends;
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

    public String startGameWithFriend(String currentUserId, String currentUsername, FriendItem friend)
            throws ExecutionException, InterruptedException {
        if (isEmpty(currentUserId)) {
            throw new IllegalArgumentException("Korisnik nije prijavljen.");
        }
        if (friend == null || isEmpty(friend.id)) {
            throw new IllegalArgumentException("Prijatelj nije pronadjen.");
        }

        String roomId = "friend_" + currentUserId + "_" + friend.id + "_" + System.currentTimeMillis();
        DocumentReference currentRef = firestore.collection("users").document(currentUserId);
        DocumentReference friendRef = firestore.collection("users").document(friend.id);
        DocumentReference roomRef = firestore.collection(MATCH_COLLECTION).document(roomId);

        Tasks.await(firestore.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot currentSnapshot = transaction.get(currentRef);
            DocumentSnapshot friendSnapshot = transaction.get(friendRef);
            if (!friendSnapshot.exists()) {
                throw new IllegalArgumentException("Prijatelj nije pronadjen.");
            }
            if (currentSnapshot.exists() && isInGame(currentSnapshot)) {
                throw new IllegalStateException("Vec ucestvujes u partiji.");
            }
            if (!isOnline(friendSnapshot) || isInGame(friendSnapshot)) {
                throw new IllegalStateException("Prijatelj trenutno nije dostupan za partiju.");
            }

            transaction.set(roomRef, newFriendWaitingState(
                    currentUserId,
                    displayName(currentUsername, "Igrac 1"),
                    friend.id,
                    displayName(friend.username, "Igrac 2")));
            transaction.set(currentRef, busyState(roomId, friend.id), SetOptions.merge());
            transaction.set(friendRef, busyState(roomId, currentUserId), SetOptions.merge());
            return roomId;
        }));
        return roomId;
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
        return new FriendItem(
                userId,
                stringValue(user, "username", "Nepoznat igrac"),
                stringValue(user, "email", ""),
                stringValue(user, "region", ""),
                stringValue(user, "avatarData", ""),
                (int) longValue(user, "avatarFramePlace"),
                monthlyRanks.containsKey(userId) ? monthlyRanks.get(userId) : 0,
                firstLongValue(user, "totalStars", "stars", "overallStars"),
                firstStringValue(user, "league", "liga", "Bez lige"),
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

    private Map<String, Object> newFriendWaitingState(String currentUserId, String currentUsername,
                                                      String friendId, String friendUsername) {
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
        state.put("phase", "waiting");
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
        state.put("inviteType", "friend");
        state.put("statusMessage", "Partija sa prijateljem je kreirana. Potvrdite spremnost.");
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
        if (Boolean.TRUE.equals(user.getBoolean("inGame"))
                || Boolean.TRUE.equals(user.getBoolean("isPlaying"))
                || Boolean.TRUE.equals(user.getBoolean("busy"))) {
            return true;
        }
        return !isEmpty(user.getString("currentRoomId"))
                || !isEmpty(user.getString("currentMatchId"))
                || !isEmpty(user.getString("activeRoomId"))
                || !isEmpty(user.getString("activeMatchId"));
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
