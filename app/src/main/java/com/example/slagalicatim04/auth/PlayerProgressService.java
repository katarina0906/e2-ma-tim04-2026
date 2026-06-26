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
        long scoreStars = Math.max(0, playerScore / SCORE_PER_STAR);
        long starDelta = winner ? WIN_BONUS_STARS + scoreStars : scoreStars - LOSS_PENALTY_STARS;
        long currentStars = longValue(snapshot, FIELD_STARS);
        long currentTotalStars = longValue(snapshot, FIELD_TOTAL_STARS);
        long currentTokens = longValue(snapshot, FIELD_TOKENS);

        long nextStarsBeforeTokens = Math.max(0, currentStars + starDelta);
        long nextTotalStars = Math.max(0, currentTotalStars + starDelta);
        long earnedTokens = nextStarsBeforeTokens / STARS_PER_TOKEN;
        long remainingStars = nextStarsBeforeTokens % STARS_PER_TOKEN;
        long nextTokens = currentTokens + earnedTokens;
        LeagueInfo league = LeagueInfo.forStars(nextTotalStars);
        String cycle = RegionRepository.currentCycle();
        long currentMonthlyStars = cycle.equals(snapshot.getString("monthlyStarsCycle"))
                ? longValue(snapshot, "monthlyStars")
                : 0L;
        long nextMonthlyStars = Math.max(0, currentMonthlyStars + starDelta);
        LeagueInfo previousLeague = LeagueInfo.forStars(currentTotalStars);
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);

        transaction.update(userRef,
                FIELD_STARS, remainingStars,
                FIELD_TOTAL_STARS, nextTotalStars,
                "overallStars", nextTotalStars,
                "monthlyStars", nextMonthlyStars,
                "monthlyStarsCycle", cycle,
                "league", league.name,
                "leagueLevel", league.level,
                "leagueIconRes", league.iconRes,
                FIELD_TOKENS, nextTokens);
        if (previousLeague.level != league.level) {
            transaction.set(userRef.collection("notifications")
                            .document("league_change_" + System.currentTimeMillis()),
                    LeagueNotificationData.create(previousLeague.name, league, nextTotalStars,
                            league.level > previousLeague.level));
        }

        return new RewardResult(starDelta, remainingStars, earnedTokens, nextTokens,
                nextTotalStars - currentTotalStars, nextTotalStars, league.level);
    }

    private long longValue(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? 0 : Math.max(0, value);
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
}
