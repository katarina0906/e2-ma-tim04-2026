package com.example.slagalicatim04.mynumber;

import com.example.slagalicatim04.auth.PlayerProgressService;
import com.example.slagalicatim04.auth.DailyMissionService;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.example.slagalicatim04.ranking.RankingCycle;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class MyNumberRepository {
    private static final String FRIEND_ROOM_PREFIX = "friend_";
    private static final int WIN_BONUS_STARS = 10;
    private static final int SCORE_PER_STAR = 40;

    public interface Listener {
        void onState(MyNumberMatchState state);
        void onError(Exception error);
    }

    private final DocumentReference matchRef;
    private final MyNumberGameService service = new MyNumberGameService();
    private final PlayerProgressService progressService;

    public MyNumberRepository(String roomId) {
        matchRef = FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(roomId);
        progressService = new PlayerProgressService(matchRef.getFirestore());
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
        final boolean[] friendlyMatchFinished = {false};
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
                    friendlyMatchFinished[0] = submitMissingAndScore(transaction, snapshot, state, updates,
                            state.getP1Result(), state.getP2Result());
                }
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> {
            if (friendlyMatchFinished[0]) {
                markFriendlyMissionForPlayer(player.getId());
            }
            awardTokensIfFinished();
        });
    }

    public void revealTarget(StepByStepPlayerSession player) {
        updateIfActive(player, "myNumberTargetShown", true);
    }

    public void revealNumbers(StepByStepPlayerSession player) {
        updateIfActive(player, "myNumberNumbersShown", true);
    }

    public void submit(StepByStepPlayerSession player, String expression) {
        final boolean[] friendlyMatchFinished = {false};
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
            boolean canResolveWithForfeit = !isEmpty(state.getForfeitedPlayerId())
                    && (p1Submitted || p2Submitted);
            if ((p1Submitted && p2Submitted) || canResolveWithForfeit) {
                friendlyMatchFinished[0] = submitMissingAndScore(
                        transaction, snapshot, state, updates, p1Result, p2Result);
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> {
            if (friendlyMatchFinished[0]) {
                markFriendlyMissionForPlayer(player.getId());
            }
            awardTokensIfFinished();
        });
    }

    public void resolveForfeitState(MyNumberMatchState state) {
        String forfeitedPlayerId = state.getForfeitedPlayerId();
        if (!state.isMyNumberGame() || isEmpty(forfeitedPlayerId)) {
            return;
        }
        int forfeitedPlayer = state.getPlayer1Id().equals(forfeitedPlayerId) ? 1
                : state.getPlayer2Id().equals(forfeitedPlayerId) ? 2 : 0;
        if (forfeitedPlayer == 0 || forfeitedPlayer != state.getActivePlayer()) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("myNumberActivePlayer", forfeitedPlayer == 1 ? 2 : 1);
        updates.put("myNumberStatusMessage",
                "Protivnik je napustio partiju. Nastavljas bez cekanja.");
        matchRef.set(updates, SetOptions.merge());
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
                updates.put("updatedAt", FieldValue.serverTimestamp());
                matchRef.set(updates, SetOptions.merge());
            }
        });
    }

    private boolean submitMissingAndScore(Transaction transaction, DocumentSnapshot snapshot,
                                          MyNumberMatchState state, Map<String, Object> updates,
                                          Integer p1, Integer p2)
            throws FirebaseFirestoreException {
        MyNumberGameService.RoundScore score = service.scoreRound(
                state.getTarget(), state.getActivePlayer(), p1, p2);
        long nextP1Score = state.getPlayer1Score() + score.p1Points;
        long nextP2Score = state.getPlayer2Score() + score.p2Points;
        updates.put("player1Score", nextP1Score);
        updates.put("player2Score", nextP2Score);
        updates.put("myNumberStatusMessage", score.message);
        updates.put(state.getRound() == 1 ? "myNumberRound1Result" : "myNumberRound2Result", score.message);
        int roundCount = state.isSoloChallenge() ? 1 : 2;
        if (state.getRound() < roundCount) {
            updates.putAll(newRoundState(2, nextP1Score, nextP2Score));
            return false;
        } else {
            updates.put("currentGame", "matchResult");
            updates.put("phase", "matchFinished");
            updates.put("myNumberStatusMessage",
                    score.message + " Moj broj i cela partija su zavrseni.");
            updates.put("statusMessage", "Partija je zavrsena.");
            updates.put("finished", true);
            applyMatchRewards(transaction, snapshot, state, updates, nextP1Score, nextP2Score);
            if (!isFriendlyMatch()
                    && !state.isSoloChallenge()
                    && !Boolean.TRUE.equals(snapshot.getBoolean("rankingRecorded"))) {
                updates.put("rankingRecorded", true);
                recordRanking(transaction, state, nextP1Score, nextP2Score);
            }
            return isFriendlyMatch();
        }
    }

    private void applyMatchRewards(Transaction transaction, DocumentSnapshot snapshot,
                                   MyNumberMatchState state, Map<String, Object> updates,
                                   long nextP1Score, long nextP2Score)
            throws FirebaseFirestoreException {
        if (Boolean.TRUE.equals(snapshot.getBoolean("matchRewardsApplied"))) {
            return;
        }
        if (isFriendlyMatch()) {
            updates.put("matchRewardsApplied", true);
            updates.put("player1StarDelta", 0L);
            updates.put("player2StarDelta", 0L);
            updates.put("player1EarnedTokens", 0L);
            updates.put("player2EarnedTokens", 0L);
            return;
        }
        if (isTournamentMatch(snapshot)) {
            applyTournamentRewards(transaction, snapshot, state, updates, nextP1Score, nextP2Score);
            return;
        }
        int winner = winner(nextP1Score, nextP2Score);
        DocumentSnapshot player1Snapshot = transaction.get(
                matchRef.getFirestore().collection("users").document(state.getPlayer1Id()));
        DocumentSnapshot player2Snapshot = transaction.get(
                matchRef.getFirestore().collection("users").document(state.getPlayer2Id()));
        PlayerProgressService.RewardResult p1Reward = state.isForfeited(state.getPlayer1Id())
                ? new PlayerProgressService.RewardResult(0, 0, 0, 0)
                : progressService.applyMatchRewards(
                transaction, state.getPlayer1Id(), player1Snapshot, nextP1Score,
                winner == 1 || state.isForfeited(state.getPlayer2Id()));
        PlayerProgressService.RewardResult p2Reward = state.isForfeited(state.getPlayer2Id())
                ? new PlayerProgressService.RewardResult(0, 0, 0, 0)
                : progressService.applyMatchRewards(
                transaction, state.getPlayer2Id(), player2Snapshot, nextP2Score,
                winner == 2 || state.isForfeited(state.getPlayer1Id()));
        updates.put("matchRewardsApplied", true);
        updates.put("player1StarDelta", p1Reward.starDelta);
        updates.put("player2StarDelta", p2Reward.starDelta);
        updates.put("player1Stars", p1Reward.remainingStars);
        updates.put("player2Stars", p2Reward.remainingStars);
        updates.put("player1EarnedTokens", p1Reward.earnedTokens);
        updates.put("player2EarnedTokens", p2Reward.earnedTokens);
    }

    public void markFriendlyMissionForPlayer(String userId) {
        if (!isFriendlyMatch() || isEmpty(userId)) {
            return;
        }
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference userRef = matchRef.getFirestore().collection("users").document(userId);
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            if (!userSnapshot.exists() || Boolean.TRUE.equals(userSnapshot.getBoolean("isGuest"))) {
                return null;
            }
            DailyMissionService.Reward reward = DailyMissionService.computeReward(
                    userSnapshot, DailyMissionService.Mission.PLAY_FRIENDLY_MATCH);
            if (reward.changed) {
                transaction.set(userRef, DailyMissionService.buildUserRewardUpdates(userSnapshot, reward),
                        SetOptions.merge());
            }
            return null;
        });
    }

    private void applyTournamentRewards(Transaction transaction, DocumentSnapshot snapshot,
                                        MyNumberMatchState state, Map<String, Object> updates,
                                        long nextP1Score, long nextP2Score)
            throws FirebaseFirestoreException {
        int winner = winner(nextP1Score, nextP2Score);
        String round = stringValue(snapshot.getString("tournamentRound"));
        DocumentSnapshot player1Snapshot = transaction.get(
                matchRef.getFirestore().collection("users").document(state.getPlayer1Id()));
        DocumentSnapshot player2Snapshot = transaction.get(
                matchRef.getFirestore().collection("users").document(state.getPlayer2Id()));
        PlayerProgressService.RewardResult p1Reward = new PlayerProgressService.RewardResult(0, 0, 0, 0);
        PlayerProgressService.RewardResult p2Reward = new PlayerProgressService.RewardResult(0, 0, 0, 0);
        long p1TokenBonus = 0L;
        long p2TokenBonus = 0L;
        long p1StarBonus = 0L;
        long p2StarBonus = 0L;

        if ("semifinal".equals(round)) {
            if (winner == 1) {
                p1Reward = progressService.applyMatchRewards(
                        transaction, state.getPlayer1Id(), player1Snapshot, nextP1Score, true);
                p1TokenBonus = 2L;
            } else if (winner == 2) {
                p2Reward = progressService.applyMatchRewards(
                        transaction, state.getPlayer2Id(), player2Snapshot, nextP2Score, true);
                p2TokenBonus = 2L;
            }
        } else if ("final".equals(round)) {
            p1Reward = progressService.applyMatchRewards(
                    transaction, state.getPlayer1Id(), player1Snapshot, nextP1Score, winner == 1);
            p2Reward = progressService.applyMatchRewards(
                    transaction, state.getPlayer2Id(), player2Snapshot, nextP2Score, winner == 2);
            if (winner == 1) {
                p1TokenBonus = 3L;
                p1StarBonus = 10L;
            } else if (winner == 2) {
                p2TokenBonus = 3L;
                p2StarBonus = 10L;
            }
        }

        applyBonus(transaction, state.getPlayer1Id(), p1StarBonus, p1TokenBonus);
        applyBonus(transaction, state.getPlayer2Id(), p2StarBonus, p2TokenBonus);
        updates.put("matchRewardsApplied", true);
        updates.put("player1StarDelta", p1Reward.starDelta + p1StarBonus);
        updates.put("player2StarDelta", p2Reward.starDelta + p2StarBonus);
        updates.put("player1Stars", p1Reward.remainingStars + p1StarBonus);
        updates.put("player2Stars", p2Reward.remainingStars + p2StarBonus);
        updates.put("player1EarnedTokens", p1Reward.earnedTokens + p1TokenBonus);
        updates.put("player2EarnedTokens", p2Reward.earnedTokens + p2TokenBonus);
    }

    private void applyBonus(Transaction transaction, String userId, long stars, long tokens) {
        if (isEmpty(userId) || (stars <= 0 && tokens <= 0)) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        if (stars > 0) {
            updates.put("stars", FieldValue.increment(stars));
            updates.put("totalStars", FieldValue.increment(stars));
            updates.put("overallStars", FieldValue.increment(stars));
            updates.put("monthlyStars", FieldValue.increment(stars));
        }
        if (tokens > 0) {
            updates.put("tokens", FieldValue.increment(tokens));
        }
        transaction.set(matchRef.getFirestore().collection("users").document(userId),
                updates, SetOptions.merge());
    }

    private int winner(long p1Score, long p2Score) {
        if (p1Score > p2Score) return 1;
        if (p2Score > p1Score) return 2;
        return 0;
    }

    private void recordRanking(Transaction transaction, MyNumberMatchState state,
                               long p1Score, long p2Score) {
        int winner = winner(p1Score, p2Score);
        long p1Stars = cycleStars(p1Score, winner == 1);
        long p2Stars = cycleStars(p2Score, winner == 2);
        RankingCycle weekly = RankingCycle.current(RankingCycle.WEEKLY);
        RankingCycle monthly = RankingCycle.current(RankingCycle.MONTHLY);
        writeCycle(transaction, weekly, state.getPlayer1Id(), state.getPlayer1Name(), p1Stars);
        writeCycle(transaction, weekly, state.getPlayer2Id(), state.getPlayer2Name(), p2Stars);
        writeCycle(transaction, monthly, state.getPlayer1Id(), state.getPlayer1Name(), p1Stars);
        writeCycle(transaction, monthly, state.getPlayer2Id(), state.getPlayer2Name(), p2Stars);
    }

    private void writeCycle(Transaction transaction, RankingCycle cycle, String userId,
                            String username, long stars) {
        if (isEmpty(userId)) {
            return;
        }
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
        entryData.put("username", isEmpty(username) ? "Igrac" : username);
        entryData.put("leagueIcon", "*");
        entryData.put("leagueName", "Liga");
        entryData.put("played", true);
        entryData.put("stars", FieldValue.increment(Math.max(0L, stars)));
        entryData.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(cycleRef.collection("entries").document(userId), entryData, SetOptions.merge());
    }

    private long cycleStars(long score, boolean winner) {
        long scoreStars = Math.max(0L, score / SCORE_PER_STAR);
        return scoreStars + (winner ? WIN_BONUS_STARS : 0L);
    }

    public void awardTokensIfFinished() {
        matchRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()
                    || isFriendlyMatch()
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
            if (isEmpty(winnerId)) {
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
            transaction.set(userRef.collection("notifications")
                            .document("ranking_reward_" + weekly.id + "_" + monthly.id),
                    rewardNotificationData(
                            weeklyAlreadyClaimed ? 0 : weeklyReward,
                            monthlyAlreadyClaimed ? 0 : monthlyReward,
                            tokens),
                    SetOptions.merge());
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

    private Map<String, Object> rewardNotificationData(long weeklyTokens,
                                                       long monthlyTokens,
                                                       long totalTokens) {
        Map<String, String> data = new HashMap<>();
        data.put("weeklyTokens", String.valueOf(weeklyTokens));
        data.put("monthlyTokens", String.valueOf(monthlyTokens));
        data.put("totalTokens", String.valueOf(totalTokens));

        Map<String, Object> notification = new HashMap<>();
        notification.put("category", "ranking");
        notification.put("title", "Nagrada rang liste");
        notification.put("message", rewardMessage(weeklyTokens, monthlyTokens, totalTokens));
        notification.put("action", "rewards_claim");
        notification.put("targetId", "ranking_rewards");
        notification.put("data", data);
        notification.put("source", "ranking_rewards");
        notification.put("read", false);
        notification.put("readAt", null);
        notification.put("actionedAt", null);
        notification.put("createdAt", FieldValue.serverTimestamp());
        return notification;
    }

    private String rewardMessage(long weeklyTokens, long monthlyTokens, long totalTokens) {
        if (weeklyTokens > 0 && monthlyTokens > 0) {
            return "Osvojio si " + totalTokens + " tokena na nedeljnoj i mesecnoj rang listi.";
        }
        if (weeklyTokens > 0) {
            return "Osvojio si " + weeklyTokens + " tokena na nedeljnoj rang listi.";
        }
        return "Osvojio si " + monthlyTokens + " tokena na mesecnoj rang listi.";
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
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private int playerNumber(DocumentSnapshot snapshot, String playerId) {
        if (playerId.equals(snapshot.getString("player1Id"))) return 1;
        if (playerId.equals(snapshot.getString("player2Id"))) return 2;
        return 0;
    }

    private boolean isFriendlyMatch() {
        return matchRef.getId().startsWith(FRIEND_ROOM_PREFIX);
    }

    private boolean isTournamentMatch(DocumentSnapshot snapshot) {
        return matchRef.getId().startsWith("tournament_")
                || !isEmpty(snapshot.getString("tournamentId"));
    }

    private String stringValue(String value) {
        return value == null ? "" : value;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
