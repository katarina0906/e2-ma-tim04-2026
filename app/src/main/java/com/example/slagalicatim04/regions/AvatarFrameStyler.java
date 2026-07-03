package com.example.slagalicatim04.regions;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public final class AvatarFrameStyler {
    private AvatarFrameStyler() {
    }

    public static void apply(View frame, int place) {
        int color;
        switch (place) {
            case 1:
                color = Color.rgb(212, 175, 55);
                break;
            case 2:
                color = Color.rgb(192, 192, 192);
                break;
            case 3:
                color = Color.rgb(205, 127, 50);
                break;
            default:
                color = Color.rgb(111, 75, 178);
                break;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(18f);
        frame.setBackground(drawable);
    }
}
