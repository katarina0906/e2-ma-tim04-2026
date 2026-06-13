package com.example.slagalicatim04.notifications;

import androidx.annotation.NonNull;

import com.example.slagalicatim04.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class SlagalicaMessagingService extends FirebaseMessagingService {

    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    public static final String EXTRA_CATEGORY = "notificationCategory";
    public static final String EXTRA_ACTION = "notificationAction";
    public static final String EXTRA_TARGET_ID = "notificationTargetId";
    public static final String EXTRA_TITLE = "notificationTitle";
    public static final String EXTRA_MESSAGE = "notificationMessage";

    @Override
    public void onNewToken(@NonNull String token) {
        NotificationTokenManager.saveCurrentToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String title = firstNonEmpty(data.get("title"),
                remoteMessage.getNotification() == null
                        ? null : remoteMessage.getNotification().getTitle(),
                getString(R.string.app_name));
        String message = firstNonEmpty(data.get("message"),
                remoteMessage.getNotification() == null
                        ? null : remoteMessage.getNotification().getBody(),
                "");
        String category = firstNonEmpty(data.get("category"), "other");
        SystemNotificationPublisher.show(this, data.get("notificationId"), category, data.get("action"),
                data.get("targetId"), title, message);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }
}
