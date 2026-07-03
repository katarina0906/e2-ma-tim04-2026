package com.example.slagalicatim04.auth;

import com.example.slagalicatim04.leagues.LeagueInfo;
import com.example.slagalicatim04.regions.RegionRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DailyMissionService {
    public static final int STARS_PER_MISSION = 3;
    public static final int ALL_MISSIONS_BONUS_STARS = 3;
    public static final int ALL_MISSIONS_BONUS_TOKENS = 2;

    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_DATE = "dailyMissionsDate";
    private static final String FIELD_MISSIONS = "dailyMissions";
    private static final String FIELD_ALL_BONUS_CLAIMED = "dailyMissionsAllBonusClaimed";
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getDefault());
    }

    public enum Mission {
        WIN_MATCH("winMatch", "Pobedi partiju"),
        SEND_CHAT_MESSAGE("sendChatMessage", "Posalji poruku u cet"),
        PLAY_FRIENDLY_MATCH("playFriendlyMatch", "Odigraj prijateljsku partiju"),
        WIN_TOURNAMENT_MATCH("winTournamentMatch", "Pobedi partiju u turniru");

        public final String id;
        public final String title;

        Mission(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public interface StatusListener {
        void onStatus(Status status);

        void onError(Exception error);
    }

    private final FirebaseFirestore firestore;

    public DailyMissionService(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public Task<Void> markCompleted(String userId, Mission mission) {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        return firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(userRef);
            Reward reward = computeReward(snapshot, mission);
            if (!reward.changed) {
                return null;
            }
            transaction.set(userRef, buildUserRewardUpdates(snapshot, reward), SetOptions.merge());
            return null;
        });
    }

    public void loadStatus(String userId, StatusListener listener) {
        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(snapshot -> listener.onStatus(statusFrom(snapshot)))
                .addOnFailureListener(listener::onError);
    }

    public static Reward computeReward(DocumentSnapshot snapshot, Mission mission) {
        return computeReward(today(), missionMap(snapshot), bonusClaimed(snapshot), mission);
    }

    public static Reward noReward(DocumentSnapshot snapshot) {
        return Reward.unchanged(today(), missionMap(snapshot), bonusClaimed(snapshot));
    }

    static Reward computeReward(String date, Map<String, Boolean> currentMissions,
                                boolean allBonusClaimed, Mission mission) {
        Map<String, Boolean> missions = new LinkedHashMap<>();
        for (Mission item : Mission.values()) {
            missions.put(item.id, currentMissions.getOrDefault(item.id, false));
        }
        if (Boolean.TRUE.equals(missions.get(mission.id))) {
            return Reward.unchanged(date, missions, allBonusClaimed);
        }

        missions.put(mission.id, true);
        int stars = STARS_PER_MISSION;
        int tokens = 0;
        boolean nextBonusClaimed = allBonusClaimed;
        if (allDone(missions) && !allBonusClaimed) {
            stars += ALL_MISSIONS_BONUS_STARS;
            tokens += ALL_MISSIONS_BONUS_TOKENS;
            nextBonusClaimed = true;
        }
        return new Reward(true, date, missions, nextBonusClaimed, stars, tokens);
    }

    public static Map<String, Object> buildUserRewardUpdates(DocumentSnapshot snapshot,
                                                             Reward reward) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_DATE, reward.date);
        updates.put(FIELD_MISSIONS, reward.missions);
        updates.put(FIELD_ALL_BONUS_CLAIMED, reward.allBonusClaimed);
        if (!reward.changed) {
            return updates;
        }

        long currentStars = longValue(snapshot, "stars");
        long currentTotalStars = firstLongValue(snapshot, "totalStars", "overallStars", "stars");
        long currentTokens = longValue(snapshot, "tokens");
        long nextStarsBeforeTokens = currentStars + reward.starDelta;
        long earnedTokens = nextStarsBeforeTokens / PlayerProgressService.STARS_PER_TOKEN;
        long remainingStars = nextStarsBeforeTokens % PlayerProgressService.STARS_PER_TOKEN;
        long nextTokens = currentTokens + earnedTokens + reward.tokenDelta;
        long nextTotalStars = currentTotalStars + reward.starDelta;

        putProgressUpdates(snapshot, updates, remainingStars, nextTotalStars, nextTokens,
                reward.starDelta);
        return updates;
    }

    public static Map<String, Object> buildMissionUpdates(DocumentSnapshot snapshot,
                                                          Mission mission,
                                                          long currentStars,
                                                          long currentTotalStars,
                                                          long currentTokens) {
        Reward reward = computeReward(snapshot, mission);
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_DATE, reward.date);
        updates.put(FIELD_MISSIONS, reward.missions);
        updates.put(FIELD_ALL_BONUS_CLAIMED, reward.allBonusClaimed);
        if (!reward.changed) {
            return updates;
        }
        long nextStarsBeforeTokens = currentStars + reward.starDelta;
        long earnedTokens = nextStarsBeforeTokens / PlayerProgressService.STARS_PER_TOKEN;
        long remainingStars = nextStarsBeforeTokens % PlayerProgressService.STARS_PER_TOKEN;
        long nextTokens = currentTokens + earnedTokens + reward.tokenDelta;
        long nextTotalStars = currentTotalStars + reward.starDelta;
        putProgressUpdates(snapshot, updates, remainingStars, nextTotalStars, nextTokens,
                reward.starDelta);
        return updates;
    }

    public static Status statusFrom(DocumentSnapshot snapshot) {
        return new Status(missionMap(snapshot), bonusClaimed(snapshot));
    }

    private static void putProgressUpdates(DocumentSnapshot snapshot, Map<String, Object> updates,
                                           long stars, long totalStars, long tokens,
                                           long monthlyStarDelta) {
        updates.put("stars", Math.max(0L, stars));
        updates.put("totalStars", Math.max(0L, totalStars));
        updates.put("overallStars", Math.max(0L, totalStars));
        updates.put("tokens", Math.max(0L, tokens));
        String cycle = RegionRepository.currentCycle();
        long currentMonthlyStars = cycle.equals(snapshot.getString("monthlyStarsCycle"))
                ? longValue(snapshot, "monthlyStars") : 0L;
        updates.put("monthlyStars", Math.max(0L, currentMonthlyStars + monthlyStarDelta));
        updates.put("monthlyStarsCycle", cycle);
        LeagueInfo league = LeagueInfo.forStars(totalStars);
        updates.put("league", league.name);
        updates.put("leagueLevel", league.level);
        updates.put("leagueIconRes", league.iconRes);
    }

    private static Map<String, Boolean> missionMap(DocumentSnapshot snapshot) {
        String savedDate = snapshot.getString(FIELD_DATE);
        if (!today().equals(savedDate)) {
            return emptyMissions();
        }
        Object raw = snapshot.get(FIELD_MISSIONS);
        if (!(raw instanceof Map)) {
            return emptyMissions();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rawMap = (Map<String, Object>) raw;
        Map<String, Boolean> result = emptyMissions();
        for (Mission mission : Mission.values()) {
            result.put(mission.id, Boolean.TRUE.equals(rawMap.get(mission.id)));
        }
        return result;
    }

    private static Map<String, Boolean> emptyMissions() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Mission mission : Mission.values()) {
            result.put(mission.id, false);
        }
        return result;
    }

    private static boolean bonusClaimed(DocumentSnapshot snapshot) {
        String savedDate = snapshot.getString(FIELD_DATE);
        return today().equals(savedDate)
                && Boolean.TRUE.equals(snapshot.getBoolean(FIELD_ALL_BONUS_CLAIMED));
    }

    private static boolean allDone(Map<String, Boolean> missions) {
        for (Mission mission : Mission.values()) {
            if (!Boolean.TRUE.equals(missions.get(mission.id))) {
                return false;
            }
        }
        return true;
    }

    private static long longValue(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long firstLongValue(DocumentSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Long value = snapshot.getLong(key);
            if (value != null) {
                return Math.max(0L, value);
            }
        }
        return 0L;
    }

    private static String today() {
        return DATE_FORMAT.format(new Date());
    }

    public static final class Reward {
        public final boolean changed;
        public final String date;
        public final Map<String, Boolean> missions;
        public final boolean allBonusClaimed;
        public final long starDelta;
        public final long tokenDelta;

        Reward(boolean changed, String date, Map<String, Boolean> missions,
               boolean allBonusClaimed, long starDelta, long tokenDelta) {
            this.changed = changed;
            this.date = date;
            this.missions = missions;
            this.allBonusClaimed = allBonusClaimed;
            this.starDelta = starDelta;
            this.tokenDelta = tokenDelta;
        }

        static Reward unchanged(String date, Map<String, Boolean> missions,
                                boolean allBonusClaimed) {
            return new Reward(false, date, missions, allBonusClaimed, 0L, 0L);
        }
    }

    public static final class Status {
        public final Map<String, Boolean> missions;
        public final boolean allBonusClaimed;

        Status(Map<String, Boolean> missions, boolean allBonusClaimed) {
            this.missions = missions;
            this.allBonusClaimed = allBonusClaimed;
        }

        public boolean isCompleted(Mission mission) {
            return Boolean.TRUE.equals(missions.get(mission.id));
        }

        public int completedCount() {
            int count = 0;
            for (Mission mission : Mission.values()) {
                if (isCompleted(mission)) {
                    count++;
                }
            }
            return count;
        }
    }
}
