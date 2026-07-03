package com.example.slagalicatim04.tournament;

import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.leagues.LeagueInfo;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentRepository {
    public interface JoinCallback {
        void onJoined(String tournamentId);
        void onError(Exception error);
    }

    public interface Listener {
        void onState(TournamentState state);
        void onError(Exception error);
    }

    public interface FinalRoomCallback {
        void onReady(String roomId);
        void onPending();
        void onError(Exception error);
    }

    private static final long ENTRY_COST = 3L;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void join(AuthUser user, JoinCallback callback) {
        firestore.collection("tournaments")
                .whereEqualTo("status", "waiting")
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    DocumentReference ref = snapshot.isEmpty()
                            ? firestore.collection("tournaments").document()
                            : snapshot.getDocuments().get(0).getReference();
                    joinTournament(ref, user, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration listen(String tournamentId, Listener listener) {
        return firestore.collection("tournaments").document(tournamentId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                    } else if (snapshot != null && snapshot.exists()) {
                        listener.onState(new TournamentState(snapshot));
                    }
                });
    }

    public void onMatchFinished(String roomId, com.example.slagalicatim04.matchresult.MatchResultState state) {
        if (roomId == null || roomId.isEmpty() || state.winner() == 0) {
            return;
        }
        DocumentReference matchRef = firestore.collection("tournamentMatches").document(roomId);
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot match = transaction.get(matchRef);
            if (!match.exists() || Boolean.TRUE.equals(match.getBoolean("processed"))) {
                return null;
            }
            String tournamentId = string(match.getString("tournamentId"));
            String round = string(match.getString("round"));
            String key = string(match.getString("key"));
            DocumentReference tournamentRef = firestore.collection("tournaments").document(tournamentId);
            DocumentSnapshot tournamentDoc = transaction.get(tournamentRef);
            if (!tournamentDoc.exists()) {
                return null;
            }
            String winnerId = state.winner() == 1 ? state.getPlayer1Id() : state.getPlayer2Id();
            String loserId = state.winner() == 1 ? state.getPlayer2Id() : state.getPlayer1Id();
            long winnerScore = state.winner() == 1 ? state.getPlayer1Score() : state.getPlayer2Score();
            long loserScore = state.winner() == 1 ? state.getPlayer2Score() : state.getPlayer1Score();
            Map<String, Object> updates = new HashMap<>();
            if ("semifinal".equals(round)) {
                updates.put(key + "WinnerId", winnerId);
                updates.put(key + "LoserId", loserId);
                updates.put(key + "WinnerScore", winnerScore);
                updates.put(key + "LoserScore", loserScore);
                String otherWinner = "semifinal1".equals(key)
                        ? string(tournamentDoc.getString("semifinal2WinnerId"))
                        : string(tournamentDoc.getString("semifinal1WinnerId"));
                if (!otherWinner.isEmpty()) {
                    createFinal(transaction, tournamentRef, new TournamentState(tournamentDoc),
                            "semifinal1".equals(key) ? winnerId : otherWinner,
                            "semifinal1".equals(key) ? otherWinner : winnerId,
                            updates);
                }
            } else if ("final".equals(round)) {
                updates.put("championId", winnerId);
                updates.put("runnerUpId", loserId);
                updates.put("championScore", winnerScore);
                updates.put("runnerUpScore", loserScore);
                updates.put("status", "finished");
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(tournamentRef, updates, SetOptions.merge());
            transaction.set(matchRef, Map.of("processed", true), SetOptions.merge());
            return null;
        });
    }

    public void findFinalRoom(String tournamentId, String userId, FinalRoomCallback callback) {
        if (tournamentId == null || tournamentId.isEmpty() || userId == null || userId.isEmpty()) {
            callback.onPending();
            return;
        }
        firestore.collection("tournaments").document(tournamentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onPending();
                        return;
                    }
                    TournamentState state = new TournamentState(snapshot);
                    if (!state.finalRoomId.isEmpty() && state.isFinalist(userId)) {
                        callback.onReady(state.finalRoomId);
                    } else {
                        callback.onPending();
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void joinTournament(DocumentReference ref, AuthUser user, JoinCallback callback) {
        firestore.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot tournament = transaction.get(ref);
            DocumentReference userRef = firestore.collection("users").document(user.getId());
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            long tokens = longValue(userSnapshot.getLong("tokens"));
            if (tokens < ENTRY_COST) {
                throw new IllegalStateException("Za turnir su potrebna 3 tokena.");
            }
            List<Map<String, Object>> participants = participantMaps(tournament.get("participants"));
            for (Map<String, Object> item : participants) {
                if (user.getId().equals(item.get("userId"))) {
                    return ref.getId();
                }
            }
            if (tournament.exists() && !"waiting".equals(string(tournament.getString("status")))) {
                throw new IllegalStateException("Turnir je vec poceo. Pokusaj ponovo.");
            }
            participants.add(new TournamentParticipant(
                    user.getId(),
                    user.getUsername(),
                    LeagueInfo.forStars(user.getTotalStars()).name,
                    user.getAvatarData()).toMap());
            transaction.update(userRef, "tokens", tokens - ENTRY_COST);
            Map<String, Object> data = new HashMap<>();
            data.put("status", participants.size() >= 4 ? "semifinals" : "waiting");
            data.put("participants", participants);
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!tournament.exists()) {
                data.put("createdAt", FieldValue.serverTimestamp());
            }
            transaction.set(ref, data, SetOptions.merge());
            if (participants.size() >= 4) {
                createSemifinals(transaction, ref, participants);
            }
            return ref.getId();
        }).addOnSuccessListener(callback::onJoined)
                .addOnFailureListener(callback::onError);
    }

    private void createSemifinals(Transaction transaction, DocumentReference tournamentRef,
                                  List<Map<String, Object>> participants) {
        String base = tournamentRef.getId();
        String room1 = "tournament_" + base + "_semi1";
        String room2 = "tournament_" + base + "_semi2";
        transaction.set(firestore.collection("stepByStepMatches").document(room1),
                matchState(room1, base, "semifinal", "semifinal1", participants.get(0), participants.get(1)));
        transaction.set(firestore.collection("stepByStepMatches").document(room2),
                matchState(room2, base, "semifinal", "semifinal2", participants.get(2), participants.get(3)));
        transaction.set(firestore.collection("tournamentMatches").document(room1),
                matchLink(base, "semifinal", "semifinal1"));
        transaction.set(firestore.collection("tournamentMatches").document(room2),
                matchLink(base, "semifinal", "semifinal2"));
        transaction.set(tournamentRef, Map.of(
                "semifinal1RoomId", room1,
                "semifinal2RoomId", room2
        ), SetOptions.merge());
    }

    private void createFinal(Transaction transaction, DocumentReference tournamentRef,
                             TournamentState state, String firstWinner, String secondWinner,
                             Map<String, Object> updates) {
        String room = "tournament_" + tournamentRef.getId() + "_final";
        Map<String, Object> first = participantById(state, firstWinner);
        Map<String, Object> second = participantById(state, secondWinner);
        transaction.set(firestore.collection("stepByStepMatches").document(room),
                matchState(room, tournamentRef.getId(), "final", "final", first, second));
        transaction.set(firestore.collection("tournamentMatches").document(room),
                matchLink(tournamentRef.getId(), "final", "final"));
        updates.put("status", "final");
        updates.put("finalRoomId", room);
    }

    private Map<String, Object> matchState(String roomId, String tournamentId, String round,
                                           String key, Map<String, Object> p1, Map<String, Object> p2) {
        Map<String, Object> state = new HashMap<>();
        state.put("player1Id", string(p1.get("userId")));
        state.put("player1Name", string(p1.get("username")));
        state.put("player2Id", string(p2.get("userId")));
        state.put("player2Name", string(p2.get("username")));
        state.put("player1Score", 0L);
        state.put("player2Score", 0L);
        state.put("player1Ready", false);
        state.put("player2Ready", false);
        state.put("round", 1L);
        state.put("phase", "waiting");
        state.put("currentGame", "waiting");
        state.put("finished", false);
        state.put("tournamentId", tournamentId);
        state.put("tournamentRound", round);
        state.put("tournamentMatchKey", key);
        state.put("statusMessage", "Turnirska partija. Potvrdite spremnost.");
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private Map<String, Object> matchLink(String tournamentId, String round, String key) {
        Map<String, Object> map = new HashMap<>();
        map.put("tournamentId", tournamentId);
        map.put("round", round);
        map.put("key", key);
        map.put("processed", false);
        return map;
    }

    private Map<String, Object> participantById(TournamentState state, String userId) {
        for (TournamentParticipant p : state.participants) {
            if (userId.equals(p.userId)) {
                return p.toMap();
            }
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> participantMaps(Object value) {
        if (!(value instanceof List<?>)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                out.add(new HashMap<>((Map<String, Object>) item));
            }
        }
        return out;
    }

    private static long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
