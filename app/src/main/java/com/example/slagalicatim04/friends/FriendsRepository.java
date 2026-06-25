package com.example.slagalicatim04.friends;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class FriendsRepository {
    private final FirebaseFirestore firestore;

    public FriendsRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public List<FriendItem> loadFriends(String userId)
            throws ExecutionException, InterruptedException {
        if (userId == null || userId.trim().isEmpty()) {
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
            friendIds.add(friendId == null || friendId.trim().isEmpty()
                    ? friendDoc.getId() : friendId);
        }

        List<FriendItem> friends = new ArrayList<>();
        for (String friendId : friendIds) {
            if (friendId == null || friendId.trim().isEmpty()) {
                continue;
            }
            DocumentSnapshot friend = Tasks.await(firestore.collection("users")
                    .document(friendId)
                    .get());
            if (!friend.exists()) {
                continue;
            }
            friends.add(new FriendItem(
                    friendId,
                    stringValue(friend, "username", "Nepoznat igrac"),
                    stringValue(friend, "email", ""),
                    stringValue(friend, "region", ""),
                    stringValue(friend, "avatarData", ""),
                    (int) longValue(friend, "avatarFramePlace")
            ));
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
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        return loadUser(userId);
    }

    public FriendItem addFriend(String currentUserId, String friendUserId)
            throws ExecutionException, InterruptedException {
        if (currentUserId == null || currentUserId.trim().isEmpty()
                || friendUserId == null || friendUserId.trim().isEmpty()) {
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
        Map<String, Object> currentFriendData = friendDocData(friendUserId);
        Map<String, Object> reverseFriendData = friendDocData(currentUserId);
        batch.set(firestore.collection("users").document(currentUserId)
                        .collection("friends").document(friendUserId),
                currentFriendData,
                SetOptions.merge());
        batch.set(firestore.collection("users").document(friendUserId)
                        .collection("friends").document(currentUserId),
                reverseFriendData,
                SetOptions.merge());
        batch.update(firestore.collection("users").document(currentUserId),
                "friendIds", FieldValue.arrayUnion(friendUserId));
        batch.update(firestore.collection("users").document(friendUserId),
                "friendIds", FieldValue.arrayUnion(currentUserId));
        Tasks.await(batch.commit());
        return friend;
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

    private String stringValue(DocumentSnapshot snapshot, String field, String fallback) {
        String value = snapshot.getString(field);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private long longValue(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value == null ? 0L : value;
    }

    private FriendItem loadUser(String userId) throws ExecutionException, InterruptedException {
        DocumentSnapshot user = Tasks.await(firestore.collection("users")
                .document(userId)
                .get());
        if (!user.exists()) {
            return null;
        }
        return new FriendItem(
                userId,
                stringValue(user, "username", "Nepoznat igrac"),
                stringValue(user, "email", ""),
                stringValue(user, "region", ""),
                stringValue(user, "avatarData", ""),
                (int) longValue(user, "avatarFramePlace")
        );
    }

    private Map<String, Object> friendDocData(String userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
