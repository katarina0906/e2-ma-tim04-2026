package com.example.slagalicatim04.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Date;

public class NotificationBackendTest {

    @Test
    public void categorySelectsExpectedAndroidChannel() {
        assertEquals(NotificationChannels.CHAT, NotificationChannels.channelFor("chat"));
        assertEquals(NotificationChannels.RANKING, NotificationChannels.channelFor("RANKING"));
        assertEquals(NotificationChannels.REWARDS, NotificationChannels.channelFor("rewards"));
        assertEquals(NotificationChannels.OTHER, NotificationChannels.channelFor("unknown"));
    }

    @Test
    public void tokenDocumentIdIsStableSha256() {
        assertEquals(
                "9f86d081884c7d659a2feaa0c55ad015"
                        + "a3bf4f1b2b0b822cd15d6c15b0f00a08",
                NotificationTokenManager.tokenId("test")
        );
    }

    @Test
    public void recentTimestampHasReadableRelativeLabel() {
        Timestamp timestamp = new Timestamp(new Date(System.currentTimeMillis() - 120_000L));
        assertTrue(NotificationTimeFormatter.format(timestamp).startsWith("pre 2 min"));
    }
}
