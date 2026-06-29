package com.example.slagalicatim04.notifications;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalicatim04.MainActivity;
import com.example.slagalicatim04.R;

public final class SystemNotificationPublisher {

    private SystemNotificationPublisher() {
    }

    public static boolean show(Context context, InAppNotification item) {
        return show(context, item.id, item.categoryKey(), item.actionHint,
                item.targetId, item.title, item.message, item.data.get("expiresAt"));
    }

    public static boolean show(Context context, String notificationId, String category,
                               String action, String targetId, String title, String message) {
        return show(context, notificationId, category, action, targetId, title, message, "");
    }

    public static boolean show(Context context, String notificationId, String category,
                               String action, String targetId, String title, String message,
                               String expiresAt) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(SlagalicaMessagingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SlagalicaMessagingService.EXTRA_CATEGORY, category)
                .putExtra(SlagalicaMessagingService.EXTRA_ACTION, action)
                .putExtra(SlagalicaMessagingService.EXTRA_TARGET_ID, targetId)
                .putExtra(SlagalicaMessagingService.EXTRA_TITLE, title)
                .putExtra(SlagalicaMessagingService.EXTRA_MESSAGE, message)
                .putExtra(SlagalicaMessagingService.EXTRA_EXPIRES_AT, expiresAt);
        int requestCode = notificationId == null
                ? (int) System.currentTimeMillis()
                : notificationId.hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NotificationChannels.channelFor(category, action))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context).notify(requestCode, builder.build());
        return true;
    }
}
