package com.example.slagalicatim04.matchresult;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MatchResultRepository {
    public interface Listener {
        void onState(MatchResultState state);
        void onError(Exception error);
    }

    public ListenerRegistration listen(String roomId, Listener listener) {
        return FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(roomId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                    } else if (snapshot != null && snapshot.exists()) {
                        listener.onState(new MatchResultState(snapshot));
                    }
                });
    }

    public void releasePlayers(String roomId, String player1Id, String player2Id)
            throws ExecutionException, InterruptedException {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Tasks.await(firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            releasePlayer(transaction, firestore, roomId, player1Id);
            releasePlayer(transaction, firestore, roomId, player2Id);
            return null;
        }));
    }

    private void releasePlayer(Transaction transaction, FirebaseFirestore firestore,
                               String roomId, String userId) throws FirebaseFirestoreException {
        if (isEmpty(roomId) || isEmpty(userId)) {
            return;
        }
        DocumentReference userRef = firestore.collection("users").document(userId);
        DocumentSnapshot user = transaction.get(userRef);
        if (!roomId.equals(user.getString("currentRoomId"))
                && !roomId.equals(user.getString("currentMatchId"))) {
            return;
        }
        Map<String, Object> state = new HashMap<>();
        state.put("inGame", false);
        state.put("currentRoomId", FieldValue.delete());
        state.put("currentMatchId", FieldValue.delete());
        state.put("currentOpponentId", FieldValue.delete());
        state.put("lastActiveAt", System.currentTimeMillis());
        transaction.set(userRef, state, SetOptions.merge());
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
