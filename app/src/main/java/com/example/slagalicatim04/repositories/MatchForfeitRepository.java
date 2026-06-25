package com.example.slagalicatim04.repositories;

import com.example.slagalicatim04.stepbystep.StepByStepMatchRepository;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class MatchForfeitRepository {
    private final DocumentReference matchRef;

    public MatchForfeitRepository(String roomId) {
        matchRef = FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(roomId);
    }

    public void forfeit(String playerId) {
        matchRef.getFirestore().runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }
            String player1Id = stringValue(snapshot.getString("player1Id"));
            String player2Id = stringValue(snapshot.getString("player2Id"));
            if (!playerId.equals(player1Id) && !playerId.equals(player2Id)) {
                return null;
            }

            String winnerId = playerId.equals(player1Id) ? player2Id : player1Id;
            Map<String, Object> updates = new HashMap<>();
            updates.put("forfeitedPlayerId", playerId);
            updates.put("winnerByForfeitId", winnerId);
            updates.put("statusMessage", "Igrac je napustio partiju. Protivnik nastavlja.");

            String currentGame = stringValue(snapshot.getString("currentGame"));
            if ("matchResult".equals(currentGame)) {
                updates.put("matchRewardsApplied", true);
            }

            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    private String stringValue(String value) {
        return value == null ? "" : value;
    }
}
