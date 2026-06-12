package com.example.slagalicatim04.auth;

import android.graphics.BitmapFactory;
import android.widget.ImageView;

import com.google.firebase.storage.FirebaseStorage;

public final class AvatarImageLoader {
    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;

    private AvatarImageLoader() {
    }

    public static void load(ImageView imageView, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            imageView.setPadding(18, 18, 18, 18);
            return;
        }

        FirebaseStorage.getInstance().getReferenceFromUrl(avatarUrl)
                .getBytes(MAX_AVATAR_BYTES)
                .addOnSuccessListener(bytes -> {
                    imageView.setPadding(0, 0, 0, 0);
                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                })
                .addOnFailureListener(error ->
                        imageView.setImageResource(android.R.drawable.ic_menu_myplaces));
    }
}
