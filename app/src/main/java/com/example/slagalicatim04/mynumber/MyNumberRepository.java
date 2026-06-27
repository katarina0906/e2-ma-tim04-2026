package com.example.slagalicatim04.mynumber;

import com.example.slagalicatim04.auth.PlayerProgressService;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class MyNumberRepository {
    public interface Listener {
        void onState(MyNumberMatchState state);
        void onError(Exception error);
    }

    private final DocumentReference matchRef;
    private final MyNumberGameService service = new MyNumberGameService();
    private final PlayerProgressService progressService;

    public MyNumberRepository(String roomId) {
        matchRef = FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(roomId);
        progressService = new PlayerProgressService(matchRef.getFirestore());
    }

    public ListenerRegistration listen(Listener listener) {
        return matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                listener.onState(new MyNumberMatchState(snapshot));
            }
        });
    }

    public void startIfNeeded() {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) return null;
            String phase = snapshot.getString("phase");
            if (MyNumberMatchState.GAME.equals(snapshot.getString("currentGame"))
                    && "myNumberSetup".equals(phase)) {
                transaction.set(matchRef,
                        newRoundState(1, snapshot.getLong("player1Score"), snapshot.getLong("player2Score")),
                        SetOptions.merge());
            }
            return null;
        });
    }

    public void tick(StepByStepPlayerSession player) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) return null;
            MyNumberMatchState state = new MyNumberMatchState(snapshot);
            int myPlayer = playerNumber(snapshot, player.getId());
            if (!state.isMyNumberGame()
                    || myPlayer != state.getActivePlayer()
                    || MyNumberGameService.PHASE_FINISHED.equals(state.getPhase())) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            if (!state.isTargetShown()) {
                int left = Math.max(0, state.getTargetRevealLeft() - 1);
                updates.put("myNumberTargetRevealLeft", left);
                if (left == 0) updates.put("myNumberTargetShown", true);
            } else if (!state.isNumbersShown()) {
                int left = Math.max(0, state.getNumbersRevealLeft() - 1);
                updates.put("myNumberNumbersRevealLeft", left);
                if (left == 0) updates.put("myNumberNumbersShown", true);
            } else {
                int left = Math.max(0, state.getSecondsLeft() - 1);
                updates.put("myNumberSecondsLeft", left);
                if (left == 0) {
                    submitMissingAndScore(transaction, snapshot, state, updates,
                            state.getP1Result(), state.getP2Result());
                }
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void revealTarget(StepByStepPlayerSession player) {
        updateIfActive(player, "myNumberTargetShown", true);
    }

    public void revealNumbers(StepByStepPlayerSession player) {
        updateIfActive(player, "myNumberNumbersShown", true);
    }

    public void submit(StepByStepPlayerSession player, String expression) {
        matchRef.getFirestore().runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) return null;
            MyNumberMatchState state = new MyNumberMatchState(snapshot);
            int myPlayer = playerNumber(snapshot, player.getId());
            if (!state.isMyNumberGame() || myPlayer == 0 || state.isSubmitted(myPlayer)) {
                return null;
            }
            Integer result;
            try {
                result = service.evaluate(expression, state.getNumbers());
            } catch (Exception ignored) {
                result = null;
            }
            Map<String, Object> updates = new HashMap<>();
            String prefix = myPlayer == 1 ? "myNumberP1" : "myNumberP2";
            updates.put(prefix + "Submitted", true);
            updates.put(prefix + "Expression", expression == null ? "" : expression);
            updates.put(prefix + "Result", result);
            boolean p1Submitted = myPlayer == 1 || state.isP1Submitted();
            boolean p2Submitted = myPlayer == 2 || state.isP2Submitted();
            Integer p1Result = myPlayer == 1 ? result : state.getP1Result();
            Integer p2Result = myPlayer == 2 ? result : state.getP2Result();
            boolean canResolveWithForfeit = !isEmpty(state.getForfeitedPlayerId())
                    && (p1Submitted || p2Submitted);
            if ((p1Submitted && p2Submitted) || canResolveWithForfeit) {
                submitMissingAndScore(transaction, snapshot, state, updates, p1Result, p2Result);
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    public void resolveForfeitState(MyNumberMatchState state) {
        String forfeitedPlayerId = state.getForfeitedPlayerId();
        if (!state.isMyNumberGame() || isEmpty(forfeitedPlayerId)) {
            return;
        }
        int forfeitedPlayer = state.getPlayer1Id().equals(forfeitedPlayerId) ? 1
                : state.getPlayer2Id().equals(forfeitedPlayerId) ? 2 : 0;
        if (forfeitedPlayer == 0 || forfeitedPlayer != state.getActivePlayer()) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("myNumberActivePlayer", forfeitedPlayer == 1 ? 2 : 1);
        updates.put("myNumberStatusMessage",
                "Protivnik je napustio partiju. Nastavljas bez cekanja.");
        matchRef.set(updates, SetOptions.merge());
    }

    private void updateIfActive(StepByStepPlayerSession player, String field, Object value) {
        matchRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;
            MyNumberMatchState state = new MyNumberMatchState(snapshot);
            if (state.isMyNumberGame()
                    && playerNumber(snapshot, player.getId()) == state.getActivePlayer()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(field, value);
                if ("myNumberTargetShown".equals(field)) updates.put("myNumberTargetRevealLeft", 0);
                if ("myNumberNumbersShown".equals(field)) updates.put("myNumberNumbersRevealLeft", 0);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                matchRef.set(updates, SetOptions.merge());
            }
        });
    }

    private void submitMissingAndScore(Transaction transaction, DocumentSnapshot snapshot,
                                       MyNumberMatchState state, Map<String, Object> updates,
                                       Integer p1, Integer p2)
            throws FirebaseFirestoreException {
        MyNumberGameService.RoundScore score = service.scoreRound(
                state.getTarget(), state.getActivePlayer(), p1, p2);
        long nextP1Score = state.getPlayer1Score() + score.p1Points;
        long nextP2Score = state.getPlayer2Score() + score.p2Points;
        updates.put("player1Score", nextP1Score);
        updates.put("player2Score", nextP2Score);
        updates.put("myNumberStatusMessage", score.message);
        updates.put(state.getRound() == 1 ? "myNumberRound1Result" : "myNumberRound2Result", score.message);
        int roundCount = state.isSoloChallenge() ? 1 : 2;
        if (state.getRound() < roundCount) {
            updates.putAll(newRoundState(2, nextP1Score, nextP2Score));
        } else {
            updates.put("currentGame", "matchResult");
            updates.put("phase", "matchFinished");
            updates.put("myNumberStatusMessage",
                    score.message + " Moj broj i cela partija su zavrseni.");
            updates.put("statusMessage", "Partija je zavrsena.");
            updates.put("finished", true);
            applyMatchRewards(transaction, snapshot, state, updates, nextP1Score, nextP2Score);
        }
    }

    private void applyMatchRewards(Transaction transaction, DocumentSnapshot snapshot,
                                   MyNumberMatchState state, Map<String, Object> updates,
                                   long nextP1Score, long nextP2Score)
            throws FirebaseFirestoreException {
        if (Boolean.TRUE.equals(snapshot.getBoolean("matchRewardsApplied"))) {
            return;
        }
        int winner = winner(nextP1Score, nextP2Score);
        DocumentSnapshot player1Snapshot = transaction.get(
                matchRef.getFirestore().collection("users").document(state.getPlayer1Id()));
        DocumentSnapshot player2Snapshot = transaction.get(
                matchRef.getFirestore().collection("users").document(state.getPlayer2Id()));
        PlayerProgressService.RewardResult p1Reward = state.isForfeited(state.getPlayer1Id())
                ? new PlayerProgressService.RewardResult(0, 0, 0, 0)
                : progressService.applyMatchRewards(
                transaction, state.getPlayer1Id(), player1Snapshot, nextP1Score,
                winner == 1 || state.isForfeited(state.getPlayer2Id()));
        PlayerProgressService.RewardResult p2Reward = state.isForfeited(state.getPlayer2Id())
                ? new PlayerProgressService.RewardResult(0, 0, 0, 0)
                : progressService.applyMatchRewards(
                transaction, state.getPlayer2Id(), player2Snapshot, nextP2Score,
                winner == 2 || state.isForfeited(state.getPlayer1Id()));
        updates.put("matchRewardsApplied", true);
        updates.put("player1StarDelta", p1Reward.starDelta);
        updates.put("player2StarDelta", p2Reward.starDelta);
        updates.put("player1Stars", p1Reward.remainingStars);
        updates.put("player2Stars", p2Reward.remainingStars);
        updates.put("player1EarnedTokens", p1Reward.earnedTokens);
        updates.put("player2EarnedTokens", p2Reward.earnedTokens);
    }

    private int winner(long p1Score, long p2Score) {
        if (p1Score > p2Score) return 1;
        if (p2Score > p1Score) return 2;
        return 0;
    }

    private Map<String, Object> newRoundState(int round, Long p1Score, Long p2Score) {
        Map<String, Object> state = new HashMap<>();
        state.put("currentGame", MyNumberMatchState.GAME);
        state.put("phase", round == 1 ? MyNumberGameService.PHASE_ROUND1 : MyNumberGameService.PHASE_ROUND2);
        state.put("myNumberRound", round);
        state.put("myNumberActivePlayer", service.activePlayerForRound(round));
        state.put("myNumberTarget", service.generateTarget());
        state.put("myNumberNumbers", service.generateNumbers());
        state.put("myNumberSecondsLeft", MyNumberGameService.ROUND_SECONDS);
        state.put("myNumberTargetRevealLeft", MyNumberGameService.REVEAL_SECONDS);
        state.put("myNumberNumbersRevealLeft", MyNumberGameService.REVEAL_SECONDS);
        state.put("myNumberTargetShown", false);
        state.put("myNumberNumbersShown", false);
        state.put("myNumberP1Submitted", false);
        state.put("myNumberP2Submitted", false);
        state.put("myNumberP1Expression", "");
        state.put("myNumberP2Expression", "");
        state.put("myNumberP1Result", null);
        state.put("myNumberP2Result", null);
        state.put("myNumberStatusMessage", "Moj broj - runda " + round);
        if (p1Score != null) state.put("player1Score", p1Score);
        if (p2Score != null) state.put("player2Score", p2Score);
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private int playerNumber(DocumentSnapshot snapshot, String playerId) {
        if (playerId.equals(snapshot.getString("player1Id"))) return 1;
        if (playerId.equals(snapshot.getString("player2Id"))) return 2;
        return 0;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
