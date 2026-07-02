package com.example.slagalicatim04.ranking;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RankingRewardSynchronizer {
    public interface Callback {
        void onDone();
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void syncCurrentRewards(String userId, Callback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onDone();
            return;
        }
        RankingCycle weekly = RankingCycle.current(RankingCycle.WEEKLY);
        RankingCycle monthly = RankingCycle.current(RankingCycle.MONTHLY);
        AtomicInteger pending = new AtomicInteger(2);
        AtomicLong weeklyReward = new AtomicLong(0);
        AtomicLong monthlyReward = new AtomicLong(0);
        AtomicLong weeklyStars = new AtomicLong(0);
        AtomicLong monthlyStars = new AtomicLong(0);
        loadCurrentCycleStats(weekly, userId, stats -> {
            weeklyReward.set(stats.reward);
            weeklyStars.set(stats.stars);
            if (pending.decrementAndGet() == 0) {
                applyCombinedRewards(userId, weekly, weeklyReward.get(),
                        weeklyStars.get(), monthly, monthlyReward.get(),
                        monthlyStars.get(), callback);
            }
        });
        loadCurrentCycleStats(monthly, userId, stats -> {
            monthlyReward.set(stats.reward);
            monthlyStars.set(stats.stars);
            if (pending.decrementAndGet() == 0) {
                applyCombinedRewards(userId, weekly, weeklyReward.get(),
                        weeklyStars.get(), monthly, monthlyReward.get(),
                        monthlyStars.get(), callback);
            }
        });
    }

    private void loadCurrentCycleStats(RankingCycle cycle, String userId, StatsCallback callback) {
        firestore.collection("rankingCycles").document(cycle.id)
                .collection("entries")
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int rank = 1;
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (userId.equals(document.getId())) {
                            callback.onStats(new CycleStats(
                                    longValue(document, "stars"),
                                    rewardForRank(cycle.type, rank)));
                            return;
                        }
                        rank++;
                    }
                    callback.onStats(new CycleStats(0, 0));
                })
                .addOnFailureListener(ignored -> callback.onStats(new CycleStats(0, 0)));
    }

    private void applyCombinedRewards(String userId, RankingCycle weekly, long weeklyReward,
                                      long weeklyStars, RankingCycle monthly, long monthlyReward,
                                      long monthlyStars, Callback callback) {
        long currentRewardTotal = weeklyReward + monthlyReward;
        long currentStarsTotal = weeklyStars + monthlyStars;
        if (currentRewardTotal <= 0 && currentStarsTotal <= 0) {
            callback.onDone();
            return;
        }
        DocumentReference userRef = firestore.collection("users").document(userId);
        DocumentReference weeklyClaim = userRef.collection("rewardClaims").document(weekly.id);
        DocumentReference monthlyClaim = userRef.collection("rewardClaims").document(monthly.id);
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            DocumentSnapshot weeklyClaimDoc = transaction.get(weeklyClaim);
            DocumentSnapshot monthlyClaimDoc = transaction.get(monthlyClaim);
            long currentTokens = longValue(user, "tokens");
            long currentStars = longValue(user, "totalStars");
            long weeklyClaimed = longValue(weeklyClaimDoc, "tokens");
            long monthlyClaimed = longValue(monthlyClaimDoc, "tokens");
            long claimDelta = Math.max(0, weeklyReward - weeklyClaimed)
                    + Math.max(0, monthlyReward - monthlyClaimed);
            long profileFloorDelta = Math.max(0, currentRewardTotal - currentTokens - claimDelta);
            long tokenDelta = claimDelta + profileFloorDelta;
            long starDelta = Math.max(0, currentStarsTotal - currentStars);
            if (tokenDelta > 0 || starDelta > 0) {
                Map<String, Object> userUpdates = new HashMap<>();
                if (tokenDelta > 0) {
                    userUpdates.put("tokens", FieldValue.increment(tokenDelta));
                }
                if (starDelta > 0) {
                    userUpdates.put("totalStars", FieldValue.increment(starDelta));
                }
                userUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.set(userRef, userUpdates, SetOptions.merge());
            }
            if (weeklyReward > weeklyClaimed) {
                transaction.set(weeklyClaim, claimData(weekly, weeklyReward), SetOptions.merge());
            }
            if (monthlyReward > monthlyClaimed) {
                transaction.set(monthlyClaim, claimData(monthly, monthlyReward), SetOptions.merge());
            }
            return null;
        }).addOnCompleteListener(ignored -> callback.onDone());
    }

    private Map<String, Object> claimData(RankingCycle cycle, long tokens) {
        Map<String, Object> data = new HashMap<>();
        data.put("cycleId", cycle.id);
        data.put("cycleType", cycle.type);
        data.put("tokens", tokens);
        data.put("claimedAt", FieldValue.serverTimestamp());
        data.put("source", "client_sync");
        return data;
    }

    public static int rewardForRank(String cycleType, int rank) {
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

    private static final class CycleStats {
        final long stars;
        final long reward;

        CycleStats(long stars, long reward) {
            this.stars = stars;
            this.reward = reward;
        }
    }

    private interface StatsCallback {
        void onStats(CycleStats stats);
    }
}
