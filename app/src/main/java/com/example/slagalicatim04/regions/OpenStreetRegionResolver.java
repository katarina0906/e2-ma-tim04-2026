package com.example.slagalicatim04.regions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class OpenStreetRegionResolver {
    private static final Map<RegionInfo, double[]> REGION_POLYGONS = buildRegionPolygons();

    private OpenStreetRegionResolver() {
    }

    public static RegionInfo regionForLocation(double latitude, double longitude) {
        for (Map.Entry<RegionInfo, double[]> entry : REGION_POLYGONS.entrySet()) {
            if (contains(entry.getValue(), latitude, longitude)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static double[] polygonFor(RegionInfo region) {
        return REGION_POLYGONS.get(region);
    }

    public static double[] centerForRegion(RegionInfo region) {
        double[] polygon = polygonFor(region);
        if (polygon == null) {
            return new double[]{44.0165, 21.0059};
        }
        double latitude = 0d;
        double longitude = 0d;
        int pointCount = polygon.length / 2;
        for (int i = 0; i < polygon.length; i += 2) {
            latitude += polygon[i];
            longitude += polygon[i + 1];
        }
        return new double[]{latitude / pointCount, longitude / pointCount};
    }

    public static double[] randomLocationForRegion(String regionName) {
        RegionInfo region = RegionInfo.byName(regionName);
        double[] polygon = polygonFor(region);
        double[] bounds = boundsOf(polygon);
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            double latitude = bounds[0] + random.nextDouble() * (bounds[1] - bounds[0]);
            double longitude = bounds[2] + random.nextDouble() * (bounds[3] - bounds[2]);
            if (contains(polygon, latitude, longitude)) {
                return new double[]{latitude, longitude};
            }
        }
        return centerForRegion(region);
    }

    private static Map<RegionInfo, double[]> buildRegionPolygons() {
        Map<RegionInfo, double[]> polygons = new LinkedHashMap<>();
        polygons.put(RegionInfo.BELGRADE, new double[]{
                45.08, 20.08, 45.05, 20.75, 44.80, 20.88, 44.35, 20.78,
                44.26, 20.28, 44.56, 20.03
        });
        polygons.put(RegionInfo.VOJVODINA, new double[]{
                46.25, 18.80, 46.20, 21.60, 45.20, 21.55, 44.78, 20.80,
                44.86, 20.25, 44.72, 19.55, 45.05, 18.85
        });
        polygons.put(RegionInfo.KOSOVO, new double[]{
                43.30, 20.00, 43.25, 21.70, 42.10, 21.80, 41.85, 20.55,
                42.25, 20.05
        });
        polygons.put(RegionInfo.SUMADIJA, new double[]{
                44.92, 18.80, 44.85, 20.10, 44.25, 21.15, 43.30, 21.25,
                43.25, 21.70, 42.05, 21.75, 42.00, 19.50, 43.20, 18.85
        });
        polygons.put(RegionInfo.JUG_ISTOK, new double[]{
                44.85, 20.78, 44.85, 22.95, 42.20, 23.05, 42.05, 21.75,
                43.25, 21.70, 43.30, 20.95, 44.25, 20.78
        });
        return polygons;
    }

    private static boolean contains(double[] polygon, double latitude, double longitude) {
        boolean inside = false;
        int pointCount = polygon.length / 2;
        for (int i = 0, j = pointCount - 1; i < pointCount; j = i++) {
            double lati = polygon[i * 2];
            double loni = polygon[i * 2 + 1];
            double latj = polygon[j * 2];
            double lonj = polygon[j * 2 + 1];
            boolean intersects = ((loni > longitude) != (lonj > longitude))
                    && (latitude < (latj - lati) * (longitude - loni) / (lonj - loni) + lati);
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double[] boundsOf(double[] polygon) {
        double minLatitude = polygon[0];
        double maxLatitude = polygon[0];
        double minLongitude = polygon[1];
        double maxLongitude = polygon[1];
        for (int i = 2; i < polygon.length; i += 2) {
            minLatitude = Math.min(minLatitude, polygon[i]);
            maxLatitude = Math.max(maxLatitude, polygon[i]);
            minLongitude = Math.min(minLongitude, polygon[i + 1]);
            maxLongitude = Math.max(maxLongitude, polygon[i + 1]);
        }
        return new double[]{minLatitude, maxLatitude, minLongitude, maxLongitude};
    }
}
