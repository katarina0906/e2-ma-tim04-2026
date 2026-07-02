package com.example.slagalicatim04.ranking;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class RankingRepository {
    public interface RankingListener {
        void onRanking(RankingCycle cycle, List<RankingEntry> entries);
        void onError(Exception error);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void loadCurrent(String type, RankingListener listener) {
        RankingCycle fallback = RankingCycle.current(type);
        firestore.collection("rankingCycles").document(fallback.id).get()
                .addOnSuccessListener(cycleDoc -> {
                    RankingCycle cycle = cycleDoc.exists()
                            ? RankingCycle.fromSnapshot(cycleDoc, type)
                            : fallback;
                    firestore.collection("rankingCycles").document(cycle.id)
                            .collection("entries")
                            .orderBy("stars", Query.Direction.DESCENDING)
                            .limit(50)
                            .get()
                            .addOnSuccessListener(entriesSnapshot -> {
                                List<RankingEntry> entries = new ArrayList<>();
                                int rank = 1;
                                for (var document : entriesSnapshot.getDocuments()) {
                                    entries.add(new RankingEntry(document, rank++));
                                }
                                listener.onRanking(cycle, entries);
                            })
                            .addOnFailureListener(listener::onError);
                })
                .addOnFailureListener(listener::onError);
    }
}
