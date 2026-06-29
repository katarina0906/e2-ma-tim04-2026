package com.example.slagalicatim04.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.StringRes;

import com.example.slagalicatim04.R;

public final class NotificationChannels {

    public static final String CHAT = "notifications_chat";
    public static final String RANKING = "notifications_ranking";
    public static final String REWARDS = "notifications_rewards";
    public static final String GAME_INVITES = "notifications_game_invites";
    public static final String OTHER = "notifications_misc";

    private NotificationChannels() {
    }

    public static String channelFor(String category) {
        return channelFor(category, null);
    }

    public static String channelFor(String category, String action) {
        if ("game_invite".equals(action)) {
            return GAME_INVITES;
        }
        if (category == null) {
            return OTHER;
        }
        switch (category.toLowerCase()) {
            case "chat":
                return CHAT;
            case "ranking":
                return RANKING;
            case "rewards":
                return REWARDS;
            default:
                return OTHER;
        }
    }

    public static void ensureCreated(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(build(context, CHAT,
                R.string.notif_channel_chat_name, R.string.notif_channel_chat_desc,
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(build(context, RANKING,
                R.string.notif_channel_ranking_name, R.string.notif_channel_ranking_desc,
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(build(context, REWARDS,
                R.string.notif_channel_rewards_name, R.string.notif_channel_rewards_desc,
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(build(context, GAME_INVITES,
                R.string.notif_channel_game_invites_name, R.string.notif_channel_game_invites_desc,
                NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(build(context, OTHER,
                R.string.notif_channel_other_name, R.string.notif_channel_other_desc,
                NotificationManager.IMPORTANCE_LOW));
    }

    private static NotificationChannel build(Context ctx, String id,
                                             @StringRes int nameRes, @StringRes int descRes, int importance) {
        NotificationChannel ch = new NotificationChannel(id, ctx.getString(nameRes), importance);
        ch.setDescription(ctx.getString(descRes));
        return ch;
    }
}
