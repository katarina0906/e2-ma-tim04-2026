package com.example.slagalicatim04.regions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SerbiaRegionsMapView extends View {
    public interface OnRegionClickListener {
        void onRegionClicked(RegionInfo region);
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Path> regionPaths = new HashMap<>();
    private final List<RegionPlayerPoint> playerPoints = new ArrayList<>();
    private OnRegionClickListener listener;
    private String selectedRegionKey = RegionInfo.SUMADIJA.key;

    public SerbiaRegionsMapView(Context context) {
        super(context);
        init();
    }

    public SerbiaRegionsMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        pointPaint.setColor(Color.WHITE);
        pointPaint.setStyle(Paint.Style.FILL);
    }

    public void setSelectedRegion(String regionName) {
        selectedRegionKey = RegionInfo.byName(regionName).key;
        invalidate();
    }

    public void setPlayerPoints(List<RegionPlayerPoint> points) {
        playerPoints.clear();
        if (points != null) {
            playerPoints.addAll(points);
        }
        invalidate();
    }

    public void setOnRegionClickListener(OnRegionClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buildPaths(getWidth(), getHeight());

        for (RegionInfo region : RegionInfo.all()) {
            Path path = regionPaths.get(region.key);
            if (path == null) {
                continue;
            }
            fillPaint.setColor(region.color);
            fillPaint.setAlpha(region.key.equals(selectedRegionKey) ? 255 : 200);
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, borderPaint);
        }

        drawLabels(canvas);
        drawPlayerPoints(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        for (RegionInfo region : RegionInfo.all()) {
            Path path = regionPaths.get(region.key);
            if (path != null && contains(path, event.getX(), event.getY())) {
                selectedRegionKey = region.key;
                invalidate();
                if (listener != null) {
                    listener.onRegionClicked(region);
                }
                return true;
            }
        }
        return true;
    }

    private void drawLabels(Canvas canvas) {
        textPaint.setTextSize(Math.max(24f, getWidth() * 0.06f));
        for (RegionInfo region : RegionInfo.all()) {
            float[] center = labelCenter(region.key);
            canvas.drawText(region.iconLabel, center[0] * getWidth(), center[1] * getHeight(), textPaint);
        }
    }

    private void drawPlayerPoints(Canvas canvas) {
        float radius = Math.max(4f, getWidth() * 0.012f);
        pointPaint.setColor(Color.WHITE);
        for (RegionPlayerPoint point : playerPoints) {
            canvas.drawCircle(point.x * getWidth(), point.y * getHeight(), radius, pointPaint);
        }
    }

    private void buildPaths(int width, int height) {
        regionPaths.clear();
        for (RegionInfo region : RegionInfo.all()) {
            regionPaths.put(region.key, path(width, height, RegionGeometry.polygonFor(region.key)));
        }
    }

    private Path path(int width, int height, float... coords) {
        Path path = new Path();
        path.moveTo(coords[0] * width, coords[1] * height);
        for (int i = 2; i < coords.length; i += 2) {
            path.lineTo(coords[i] * width, coords[i + 1] * height);
        }
        path.close();
        return path;
    }

    private boolean contains(Path path, float x, float y) {
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        android.graphics.Region region = new android.graphics.Region();
        region.setPath(path, new android.graphics.Region(
                (int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));
        return region.contains((int) x, (int) y);
    }

    private float[] labelCenter(String key) {
        if (RegionInfo.VOJVODINA.key.equals(key)) return new float[]{0.52f, 0.18f};
        if (RegionInfo.BELGRADE.key.equals(key)) return new float[]{0.52f, 0.39f};
        if (RegionInfo.JUG_ISTOK.key.equals(key)) return new float[]{0.68f, 0.61f};
        if (RegionInfo.KOSOVO.key.equals(key)) return new float[]{0.47f, 0.88f};
        return new float[]{0.32f, 0.58f};
    }
}
