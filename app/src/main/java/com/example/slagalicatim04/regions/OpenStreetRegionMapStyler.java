package com.example.slagalicatim04.regions;

import android.content.Context;
import android.graphics.Color;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

public final class OpenStreetRegionMapStyler {
    private OpenStreetRegionMapStyler() {
    }

    public static void configure(Context context, MapView mapView, double zoom) {
        Configuration.getInstance().setUserAgentValue(context.getPackageName());
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(new GeoPoint(44.0165, 21.0059));
    }

    public static void addRegionOverlays(MapView mapView, String selectedRegionKey) {
        for (RegionInfo region : RegionInfo.all()) {
            double[] polygon = OpenStreetRegionResolver.polygonFor(region);
            if (polygon == null) {
                continue;
            }
            Polygon overlay = new Polygon(mapView);
            overlay.setPoints(toGeoPoints(polygon));
            boolean selected = region.key.equals(selectedRegionKey);
            overlay.setFillColor(colorWithAlpha(region.color, selected ? 95 : 45));
            overlay.setStrokeColor(colorWithAlpha(region.color, selected ? 235 : 180));
            overlay.setStrokeWidth(selected ? 6f : 3f);
            overlay.setTitle(region.name);
            mapView.getOverlays().add(overlay);
        }
    }

    public static void focusRegion(MapView mapView, RegionInfo region, double zoom) {
        double[] center = OpenStreetRegionResolver.centerForRegion(region);
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(new GeoPoint(center[0], center[1]));
    }

    private static List<GeoPoint> toGeoPoints(double[] polygon) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < polygon.length; i += 2) {
            points.add(new GeoPoint(polygon[i], polygon[i + 1]));
        }
        return points;
    }

    private static int colorWithAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
