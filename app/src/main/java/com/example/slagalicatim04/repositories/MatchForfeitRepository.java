package com.example.slagalicatim04.repositories;

import com.example.slagalicatim04.stepbystep.StepByStepMatchRepository;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
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

            String alreadyForfeitedPlayerId = stringValue(snapshot.getString("forfeitedPlayerId"));
            if (!alreadyForfeitedPlayerId.isEmpty() && !alreadyForfeitedPlayerId.equals(playerId)) {
                finishRoomForBothPlayers(transaction, player1Id, player2Id);
                return null;
            }

            String winnerId = playerId.equals(player1Id) ? player2Id : player1Id;
            Map<String, Object> updates = new HashMap<>();
            updates.put("forfeitedPlayerId", playerId);
            updates.put("winnerByForfeitId", winnerId);
            updates.put("statusMessage", "Igrac je napustio partiju. Protivnik nastavlja.");

            String currentGame = stringValue(snapshot.getString("currentGame"));
            applyContinuationState(snapshot, updates, playerId, player1Id, player2Id);
            if ("matchResult".equals(currentGame)) {
                updates.put("matchRewardsApplied", true);
            }

            transaction.set(matchRef, updates, SetOptions.merge());
            clearPlayer(transaction, playerId);
            return null;
        });
    }

    private void finishRoomForBothPlayers(com.google.firebase.firestore.Transaction transaction,
                                          String player1Id, String player2Id) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", "matchFinished");
        updates.put("currentGame", "matchResult");
        updates.put("inviteStatus", "finished");
        updates.put("finished", true);
        updates.put("statusMessage", "Partija je zavrsena jer su oba igraca napustila igru.");
        updates.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(matchRef, updates, SetOptions.merge());
        clearPlayer(transaction, player1Id);
        clearPlayer(transaction, player2Id);
    }

    private void clearPlayer(com.google.firebase.firestore.Transaction transaction, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        transaction.set(matchRef.getFirestore().collection("users").document(userId),
                clearBusyState(), SetOptions.merge());
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

    private void applyContinuationState(DocumentSnapshot snapshot, Map<String, Object> updates,
                                        String forfeitedPlayerId,
                                        String player1Id, String player2Id) {
        String remainingPlayerId = forfeitedPlayerId.equals(player1Id) ? player2Id : player1Id;
        int remainingPlayerNumber = forfeitedPlayerId.equals(player1Id) ? 2 : 1;
        int forfeitedPlayerNumber = forfeitedPlayerId.equals(player1Id) ? 1 : 2;
        String currentGame = stringValue(snapshot.getString("currentGame"));
        String phase = stringValue(snapshot.getString("phase"));

        if ("koZnaZna".equals(currentGame) && "koZnaZnaPlaying".equals(phase)) {
            return;
        }
        if ("spojnice".equals(currentGame) && "spojnicePlaying".equals(phase)) {
            String currentPlayer = stringValue(snapshot.getString("spCurrentPlayer"));
            if (currentPlayer.equals(forfeitedPlayerId)) {
                updates.put("spCurrentPlayer", remainingPlayerId);
            }
            return;
        }
        if ("associations".equals(currentGame) && "associationRound".equals(phase)) {
            Long activePlayer = snapshot.getLong("associationActivePlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("associationActivePlayer", (long) remainingPlayerNumber);
            }
            return;
        }
        if ("skocko".equals(currentGame)) {
            Long activePlayer = snapshot.getLong("activePlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("activePlayer", (long) remainingPlayerNumber);
            }
            return;
        }
        if ("stepByStep".equals(currentGame)) {
            Long activePlayer = snapshot.getLong("activePlayer");
            Long stealPlayer = snapshot.getLong("stealPlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("activePlayer", (long) remainingPlayerNumber);
            }
            if (stealPlayer != null && stealPlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("stealPlayer", (long) remainingPlayerNumber);
            }
            return;
        }
        if ("myNumber".equals(currentGame)) {
            Long activePlayer = snapshot.getLong("myNumberActivePlayer");
            if (activePlayer != null && activePlayer.intValue() == forfeitedPlayerNumber) {
                updates.put("myNumberActivePlayer", (long) remainingPlayerNumber);
            }
        }
    }

    private String stringValue(String value) {
        return value == null ? "" : value;
    }
}
