package com.example.slagalicatim04.regions;

import androidx.annotation.NonNull;

import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.notifications.InAppNotification;
import com.example.slagalicatim04.notifications.NotificationRouter;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionChatRepository {

    public interface Listener {
        void onMessages(List<RegionChatMessage> messages);

        void onError(Exception error);
    }

    private final FirebaseFirestore firestore;

    public RegionChatRepository() {
        this(FirebaseFirestore.getInstance());
    }

    RegionChatRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public ListenerRegistration listen(String regionKey, @NonNull Listener listener) {
        return messagesRef(regionKey)
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .limitToLast(200)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<RegionChatMessage> result = new ArrayList<>();
                    if (snapshot != null) {
                        snapshot.getDocuments().forEach(document ->
                                result.add(RegionChatMessage.fromDocument(document)));
                    }
                    listener.onMessages(result);
                });
    }

    public void sendMessage(AuthUser sender, String rawText,
                            Runnable onSuccess,
                            java.util.function.Consumer<Exception> onError) {
        if (sender == null) {
            onError.accept(new IllegalArgumentException("Korisnik nije prijavljen."));
            return;
        }
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            onError.accept(new IllegalArgumentException("Poruka je prazna."));
            return;
        }
        RegionInfo region = RegionInfo.byName(sender.getRegion());
        String regionKey = region.key;
        String senderName = sender.getUsername() == null || sender.getUsername().trim().isEmpty()
                ? sender.getEmail() : sender.getUsername();

        CollectionReference messages = messagesRef(regionKey);
        String messageId = messages.document().getId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", sender.getId());
        payload.put("senderName", senderName);
        payload.put("regionKey", regionKey);
        payload.put("regionName", region.name);
        payload.put("text", text);
        payload.put("sentAt", FieldValue.serverTimestamp());

        messages.document(messageId).set(payload)
                .addOnSuccessListener(ignored -> notifyOfflinePlayers(
                        sender, region, senderName, text, messageId, onSuccess, onError))
                .addOnFailureListener(onError::accept);
    }

    private void notifyOfflinePlayers(AuthUser sender, RegionInfo region, String senderName,
                                      String text, String messageId,
                                      Runnable onSuccess,
                                      java.util.function.Consumer<Exception> onError) {
        firestore.collection("users")
                .whereEqualTo("region", region.name)
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = firestore.batch();
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot user : snapshot.getDocuments()) {
                        if (sender.getId().equals(user.getId())) {
                            continue;
                        }
                        if (Boolean.TRUE.equals(user.getBoolean("active"))) {
                            continue;
                        }
                        String notificationId = "region_chat_" + messageId;
                        Map<String, String> data = new HashMap<>();
                        data.put("regionKey", region.key);
                        data.put("regionName", region.name);
                        data.put("senderName", senderName);
                        data.put("messageId", messageId);

                        Map<String, Object> notification = new HashMap<>();
                        notification.put("category", InAppNotification.Category.CHAT.name().toLowerCase());
                        notification.put("title", "Nova poruka u regionalnom četu");
                        notification.put("message", senderName + ": " + preview(text));
                        notification.put("action", NotificationRouter.ACTION_CHAT);
                        notification.put("targetId", region.key);
                        notification.put("data", data);
                        notification.put("source", "region_chat");
                        notification.put("read", false);
                        notification.put("readAt", null);
                        notification.put("actionedAt", null);
                        notification.put("createdAt", FieldValue.serverTimestamp());
                        batch.set(firestore.collection("users")
                                .document(user.getId())
                                .collection("notifications")
                                .document(notificationId), notification);
                        count++;
                    }
                    if (count == 0) {
                        onSuccess.run();
                        return;
                    }
                    batch.commit()
                            .addOnSuccessListener(ignored -> onSuccess.run())
                            .addOnFailureListener(onError::accept);
                })
                .addOnFailureListener(onError::accept);
    }

    private CollectionReference messagesRef(String regionKey) {
        return firestore.collection("regionChats")
                .document(isEmpty(regionKey) ? RegionInfo.SUMADIJA.key : regionKey)
                .collection("messages");
    }

    private static String preview(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
