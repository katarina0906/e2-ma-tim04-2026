package com.example.slagalicatim04.regions;

import androidx.annotation.NonNull;

import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.DailyMissionService;
import com.example.slagalicatim04.leagues.LeagueInfo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RegionChallengeRepository {
    public interface Listener {
        void onChallenges(List<RegionChallenge> challenges);

        void onError(Exception error);
    }

    public interface ChallengeListener {
        void onChallenge(RegionChallenge challenge);

        void onError(Exception error);
    }

    private static final int MAX_PLAYERS = 4;
    private static final long MAX_STAKE_STARS = 10L;
    private static final long MAX_STAKE_TOKENS = 2L;
    private static final String SOLO_CHALLENGE_OPPONENT_ID = "__solo_challenge__";

    private final FirebaseFirestore firestore;

    public RegionChallengeRepository() {
        this(FirebaseFirestore.getInstance());
    }

    RegionChallengeRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public ListenerRegistration listen(String regionKey, @NonNull Listener listener) {
        return firestore.collection("regionChallenges")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<RegionChallenge> result = new ArrayList<>();
                    String expectedRegionKey = emptyRegionKey(regionKey);
                    if (snapshot != null) {
                        snapshot.getDocuments().forEach(document -> {
                            RegionChallenge challenge = RegionChallenge.fromDocument(document);
                            if (expectedRegionKey.equals(challenge.regionKey)) {
                                result.add(challenge);
                            }
                        });
                    }
                    listener.onChallenges(result);
                });
    }

    public ListenerRegistration listenChallenge(String challengeId, @NonNull ChallengeListener listener) {
        return challengeRef(challengeId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                listener.onChallenge(RegionChallenge.fromDocument(snapshot));
            }
        });
    }

    public void createChallenge(AuthUser user, long stakeStars, long stakeTokens,
                                Runnable onSuccess,
                                java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        if (stakeStars < 0 || stakeStars > MAX_STAKE_STARS) {
            onError.accept(new IllegalArgumentException("Ulog zvezda mora biti od 0 do 10."));
            return;
        }
        if (stakeTokens < 0 || stakeTokens > MAX_STAKE_TOKENS) {
            onError.accept(new IllegalArgumentException("Ulog tokena mora biti od 0 do 2."));
            return;
        }
        if (stakeStars == 0 && stakeTokens == 0) {
            onError.accept(new IllegalArgumentException("Postavi bar jedan ulog."));
            return;
        }

        RegionInfo region = RegionInfo.byName(user.getRegion());
        String challengeId = firestore.collection("regionChallenges").document().getId();
        DocumentReference challengeRef = firestore.collection("regionChallenges").document(challengeId);
        DocumentReference userRef = firestore.collection("users").document(user.getId());
        Timestamp createdAt = Timestamp.now();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            ensureFunds(userSnapshot, stakeStars, stakeTokens);

            Map<String, Object> participants = new LinkedHashMap<>();
            participants.put(user.getId(), participantPayload(displayName(user), 0L,
                    false, null, 0L, 0L));

            Map<String, Object> payload = new HashMap<>();
            payload.put("regionKey", region.key);
            payload.put("regionName", region.name);
            payload.put("creatorId", user.getId());
            payload.put("creatorName", displayName(user));
            payload.put("stakeStars", stakeStars);
            payload.put("stakeTokens", stakeTokens);
            payload.put("status", RegionChallenge.STATUS_OPEN);
            payload.put("maxPlayers", MAX_PLAYERS);
            payload.put("participants", participants);
            payload.put("createdAt", createdAt);
            payload.put("startedAt", null);
            payload.put("finishedAt", null);
            transaction.set(challengeRef, payload);
            deductStake(transaction, userRef, userSnapshot, stakeStars, stakeTokens);
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void joinChallenge(AuthUser user, String challengeId,
                              Runnable onSuccess,
                              java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        DocumentReference challengeRef = challengeRef(challengeId);
        DocumentReference userRef = firestore.collection("users").document(user.getId());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot challenge = transaction.get(challengeRef);
            RegionChallenge state = RegionChallenge.fromDocument(challenge);
            if (!state.isOpen()) {
                throw new IllegalArgumentException("Izazov vise nije otvoren.");
            }
            if (!state.regionKey.equals(RegionInfo.byName(user.getRegion()).key)) {
                throw new IllegalArgumentException("Mozes prihvatiti samo izazov svog regiona.");
            }
            if (state.hasParticipant(user.getId())) {
                throw new IllegalArgumentException("Vec si u ovom izazovu.");
            }
            if (state.participantCount() >= state.maxPlayers) {
                throw new IllegalArgumentException("Izazov je popunjen.");
            }

            DocumentSnapshot userSnapshot = transaction.get(userRef);
            ensureFunds(userSnapshot, state.stakeStars, state.stakeTokens);
            Map<String, Object> participants = mutableMap(challenge.get("participants"));
            participants.put(user.getId(), participantPayload(displayName(user), 0L,
                    false, null, 0L, 0L));
            transaction.set(challengeRef, Collections.singletonMap("participants", participants),
                    SetOptions.merge());
            deductStake(transaction, userRef, userSnapshot, state.stakeStars, state.stakeTokens);
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void startChallenge(AuthUser user, String challengeId,
                               Runnable onSuccess,
                               java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        DocumentReference challengeRef = challengeRef(challengeId);
        Timestamp startedAt = Timestamp.now();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot challenge = transaction.get(challengeRef);
            RegionChallenge state = RegionChallenge.fromDocument(challenge);
            if (!state.canStart(user.getId())) {
                throw new IllegalArgumentException("Izazov moze pokrenuti samo kreator kada ima najmanje 2 igraca.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", RegionChallenge.STATUS_ACTIVE);
            updates.put("startedAt", startedAt);
            transaction.set(challengeRef, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void finishChallenge(AuthUser user, String challengeId,
                                Runnable onSuccess,
                                java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        DocumentReference challengeRef = challengeRef(challengeId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot challenge = transaction.get(challengeRef);
            RegionChallenge state = RegionChallenge.fromDocument(challenge);
            if (!state.canFinish(user.getId())) {
                throw new IllegalArgumentException("Izazov moze zavrsiti samo kreator dok je aktivan.");
            }
            Map<String, Object> participants = mutableMap(challenge.get("participants"));
            Timestamp submittedAt = Timestamp.now();
            for (Map.Entry<String, Object> entry : new ArrayList<>(participants.entrySet())) {
                RegionChallengeParticipant participant =
                        RegionChallengeParticipant.fromMap(entry.getKey(), entry.getValue());
                if (!participant.submitted) {
                    participants.put(entry.getKey(), participantPayload(
                            participant.username,
                            participant.score,
                            true,
                            submittedAt,
                            participant.starsAwarded,
                            participant.tokensAwarded));
                }
            }
            finalizeChallenge(transaction, challengeRef, state, participants);
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void submitScore(AuthUser user, String challengeId, long score,
                            Runnable onSuccess,
                            java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        if (score < 0L) {
            onError.accept(new IllegalArgumentException("Rezultat ne moze biti negativan."));
            return;
        }
        DocumentReference challengeRef = challengeRef(challengeId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot challenge = transaction.get(challengeRef);
            RegionChallenge state = RegionChallenge.fromDocument(challenge);
            if (!state.isActive()) {
                throw new IllegalArgumentException("Izazov nije aktivan.");
            }
            RegionChallengeParticipant participant = state.participantsById.get(user.getId());
            if (participant == null) {
                throw new IllegalArgumentException("Nisi prijavljen u ovaj izazov.");
            }
            if (participant.submitted) {
                throw new IllegalArgumentException("Rezultat je vec poslat.");
            }

            Map<String, Object> participants = mutableMap(challenge.get("participants"));
            Timestamp submittedAt = Timestamp.now();
            participants.put(user.getId(), participantPayload(participant.username, score,
                    true, submittedAt, participant.starsAwarded,
                    participant.tokensAwarded));

            Map<String, Object> updates = new HashMap<>();
            updates.put("participants", participants);
            transaction.set(challengeRef, updates, SetOptions.merge());

            if (allSubmitted(participants)) {
                finalizeChallenge(transaction, challengeRef, state, participants);
            }
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void savePreviewScore(AuthUser user, String challengeId, long score,
                                 Runnable onSuccess,
                                 java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        if (score < 0L) {
            onError.accept(new IllegalArgumentException("Rezultat ne moze biti negativan."));
            return;
        }
        DocumentReference challengeRef = challengeRef(challengeId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot challenge = transaction.get(challengeRef);
            RegionChallenge state = RegionChallenge.fromDocument(challenge);
            RegionChallengeParticipant participant = state.participantsById.get(user.getId());
            if (participant == null) {
                throw new IllegalArgumentException("Nisi prijavljen u ovaj izazov.");
            }
            Map<String, Object> participants = mutableMap(challenge.get("participants"));
            long nextScore = Math.max(participant.score, score);
            participants.put(user.getId(), participantPayload(
                    participant.username,
                    nextScore,
                    participant.submitted,
                    participant.submittedAt,
                    participant.starsAwarded,
                    participant.tokensAwarded));
            transaction.set(challengeRef, Collections.singletonMap("participants", participants),
                    SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public void ensureSoloChallengeMatch(AuthUser user, String challengeId,
                                         java.util.function.Consumer<String> onSuccess,
                                         java.util.function.Consumer<Exception> onError) {
        String validationError = validateUser(user);
        if (validationError != null) {
            onError.accept(new IllegalArgumentException(validationError));
            return;
        }
        String roomId = soloChallengeRoomId(challengeId, user.getId());
        DocumentReference matchRef = firestore.collection("stepByStepMatches").document(roomId);
        DocumentReference userRef = firestore.collection("users").document(user.getId());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot challengeSnapshot = transaction.get(challengeRef(challengeId));
            RegionChallenge challenge = RegionChallenge.fromDocument(challengeSnapshot);
            if (!challenge.hasParticipant(user.getId())) {
                throw new IllegalArgumentException("Nisi prijavljen u ovaj izazov.");
            }

            DocumentSnapshot matchSnapshot = transaction.get(matchRef);
            if (!matchSnapshot.exists()) {
                Map<String, Object> state = new HashMap<>();
                state.put("player1Id", user.getId());
                state.put("player1Name", displayName(user));
                state.put("player2Id", SOLO_CHALLENGE_OPPONENT_ID);
                state.put("player2Name", "");
                state.put("player1Score", 0L);
                state.put("player2Score", 0L);
                state.put("player1Ready", true);
                state.put("player2Ready", false);
                state.put("round", 1L);
                state.put("phase", "koZnaZnaPlaying");
                state.put("currentGame", "koZnaZna");
                state.put("activePlayer", 1L);
                state.put("stealPlayer", 0L);
                state.put("roundStartedAt", 0L);
                state.put("stealStartedAt", 0L);
                state.put("visibleStepCount", 0L);
                state.put("secondsLeft", 0L);
                state.put("round1Result", "");
                state.put("round2Result", "");
                state.put("finalResult", "");
                state.put("finished", false);
                state.put("forfeitedPlayerId", SOLO_CHALLENGE_OPPONENT_ID);
                state.put("winnerByForfeitId", user.getId());
                state.put("soloChallenge", true);
                state.put("challengeId", challengeId);
                state.put("statusMessage", "Samostalna partija izazova je pokrenuta.");
                state.put("kzzCurrentQuestion", 0L);
                state.put("kzzAnswers", new HashMap<>());
                state.put("updatedAt", FieldValue.serverTimestamp());
                transaction.set(matchRef, state);
            }

            Map<String, Object> userState = new HashMap<>();
            userState.put("active", true);
            userState.put("inGame", true);
            userState.put("lastActiveAt", System.currentTimeMillis());
            userState.put("currentRoomId", roomId);
            userState.put("currentMatchId", roomId);
            userState.put("currentOpponentId", challengeId);
            transaction.set(userRef, userState, SetOptions.merge());
            return roomId;
        }).addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    private void finalizeChallenge(com.google.firebase.firestore.Transaction transaction,
                                   DocumentReference challengeRef,
                                   RegionChallenge existingState,
                                   Map<String, Object> rawParticipants) throws FirebaseFirestoreException {
        List<Map.Entry<String, RegionChallengeParticipant>> ranking = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawParticipants.entrySet()) {
            ranking.add(new java.util.AbstractMap.SimpleEntry<>(
                    entry.getKey(),
                    RegionChallengeParticipant.fromMap(entry.getKey(), entry.getValue())));
        }
        ranking.sort((left, right) -> {
            int byScore = Long.compare(right.getValue().score, left.getValue().score);
            if (byScore != 0) {
                return byScore;
            }
            if (left.getValue().submittedAt == right.getValue().submittedAt) {
                return left.getKey().compareTo(right.getKey());
            }
            if (left.getValue().submittedAt == null) {
                return 1;
            }
            if (right.getValue().submittedAt == null) {
                return -1;
            }
            return left.getValue().submittedAt.compareTo(right.getValue().submittedAt);
        });

        long winnerStars = (existingState.totalStakeStars() * 75L) / 100L;
        long winnerTokens = (existingState.totalStakeTokens() * 75L) / 100L;
        long secondStars = existingState.stakeStars;
        long secondTokens = existingState.stakeTokens;
        Timestamp finishedAt = Timestamp.now();
        Map<String, Object> participants = new LinkedHashMap<>();
        Map<String, DocumentSnapshot> rewardSnapshots = new HashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            String userId = ranking.get(i).getKey();
            long starsAwarded = i == 0 ? winnerStars : (i == 1 ? secondStars : 0L);
            long tokensAwarded = i == 0 ? winnerTokens : (i == 1 ? secondTokens : 0L);
            if (i == 0 || starsAwarded > 0L || tokensAwarded > 0L) {
                DocumentReference userRef = firestore.collection("users").document(userId);
                rewardSnapshots.put(userId, transaction.get(userRef));
            }
        }
        for (int i = 0; i < ranking.size(); i++) {
            String userId = ranking.get(i).getKey();
            RegionChallengeParticipant participant = ranking.get(i).getValue();
            long starsAwarded = i == 0 ? winnerStars : (i == 1 ? secondStars : 0L);
            long tokensAwarded = i == 0 ? winnerTokens : (i == 1 ? secondTokens : 0L);
            participants.put(userId, participantPayload(participant.username, participant.score,
                    true, participant.submittedAt, starsAwarded, tokensAwarded));
            if (starsAwarded > 0L || tokensAwarded > 0L) {
                DocumentReference userRef = firestore.collection("users").document(userId);
                DocumentSnapshot userSnapshot = rewardSnapshots.get(userId);
                long currentTokens = longValue(userSnapshot.getLong("tokens"));
                Map<String, Object> updates = rewardUpdates(userSnapshot,
                        starsAwarded, tokensAwarded,
                        resolvedStars(userSnapshot) + starsAwarded,
                        currentTokens + tokensAwarded);
                if (i == 0) {
                    updates.putAll(DailyMissionService.buildMissionUpdates(userSnapshot,
                            DailyMissionService.Mission.WIN_TOURNAMENT_MATCH,
                            resolvedStars(userSnapshot) + starsAwarded,
                            resolvedStars(userSnapshot) + starsAwarded,
                            currentTokens + tokensAwarded));
                }
                transaction.set(userRef, updates, SetOptions.merge());
            } else if (i == 0) {
                DocumentReference userRef = firestore.collection("users").document(userId);
                DocumentSnapshot userSnapshot = rewardSnapshots.get(userId);
                transaction.set(userRef, DailyMissionService.buildUserRewardUpdates(userSnapshot,
                        DailyMissionService.computeReward(userSnapshot,
                                DailyMissionService.Mission.WIN_TOURNAMENT_MATCH)),
                        SetOptions.merge());
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("participants", participants);
        updates.put("status", RegionChallenge.STATUS_FINISHED);
        updates.put("finishedAt", finishedAt);
        updates.put("winnerId", ranking.isEmpty() ? "" : ranking.get(0).getKey());
        updates.put("runnerUpId", ranking.size() > 1 ? ranking.get(1).getKey() : "");
        transaction.set(challengeRef, updates, SetOptions.merge());
    }

    private static boolean allSubmitted(Map<String, Object> rawParticipants) {
        for (Object value : rawParticipants.values()) {
            if (!RegionChallengeParticipant.fromMap("", value).submitted) {
                return false;
            }
        }
        return !rawParticipants.isEmpty();
    }

    private static Map<String, Object> participantPayload(String username, long score,
                                                          boolean submitted, Object submittedAt,
                                                          long starsAwarded, long tokensAwarded) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("score", score);
        payload.put("submitted", submitted);
        payload.put("submittedAt", submittedAt);
        payload.put("starsAwarded", starsAwarded);
        payload.put("tokensAwarded", tokensAwarded);
        return payload;
    }

    private static void ensureFunds(DocumentSnapshot userSnapshot, long stakeStars, long stakeTokens) {
        long stars = resolvedStars(userSnapshot);
        long tokens = longValue(userSnapshot.getLong("tokens"));
        if (stars < stakeStars) {
            throw new IllegalArgumentException("Nemas dovoljno zvezda za ovaj izazov.");
        }
        if (tokens < stakeTokens) {
            throw new IllegalArgumentException("Nemas dovoljno tokena za ovaj izazov.");
        }
    }

    private static void deductStake(com.google.firebase.firestore.Transaction transaction,
                                    DocumentReference userRef, DocumentSnapshot userSnapshot,
                                    long stakeStars, long stakeTokens) {
        long nextStars = Math.max(0L, resolvedStars(userSnapshot) - stakeStars);
        long nextTokens = Math.max(0L, longValue(userSnapshot.getLong("tokens")) - stakeTokens);
        transaction.set(userRef, rewardUpdates(userSnapshot, -stakeStars, -stakeTokens,
                nextStars, nextTokens), SetOptions.merge());
    }

    private static Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return new LinkedHashMap<>(map);
    }

    private static long longValue(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long resolvedStars(DocumentSnapshot snapshot) {
        Long totalStars = snapshot.getLong("totalStars");
        if (totalStars != null) {
            return Math.max(0L, totalStars);
        }
        Long overallStars = snapshot.getLong("overallStars");
        if (overallStars != null) {
            return Math.max(0L, overallStars);
        }
        return longValue(snapshot.getLong("stars"));
    }

    private static Map<String, Object> rewardUpdates(DocumentSnapshot snapshot,
                                                     long starDelta, long tokenDelta,
                                                     long nextStars, long nextTokens) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("stars", nextStars);
        updates.put("totalStars", nextStars);
        updates.put("overallStars", nextStars);
        updates.put("tokens", nextTokens);
        String cycle = RegionRepository.currentCycle();
        long currentMonthlyStars = cycle.equals(snapshot.getString("monthlyStarsCycle"))
                ? longValue(snapshot.getLong("monthlyStars")) : 0L;
        updates.put("monthlyStars", Math.max(0L, currentMonthlyStars + starDelta));
        updates.put("monthlyStarsCycle", cycle);
        LeagueInfo league = LeagueInfo.forStars(nextStars);
        updates.put("league", league.name);
        updates.put("leagueLevel", league.level);
        updates.put("leagueIconRes", league.iconRes);
        return updates;
    }

    private static String validateUser(AuthUser user) {
        if (user == null || isEmpty(user.getId())) {
            return "Korisnik nije prijavljen.";
        }
        if (isEmpty(user.getRegion())) {
            return "Korisnik nema dodeljen region.";
        }
        return null;
    }

    private static String displayName(AuthUser user) {
        if (user == null) {
            return "";
        }
        if (!isEmpty(user.getUsername())) {
            return user.getUsername();
        }
        return isEmpty(user.getEmail()) ? user.getId() : user.getEmail();
    }

    private static String emptyRegionKey(String regionKey) {
        return isEmpty(regionKey) ? RegionInfo.SUMADIJA.key : regionKey.trim();
    }

    private DocumentReference challengeRef(String challengeId) {
        return firestore.collection("regionChallenges").document(challengeId);
    }

    private static String soloChallengeRoomId(String challengeId, String userId) {
        return "challenge_" + safeKey(challengeId) + "_" + safeKey(userId);
    }

    private static String safeKey(String value) {
        return isEmpty(value) ? "unknown" : value.trim().replace('/', '_');
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
