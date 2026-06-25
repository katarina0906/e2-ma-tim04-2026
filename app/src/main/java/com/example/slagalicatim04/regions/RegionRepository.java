package com.example.slagalicatim04.regions;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
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

        for (DocumentSnapshot user : users.getDocuments()) {
            RegionInfo region = RegionInfo.byName(user.getString("region"));
            totalByRegion.put(region.key, totalByRegion.get(region.key) + 1L);

            String userCycle = user.getString("monthlyStarsCycle");
            if (cycle.equals(userCycle)) {
                starsByRegion.put(region.key, starsByRegion.get(region.key) + longValue(user, "monthlyStars"));
            } else if (longValue(user, "monthlyStars") != 0L || userCycle == null || !userCycle.isEmpty()) {
                staleCycleReset.update(user.getReference(),
                        "monthlyStars", 0L,
                        "monthlyStarsCycle", cycle);
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

    private static Double doubleValue(DocumentSnapshot snapshot, String field) {
        Double value = snapshot.getDouble(field);
        if (value != null) {
            return value;
        }
        Long longValue = snapshot.getLong(field);
        return longValue == null ? null : longValue.doubleValue();
    }
}
