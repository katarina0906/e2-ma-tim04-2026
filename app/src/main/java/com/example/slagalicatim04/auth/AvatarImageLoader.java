package com.example.slagalicatim04.auth;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

public final class AvatarImageLoader {
    private AvatarImageLoader() {
    }

    public static void load(ImageView imageView, String avatarData) {
        if (avatarData == null || avatarData.isEmpty()) {
            imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            imageView.setPadding(18, 18, 18, 18);
            return;
        }

        try {
            byte[] bytes = Base64.decode(avatarData, Base64.DEFAULT);
            imageView.setPadding(0, 0, 0, 0);
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } catch (IllegalArgumentException error) {
            imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
        }
    }
}
