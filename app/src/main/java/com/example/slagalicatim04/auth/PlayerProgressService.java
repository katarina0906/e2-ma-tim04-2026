package com.example.slagalicatim04.auth;

import com.example.slagalicatim04.leagues.LeagueInfo;
import com.example.slagalicatim04.notifications.LeagueNotificationData;
import com.example.slagalicatim04.regions.RegionRepository;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

public class PlayerProgressService {
    public static final int WIN_BONUS_STARS = 10;
    public static final int LOSS_PENALTY_STARS = 10;
    public static final int SCORE_PER_STAR = 40;
    public static final int STARS_PER_TOKEN = 50;

    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_STARS = "stars";
    private static final String FIELD_TOTAL_STARS = "totalStars";
    private static final String FIELD_TOKENS = "tokens";

    private final FirebaseFirestore firestore;

    public PlayerProgressService(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public RewardResult applyMatchRewards(Transaction transaction, String userId,
                                          long playerScore, boolean winner)
            throws FirebaseFirestoreException {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentSnapshot snapshot = transaction.get(userRef);
        return applyMatchRewards(transaction, userId, snapshot, playerScore, winner);
    }

    public RewardResult applyMatchRewards(Transaction transaction, String userId,
                                          DocumentSnapshot snapshot,
                                          long playerScore, boolean winner) {
        long currentStars = longValue(snapshot, FIELD_STARS);
        long currentTotalStars = firstLongValue(snapshot, FIELD_TOTAL_STARS, "overallStars", FIELD_STARS);
        long currentTokens = longValue(snapshot, FIELD_TOKENS);
        RewardComputation computation = computeReward(playerScore, winner, currentStars, currentTotalStars,
                currentTokens);

        LeagueInfo league = LeagueInfo.forStars(computation.nextTotalStars);
        String cycle = RegionRepository.currentCycle();
        long currentMonthlyStars = cycle.equals(snapshot.getString("monthlyStarsCycle"))
                ? longValue(snapshot, "monthlyStars")
                : 0L;
        long nextMonthlyStars = Math.max(0, currentMonthlyStars + computation.starDelta);
        LeagueInfo previousLeague = LeagueInfo.forStars(currentTotalStars);
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);

        transaction.update(userRef,
                FIELD_STARS, computation.remainingStars,
                FIELD_TOTAL_STARS, computation.nextTotalStars,
                "overallStars", computation.nextTotalStars,
                "monthlyStars", nextMonthlyStars,
                "monthlyStarsCycle", cycle,
                "league", league.name,
                "leagueLevel", league.level,
                "leagueIconRes", league.iconRes,
                FIELD_TOKENS, computation.nextTokens);
        if (previousLeague.level != league.level) {
            transaction.set(userRef.collection("notifications")
                            .document("league_change_" + System.currentTimeMillis()),
                    LeagueNotificationData.create(previousLeague.name, league, computation.nextTotalStars,
                            league.level > previousLeague.level));
        }

        return new RewardResult(computation.starDelta, computation.remainingStars,
                computation.earnedTokens, computation.nextTokens,
                computation.nextTotalStars - currentTotalStars, computation.nextTotalStars, league.level);
    }

    static RewardComputation computeReward(long playerScore, boolean winner,
                                           long currentStars, long currentTotalStars,
                                           long currentTokens) {
        long scoreStars = Math.max(0, playerScore / SCORE_PER_STAR);
        long starDelta = winner ? WIN_BONUS_STARS + scoreStars : scoreStars - LOSS_PENALTY_STARS;
        long nextStarsBeforeTokens = Math.max(0, currentStars + starDelta);
        long nextTotalStars = Math.max(0, currentTotalStars + starDelta);
        long earnedTokens = nextStarsBeforeTokens / STARS_PER_TOKEN;
        long remainingStars = nextStarsBeforeTokens % STARS_PER_TOKEN;
        long nextTokens = Math.max(0, currentTokens) + earnedTokens;
        return new RewardComputation(starDelta, remainingStars, earnedTokens, nextTokens, nextTotalStars);
    }

    private long longValue(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? 0 : Math.max(0, value);
    }

    private long firstLongValue(DocumentSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Long value = snapshot.getLong(key);
            if (value != null) {
                return Math.max(0, value);
            }
        }
        return 0L;
    }

    public static final class RewardResult {
        public final long starDelta;
        public final long remainingStars;
        public final long earnedTokens;
        public final long totalTokens;
        public final long collectedStars;
        public final long totalStars;
        public final int leagueLevel;

        public RewardResult(long starDelta, long remainingStars, long earnedTokens, long totalTokens) {
            this(starDelta, remainingStars, earnedTokens, totalTokens, 0, 0, 0);
        }

        public RewardResult(long starDelta, long remainingStars, long earnedTokens,
                            long totalTokens, long collectedStars, long totalStars,
                            int leagueLevel) {
            this.starDelta = starDelta;
            this.remainingStars = remainingStars;
            this.earnedTokens = earnedTokens;
            this.totalTokens = totalTokens;
            this.collectedStars = collectedStars;
            this.totalStars = totalStars;
            this.leagueLevel = leagueLevel;
        }
    }

    static final class RewardComputation {
        final long starDelta;
        final long remainingStars;
        final long earnedTokens;
        final long nextTokens;
        final long nextTotalStars;

        RewardComputation(long starDelta, long remainingStars, long earnedTokens,
                          long nextTokens, long nextTotalStars) {
            this.starDelta = starDelta;
            this.remainingStars = remainingStars;
            this.earnedTokens = earnedTokens;
            this.nextTokens = nextTokens;
            this.nextTotalStars = nextTotalStars;
        }
    }
}
