package com.example.slagalicatim04.regions;

import com.example.slagalicatim04.leagues.LeagueInfo;
import com.example.slagalicatim04.notifications.LeagueNotificationData;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class RegionRepository {
    private static final long ACTIVE_WINDOW_MS = 30L * 60L * 1000L;

    private final FirebaseFirestore firestore;

    public RegionRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public RegionDashboard loadDashboard(String currentRegionName)
            throws ExecutionException, InterruptedException {
        String cycle = currentCycle();
        RegionInfo currentRegion = currentRegionName == null || currentRegionName.trim().isEmpty()
                ? null : RegionInfo.byName(currentRegionName);
        QuerySnapshot users = Tasks.await(firestore.collection("users").get());

        Map<String, Long> starsByRegion = emptyLongMap();
        Map<String, Long> activeByRegion = emptyLongMap();
        Map<String, Long> totalByRegion = emptyLongMap();
        List<RegionPlayerPoint> points = new ArrayList<>();
        WriteBatch staleCycleReset = firestore.batch();
        int staleCycleUpdates = 0;
        long now = System.currentTimeMillis();
        Map<String, Map<String, Long>> finishedCycleStars = new HashMap<>();

        for (DocumentSnapshot user : users.getDocuments()) {
            RegionInfo region = RegionInfo.byName(user.getString("region"));
            totalByRegion.put(region.key, totalByRegion.get(region.key) + 1L);

            String userCycle = user.getString("monthlyStarsCycle");
            if (cycle.equals(userCycle)) {
                starsByRegion.put(region.key, starsByRegion.get(region.key) + longValue(user, "monthlyStars"));
            } else {
                long staleStars = longValue(user, "monthlyStars");
                if (userCycle != null && !userCycle.trim().isEmpty() && staleStars > 0L) {
                    Map<String, Long> cycleStars = finishedCycleStars.get(userCycle);
                    if (cycleStars == null) {
                        cycleStars = emptyLongMap();
                        finishedCycleStars.put(userCycle, cycleStars);
                    }
                    cycleStars.put(region.key, cycleStars.get(region.key) + staleStars);
                }
                MonthlyCycleReset cycleReset = monthlyCycleReset(user, userCycle, cycle, staleStars);
                staleCycleReset.update(user.getReference(), cycleReset.userUpdates);
                if (cycleReset.leagueNotification != null) {
                    staleCycleReset.set(user.getReference().collection("notifications")
                                    .document("league_change_" + userCycle),
                            cycleReset.leagueNotification);
                }
                staleCycleUpdates++;
            }

            if (isActive(user, now)) {
                activeByRegion.put(region.key, activeByRegion.get(region.key) + 1L);
            }

            Double latitude = doubleValue(user, "regionMapLatitude");
            Double longitude = doubleValue(user, "regionMapLongitude");
            if (latitude == null || longitude == null) {
                double[] fallbackLocation = OpenStreetRegionResolver.centerForRegion(region);
                latitude = fallbackLocation[0];
                longitude = fallbackLocation[1];
            }
            points.add(new RegionPlayerPoint(user.getId(), region.key, latitude, longitude));
        }
        finalizeFinishedCycles(finishedCycleStars);
        if (staleCycleUpdates > 0) {
            Tasks.await(staleCycleReset.commit());
        }

        Map<String, RegionStats> statsByRegion = loadStats(activeByRegion, totalByRegion);
        List<RegionRankItem> ranking = buildRanking(starsByRegion,
                currentRegion == null ? "" : currentRegion.key);
        return new RegionDashboard(ranking, points, statsByRegion);
    }

    public void addMonthlyStars(String userId, long stars)
            throws ExecutionException, InterruptedException {
        if (userId == null || userId.trim().isEmpty() || stars <= 0L) {
            return;
        }
        String cycle = currentCycle();
        Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(firestore.collection("users").document(userId));
            String userCycle = user.getString("monthlyStarsCycle");
            long currentStars = cycle.equals(userCycle) ? longValue(user, "monthlyStars") : 0L;
            transaction.update(user.getReference(),
                    "monthlyStars", currentStars + stars,
                    "monthlyStarsCycle", cycle);
            return null;
        }));
    }

    public static float[] randomPointForRegion(String regionName) {
        return RegionGeometry.randomPointForRegion(regionName);
    }

    public static String currentCycle() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private Map<String, RegionStats> loadStats(Map<String, Long> activeByRegion,
                                               Map<String, Long> totalByRegion)
            throws ExecutionException, InterruptedException {
        Map<String, RegionStats> stats = new HashMap<>();
        for (RegionInfo region : RegionInfo.all()) {
            DocumentSnapshot stat = Tasks.await(firestore.collection("regionStats")
                    .document(region.key)
                    .get());
            stats.put(region.key, new RegionStats(
                    region,
                    longValue(stat, "firstPlaces"),
                    longValue(stat, "secondPlaces"),
                    longValue(stat, "thirdPlaces"),
                    activeByRegion.get(region.key),
                    totalByRegion.get(region.key)
            ));
        }
        return stats;
    }

    private List<RegionRankItem> buildRanking(Map<String, Long> starsByRegion,
                                              String currentRegionKey) {
        List<RegionInfo> sorted = new ArrayList<>(RegionInfo.all());
        Collections.sort(sorted, (left, right) -> Long.compare(
                starsByRegion.get(right.key), starsByRegion.get(left.key)));

        List<RegionRankItem> result = new ArrayList<>();
        long previousStars = Long.MIN_VALUE;
        int previousRank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            RegionInfo region = sorted.get(i);
            long stars = starsByRegion.get(region.key);
            int rank = stars == previousStars ? previousRank : i + 1;
            previousStars = stars;
            previousRank = rank;
            result.add(new RegionRankItem(region, stars, rank, region.key.equals(currentRegionKey)));
        }
        result.sort(Comparator.comparingInt(item -> item.rank));
        return result;
    }

    private void finalizeFinishedCycles(Map<String, Map<String, Long>> starsByCycle)
            throws ExecutionException, InterruptedException {
        for (Map.Entry<String, Map<String, Long>> cycleEntry : starsByCycle.entrySet()) {
            String cycle = cycleEntry.getKey();
            List<RegionRankItem> ranking = buildRanking(cycleEntry.getValue(), "");
            Map<String, Integer> podiumByRegion = podiumByRegion(ranking);
            Boolean applied = Tasks.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot cycleDoc = transaction.get(firestore.collection("regionMonthlyCycles")
                        .document(cycle));
                if (Boolean.TRUE.equals(cycleDoc.getBoolean("statsApplied"))) {
                    return false;
                }

                for (int i = 0; i < ranking.size(); i++) {
                    RegionRankItem item = ranking.get(i);
                    if (item.monthlyStars <= 0L) {
                        continue;
                    }
                    String field = podiumField(i + 1);
                    if (field == null) {
                        continue;
                    }
                    transaction.set(firestore.collection("regionStats").document(item.region.key),
                            Collections.singletonMap(field, FieldValue.increment(1L)),
                            SetOptions.merge());
                }
                Map<String, Object> cycleData = new HashMap<>();
                cycleData.put("statsApplied", true);
                cycleData.put("appliedAt", FieldValue.serverTimestamp());
                transaction.set(firestore.collection("regionMonthlyCycles").document(cycle),
                        cycleData,
                        SetOptions.merge());
                return true;
            }));
            if (Boolean.TRUE.equals(applied)) {
                applyAvatarFramesForCycle(cycle, podiumByRegion);
            }
        }
    }

    private Map<String, Integer> podiumByRegion(List<RegionRankItem> ranking) {
        Map<String, Integer> podium = new HashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            RegionRankItem item = ranking.get(i);
            int place = i + 1;
            if (item.monthlyStars <= 0L || podiumField(place) == null) {
                continue;
            }
            podium.put(item.region.key, place);
        }
        return podium;
    }

    private void applyAvatarFramesForCycle(String cycle, Map<String, Integer> podiumByRegion)
            throws ExecutionException, InterruptedException {
        QuerySnapshot users = Tasks.await(firestore.collection("users").get());
        WriteBatch batch = firestore.batch();
        int pendingWrites = 0;
        for (DocumentSnapshot user : users.getDocuments()) {
            RegionInfo region = RegionInfo.byName(user.getString("region"));
            Integer place = podiumByRegion.get(region.key);
            batch.update(user.getReference(),
                    "avatarFramePlace", place == null ? 0L : place.longValue(),
                    "avatarFrameCycle", cycle);
            pendingWrites++;
            if (pendingWrites == 450) {
                Tasks.await(batch.commit());
                batch = firestore.batch();
                pendingWrites = 0;
            }
        }
        if (pendingWrites > 0) {
            Tasks.await(batch.commit());
        }
    }

    private String podiumField(int rank) {
        if (rank == 1) {
            return "firstPlaces";
        }
        if (rank == 2) {
            return "secondPlaces";
        }
        if (rank == 3) {
            return "thirdPlaces";
        }
        return null;
    }

    private MonthlyCycleReset monthlyCycleReset(DocumentSnapshot user, String userCycle,
                                                String newCycle, long previousMonthlyStars) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("monthlyStars", 0L);
        updates.put("monthlyStarsCycle", newCycle);

        if (userCycle == null || userCycle.trim().isEmpty() || previousMonthlyStars > 0L
                || userCycle.equals(user.getString("lastMonthlyNoPlacementPenaltyCycle"))) {
            return new MonthlyCycleReset(updates, null);
        }

        long currentTotalStars = firstLongValue(user, "totalStars", "overallStars", "stars");
        long nextTotalStars = (currentTotalStars * 70L) / 100L;
        LeagueInfo previousLeague = LeagueInfo.forStars(currentTotalStars);
        LeagueInfo league = LeagueInfo.forStars(nextTotalStars);
        updates.put("totalStars", nextTotalStars);
        updates.put("overallStars", nextTotalStars);
        updates.put("league", league.name);
        updates.put("leagueLevel", league.level);
        updates.put("leagueIconRes", league.iconRes);
        updates.put("lastMonthlyNoPlacementPenaltyCycle", userCycle);
        updates.put("lastMonthlyNoPlacementPenaltyPercent", 30L);
        updates.put("lastMonthlyNoPlacementPenaltyAt", FieldValue.serverTimestamp());
        Map<String, Object> notification = previousLeague.level == league.level
                ? null
                : LeagueNotificationData.create(previousLeague.name, league, nextTotalStars, false);
        return new MonthlyCycleReset(updates, notification);
    }

    private static class MonthlyCycleReset {
        final Map<String, Object> userUpdates;
        final Map<String, Object> leagueNotification;

        MonthlyCycleReset(Map<String, Object> userUpdates,
                          Map<String, Object> leagueNotification) {
            this.userUpdates = userUpdates;
            this.leagueNotification = leagueNotification;
        }
    }

    private static Map<String, Long> emptyLongMap() {
        Map<String, Long> map = new HashMap<>();
        for (RegionInfo region : RegionInfo.all()) {
            map.put(region.key, 0L);
        }
        return map;
    }

    private static boolean isActive(DocumentSnapshot user, long now) {
        Boolean active = user.getBoolean("active");
        if (Boolean.TRUE.equals(active)) {
            return true;
        }
        Long lastActiveAt = user.getLong("lastActiveAt");
        return lastActiveAt != null && now - lastActiveAt <= ACTIVE_WINDOW_MS;
    }

    private static long longValue(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value == null ? 0L : value;
    }

    private static long firstLongValue(DocumentSnapshot snapshot, String... fields) {
        for (String field : fields) {
            long value = longValue(snapshot, field);
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private static Double doubleValue(DocumentSnapshot snapshot, String field) {
        Double value = snapshot.getDouble(field);
        if (value != null) {
            return value;
        }
        Long longValue = snapshot.getLong(field);
        return longValue == null ? null : longValue.doubleValue();
    }
}
