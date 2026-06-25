package com.example.slagalicatim04.auth;

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

        long scoreStars = Math.max(0, playerScore / SCORE_PER_STAR);
        long starDelta = winner ? WIN_BONUS_STARS + scoreStars : scoreStars - LOSS_PENALTY_STARS;
        long currentStars = longValue(snapshot, FIELD_STARS);
        long currentTokens = longValue(snapshot, FIELD_TOKENS);

        long nextStarsBeforeTokens = Math.max(0, currentStars + starDelta);
        long earnedTokens = nextStarsBeforeTokens / STARS_PER_TOKEN;
        long remainingStars = nextStarsBeforeTokens % STARS_PER_TOKEN;
        long nextTokens = currentTokens + earnedTokens;

        transaction.update(userRef,
                FIELD_STARS, remainingStars,
                FIELD_TOKENS, nextTokens);

        return new RewardResult(starDelta, remainingStars, earnedTokens, nextTokens);
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

        public RewardResult(long starDelta, long remainingStars, long earnedTokens, long totalTokens) {
            this.starDelta = starDelta;
            this.remainingStars = remainingStars;
            this.earnedTokens = earnedTokens;
            this.totalTokens = totalTokens;
        }
    }
}
