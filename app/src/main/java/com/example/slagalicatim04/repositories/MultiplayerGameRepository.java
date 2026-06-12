package com.example.slagalicatim04.repositories;

import android.content.Context;

import com.example.slagalicatim04.models.MatchingMultiplayerState;
import com.example.slagalicatim04.models.QuizMultiplayerState;
import com.example.slagalicatim04.multiplayer.TestRoomPlayerProvider;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiplayerGameRepository {
    public static final String TEST_ROOM_ID = "test-room";

    private static final int QUIZ_QUESTION_COUNT = 5;
    private static final long QUIZ_DURATION_MS = 5_000L;
    private static final int MATCHING_PAIR_COUNT = 5;
    private static final int MATCHING_ROUND_COUNT = 2;
    private static final long MATCHING_DURATION_MS = 30_000L;

    private final FirebaseFirestore firestore;
    private final String playerId;
    private final DocumentReference roomRef;
    private final DocumentReference quizRef;
    private final DocumentReference matchingRef;

    public MultiplayerGameRepository(Context context) {
        firestore = FirebaseFirestore.getInstance();
        playerId = new TestRoomPlayerProvider(context).getPlayerId();
        roomRef = firestore.collection("rooms").document(TEST_ROOM_ID);
        quizRef = roomRef.collection("games").document("ko_zna_zna");
        matchingRef = roomRef.collection("games").document("spojnice");
    }

    public String getPlayerId() {
        return playerId;
    }

    public Subscription joinQuiz(StateListener<QuizMultiplayerState> listener) {
        joinRoomAndEnsureGame(true, listener);
        ListenerRegistration registration = quizRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
            } else if (snapshot != null && snapshot.exists()) {
                listener.onState(new QuizMultiplayerState(snapshot));
            }
        });
        return registration::remove;
    }

    public void leaveQuizWaitingRoom() {
        leaveWaitingRoom(quizRef);
    }

    public void leaveMatchingWaitingRoom() {
        leaveWaitingRoom(matchingRef);
    }

    private void leaveWaitingRoom(DocumentReference gameRef) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            if (!game.exists() || !"waiting".equals(game.getString("status"))) {
                return null;
            }
            List<String> readyPlayers = mutableStringList(game.get("readyPlayers"));
            if (readyPlayers.remove(playerId)) {
                transaction.update(gameRef, "readyPlayers", readyPlayers);
            }
            return null;
        });
    }

    public Subscription joinMatching(StateListener<MatchingMultiplayerState> listener) {
        joinRoomAndEnsureGame(false, listener);
        ListenerRegistration registration = matchingRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
            } else if (snapshot != null && snapshot.exists()) {
                listener.onState(new MatchingMultiplayerState(snapshot));
            }
        });
        return registration::remove;
    }

    public void submitQuizAnswer(int questionIndex, String answerId, boolean correct) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(quizRef);
            if (!isActive(game) || intValue(game.getLong("currentQuestion")) != questionIndex) {
                return null;
            }

            Map<String, Object> answers = mutableObjectMap(game.get("answers"));
            if (answers.containsKey(playerId)) {
                return null;
            }

            Map<String, Object> answer = new HashMap<>();
            answer.put("answerId", answerId);
            answer.put("correct", correct);
            answer.put("answeredAt", FieldValue.serverTimestamp());
            answers.put(playerId, answer);

            Map<String, Object> updates = new HashMap<>();
            updates.put("answers", answers);
            if (!correct) {
                Map<String, Long> scores = mutableLongMap(game.get("scores"));
                scores.put(playerId, scoreOf(scores, playerId) - 5L);
                updates.put("scores", scores);
            }
            transaction.update(quizRef, updates);
            return null;
        });
    }

    public void advanceQuizIfReady(int questionIndex) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(quizRef);
            Map<String, Object> answers = mutableObjectMap(game.get("answers"));
            if (isActive(game)
                    && intValue(game.getLong("currentQuestion")) == questionIndex
                    && answers.size() >= 2) {
                advanceQuiz(transaction, game, answers);
            }
            return null;
        });
    }

    public void expireQuizQuestion(int questionIndex) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(quizRef);
            if (isActive(game) && intValue(game.getLong("currentQuestion")) == questionIndex) {
                advanceQuiz(transaction, game, mutableObjectMap(game.get("answers")));
            }
            return null;
        });
    }

    public void submitMatchingAttempt(int roundIndex, int leftIndex, int rightPairIndex) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(matchingRef);
            if (!isActive(game)
                    || intValue(game.getLong("currentRound")) != roundIndex
                    || !playerId.equals(game.getString("currentPlayer"))) {
                return null;
            }

            List<Long> matched = mutableLongList(game.get("matchedPairs"));
            List<Long> attempted = mutableLongList(game.get("attemptedPairs"));
            int turnPairCount = intValue(game.getLong("turnPairCount"));
            if (turnPairCount <= 0) {
                turnPairCount = MATCHING_PAIR_COUNT;
            }
            if (matched.contains((long) leftIndex) || attempted.contains((long) leftIndex)) {
                return null;
            }
            attempted.add((long) leftIndex);

            Map<String, Long> scores = mutableLongMap(game.get("scores"));
            if (leftIndex == rightPairIndex) {
                matched.add((long) leftIndex);
                scores.put(playerId, scoreOf(scores, playerId) + 2L);
            }

            if (matched.size() == MATCHING_PAIR_COUNT || attempted.size() >= turnPairCount) {
                advanceMatching(transaction, game, matched, scores);
            } else {
                Map<String, Object> updates = new HashMap<>();
                updates.put("matchedPairs", matched);
                updates.put("attemptedPairs", attempted);
                updates.put("scores", scores);
                transaction.update(matchingRef, updates);
            }
            return null;
        });
    }

    public void expireMatchingChance(int roundIndex, String expectedPlayer, boolean secondChance) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(matchingRef);
            if (isActive(game)
                    && intValue(game.getLong("currentRound")) == roundIndex
                    && expectedPlayer.equals(game.getString("currentPlayer"))
                    && secondChance == Boolean.TRUE.equals(game.getBoolean("secondChance"))) {
                advanceMatching(transaction, game, mutableLongList(game.get("matchedPairs")),
                        mutableLongMap(game.get("scores")));
            }
            return null;
        });
    }

    private <T> void joinRoomAndEnsureGame(boolean quiz, StateListener<T> listener) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot room = transaction.get(roomRef);
            String player1 = room.getString("player1Id");
            String player2 = room.getString("player2Id");

            if (!playerId.equals(player1) && !playerId.equals(player2)) {
                if (player1 == null || player1.isEmpty()) {
                    player1 = playerId;
                } else if (player2 == null || player2.isEmpty()) {
                    player2 = playerId;
                } else {
                    throw new IllegalStateException("Test soba vec ima dva igraca.");
                }
            }

            Map<String, Object> roomData = new HashMap<>();
            roomData.put("player1Id", player1);
            roomData.put("player2Id", player2);
            transaction.set(roomRef, roomData);
            return new RoomPlayers(player1, player2);
        }).addOnSuccessListener(players -> {
            if (players.isReady()) {
                ensureGame(quiz, players);
            }
        }).addOnFailureListener(listener::onError);
    }

    private void ensureGame(boolean quiz, RoomPlayers players) {
        DocumentReference gameRef = quiz ? quizRef : matchingRef;
        firestore.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            if (game.exists()) {
                if ("waiting".equals(game.getString("status"))) {
                    List<String> readyPlayers = mutableStringList(game.get("readyPlayers"));
                    if (!readyPlayers.contains(playerId)) {
                        readyPlayers.add(playerId);
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("readyPlayers", readyPlayers);
                    if (readyPlayers.contains(players.player1)
                            && readyPlayers.contains(players.player2)) {
                        updates.put("status", "playing");
                        if (quiz) {
                            updates.put("currentQuestion", 0);
                            updates.put("answers", new HashMap<>());
                        } else {
                            updates.put("currentRound", 0);
                            updates.put("currentPlayer", players.player1);
                            updates.put("secondChance", false);
                            updates.put("matchedPairs", new ArrayList<>());
                            updates.put("attemptedPairs", new ArrayList<>());
                            updates.put("turnPairCount", MATCHING_PAIR_COUNT);
                        }
                    }
                    transaction.update(gameRef, updates);
                }
                if (!quiz && game.getLong("turnPairCount") == null) {
                    List<Long> matched = mutableLongList(game.get("matchedPairs"));
                    boolean secondChance = Boolean.TRUE.equals(game.getBoolean("secondChance"));
                    int turnPairCount = secondChance
                            ? MATCHING_PAIR_COUNT - matched.size()
                            : MATCHING_PAIR_COUNT;
                    transaction.update(gameRef, "turnPairCount", turnPairCount);
                }
                return null;
            }

            Map<String, Object> data = baseGame(players);
            if (quiz) {
                data.put("status", "waiting");
                data.put("currentQuestion", 0);
                data.put("answers", new HashMap<>());
                data.put("readyPlayers", new ArrayList<>(Collections.singletonList(playerId)));
            } else {
                data.put("status", "waiting");
                data.put("currentRound", 0);
                data.put("currentPlayer", players.player1);
                data.put("secondChance", false);
                data.put("matchedPairs", new ArrayList<>());
                data.put("attemptedPairs", new ArrayList<>());
                data.put("turnPairCount", MATCHING_PAIR_COUNT);
                data.put("readyPlayers", new ArrayList<>(Collections.singletonList(playerId)));
            }
            transaction.set(gameRef, data);
            return null;
        });
    }

    private Map<String, Object> baseGame(RoomPlayers players) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "playing");
        data.put("player1Id", players.player1);
        data.put("player2Id", players.player2);
        Map<String, Long> scores = new HashMap<>();
        scores.put(players.player1, 0L);
        scores.put(players.player2, 0L);
        data.put("scores", scores);
        return data;
    }

    private void advanceQuiz(com.google.firebase.firestore.Transaction transaction,
                             DocumentSnapshot game, Map<String, Object> answers) {
        Map<String, Long> scores = mutableLongMap(game.get("scores"));
        String player1 = game.getString("player1Id");
        String player2 = game.getString("player2Id");
        applyQuizScores(scores, player1, player2, answers);

        int nextQuestion = intValue(game.getLong("currentQuestion")) + 1;
        Map<String, Object> updates = new HashMap<>();
        updates.put("scores", scores);
        updates.put("answers", new HashMap<>());
        if (nextQuestion >= QUIZ_QUESTION_COUNT) {
            updates.put("status", "finished");
            updates.put("deadlineAt", 0L);
        } else {
            updates.put("currentQuestion", nextQuestion);
            updates.put("deadlineAt", now() + QUIZ_DURATION_MS);
        }
        transaction.update(quizRef, updates);
    }

    private void applyQuizScores(Map<String, Long> scores, String player1, String player2,
                                 Map<String, Object> answers) {
        Map<String, Object> answer1 = nestedMap(answers.get(player1));
        Map<String, Object> answer2 = nestedMap(answers.get(player2));
        boolean correct1 = Boolean.TRUE.equals(answer1.get("correct"));
        boolean correct2 = Boolean.TRUE.equals(answer2.get("correct"));

        if (correct1 && correct2) {
            String winner = earlierPlayer(player1, answer1, player2, answer2);
            scores.put(winner, scoreOf(scores, winner) + 10L);
        } else if (correct1) {
            scores.put(player1, scoreOf(scores, player1) + 10L);
        } else if (correct2) {
            scores.put(player2, scoreOf(scores, player2) + 10L);
        }
    }

    private String earlierPlayer(String player1, Map<String, Object> answer1,
                                 String player2, Map<String, Object> answer2) {
        Timestamp time1 = (Timestamp) answer1.get("answeredAt");
        Timestamp time2 = (Timestamp) answer2.get("answeredAt");
        if (time1 == null || time2 == null || time1.equals(time2)) {
            return player1.compareTo(player2) <= 0 ? player1 : player2;
        }
        return time1.compareTo(time2) < 0 ? player1 : player2;
    }

    private void advanceMatching(com.google.firebase.firestore.Transaction transaction,
                                 DocumentSnapshot game, List<Long> matched,
                                 Map<String, Long> scores) {
        boolean secondChance = Boolean.TRUE.equals(game.getBoolean("secondChance"));
        int currentRound = intValue(game.getLong("currentRound"));
        String player1 = game.getString("player1Id");
        String player2 = game.getString("player2Id");
        Map<String, Object> updates = new HashMap<>();
        updates.put("scores", scores);

        if (!secondChance && matched.size() < MATCHING_PAIR_COUNT) {
            String currentPlayer = game.getString("currentPlayer");
            updates.put("secondChance", true);
            updates.put("currentPlayer", player1.equals(currentPlayer) ? player2 : player1);
            updates.put("attemptedPairs", new ArrayList<>());
            updates.put("matchedPairs", matched);
            updates.put("turnPairCount", MATCHING_PAIR_COUNT - matched.size());
            updates.put("deadlineAt", now() + MATCHING_DURATION_MS);
        } else if (currentRound + 1 < MATCHING_ROUND_COUNT) {
            updates.put("currentRound", currentRound + 1);
            updates.put("currentPlayer", player2);
            updates.put("secondChance", false);
            updates.put("matchedPairs", new ArrayList<>());
            updates.put("attemptedPairs", new ArrayList<>());
            updates.put("turnPairCount", MATCHING_PAIR_COUNT);
            updates.put("deadlineAt", now() + MATCHING_DURATION_MS);
        } else {
            updates.put("status", "finished");
            updates.put("deadlineAt", 0L);
        }
        transaction.update(matchingRef, updates);
    }

    private boolean isActive(DocumentSnapshot game) {
        return game.exists() && "playing".equals(game.getString("status"));
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private static long scoreOf(Map<String, Long> scores, String player) {
        Long score = scores.get(player);
        return score == null ? 0L : score;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableObjectMap(Object value) {
        return value instanceof Map ? new HashMap<>((Map<String, Object>) value) : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> mutableLongMap(Object value) {
        return value instanceof Map ? new HashMap<>((Map<String, Long>) value) : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> mutableLongList(Object value) {
        return value instanceof List ? new ArrayList<>((List<Long>) value) : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> mutableStringList(Object value) {
        return value instanceof List ? new ArrayList<>((List<String>) value) : new ArrayList<>();
    }

    public interface StateListener<T> {
        void onState(T state);

        void onError(Exception error);
    }

    public interface Subscription {
        void remove();
    }

    private static class RoomPlayers {
        private final String player1;
        private final String player2;

        private RoomPlayers(String player1, String player2) {
            this.player1 = player1;
            this.player2 = player2;
        }

        private boolean isReady() {
            return player1 != null && !player1.isEmpty() && player2 != null && !player2.isEmpty();
        }
    }
}
