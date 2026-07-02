package com.example.slagalicatim04.mynumber;

import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.example.slagalicatim04.ranking.RankingCycle;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class MyNumberRepository {
    public interface Listener {
        void onState(MyNumberMatchState state);
        void onError(Exception error);
    }

    private final DocumentReference matchRef;
    private final MyNumberGameService service = new MyNumberGameService();

    public MyNumberRepository(String roomId) {
        matchRef = FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(roomId);
    }

    public ListenerRegistration listen(Listener listener) {
        return matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                listener.onState(new MyNumberMatchState(snapshot));
            }
        });
    }

    public void startIfNeeded() {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) return null;
            String phase = snapshot.getString("phase");
            if (MyNumberMatchState.GAME.equals(snapshot.getString("currentGame"))
                    && "myNumberSetup".equals(phase)) {
                transaction.set(matchRef,
                        newRoundState(1, snapshot.getLong("player1Score"), snapshot.getLong("player2Score")),
                        SetOptions.merge());
            }
            return null;
        });
    }

    public void tick(StepByStepPlayerSession player) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) return null;
            MyNumberMatchState state = new MyNumberMatchState(snapshot);
            int myPlayer = playerNumber(snapshot, player.getId());
            if (!state.isMyNumberGame()
                    || myPlayer != state.getActivePlayer()
                    || MyNumberGameService.PHASE_FINISHED.equals(state.getPhase())) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            if (!state.isTargetShown()) {
                int left = Math.max(0, state.getTargetRevealLeft() - 1);
                updates.put("myNumberTargetRevealLeft", left);
                if (left == 0) updates.put("myNumberTargetShown", true);
            } else if (!state.isNumbersShown()) {
                int left = Math.max(0, state.getNumbersRevealLeft() - 1);
                updates.put("myNumberNumbersRevealLeft", left);
                if (left == 0) updates.put("myNumberNumbersShown", true);
            } else {
                int left = Math.max(0, state.getSecondsLeft() - 1);
                updates.put("myNumberSecondsLeft", left);
                if (left == 0) {
                    submitMissingAndScore(transaction, state, updates,
                            state.getP1Result(), state.getP2Result());
                }
            }
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> awardTokensIfFinished());
    }

    public void revealTarget(StepByStepPlayerSession player) {
        updateIfActive(player, "myNumberTargetShown", true);
    }

    public void revealNumbers(StepByStepPlayerSession player) {
        updateIfActive(player, "myNumberNumbersShown", true);
    }

    public void submit(StepByStepPlayerSession player, String expression) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) return null;
            MyNumberMatchState state = new MyNumberMatchState(snapshot);
            int myPlayer = playerNumber(snapshot, player.getId());
            if (!state.isMyNumberGame() || myPlayer == 0 || state.isSubmitted(myPlayer)) {
                return null;
            }
            Integer result;
            try {
                result = service.evaluate(expression, state.getNumbers());
            } catch (Exception ignored) {
                result = null;
            }
            Map<String, Object> updates = new HashMap<>();
            String prefix = myPlayer == 1 ? "myNumberP1" : "myNumberP2";
            updates.put(prefix + "Submitted", true);
            updates.put(prefix + "Expression", expression == null ? "" : expression);
            updates.put(prefix + "Result", result);
            boolean p1Submitted = myPlayer == 1 || state.isP1Submitted();
            boolean p2Submitted = myPlayer == 2 || state.isP2Submitted();
            Integer p1Result = myPlayer == 1 ? result : state.getP1Result();
            Integer p2Result = myPlayer == 2 ? result : state.getP2Result();
            if (p1Submitted && p2Submitted) {
                submitMissingAndScore(transaction, state, updates, p1Result, p2Result);
            }
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> awardTokensIfFinished());
    }

    private void updateIfActive(StepByStepPlayerSession player, String field, Object value) {
        matchRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;
            MyNumberMatchState state = new MyNumberMatchState(snapshot);
            if (state.isMyNumberGame()
                    && playerNumber(snapshot, player.getId()) == state.getActivePlayer()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(field, value);
                if ("myNumberTargetShown".equals(field)) updates.put("myNumberTargetRevealLeft", 0);
                if ("myNumberNumbersShown".equals(field)) updates.put("myNumberNumbersRevealLeft", 0);
                matchRef.set(updates, SetOptions.merge());
            }
        });
    }

    private void submitMissingAndScore(Transaction transaction, MyNumberMatchState state,
                                       Map<String, Object> updates, Integer p1, Integer p2) {
        MyNumberGameService.RoundScore score = service.scoreRound(
                state.getTarget(), state.getActivePlayer(), p1, p2);
        long nextP1Score = state.getPlayer1Score() + score.p1Points;
        long nextP2Score = state.getPlayer2Score() + score.p2Points;
        updates.put("player1Score", nextP1Score);
        updates.put("player2Score", nextP2Score);
        updates.put("myNumberStatusMessage", score.message);
        updates.put(state.getRound() == 1 ? "myNumberRound1Result" : "myNumberRound2Result", score.message);
        if (state.getRound() == 1) {
            updates.putAll(newRoundState(2, nextP1Score, nextP2Score));
        } else {
            updates.put("currentGame", "matchResult");
            updates.put("phase", "matchFinished");
            updates.put("myNumberStatusMessage",
                    score.message + " Moj broj i cela partija su zavrseni.");
            updates.put("statusMessage", "Partija je zavrsena.");
            updates.put("finished", true);
            if (!state.isRankingRecorded()) {
                updates.put("rankingRecorded", true);
                recordRanking(transaction, state, nextP1Score, nextP2Score);
            }
        }
    }

    private void recordRanking(Transaction transaction, MyNumberMatchState state,
                               long p1Score, long p2Score) {
        int winner = p1Score > p2Score ? 1 : p2Score > p1Score ? 2 : 0;
        if (winner == 0) {
            return;
        }
        String winnerId = winner == 1 ? state.getPlayer1Id() : state.getPlayer2Id();
        String winnerName = winner == 1 ? state.getPlayer1Name() : state.getPlayer2Name();
        Map<String, Object> userTotals = new HashMap<>();
        userTotals.put("totalStars", FieldValue.increment(1));
        userTotals.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(matchRef.getFirestore().collection("users").document(winnerId),
                userTotals, SetOptions.merge());
        writeCycle(transaction, RankingCycle.current(RankingCycle.WEEKLY), winnerId, winnerName);
        writeCycle(transaction, RankingCycle.current(RankingCycle.MONTHLY), winnerId, winnerName);
    }

    private void writeCycle(Transaction transaction, RankingCycle cycle, String userId,
                            String username) {
        DocumentReference cycleRef = matchRef.getFirestore()
                .collection("rankingCycles").document(cycle.id);
        Map<String, Object> cycleData = new HashMap<>();
        cycleData.put("type", cycle.type);
        cycleData.put("startAt", new Timestamp(cycle.startAt));
        cycleData.put("endAt", new Timestamp(cycle.endAt));
        cycleData.put("rewardsDistributed", false);
        cycleData.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(cycleRef, cycleData, SetOptions.merge());

        Map<String, Object> entryData = new HashMap<>();
        entryData.put("userId", userId);
        entryData.put("username", username == null || username.isEmpty() ? "Igrac" : username);
        entryData.put("leagueIcon", "🏆");
        entryData.put("leagueName", "Zlatna liga");
        entryData.put("played", true);
        entryData.put("stars", FieldValue.increment(1));
        entryData.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(cycleRef.collection("entries").document(userId), entryData, SetOptions.merge());
    }

    public void awardTokensIfFinished() {
        matchRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()
                    || !Boolean.TRUE.equals(snapshot.getBoolean("finished"))
                    || Boolean.TRUE.equals(snapshot.getBoolean("clientTokensAwarded"))) {
                return;
            }
            long p1Score = longValue(snapshot, "player1Score");
            long p2Score = longValue(snapshot, "player2Score");
            String winnerId;
            if (p1Score > p2Score) {
                winnerId = snapshot.getString("player1Id");
            } else if (p2Score > p1Score) {
                winnerId = snapshot.getString("player2Id");
            } else {
                return;
            }
            if (winnerId == null || winnerId.isEmpty()) {
                return;
            }
            awardTokensForCycles(winnerId);
        });
    }

    private void awardTokensForCycles(String winnerId) {
        RankingCycle weekly = RankingCycle.current(RankingCycle.WEEKLY);
        RankingCycle monthly = RankingCycle.current(RankingCycle.MONTHLY);
        loadCycleReward(weekly, winnerId, weeklyReward ->
                loadCycleReward(monthly, winnerId, monthlyReward -> {
                    long totalReward = weeklyReward + monthlyReward;
                    if (totalReward <= 0) {
                        markClientTokensAwarded();
                    } else {
                        grantClientTokens(winnerId, weekly, weeklyReward, monthly, monthlyReward);
                    }
                }));
    }

    private void loadCycleReward(RankingCycle cycle, String winnerId, RewardCallback callback) {
        matchRef.getFirestore().collection("rankingCycles").document(cycle.id)
                .collection("entries")
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int rank = 1;
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (winnerId.equals(document.getId())) {
                            callback.onReward(rewardForRank(cycle.type, rank));
                            return;
                        }
                        rank++;
                    }
                    callback.onReward(0);
                })
                .addOnFailureListener(ignored -> callback.onReward(0));
    }

    private void grantClientTokens(String winnerId, RankingCycle weekly, long weeklyReward,
                                   RankingCycle monthly, long monthlyReward) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()
                    || Boolean.TRUE.equals(snapshot.getBoolean("clientTokensAwarded"))) {
                return null;
            }
            DocumentReference userRef = matchRef.getFirestore().collection("users").document(winnerId);
            DocumentReference weeklyClaim = userRef.collection("rewardClaims").document(weekly.id);
            DocumentReference monthlyClaim = userRef.collection("rewardClaims").document(monthly.id);
            boolean weeklyAlreadyClaimed = transaction.get(weeklyClaim).exists();
            boolean monthlyAlreadyClaimed = transaction.get(monthlyClaim).exists();
            long tokens = (weeklyAlreadyClaimed ? 0 : weeklyReward)
                    + (monthlyAlreadyClaimed ? 0 : monthlyReward);
            transaction.set(matchRef, Map.of(
                    "clientTokensAwarded", true,
                    "clientTokensAwardedAt", FieldValue.serverTimestamp(),
                    "clientTokensAwardedCount", tokens
            ), SetOptions.merge());
            if (tokens <= 0) {
                return null;
            }
            transaction.set(userRef,
                    Map.of(
                            "tokens", FieldValue.increment(tokens),
                            "updatedAt", FieldValue.serverTimestamp()
                    ), SetOptions.merge());
            if (!weeklyAlreadyClaimed && weeklyReward > 0) {
                transaction.set(weeklyClaim, rewardClaimData(weekly, weeklyReward));
            }
            if (!monthlyAlreadyClaimed && monthlyReward > 0) {
                transaction.set(monthlyClaim, rewardClaimData(monthly, monthlyReward));
            }
            return null;
        });
    }

    private Map<String, Object> rewardClaimData(RankingCycle cycle, long tokens) {
        Map<String, Object> data = new HashMap<>();
        data.put("cycleId", cycle.id);
        data.put("cycleType", cycle.type);
        data.put("tokens", tokens);
        data.put("claimedAt", FieldValue.serverTimestamp());
        data.put("source", "client");
        return data;
    }

    private void markClientTokensAwarded() {
        matchRef.set(Map.of(
                "clientTokensAwarded", true,
                "clientTokensAwardedAt", FieldValue.serverTimestamp(),
                "clientTokensAwardedCount", 0
        ), SetOptions.merge());
    }

    private static int rewardForRank(String cycleType, int rank) {
        boolean monthly = RankingCycle.MONTHLY.equals(cycleType);
        if (rank == 1) return monthly ? 10 : 5;
        if (rank == 2) return monthly ? 6 : 3;
        if (rank == 3) return monthly ? 4 : 2;
        if (rank >= 4 && rank <= 10) return monthly ? 2 : 1;
        return 0;
    }

    private static long longValue(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? 0 : value;
    }

    private interface RewardCallback {
        void onReward(long reward);
    }

    private Map<String, Object> newRoundState(int round, Long p1Score, Long p2Score) {
        Map<String, Object> state = new HashMap<>();
        state.put("currentGame", MyNumberMatchState.GAME);
        state.put("phase", round == 1 ? MyNumberGameService.PHASE_ROUND1 : MyNumberGameService.PHASE_ROUND2);
        state.put("myNumberRound", round);
        state.put("myNumberActivePlayer", service.activePlayerForRound(round));
        state.put("myNumberTarget", service.generateTarget());
        state.put("myNumberNumbers", service.generateNumbers());
        state.put("myNumberSecondsLeft", MyNumberGameService.ROUND_SECONDS);
        state.put("myNumberTargetRevealLeft", MyNumberGameService.REVEAL_SECONDS);
        state.put("myNumberNumbersRevealLeft", MyNumberGameService.REVEAL_SECONDS);
        state.put("myNumberTargetShown", false);
        state.put("myNumberNumbersShown", false);
        state.put("myNumberP1Submitted", false);
        state.put("myNumberP2Submitted", false);
        state.put("myNumberP1Expression", "");
        state.put("myNumberP2Expression", "");
        state.put("myNumberP1Result", null);
        state.put("myNumberP2Result", null);
        state.put("myNumberStatusMessage", "Moj broj - runda " + round);
        if (p1Score != null) state.put("player1Score", p1Score);
        if (p2Score != null) state.put("player2Score", p2Score);
        return state;
    }

    private int playerNumber(DocumentSnapshot snapshot, String playerId) {
        if (playerId.equals(snapshot.getString("player1Id"))) return 1;
        if (playerId.equals(snapshot.getString("player2Id"))) return 2;
        return 0;
    }
}
