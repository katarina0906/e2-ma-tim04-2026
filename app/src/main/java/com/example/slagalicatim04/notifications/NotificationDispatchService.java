package com.example.slagalicatim04.notifications;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NotificationDispatchService {

    private final FirebaseFunctions functions;

    public NotificationDispatchService() {
        functions = FirebaseFunctions.getInstance("europe-west1");
    }

    public Task<HttpsCallableResult> send(String recipientId, InAppNotification.Category category,
                                          String action, String title, String message,
                                          String targetId, Map<String, String> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", recipientId);
        payload.put("category", category.name().toLowerCase());
        payload.put("action", action);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("targetId", targetId == null ? "" : targetId);
        payload.put("data", data == null ? Collections.emptyMap() : data);
        return functions.getHttpsCallable("sendUserNotification").call(payload);
    }

}
