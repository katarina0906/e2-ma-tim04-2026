package com.example.slagalicatim04.regions;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class RegionGeometry {
    private static final int MAX_RANDOM_ATTEMPTS = 1000;
    private static final Map<String, float[]> POLYGONS = new HashMap<>();

    static {
        POLYGONS.put(RegionInfo.VOJVODINA.key, new float[]{
                0.24f, 0.06f, 0.72f, 0.04f, 0.80f, 0.18f, 0.64f, 0.31f,
                0.43f, 0.30f, 0.24f, 0.24f
        });
        POLYGONS.put(RegionInfo.BELGRADE.key, new float[]{
                0.43f, 0.31f, 0.62f, 0.32f, 0.63f, 0.43f, 0.48f, 0.45f,
                0.39f, 0.39f
        });
        POLYGONS.put(RegionInfo.SUMADIJA.key, new float[]{
                0.20f, 0.30f, 0.43f, 0.31f, 0.39f, 0.40f, 0.49f, 0.46f,
                0.51f, 0.62f, 0.38f, 0.77f, 0.18f, 0.70f, 0.10f, 0.48f
        });
        POLYGONS.put(RegionInfo.JUG_ISTOK.key, new float[]{
                0.50f, 0.45f, 0.72f, 0.33f, 0.88f, 0.51f, 0.81f, 0.76f,
                0.63f, 0.84f, 0.51f, 0.69f
        });
        POLYGONS.put(RegionInfo.KOSOVO.key, new float[]{
                0.39f, 0.76f, 0.62f, 0.84f, 0.58f, 0.96f, 0.40f, 0.94f,
                0.30f, 0.84f
        });
    }

    private RegionGeometry() {
    }

    public static float[] polygonFor(String regionKey) {
        float[] polygon = POLYGONS.get(regionKey);
        return polygon == null ? POLYGONS.get(RegionInfo.SUMADIJA.key) : polygon;
    }

    public static float[] randomPointForRegion(String regionName) {
        RegionInfo region = RegionInfo.byName(regionName);
        float[] polygon = polygonFor(region.key);
        float[] bounds = boundsOf(polygon);
        Random random = new Random();

        for (int i = 0; i < MAX_RANDOM_ATTEMPTS; i++) {
            float x = bounds[0] + random.nextFloat() * (bounds[1] - bounds[0]);
            float y = bounds[2] + random.nextFloat() * (bounds[3] - bounds[2]);
            if (contains(polygon, x, y)) {
                return new float[]{x, y};
            }
        }
        return new float[]{(bounds[0] + bounds[1]) / 2f, (bounds[2] + bounds[3]) / 2f};
    }

    public static boolean contains(float[] polygon, float x, float y) {
        boolean inside = false;
        int pointCount = polygon.length / 2;
        for (int i = 0, j = pointCount - 1; i < pointCount; j = i++) {
            float xi = polygon[i * 2];
            float yi = polygon[i * 2 + 1];
            float xj = polygon[j * 2];
            float yj = polygon[j * 2 + 1];
            boolean intersects = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static float[] boundsOf(float[] polygon) {
        float minX = polygon[0];
        float maxX = polygon[0];
        float minY = polygon[1];
        float maxY = polygon[1];
        for (int i = 2; i < polygon.length; i += 2) {
            minX = Math.min(minX, polygon[i]);
            maxX = Math.max(maxX, polygon[i]);
            minY = Math.min(minY, polygon[i + 1]);
            maxY = Math.max(maxY, polygon[i + 1]);
        }
        return new float[]{minX, maxX, minY, maxY};
    }
}
