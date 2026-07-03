package com.example.slagalicatim04.regions;

import com.google.firebase.Timestamp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RegionChallengeParticipant {
    public final String userId;
    public final String username;
    public final long score;
    public final boolean submitted;
    public final Timestamp submittedAt;
    public final long starsAwarded;
    public final long tokensAwarded;

    public RegionChallengeParticipant(String userId, String username, long score, boolean submitted,
                                      Timestamp submittedAt, long starsAwarded, long tokensAwarded) {
        this.userId = empty(userId);
        this.username = empty(username);
        this.score = Math.max(0L, score);
        this.submitted = submitted;
        this.submittedAt = submittedAt;
        this.starsAwarded = Math.max(0L, starsAwarded);
        this.tokensAwarded = Math.max(0L, tokensAwarded);
    }

    public static RegionChallengeParticipant fromMap(String userId, Object rawValue) {
        if (!(rawValue instanceof Map)) {
            return new RegionChallengeParticipant(userId, "", 0L, false,
                    null, 0L, 0L);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawValue;
        return new RegionChallengeParticipant(
                userId,
                stringValue(map.get("username")),
                longValue(map.get("score")),
                Boolean.TRUE.equals(map.get("submitted")),
                map.get("submittedAt") instanceof Timestamp ? (Timestamp) map.get("submittedAt") : null,
                longValue(map.get("starsAwarded")),
                longValue(map.get("tokensAwarded"))
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("username", username);
        map.put("score", score);
        map.put("submitted", submitted);
        map.put("submittedAt", submittedAt);
        map.put("starsAwarded", starsAwarded);
        map.put("tokensAwarded", tokensAwarded);
        return Collections.unmodifiableMap(map);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long longValue(Object value) {
        return value instanceof Number ? Math.max(0L, ((Number) value).longValue()) : 0L;
    }

    private static String empty(String value) {
        return value == null ? "" : value.trim();
    }
}
