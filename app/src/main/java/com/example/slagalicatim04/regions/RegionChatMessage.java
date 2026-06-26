package com.example.slagalicatim04.regions;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class RegionChatMessage {
    private final String id;
    private final String senderId;
    private final String senderName;
    private final String regionKey;
    private final String text;
    private final Timestamp sentAt;

    public RegionChatMessage(String id, String senderId, String senderName,
                             String regionKey, String text, Timestamp sentAt) {
        this.id = id == null ? "" : id;
        this.senderId = senderId == null ? "" : senderId;
        this.senderName = senderName == null ? "" : senderName;
        this.regionKey = regionKey == null ? "" : regionKey;
        this.text = text == null ? "" : text;
        this.sentAt = sentAt == null ? Timestamp.now() : sentAt;
    }

    public static RegionChatMessage fromDocument(DocumentSnapshot document) {
        return new RegionChatMessage(
                document.getId(),
                document.getString("senderId"),
                document.getString("senderName"),
                document.getString("regionKey"),
                document.getString("text"),
                document.getTimestamp("sentAt")
        );
    }

    public String getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public String getText() {
        return text;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }
}
