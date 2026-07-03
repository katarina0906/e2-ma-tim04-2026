package com.example.slagalicatim04.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.PlayerHeaderLoader;
import com.example.slagalicatim04.friends.GameSessionRepository;
import com.example.slagalicatim04.multiplayer.TestRoomPlayerProvider;
import com.example.slagalicatim04.repositories.MatchForfeitRepository;
import com.example.slagalicatim04.skocko.SkockoMatchRepository;
import com.example.slagalicatim04.skocko.SkockoMatchState;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Arrays;
import java.util.List;

public class SkockoFragment extends Fragment implements ExitConfirmationHandler {
    private static final int CODE_LEN = 4;
    private static final int NUM_SYMBOLS = 6;
    private static final int MAX_ATTEMPTS = 6;
    private static final int SYMBOL_COLOR = 0xFF7E57C2;

    private static final String[] SYMBOLS = {
            "\u263A", "\u25A0", "\u25CF", "\u2665", "\u25B2", "\u2605",
    };

    private static final int[] PALETTE_IDS = {
            R.id.skPal0, R.id.skPal1, R.id.skPal2, R.id.skPal3, R.id.skPal4, R.id.skPal5,
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderCurrentState;
    private final int[] draft = {-1, -1, -1, -1};
    private final TextView[] draftSlots = new TextView[CODE_LEN];
    private final TextView[][] guessSlots = new TextView[MAX_ATTEMPTS][CODE_LEN];
    private final View[][] pegViews = new View[MAX_ATTEMPTS][CODE_LEN];

    private TextView roundText;
    private TextView statusText;
    private TextView timerText;
    private TextView score0;
    private TextView score1;
    private TextView player1MetaText;
    private TextView player2MetaText;
    private TextView resultText;
    private LinearLayout historyBlock;
    private View stealCard;
    private Button clearButton;
    private Button submitButton;
    private Button nextRoundButton;

    private SkockoMatchRepository repository;
    private MatchForfeitRepository forfeitRepository;
    private SkockoMatchState currentState;
    private StepByStepPlayerSession playerSession;
    private ListenerRegistration listenerRegistration;
    private String roomId = SkockoMatchRepository.DEFAULT_MATCH_ID;
    private int lastRenderedRound = -1;
    private String lastRenderedPhase = "";
    private String lastPhaseToken = "";
    private int lastActivePlayer = -1;
    private long localPhaseStartedAt;
    private boolean phaseExpirySent;
    private boolean navigatedToStepByStep;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skocko, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        buildHistoryRows();

        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        playerSession = resolveCurrentUser();
        repository = new SkockoMatchRepository(roomId);
        forfeitRepository = new MatchForfeitRepository(roomId);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!handleExitRequest()) {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        for (int i = 0; i < NUM_SYMBOLS; i++) {
            final int symbol = i;
            view.findViewById(PALETTE_IDS[i]).setOnClickListener(v -> onPaletteTap(symbol));
        }
        clearButton.setOnClickListener(v -> clearDraft());
        submitButton.setOnClickListener(v -> submitGuess());
        nextRoundButton.setVisibility(View.GONE);

        setInputsEnabled(false);
        statusText.setText("Povezivanje sa Skočko test sobom...");
        listenerRegistration = repository.listen(new SkockoMatchRepository.Listener() {
            @Override
            public void onStateChanged(SkockoMatchState state) {
                currentState = state;
                renderCurrentState();
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        uiHandler.removeCallbacks(ticker);
        if (!navigatedToStepByStep) {
            new GameSessionRepository().abandonRoom(roomId);
        }
        super.onDestroyView();
    }

    private void bindViews(View view) {
        roundText = view.findViewById(R.id.skRoundText);
        statusText = view.findViewById(R.id.skStatusText);
        timerText = view.findViewById(R.id.skTimerText);
        score0 = view.findViewById(R.id.skScore0);
        score1 = view.findViewById(R.id.skScore1);
        player1MetaText = view.findViewById(R.id.skPlayer1Meta);
        player2MetaText = view.findViewById(R.id.skPlayer2Meta);
        resultText = view.findViewById(R.id.skResultText);
        historyBlock = view.findViewById(R.id.skHistoryBlock);
        stealCard = view.findViewById(R.id.skStealCard);
        draftSlots[0] = view.findViewById(R.id.skDraft0);
        draftSlots[1] = view.findViewById(R.id.skDraft1);
        draftSlots[2] = view.findViewById(R.id.skDraft2);
        draftSlots[3] = view.findViewById(R.id.skDraft3);
        clearButton = view.findViewById(R.id.skClearButton);
        submitButton = view.findViewById(R.id.skSubmitButton);
        nextRoundButton = view.findViewById(R.id.skNextRoundButton);
    }

    private void buildHistoryRows() {
        Context context = requireContext();
        float density = context.getResources().getDisplayMetrics().density;
        int margin = Math.round(4 * density);
        int slotHeight = Math.round(40 * density);
        int pegSize = Math.round(12 * density);

        for (int rowIndex = 0; rowIndex < MAX_ATTEMPTS; rowIndex++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = margin;
            row.setLayoutParams(rowParams);

            TextView number = new TextView(context);
            number.setText(String.valueOf(rowIndex + 1));
            number.setWidth(Math.round(26 * density));
            number.setGravity(Gravity.CENTER);
            number.setTextColor(0xFF888888);
            row.addView(number);

            for (int column = 0; column < CODE_LEN; column++) {
                TextView slot = new TextView(context);
                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(0, slotHeight, 1f);
                params.setMarginEnd(margin / 2);
                slot.setLayoutParams(params);
                slot.setBackgroundResource(R.drawable.skocko_slot_bg);
                slot.setGravity(Gravity.CENTER);
                slot.setTextColor(SYMBOL_COLOR);
                slot.setTextSize(24);
                row.addView(slot);
                guessSlots[rowIndex][column] = slot;
            }

            GridLayout pegGrid = new GridLayout(context);
            pegGrid.setColumnCount(2);
            pegGrid.setRowCount(2);
            for (int i = 0; i < CODE_LEN; i++) {
                View peg = new View(context);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = pegSize;
                params.height = pegSize;
                params.setMargins(2, 2, 2, 2);
                peg.setLayoutParams(params);
                peg.setBackgroundResource(R.drawable.skocko_peg_empty);
                pegGrid.addView(peg);
                pegViews[rowIndex][i] = peg;
            }
            row.addView(pegGrid);
            historyBlock.addView(row);
        }
    }

    private void renderCurrentState() {
        uiHandler.removeCallbacks(ticker);
        if (currentState == null || !isAdded()) {
            return;
        }
        if ("stepByStep".equals(currentState.getCurrentGame())) {
            navigateToStepByStep();
            return;
        }

        int myPlayer = currentState.playerNumber(playerSession.getId());
        boolean phaseChanged = currentState.getRound() != lastRenderedRound
                || !currentState.getPhase().equals(lastRenderedPhase)
                || currentState.getActivePlayer() != lastActivePlayer
                || !currentState.getPhaseToken().equals(lastPhaseToken);
        if (phaseChanged) {
            lastRenderedRound = currentState.getRound();
            lastRenderedPhase = currentState.getPhase();
            lastPhaseToken = currentState.getPhaseToken();
            lastActivePlayer = currentState.getActivePlayer();
            localPhaseStartedAt = SystemClock.elapsedRealtime();
            phaseExpirySent = false;
            clearDraft();
        }
        if (currentState.isForfeited(currentState.getPlayer1Id())
                || currentState.isForfeited(currentState.getPlayer2Id())) {
            repository.resolveForfeitTurn(currentState);
        }

        roundText.setText(getString(R.string.sk_round_fmt, currentState.getRound(),
                currentState.isSoloChallenge() ? 1 : 2));
        score0.setText(playerLabel(currentState.getPlayer1Id(), currentState.getPlayer1Name(), "Igrac 1")
                + ": " + (int) currentState.getPlayer1Score());
        score1.setText(playerLabel(currentState.getPlayer2Id(), currentState.getPlayer2Name(), "Igrac 2")
                + ": " + (int) currentState.getPlayer2Score());
        score1.setVisibility(currentState.isSoloChallenge() ? View.GONE : View.VISIBLE);
        score0.setTextColor(currentState.isForfeited(currentState.getPlayer1Id()) ? 0xFFD32F2F : Color.BLACK);
        score1.setTextColor(currentState.isForfeited(currentState.getPlayer2Id()) ? 0xFFD32F2F : Color.BLACK);
        player2MetaText.setVisibility(currentState.isSoloChallenge() ? View.GONE : View.VISIBLE);
        PlayerHeaderLoader.loadProfileSummary(currentState.getPlayer1Id(), player1MetaText);
        if (!currentState.isSoloChallenge()) {
            PlayerHeaderLoader.loadProfileSummary(currentState.getPlayer2Id(), player2MetaText);
        }
        renderHistory();

        boolean steal = SkockoMatchState.PHASE_STEAL.equals(currentState.getPhase());
        stealCard.setVisibility(steal ? View.VISIBLE : View.GONE);
        long duration = steal ? SkockoMatchRepository.STEAL_DURATION_MS
                : SkockoMatchRepository.ROUND_DURATION_MS;
        int secondsLeft = localSecondsLeft(duration);
        if (steal || SkockoMatchState.PHASE_ROUND.equals(currentState.getPhase())) {
            timerText.setText(getString(R.string.sk_timer_fmt, 0, secondsLeft));
        } else {
            timerText.setText("");
        }

        boolean myTurn = !currentState.isFinished()
                && myPlayer != 0
                && myPlayer == currentState.getActivePlayer()
                && (SkockoMatchState.PHASE_ROUND.equals(currentState.getPhase()) || steal);
        setInputsEnabled(myTurn);
        statusText.setText(statusForPlayer(myPlayer, myTurn));

        if (currentState.isFinished()) {
            resultText.setText(currentState.isSoloChallenge()
                    ? "Rezultat: " + (int) currentState.getPlayer1Score()
                    : getString(
                    R.string.sk_game_over_fmt,
                    (int) currentState.getPlayer1Score(),
                    (int) currentState.getPlayer2Score()
            ));
        } else {
            resultText.setText(currentState.getStatusMessage());
        }

        if (secondsLeft <= 0 && myTurn && !phaseExpirySent) {
            phaseExpirySent = true;
            setInputsEnabled(false);
            repository.expirePhase(playerSession, currentState);
        }
        if (!currentState.isFinished()
                && !SkockoMatchState.PHASE_WAITING.equals(currentState.getPhase())) {
            uiHandler.postDelayed(ticker, 500);
        }
    }

    private void renderHistory() {
        clearHistory();
        List<List<Integer>> guesses = currentState.getGuesses();
        List<List<Integer>> feedback = currentState.getFeedback();
        int rows = Math.min(MAX_ATTEMPTS, guesses.size());
        for (int row = 0; row < rows; row++) {
            List<Integer> guess = guesses.get(row);
            for (int column = 0; column < Math.min(CODE_LEN, guess.size()); column++) {
                int symbol = guess.get(column);
                if (symbol >= 0 && symbol < SYMBOLS.length) {
                    guessSlots[row][column].setText(SYMBOLS[symbol]);
                }
            }
            if (row < feedback.size() && feedback.get(row).size() >= 2) {
                setPegs(row, feedback.get(row).get(0), feedback.get(row).get(1), true);
            }
        }
    }

    private void clearHistory() {
        for (int row = 0; row < MAX_ATTEMPTS; row++) {
            for (int column = 0; column < CODE_LEN; column++) {
                guessSlots[row][column].setText("");
            }
            setPegs(row, 0, 0, false);
        }
    }

    private void setPegs(int row, int exact, int partial, boolean active) {
        for (int i = 0; i < CODE_LEN; i++) {
            if (!active) {
                pegViews[row][i].setBackgroundResource(R.drawable.skocko_peg_empty);
            } else if (i < exact) {
                pegViews[row][i].setBackgroundResource(R.drawable.skocko_peg_exact);
            } else if (i < exact + partial) {
                pegViews[row][i].setBackgroundResource(R.drawable.skocko_peg_partial);
            } else {
                pegViews[row][i].setBackgroundResource(R.drawable.skocko_peg_empty);
            }
        }
    }

    private String statusForPlayer(int myPlayer, boolean myTurn) {
        if (!currentState.isSoloChallenge() && currentState.hasForfeit()) {
            String status = currentState.getStatusMessage();
            return isEmpty(status) ? "Protivnik je napustio partiju." : status;
        }
        if (currentState.isFinished()) {
            return "Skočko je završen.";
        }
        if (myPlayer == 0) {
            return "Ova test soba je popunjena.";
        }
        if (myTurn) {
            return SkockoMatchState.PHASE_STEAL.equals(currentState.getPhase())
                    ? "Tvoj ukradeni pokušaj. Imaš jednu šansu."
                    : "Tvoj red. Složi kombinaciju i pošalji.";
        }
        if (currentState.isSoloChallenge()) {
            return "Samostalna partija je u toku.";
        }
        return "Igrač " + currentState.getActivePlayer()
                + " je na potezu. Čekaj svoj red.";
    }

    private void onPaletteTap(int symbolIndex) {
        if (!canAcceptInput()) {
            return;
        }
        for (int i = 0; i < CODE_LEN; i++) {
            if (draft[i] < 0) {
                draft[i] = symbolIndex;
                updateDraftUi();
                return;
            }
        }
        draft[CODE_LEN - 1] = symbolIndex;
        updateDraftUi();
    }

    private void submitGuess() {
        if (!canAcceptInput()) {
            return;
        }
        for (int symbol : draft) {
            if (symbol < 0) {
                Toast.makeText(
                        requireContext(), R.string.sk_draft_incomplete, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        int[] guess = Arrays.copyOf(draft, draft.length);
        setInputsEnabled(false);
        repository.submitGuess(playerSession, guess, this::showError);
        clearDraft();
    }

    private boolean canAcceptInput() {
        if (currentState == null || currentState.isFinished()) {
            return false;
        }
        int myPlayer = currentState.playerNumber(playerSession.getId());
        return myPlayer != 0 && myPlayer == currentState.getActivePlayer()
                && (SkockoMatchState.PHASE_ROUND.equals(currentState.getPhase())
                || SkockoMatchState.PHASE_STEAL.equals(currentState.getPhase()));
    }

    private void clearDraft() {
        Arrays.fill(draft, -1);
        updateDraftUi();
    }

    private void updateDraftUi() {
        for (int i = 0; i < CODE_LEN; i++) {
            draftSlots[i].setText(draft[i] < 0 ? "" : SYMBOLS[draft[i]]);
        }
    }

    private void setInputsEnabled(boolean enabled) {
        clearButton.setEnabled(enabled);
        submitButton.setEnabled(enabled);
        if (getView() != null) {
            for (int id : PALETTE_IDS) {
                getView().findViewById(id).setEnabled(enabled);
            }
        }
    }

    private StepByStepPlayerSession resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = firebaseUser == null
                ? new TestRoomPlayerProvider(requireContext()).getPlayerId()
                : firebaseUser.getUid();
        String userName;
        if (authUser != null) {
            userName = authUser.getUsername().isEmpty()
                    ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            userName = firebaseUser.getEmail() == null
                    ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            userName = "Gost";
        }
        return new StepByStepPlayerSession(userId, userName);
    }

    private int localSecondsLeft(long durationMs) {
        if (localPhaseStartedAt <= 0) {
            return (int) (durationMs / 1000);
        }
        long elapsed = SystemClock.elapsedRealtime() - localPhaseStartedAt;
        return (int) Math.max(0, (durationMs - elapsed + 999) / 1000);
    }

    private void navigateToStepByStep() {
        if (navigatedToStepByStep || getView() == null) {
            return;
        }
        navigatedToStepByStep = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        Navigation.findNavController(requireView()).navigate(R.id.stepByStepFragment, args);
    }

    private void showError(Exception error) {
        if (isAdded()) {
            setInputsEnabled(canAcceptInput());
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean handleExitRequest() {
        if (currentState == null || currentState.isFinished()
                || "stepByStep".equals(currentState.getCurrentGame())) {
            return false;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Napusti partiju?")
                .setMessage("Ako izađeš sada, izgubićeš partiju. Da li želiš da napustiš igru?")
                .setNegativeButton("Ostani", null)
                .setPositiveButton("Napusti", (dialog, which) -> {
                    forfeitRepository.forfeit(playerSession.getId());
                    Navigation.findNavController(requireView()).navigate(
                            R.id.homeFragment,
                            null,
                            new androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build());
                })
                .show();
        return true;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String playerLabel(String playerId, String name, String fallback) {
        if (!isEmpty(name)) {
            return name;
        }
        return isEmpty(playerId) ? fallback : playerId;
    }
}
