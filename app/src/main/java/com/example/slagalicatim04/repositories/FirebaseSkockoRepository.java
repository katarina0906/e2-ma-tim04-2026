package com.example.slagalicatim04.repositories;

import com.example.slagalicatim04.models.SkockoGameResult;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseSkockoRepository implements SkockoRepository {
    private static final String GAME_NAME = "skocko";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public FirebaseSkockoRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public Task<Void> saveCompletedGame(SkockoGameResult result) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return com.google.android.gms.tasks.Tasks.forResult(null);
        }

        DocumentReference gameRef = firestore.collection("gameResults").document();
        DocumentReference statsRef = firestore.collection("users")
                .document(user.getUid())
                .collection("gameStats")
                .document(GAME_NAME);

        Map<String, Object> gameData = createGameData(user, result);
        return firestore.runTransaction(transaction -> {
            transaction.set(gameRef, gameData);

            Map<String, Object> updates = new HashMap<>();
            updates.put("gamesPlayed", FieldValue.increment(1));
            updates.put("roundsPlayed", FieldValue.increment(result.getRounds().size()));
            updates.put("totalPoints", FieldValue.increment(result.getPlayer1Score()));
            updates.put("updatedAt", Timestamp.now());

            for (SkockoGameResult.RoundResult round : result.getRounds()) {
                if (round.getStarter() == 0 && round.getSolvedAttempt() > 0) {
                    updates.put("solvedRounds", FieldValue.increment(1));
                    updates.put("solvedOnAttempt" + round.getSolvedAttempt(), FieldValue.increment(1));
                }
                if (round.getStarter() == 1 && round.isStealAttempted()) {
                    updates.put("stealAttempts", FieldValue.increment(1));
                    if (round.isStealSolved()) {
                        updates.put("stealSolved", FieldValue.increment(1));
                    }
                }
            }
            transaction.set(statsRef, updates, com.google.firebase.firestore.SetOptions.merge());
            return null;
        });
    }

    private Map<String, Object> createGameData(FirebaseUser user, SkockoGameResult result) {
        Map<String, Object> data = new HashMap<>();
        data.put("game", GAME_NAME);
        data.put("userId", user.getUid());
        data.put("player1Score", result.getPlayer1Score());
        data.put("player2Score", result.getPlayer2Score());
        data.put("createdAt", FieldValue.serverTimestamp());

        List<Map<String, Object>> rounds = new ArrayList<>();
        for (SkockoGameResult.RoundResult round : result.getRounds()) {
            Map<String, Object> roundData = new HashMap<>();
            roundData.put("starter", round.getStarter());
            roundData.put("solvedAttempt", round.getSolvedAttempt());
            roundData.put("stealAttempted", round.isStealAttempted());
            roundData.put("stealSolved", round.isStealSolved());
            rounds.add(roundData);
        }
        data.put("rounds", rounds);
        return data;
    }
}
