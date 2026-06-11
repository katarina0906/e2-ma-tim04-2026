package com.example.slagalicatim04.notifications;

import androidx.annotation.Nullable;

import com.example.slagalicatim04.R;

/** Model za istoriju sistemskih obavestenja u aplikaciji (prikaz bez backend-a). */
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
    /** Kratko vreme kao u listi, samo za prikaz. */
    public final String timeAgoLabel;
    public boolean read;

    /** Oznaka akcije koja odredjuje koju stranicu otvara klik na notifikaciju. */
    @Nullable
    public final String actionHint;

    public InAppNotification(String id, Category category, String title, String message,
                             String timeAgoLabel, boolean read,
                             @Nullable String actionHint) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.message = message;
        this.timeAgoLabel = timeAgoLabel;
        this.read = read;
        this.actionHint = actionHint;
    }
}
