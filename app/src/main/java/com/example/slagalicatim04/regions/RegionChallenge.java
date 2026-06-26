package com.example.slagalicatim04.regions;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionChallenge {
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FINISHED = "finished";

    public final String id;
    public final String regionKey;
    public final String regionName;
    public final String creatorId;
    public final String creatorName;
    public final long stakeStars;
    public final long stakeTokens;
    public final String status;
    public final int maxPlayers;
    public final Timestamp createdAt;
    public final Timestamp startedAt;
    public final Timestamp finishedAt;
    public final Map<String, RegionChallengeParticipant> participantsById;
    public final List<RegionChallengeParticipant> participants;

    public RegionChallenge(String id, String regionKey, String regionName, String creatorId,
                           String creatorName, long stakeStars, long stakeTokens, String status,
                           int maxPlayers, Timestamp createdAt, Timestamp startedAt,
                           Timestamp finishedAt,
                           Map<String, RegionChallengeParticipant> participantsById) {
        this.id = safe(id);
        this.regionKey = safe(regionKey);
        this.regionName = safe(regionName);
        this.creatorId = safe(creatorId);
        this.creatorName = safe(creatorName);
        this.stakeStars = Math.max(0L, stakeStars);
        this.stakeTokens = Math.max(0L, stakeTokens);
        this.status = safe(status);
        this.maxPlayers = maxPlayers <= 0 ? 4 : maxPlayers;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.participantsById = participantsById == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(participantsById));
        List<RegionChallengeParticipant> list = new ArrayList<>(this.participantsById.values());
        list.sort(Comparator
                .comparingLong((RegionChallengeParticipant item) -> item.score).reversed()
                .thenComparing(item -> item.submittedAt, RegionChallenge::compareTimestamp)
                .thenComparing(item -> item.username));
        this.participants = Collections.unmodifiableList(list);
    }

    public static RegionChallenge fromDocument(DocumentSnapshot document) {
        Map<String, RegionChallengeParticipant> participants = new HashMap<>();
        Object rawParticipants = document.get("participants");
        if (rawParticipants instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawParticipants;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                participants.put(entry.getKey(),
                        RegionChallengeParticipant.fromMap(entry.getKey(), entry.getValue()));
            }
        }
        return new RegionChallenge(
                document.getId(),
                stringValue(document.getString("regionKey")),
                stringValue(document.getString("regionName")),
                stringValue(document.getString("creatorId")),
                stringValue(document.getString("creatorName")),
                longValue(document.getLong("stakeStars")),
                longValue(document.getLong("stakeTokens")),
                stringValue(document.getString("status")),
                intValue(document.getLong("maxPlayers"), 4),
                document.getTimestamp("createdAt"),
                document.getTimestamp("startedAt"),
                document.getTimestamp("finishedAt"),
                participants
        );
    }

    public int participantCount() {
        return participantsById.size();
    }

    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isFinished() {
        return STATUS_FINISHED.equals(status);
    }

    public boolean hasParticipant(String userId) {
        return participantsById.containsKey(safe(userId));
    }

    public boolean canJoin(String userId) {
        return isOpen() && !hasParticipant(userId) && participantCount() < maxPlayers;
    }

    public boolean canStart(String userId) {
        return isOpen() && creatorId.equals(safe(userId)) && participantCount() >= 2;
    }

    public boolean canSubmit(String userId) {
        RegionChallengeParticipant participant = participantsById.get(safe(userId));
        return isActive() && participant != null && !participant.submitted;
    }

    public long totalStakeStars() {
        return participantCount() * stakeStars;
    }

    public long totalStakeTokens() {
        return participantCount() * stakeTokens;
    }

    private static int compareTimestamp(Timestamp left, Timestamp right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static String stringValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static long longValue(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static int intValue(Long value, int fallback) {
        return value == null ? fallback : Math.max(1, value.intValue());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
