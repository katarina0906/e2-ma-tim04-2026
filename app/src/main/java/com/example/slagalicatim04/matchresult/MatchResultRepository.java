package com.example.slagalicatim04.matchresult;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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
}
