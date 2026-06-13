package com.example.slagalicatim04.notifications;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public final class NotificationTokenManager {

    private NotificationTokenManager() {
    }

    public static void syncCurrentDevice() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> saveToken(user.getUid(), token));
    }

    public static void saveCurrentToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && token != null && !token.isEmpty()) {
            saveToken(user.getUid(), token);
        }
    }

    public static void unregisterCurrentDevice() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                FirebaseFirestore.getInstance()
                        .collection("users").document(user.getUid())
                        .collection("devices").document(tokenId(token))
                        .delete());
    }

    private static void saveToken(String userId, String token) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("platform", "android");
        data.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("devices").document(tokenId(token))
                .set(data);
    }

    static String tokenId(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(token.hashCode());
        }
    }
}
