package com.example.slagalicatim04.tournament;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class TournamentState {
    public final String id;
    public final String status;
    public final String semifinal1RoomId;
    public final String semifinal2RoomId;
    public final String finalRoomId;
    public final String semifinal1WinnerId;
    public final String semifinal2WinnerId;
    public final String semifinal1LoserId;
    public final String semifinal2LoserId;
    public final String championId;
    public final String runnerUpId;
    public final long semifinal1WinnerScore;
    public final long semifinal1LoserScore;
    public final long semifinal2WinnerScore;
    public final long semifinal2LoserScore;
    public final long championScore;
    public final long runnerUpScore;
    public final List<TournamentParticipant> participants;

    public TournamentState(DocumentSnapshot snapshot) {
        id = snapshot.getId();
        status = string(snapshot.getString("status"));
        semifinal1RoomId = string(snapshot.getString("semifinal1RoomId"));
        semifinal2RoomId = string(snapshot.getString("semifinal2RoomId"));
        finalRoomId = string(snapshot.getString("finalRoomId"));
        semifinal1WinnerId = string(snapshot.getString("semifinal1WinnerId"));
        semifinal2WinnerId = string(snapshot.getString("semifinal2WinnerId"));
        semifinal1LoserId = string(snapshot.getString("semifinal1LoserId"));
        semifinal2LoserId = string(snapshot.getString("semifinal2LoserId"));
        championId = string(snapshot.getString("championId"));
        runnerUpId = string(snapshot.getString("runnerUpId"));
        semifinal1WinnerScore = longValue(snapshot.getLong("semifinal1WinnerScore"));
        semifinal1LoserScore = longValue(snapshot.getLong("semifinal1LoserScore"));
        semifinal2WinnerScore = longValue(snapshot.getLong("semifinal2WinnerScore"));
        semifinal2LoserScore = longValue(snapshot.getLong("semifinal2LoserScore"));
        championScore = longValue(snapshot.getLong("championScore"));
        runnerUpScore = longValue(snapshot.getLong("runnerUpScore"));
        participants = participants(snapshot.get("participants"));
    }

    public String roomFor(String userId) {
        if ("finished".equals(status) || "waiting".equals(status)) {
            return "";
        }
        if ("final".equals(status)) {
            return isFinalist(userId) ? finalRoomId : "";
        }
        if (isInMatch(userId, 0, 1) && semifinal1WinnerId.isEmpty()) {
            return semifinal1RoomId;
        }
        if (isInMatch(userId, 2, 3) && semifinal2WinnerId.isEmpty()) {
            return semifinal2RoomId;
        }
        return "";
    }

    public boolean hasStarted() {
        return "semifinals".equals(status) || "final".equals(status) || "finished".equals(status);
    }

    public boolean isFinalist(String userId) {
        return userId.equals(semifinal1WinnerId) || userId.equals(semifinal2WinnerId);
    }

    public boolean hasBothSemifinalLosers() {
        return !semifinal1LoserId.isEmpty() && !semifinal2LoserId.isEmpty();
    }

    private boolean isInMatch(String userId, int first, int second) {
        return participants.size() > second
                && (userId.equals(participants.get(first).userId)
                || userId.equals(participants.get(second).userId));
    }

    private static List<TournamentParticipant> participants(Object value) {
        List<TournamentParticipant> out = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                out.add(TournamentParticipant.fromMap(item));
            }
        }
        return out;
    }

    private static String string(String value) {
        return value == null ? "" : value;
    }

    private static long longValue(Long value) {
        return value == null ? 0L : value;
    }
}
