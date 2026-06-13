package com.example.slagalicatim04.notifications;

import com.google.firebase.Timestamp;

import java.util.Collections;
import java.util.List;

public final class DemoNotifications {

    private DemoNotifications() {
    }

    public static List<InAppNotification> create() {
        long suffix = System.currentTimeMillis();
        Timestamp now = Timestamp.now();
        return java.util.Arrays.asList(
                item("demo-chat-" + suffix, InAppNotification.Category.CHAT,
                        "Nova poruka u cetu",
                        "marko99: Kad igramo sledecu partiju?",
                        NotificationRouter.ACTION_CHAT, "demo-regional-chat", now),
                item("demo-ranking-" + suffix, InAppNotification.Category.RANKING,
                        "Plasman na rang listi",
                        "Trenutno si 142. na nedeljnoj rang listi.",
                        NotificationRouter.ACTION_RANKING, "demo-weekly-ranking", now),
                item("demo-reward-" + suffix, InAppNotification.Category.REWARDS,
                        "Nagrada je dostupna",
                        "Osvojila si 3 tokena za plasman u prethodnom ciklusu.",
                        NotificationRouter.ACTION_REWARD, "demo-reward", now),
                item("demo-friend-" + suffix, InAppNotification.Category.OTHER,
                        "Zahtev za prijateljstvo",
                        "ana_me ti je poslala zahtev za prijateljstvo.",
                        NotificationRouter.ACTION_FRIEND_REQUEST, "demo-friend-request", now),
                item("demo-league-" + suffix, InAppNotification.Category.RANKING,
                        "Nova liga",
                        "Presla si iz Srebrne u Zlatnu ligu.",
                        NotificationRouter.ACTION_LEAGUE, "demo-league", now)
        );
    }

    private static InAppNotification item(
            String id, InAppNotification.Category category, String title, String message,
            String action, String targetId, Timestamp createdAt) {
        return new InAppNotification(id, category, title, message, createdAt,
                false, action, targetId, Collections.singletonMap("demo", "true"));
    }
}
