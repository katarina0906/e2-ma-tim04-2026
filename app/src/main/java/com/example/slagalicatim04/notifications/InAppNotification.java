package com.example.slagalicatim04.notifications;

import androidx.annotation.Nullable;

import com.example.slagalicatim04.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class InAppNotification {

    public enum Category {
        CHAT(R.string.notif_cat_chat_label),
        RANKING(R.string.notif_cat_ranking_label),
        REWARDS(R.string.notif_cat_rewards_label),
        OTHER(R.string.notif_cat_other_label);

        public final int labelRes;

        Category(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    public final String id;
    public final Category category;
    public final String title;
    public final String message;
    public final Timestamp createdAt;
    public boolean read;

    @Nullable
    public final String actionHint;
    @Nullable
    public final String targetId;
    public final Map<String, String> data;

    public InAppNotification(String id, Category category, String title, String message,
                             Timestamp createdAt, boolean read, @Nullable String actionHint,
                             @Nullable String targetId, Map<String, String> data) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt == null ? Timestamp.now() : createdAt;
        this.read = read;
        this.actionHint = actionHint;
        this.targetId = targetId;
        this.data = Collections.unmodifiableMap(new HashMap<>(data));
    }

    public static InAppNotification fromDocument(DocumentSnapshot document) {
        return new InAppNotification(
                document.getId(),
                categoryFrom(document.getString("category")),
                valueOrEmpty(document.getString("title")),
                valueOrEmpty(document.getString("message")),
                document.getTimestamp("createdAt"),
                Boolean.TRUE.equals(document.getBoolean("read")),
                document.getString("action"),
                document.getString("targetId"),
                stringMap(document.get("data"))
        );
    }

    public String categoryKey() {
        return category.name().toLowerCase();
    }

    private static Category categoryFrom(String value) {
        if (value == null) {
            return Category.OTHER;
        }
        try {
            return Category.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Category.OTHER;
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> stringMap(Object rawValue) {
        if (!(rawValue instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawValue).entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
