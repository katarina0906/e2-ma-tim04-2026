package com.example.slagalicatim04.repositories;

import android.content.Context;

import com.example.slagalicatim04.associations.AssociationGameService;
import com.example.slagalicatim04.associations.AssociationMatchState;
import com.example.slagalicatim04.associations.AssociationPuzzle;
import com.example.slagalicatim04.models.MatchingMultiplayerState;
import com.example.slagalicatim04.models.QuizMultiplayerState;
import com.example.slagalicatim04.multiplayer.TestRoomPlayerProvider;
import com.example.slagalicatim04.stepbystep.StepByStepMatchRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiplayerGameRepository {
    public static final String TEST_ROOM_ID = StepByStepMatchRepository.DEFAULT_MATCH_ID;

    private static final int QUIZ_QUESTION_COUNT = 5;
    private static final int MATCHING_PAIR_COUNT = 5;
    private static final int MATCHING_ROUND_COUNT = 2;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final String playerId;
    private final DocumentReference matchRef;

    public MultiplayerGameRepository(Context context) {
        playerId = new TestRoomPlayerProvider(context).getPlayerId();
        matchRef = firestore.collection("stepByStepMatches").document(TEST_ROOM_ID);
    }

    public String getPlayerId() {
        return playerId;
    }

    public Subscription joinQuiz(StateListener<QuizMultiplayerState> listener) {
        ListenerRegistration registration = matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
            } else if (snapshot != null && snapshot.exists()) {
                listener.onState(new QuizMultiplayerState(snapshot));
            }
        });
        return registration::remove;
    }

    public Subscription joinMatching(StateListener<MatchingMultiplayerState> listener) {
        ListenerRegistration registration = matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
            } else if (snapshot != null && snapshot.exists()) {
                listener.onState(new MatchingMultiplayerState(snapshot));
            }
        });
        return registration::remove;
    }

    public void leaveQuizWaitingRoom() {
    }

    public void leaveMatchingWaitingRoom() {
    }

    public void submitQuizAnswer(int questionIndex, String answerId, boolean correct) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot match = transaction.get(matchRef);
            if (!isPhase(match, "koZnaZnaPlaying")
                    || intValue(match.getLong("kzzCurrentQuestion")) != questionIndex
                    || !isParticipant(match)) {
                return null;
            }

            Map<String, Object> answers = mutableObjectMap(match.get("kzzAnswers"));
            if (answers.containsKey(playerId)) {
                return null;
            }
            Map<String, Object> answer = new HashMap<>();
            answer.put("answerId", answerId);
            answer.put("correct", correct);
            answer.put("answeredAt", FieldValue.serverTimestamp());
            answers.put(playerId, answer);

            Map<String, Object> updates = new HashMap<>();
            updates.put("kzzAnswers", answers);
            if (!correct) {
                addToTotalScore(match, updates, playerId, -5);
            }
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void advanceQuizIfReady(int questionIndex) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot match = transaction.get(matchRef);
            Map<String, Object> answers = mutableObjectMap(match.get("kzzAnswers"));
            String forfeitedPlayerId = match.getString("forfeitedPlayerId");
            int requiredAnswers = isEmpty(forfeitedPlayerId) ? 2 : 1;
            if (isPhase(match, "koZnaZnaPlaying")
                    && intValue(match.getLong("kzzCurrentQuestion")) == questionIndex
                    && answers.size() >= requiredAnswers) {
                advanceQuiz(transaction, match, answers);
            }
            return null;
        });
    }

    public void expireQuizQuestion(int questionIndex) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot match = transaction.get(matchRef);
            if (isPhase(match, "koZnaZnaPlaying")
                    && intValue(match.getLong("kzzCurrentQuestion")) == questionIndex) {
                advanceQuiz(transaction, match, mutableObjectMap(match.get("kzzAnswers")));
            }
            return null;
        });
    }

    public void submitMatchingAttempt(int roundIndex, int leftIndex, int rightPairIndex) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot match = transaction.get(matchRef);
            if (!isPhase(match, "spojnicePlaying")
                    || intValue(match.getLong("spCurrentRound")) != roundIndex
                    || !playerId.equals(match.getString("spCurrentPlayer"))) {
                return null;
            }

            List<Long> matched = mutableLongList(match.get("spMatchedPairs"));
            List<Long> attempted = mutableLongList(match.get("spAttemptedPairs"));
            int turnPairCount = intValue(match.getLong("spTurnPairCount"));
            if (matched.contains((long) leftIndex) || attempted.contains((long) leftIndex)) {
                return null;
            }
            attempted.add((long) leftIndex);

            Map<String, Object> updates = new HashMap<>();
            if (leftIndex == rightPairIndex) {
                matched.add((long) leftIndex);
                addToTotalScore(match, updates, playerId, 2);
            }
            if (matched.size() == MATCHING_PAIR_COUNT || attempted.size() >= turnPairCount) {
                advanceMatching(transaction, match, matched, updates);
            } else {
                updates.put("spMatchedPairs", matched);
                updates.put("spAttemptedPairs", attempted);
                transaction.set(matchRef, updates, SetOptions.merge());
            }
            return null;
        });
    }

    public void expireMatchingChance(int roundIndex, String expectedPlayer, boolean secondChance) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot match = transaction.get(matchRef);
            if (isPhase(match, "spojnicePlaying")
                    && intValue(match.getLong("spCurrentRound")) == roundIndex
                    && expectedPlayer.equals(match.getString("spCurrentPlayer"))
                    && secondChance == Boolean.TRUE.equals(match.getBoolean("spSecondChance"))) {
                advanceMatching(transaction, match, mutableLongList(match.get("spMatchedPairs")),
                        new HashMap<>());
            }
            return null;
        });
    }

    private void advanceQuiz(com.google.firebase.firestore.Transaction transaction,
                             DocumentSnapshot match, Map<String, Object> answers) {
        Map<String, Object> updates = new HashMap<>();
        applyQuizCorrectScores(match, updates, answers);
        int nextQuestion = intValue(match.getLong("kzzCurrentQuestion")) + 1;
        updates.put("kzzAnswers", new HashMap<>());
        if (nextQuestion >= QUIZ_QUESTION_COUNT) {
            updates.putAll(newMatchingState(match));
        } else {
            updates.put("kzzCurrentQuestion", nextQuestion);
        }
        transaction.set(matchRef, updates, SetOptions.merge());
    }

    private void applyQuizCorrectScores(DocumentSnapshot match, Map<String, Object> updates,
                                        Map<String, Object> answers) {
        String player1 = match.getString("player1Id");
        String player2 = match.getString("player2Id");
        Map<String, Object> answer1 = nestedMap(answers.get(player1));
        Map<String, Object> answer2 = nestedMap(answers.get(player2));
        boolean correct1 = Boolean.TRUE.equals(answer1.get("correct"));
        boolean correct2 = Boolean.TRUE.equals(answer2.get("correct"));
        if (correct1 && correct2) {
            addToTotalScore(match, updates, earlierPlayer(player1, answer1, player2, answer2), 10);
        } else if (correct1) {
            addToTotalScore(match, updates, player1, 10);
        } else if (correct2) {
            addToTotalScore(match, updates, player2, 10);
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

    private Map<String, Object> newMatchingState(DocumentSnapshot match) {
        Map<String, Object> state = new HashMap<>();
        state.put("currentGame", "spojnice");
        state.put("phase", "spojnicePlaying");
        state.put("spCurrentRound", 0L);
        state.put("spCurrentPlayer", match.getString("player1Id"));
        state.put("spSecondChance", false);
        state.put("spMatchedPairs", new ArrayList<>());
        state.put("spAttemptedPairs", new ArrayList<>());
        state.put("spTurnPairCount", MATCHING_PAIR_COUNT);
        state.put("statusMessage", "");
        return state;
    }

    private void advanceMatching(com.google.firebase.firestore.Transaction transaction,
                                 DocumentSnapshot match, List<Long> matched,
                                 Map<String, Object> updates) {
            boolean secondChance = Boolean.TRUE.equals(match.getBoolean("spSecondChance"));
            int currentRound = intValue(match.getLong("spCurrentRound"));
            String forfeitedPlayerId = match.getString("forfeitedPlayerId");
            if (!secondChance && matched.size() < MATCHING_PAIR_COUNT) {
                String currentPlayer = match.getString("spCurrentPlayer");
                String nextPlayer = currentPlayer.equals(match.getString("player1Id"))
                        ? match.getString("player2Id") : match.getString("player1Id");
                if (!isEmpty(forfeitedPlayerId) && forfeitedPlayerId.equals(nextPlayer)) {
                    if (currentRound + 1 < MATCHING_ROUND_COUNT) {
                        updates.put("spCurrentRound", currentRound + 1);
                        updates.put("spCurrentPlayer", match.getString("player2Id"));
                        updates.put("spSecondChance", false);
                        updates.put("spMatchedPairs", new ArrayList<>());
                        updates.put("spAttemptedPairs", new ArrayList<>());
                        updates.put("spTurnPairCount", MATCHING_PAIR_COUNT);
                    } else {
                        updates.putAll(newAssociationState());
                    }
                    transaction.set(matchRef, updates, SetOptions.merge());
                    return;
                }
                updates.put("spSecondChance", true);
                updates.put("spCurrentPlayer", nextPlayer);
                updates.put("spAttemptedPairs", new ArrayList<>());
            updates.put("spMatchedPairs", matched);
            updates.put("spTurnPairCount", MATCHING_PAIR_COUNT - matched.size());
        } else if (currentRound + 1 < MATCHING_ROUND_COUNT) {
            updates.put("spCurrentRound", currentRound + 1);
            updates.put("spCurrentPlayer", match.getString("player2Id"));
            updates.put("spSecondChance", false);
            updates.put("spMatchedPairs", new ArrayList<>());
            updates.put("spAttemptedPairs", new ArrayList<>());
            updates.put("spTurnPairCount", MATCHING_PAIR_COUNT);
        } else {
            updates.putAll(newAssociationState());
        }
        transaction.set(matchRef, updates, SetOptions.merge());
    }

    private Map<String, Object> newAssociationState() {
        Map<String, Object> state = new HashMap<>();
        state.put("currentGame", AssociationMatchState.GAME);
        state.put("phase", AssociationMatchState.PHASE_ROUND);
        state.put("associationRound", 1L);
        state.put("associationActivePlayer", 1L);
        state.put("associationOpenPhase", true);
        state.put("associationCanContinueAfterCorrect", false);
        state.put("associationSecondsLeft", AssociationGameService.ROUND_SECONDS);
        state.put("associationPuzzleId", "association-1");
        state.put("associationRevealed", falseList(
                AssociationPuzzle.COLUMN_COUNT * AssociationPuzzle.CLUES_PER_COLUMN));
        state.put("associationSolvedColumns", falseList(AssociationPuzzle.COLUMN_COUNT));
        state.put("associationFinalSolved", false);
        state.put("associationRoundPlayer1Score", 0L);
        state.put("associationRoundPlayer2Score", 0L);
        state.put("finished", false);
        state.put("statusMessage", "");
        return state;
    }

    private List<Boolean> falseList(int size) {
        List<Boolean> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(false);
        }
        return values;
    }

    private void addToTotalScore(DocumentSnapshot match, Map<String, Object> updates,
                                 String scoringPlayerId, long points) {
        if (scoringPlayerId == null) {
            return;
        }
        boolean player1 = scoringPlayerId.equals(match.getString("player1Id"));
        String key = player1 ? "player1Score" : "player2Score";
        long current = longValue(match.getLong(key));
        Object pending = updates.get(key);
        if (pending instanceof Number) {
            current = ((Number) pending).longValue();
        }
        updates.put(key, current + points);
    }

    private boolean isParticipant(DocumentSnapshot match) {
        return playerId.equals(match.getString("player1Id"))
                || playerId.equals(match.getString("player2Id"));
    }

    private boolean isPhase(DocumentSnapshot match, String phase) {
        return match.exists() && phase.equals(match.getString("phase"));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static long longValue(Long value) {
        return value == null ? 0L : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableObjectMap(Object value) {
        return value instanceof Map ? new HashMap<>((Map<String, Object>) value) : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> mutableLongList(Object value) {
        return value instanceof List ? new ArrayList<>((List<Long>) value) : new ArrayList<>();
    }

    public interface StateListener<T> {
        void onState(T state);

        void onError(Exception error);
    }

    public interface Subscription {
        void remove();
    }
}
