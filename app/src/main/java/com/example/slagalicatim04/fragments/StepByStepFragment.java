package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.stepbystep.StepByStepRound;
import com.example.slagalicatim04.stepbystep.StepByStepRoundRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StepByStepFragment extends Fragment {

    private static final String MATCH_ID = "demo-step-by-step";
    private static final int ROUND_DURATION_MS = 70_000;
    private static final int STEAL_DURATION_MS = 10_000;

    private final TextView[] stepViews = new TextView[7];
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderCurrentSnapshot;
    private final StepByStepRoundRepository roundRepository = new StepByStepRoundRepository();
    private final List<StepByStepRound> rounds = new ArrayList<>();

    private FirebaseFirestore firestore;
    private DocumentReference matchRef;
    private ListenerRegistration listenerRegistration;
    private DocumentSnapshot currentSnapshot;
    private String currentUserId;
    private String currentUserName;

    private ScrollView scrollView;
    private TextView roundLabelText;
    private TextView statusText;
    private TextView timerValue;
    private TextView currentStepValue;
    private TextView currentPointsValue;
    private TextView player1ScoreText;
    private TextView player2ScoreText;
    private View resultBanner;
    private TextView resultBannerTitle;
    private TextView resultBannerMessage;
    private TextInputEditText answerInput;
    private MaterialButton confirmButton;
    private MaterialButton giveUpButton;
    private MaterialButton newGameButton;

    public StepByStepFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_by_step, container, false);

        bindViews(view);
        firestore = FirebaseFirestore.getInstance();
        matchRef = firestore.collection("stepByStepMatches").document(MATCH_ID);
        resolveCurrentUser();

        resultBanner.setVisibility(View.GONE);
        newGameButton.setVisibility(View.GONE);
        statusText.setText("Ucitavanje pitanja iz baze...");
        setControlsEnabled(false);
        loadRounds();

        confirmButton.setOnClickListener(v -> submitAnswer());
        giveUpButton.setOnClickListener(v -> giveUpRound());
        newGameButton.setOnClickListener(v -> resetMatch());

        return view;
    }

    @Override
    public void onDestroyView() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        uiHandler.removeCallbacks(ticker);
        super.onDestroyView();
    }

    private void bindViews(View view) {
        scrollView = view.findViewById(R.id.stepByStepScrollView);
        roundLabelText = view.findViewById(R.id.roundLabelText);
        statusText = view.findViewById(R.id.statusText);
        timerValue = view.findViewById(R.id.timerValue);
        currentStepValue = view.findViewById(R.id.currentStepValue);
        currentPointsValue = view.findViewById(R.id.currentPointsValue);
        player1ScoreText = view.findViewById(R.id.player1ScoreText);
        player2ScoreText = view.findViewById(R.id.player2ScoreText);
        resultBanner = view.findViewById(R.id.stepByStepResultBanner);
        resultBannerTitle = view.findViewById(R.id.stepByStepResultTitle);
        resultBannerMessage = view.findViewById(R.id.stepByStepResultMessage);
        answerInput = view.findViewById(R.id.answerInput);
        confirmButton = view.findViewById(R.id.confirmAnswerButton);
        giveUpButton = view.findViewById(R.id.giveUpButton);
        newGameButton = view.findViewById(R.id.nextStepButton);

        stepViews[0] = view.findViewById(R.id.step1Text);
        stepViews[1] = view.findViewById(R.id.step2Text);
        stepViews[2] = view.findViewById(R.id.step3Text);
        stepViews[3] = view.findViewById(R.id.step4Text);
        stepViews[4] = view.findViewById(R.id.step5Text);
        stepViews[5] = view.findViewById(R.id.step6Text);
        stepViews[6] = view.findViewById(R.id.step7Text);
    }

    private void loadRounds() {
        roundRepository.loadOrSeed(new StepByStepRoundRepository.Callback() {
            @Override
            public void onSuccess(List<StepByStepRound> loadedRounds) {
                rounds.clear();
                rounds.addAll(loadedRounds);
                joinMatch();
                listenToMatch();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                statusText.setText("Nema podataka u bazi za Korak po korak.");
            }
        });
    }

    private void resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (authUser != null) {
            currentUserId = authUser.getId();
            currentUserName = authUser.getUsername().isEmpty() ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            currentUserId = firebaseUser.getUid();
            currentUserName = firebaseUser.getEmail() == null ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            currentUserId = "guest-" + System.currentTimeMillis();
            currentUserName = "Gost";
        }
    }

    private void joinMatch() {
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            long now = System.currentTimeMillis();

            if (!snapshot.exists()) {
                transaction.set(matchRef, newMatchState(now));
                return null;
            }

            boolean finished = Boolean.TRUE.equals(snapshot.getBoolean("finished"));
            String phase = snapshot.getString("phase");
            Long roundValue = snapshot.getLong("round");
            if (finished || "finished".equals(phase) || roundValue == null || roundValue < 1 || roundValue > 2) {
                transaction.set(matchRef, newMatchState(now));
                return null;
            }

            String player1Id = snapshot.getString("player1Id");
            String player2Id = snapshot.getString("player2Id");
            Map<String, Object> updates = new HashMap<>();

            if (isEmpty(player1Id)) {
                updates.put("player1Id", currentUserId);
                updates.put("player1Name", currentUserName);
            } else if (!currentUserId.equals(player1Id)
                    && !currentUserId.equals(player2Id)
                    && isEmpty(player2Id)) {
                updates.put("player2Id", currentUserId);
                updates.put("player2Name", currentUserName);
                updates.put("statusMessage", "Igrac 2 se pridruzio. Runda 1 je na igracu 1.");
            }

            if (!updates.isEmpty()) {
                transaction.update(matchRef, updates);
            }
            return null;
        }).addOnFailureListener(error ->
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private Map<String, Object> newMatchState(long now) {
        Map<String, Object> state = new HashMap<>();
        state.put("player1Id", currentUserId);
        state.put("player1Name", currentUserName);
        state.put("player2Id", "");
        state.put("player2Name", "");
        state.put("player1Score", 0L);
        state.put("player2Score", 0L);
        state.put("round", 1L);
        state.put("phase", "playing");
        state.put("activePlayer", 1L);
        state.put("stealPlayer", 0L);
        state.put("roundStartedAt", now);
        state.put("stealStartedAt", 0L);
        state.put("finished", false);
        state.put("statusMessage", "Ceka se igrac 2. Runda 1 je na igracu 1.");
        state.put("updatedAt", FieldValue.serverTimestamp());
        return state;
    }

    private void listenToMatch() {
        listenerRegistration = matchRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            currentSnapshot = snapshot;
            renderCurrentSnapshot();
        });
    }

    private void renderCurrentSnapshot() {
        uiHandler.removeCallbacks(ticker);
        if (currentSnapshot == null || !isAdded()) {
            return;
        }

        int round = (int) getLong("round", 1);
        String phase = getString("phase", "playing");
        int activePlayer = (int) getLong("activePlayer", 1);
        int stealPlayer = (int) getLong("stealPlayer", 0);
        long roundStartedAt = getLong("roundStartedAt", System.currentTimeMillis());
        long stealStartedAt = getLong("stealStartedAt", 0);
        boolean finished = Boolean.TRUE.equals(currentSnapshot.getBoolean("finished"));
        StepByStepRound roundData = currentRoundData(round);
        if (roundData == null) {
            statusText.setText("Pitanja se ucitavaju iz baze...");
            setControlsEnabled(false);
            timerValue.setText("--");
            return;
        }

        int roundIndex = Math.max(0, Math.min(round - 1, rounds.size() - 1));
        int openedSteps = openedStepsForPhase(phase, roundStartedAt);
        int secondsLeft = secondsLeftForPhase(phase, roundStartedAt, stealStartedAt);
        int myPlayer = myPlayerNumber();
        boolean isMyTurn = isMyTurn(phase, activePlayer, stealPlayer, myPlayer);

        roundLabelText.setText("Runda " + round + " / 2");
        timerValue.setText(finished ? "Kraj" : secondsLeft + "s");
        currentStepValue.setText(openedSteps + " / 7");
        currentPointsValue.setText(pointsForStep(openedSteps) + " bodova");
        player1ScoreText.setText("Igrac 1: " + getLong("player1Score", 0));
        player2ScoreText.setText("Igrac 2: " + getLong("player2Score", 0));
        statusText.setText(statusText(phase, activePlayer, stealPlayer, myPlayer, finished));
        updateStepCards(roundData, openedSteps);
        updateControls(phase, finished, isMyTurn);
        updateBanner();

        maybeExpirePhase(phase, round, roundStartedAt, stealStartedAt);

        if (!finished) {
            uiHandler.postDelayed(ticker, 1000);
        }
    }

    private String statusText(String phase, int activePlayer, int stealPlayer, int myPlayer, boolean finished) {
        if (finished) {
            return "Kraj igre. Konacan rezultat je prikazan iznad.";
        }
        String baseMessage = getString("statusMessage", "");
        if ("steal".equals(phase)) {
            return stealPlayer == myPlayer
                    ? "Tvoja sansa za 5 bodova. Imas 10 sekundi."
                    : "Protivnik ima sansu za 5 bodova.";
        }
        if (activePlayer == myPlayer) {
            return "Tvoja runda. Pogodi pojam pre isteka vremena.";
        }
        return baseMessage.isEmpty() ? "Cekas potez drugog igraca." : baseMessage;
    }

    private void updateStepCards(StepByStepRound roundData, int openedSteps) {
        List<String> hints = roundData.getSteps();
        for (int i = 0; i < stepViews.length; i++) {
            if (i < openedSteps && i < hints.size()) {
                stepViews[i].setText(hints.get(i));
                stepViews[i].setBackgroundColor(0xFFF6F2FF);
                stepViews[i].setTextColor(0xFF1E1E2F);
            } else {
                stepViews[i].setText((i + 1) + ". Zakljucano");
                stepViews[i].setBackgroundColor(0xFFECECEC);
                stepViews[i].setTextColor(0xFF7D7D7D);
            }
        }
    }

    private void updateControls(String phase, boolean finished, boolean isMyTurn) {
        answerInput.setEnabled(!finished && isMyTurn);
        confirmButton.setEnabled(!finished && isMyTurn);
        giveUpButton.setEnabled(!finished && isMyTurn && "playing".equals(phase));

        newGameButton.setVisibility(finished ? View.VISIBLE : View.GONE);
        newGameButton.setText("Nova igra");
    }

    private void setControlsEnabled(boolean enabled) {
        answerInput.setEnabled(enabled);
        confirmButton.setEnabled(enabled);
        giveUpButton.setEnabled(enabled);
    }

    private void updateBanner() {
        String message = getString("statusMessage", "");
        if (message.isEmpty()) {
            resultBanner.setVisibility(View.GONE);
            return;
        }
        resultBanner.setVisibility(View.VISIBLE);
        resultBanner.setBackgroundColor(0xFFF6F2FF);
        resultBannerTitle.setText("Stanje igre");
        resultBannerMessage.setText(message);
    }

    private void submitAnswer() {
        if (currentSnapshot == null || answerInput.getText() == null) {
            return;
        }
        String answer = answerInput.getText().toString().trim();
        if (answer.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi odgovor.", Toast.LENGTH_SHORT).show();
            return;
        }

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }

            int round = snapshot.getLong("round").intValue();
            String phase = snapshot.getString("phase");
            int activePlayer = snapshot.getLong("activePlayer").intValue();
            int stealPlayer = snapshot.getLong("stealPlayer").intValue();
            int myPlayer = myPlayerNumber(snapshot);

            if (!isMyTurn(phase, activePlayer, stealPlayer, myPlayer)) {
                return null;
            }

        StepByStepRound roundData = currentRoundData(round);
        if (roundData == null) {
            return null;
        }
        boolean correct = normalized(answer).equals(normalized(roundData.getAnswer()));
            Map<String, Object> updates = new HashMap<>();

            if (correct) {
                if ("steal".equals(phase)) {
                    addScore(snapshot, updates, stealPlayer, 5);
                    updates.put("statusMessage", "Igrac " + stealPlayer + " je ukrao rundu i osvojio 5 bodova.");
                } else {
                    int openedSteps = openedStepsFor(snapshot);
                    int points = pointsForStep(openedSteps);
                    addScore(snapshot, updates, activePlayer, points);
                    updates.put("statusMessage", "Igrac " + activePlayer + " je pogodio u " + openedSteps
                            + ". koraku i osvojio " + points + " bodova.");
                }
                applyNextRound(updates, round);
            } else {
                updates.put("statusMessage", "Odgovor nije tacan. Pokusaj ponovo dok imas vremena.");
            }

            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(ignored -> answerInput.setText(""));
    }

    private void giveUpRound() {
        if (currentSnapshot == null) {
            return;
        }
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(matchRef);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = snapshot.getString("phase");
            int activePlayer = snapshot.getLong("activePlayer").intValue();
            int myPlayer = myPlayerNumber(snapshot);
            if (!"playing".equals(phase) || activePlayer != myPlayer) {
                return null;
            }

            int opponent = activePlayer == 1 ? 2 : 1;
            Map<String, Object> updates = new HashMap<>();
            updates.put("phase", "steal");
            updates.put("stealPlayer", opponent);
            updates.put("stealStartedAt", System.currentTimeMillis());
            updates.put("statusMessage", "Igrac " + activePlayer + " nije pogodio. Igrac "
                    + opponent + " ima 10 sekundi za 5 bodova.");
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(matchRef, updates, SetOptions.merge());
            return null;
        });
    }

    private void maybeExpirePhase(String phase, int round, long roundStartedAt, long stealStartedAt) {
        long now = System.currentTimeMillis();
        if ("playing".equals(phase) && now - roundStartedAt >= ROUND_DURATION_MS) {
            int activePlayer = (int) getLong("activePlayer", 1);
            int opponent = activePlayer == 1 ? 2 : 1;
            Map<String, Object> updates = new HashMap<>();
            updates.put("phase", "steal");
            updates.put("stealPlayer", opponent);
            updates.put("stealStartedAt", now);
            updates.put("statusMessage", "Vreme je isteklo. Igrac " + opponent
                    + " ima 10 sekundi za 5 bodova.");
            updates.put("updatedAt", FieldValue.serverTimestamp());
            matchRef.set(updates, SetOptions.merge());
        } else if ("steal".equals(phase) && now - stealStartedAt >= STEAL_DURATION_MS) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("statusMessage", "Ukradeni pokusaj je istekao bez bodova.");
            applyNextRound(updates, round);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            matchRef.set(updates, SetOptions.merge());
        }
    }

    private void applyNextRound(Map<String, Object> updates, int currentRound) {
        if (currentRound >= 2) {
            updates.put("finished", true);
            updates.put("phase", "finished");
            updates.put("activePlayer", 0);
            updates.put("stealPlayer", 0);
            return;
        }
        updates.put("round", currentRound + 1);
        updates.put("phase", "playing");
        updates.put("activePlayer", 2);
        updates.put("stealPlayer", 0);
        updates.put("roundStartedAt", System.currentTimeMillis());
        updates.put("stealStartedAt", 0);
    }

    private void resetMatch() {
        matchRef.set(newMatchState(System.currentTimeMillis()));
    }

    private int openedStepsFor(DocumentSnapshot snapshot) {
        long startedAt = snapshot.getLong("roundStartedAt") == null
                ? System.currentTimeMillis()
                : snapshot.getLong("roundStartedAt");
        return openedStepsForPhase(snapshot.getString("phase"), startedAt);
    }

    private int openedStepsForPhase(String phase, long roundStartedAt) {
        if (!"playing".equals(phase) && !"steal".equals(phase)) {
            return 7;
        }
        int elapsed = (int) Math.max(0, System.currentTimeMillis() - roundStartedAt);
        return Math.max(1, Math.min(7, 1 + (elapsed / 10_000)));
    }

    private int secondsLeftForPhase(String phase, long roundStartedAt, long stealStartedAt) {
        long now = System.currentTimeMillis();
        if ("steal".equals(phase)) {
            return Math.max(0, (int) ((STEAL_DURATION_MS - (now - stealStartedAt)) / 1000));
        }
        if ("playing".equals(phase)) {
            return Math.max(0, (int) ((ROUND_DURATION_MS - (now - roundStartedAt)) / 1000));
        }
        return 0;
    }

    private int pointsForStep(int openedSteps) {
        return Math.max(8, 22 - (openedSteps * 2));
    }

    private void addScore(DocumentSnapshot snapshot, Map<String, Object> updates, int player, int points) {
        String key = player == 1 ? "player1Score" : "player2Score";
        Long value = snapshot.getLong(key);
        long current = value == null ? 0 : value;
        updates.put(key, current + points);
    }

    private int myPlayerNumber() {
        return myPlayerNumber(currentSnapshot);
    }

    private int myPlayerNumber(DocumentSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        if (currentUserId.equals(snapshot.getString("player1Id"))) {
            return 1;
        }
        if (currentUserId.equals(snapshot.getString("player2Id"))) {
            return 2;
        }
        return 0;
    }

    private boolean isMyTurn(String phase, int activePlayer, int stealPlayer, int myPlayer) {
        if (myPlayer == 0) {
            return false;
        }
        if ("steal".equals(phase)) {
            return stealPlayer == myPlayer;
        }
        return "playing".equals(phase) && activePlayer == myPlayer;
    }

    private String getString(String key, String fallback) {
        String value = currentSnapshot.getString(key);
        return value == null ? fallback : value;
    }

    private long getLong(String key, long fallback) {
        Long value = currentSnapshot.getLong(key);
        return value == null ? fallback : value;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalized(String value) {
        String withoutMarks = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutMarks.trim().toLowerCase(Locale.ROOT);
    }

    private StepByStepRound currentRoundData(int round) {
        int index = round - 1;
        if (index < 0 || index >= rounds.size()) {
            return null;
        }
        return rounds.get(index);
    }

    private void scrollToTop() {
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }
}
