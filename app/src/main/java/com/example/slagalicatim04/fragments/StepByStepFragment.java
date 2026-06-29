package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.PlayerHeaderLoader;
import com.example.slagalicatim04.friends.GameSessionRepository;
import com.example.slagalicatim04.multiplayer.TestRoomPlayerProvider;
import com.example.slagalicatim04.repositories.MatchForfeitRepository;
import com.example.slagalicatim04.stepbystep.StepByStepGameService;
import com.example.slagalicatim04.stepbystep.StepByStepMatchRepository;
import com.example.slagalicatim04.stepbystep.StepByStepMatchState;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.example.slagalicatim04.stepbystep.StepByStepRound;
import com.example.slagalicatim04.stepbystep.StepByStepRoundRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class StepByStepFragment extends Fragment implements ExitConfirmationHandler {
    private final TextView[] stepViews = new TextView[7];
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderCurrentState;
    private final StepByStepRoundRepository roundRepository = new StepByStepRoundRepository();
    private final StepByStepGameService gameService = new StepByStepGameService();
    private final List<StepByStepRound> rounds = new ArrayList<>();

    private StepByStepMatchRepository matchRepository;
    private MatchForfeitRepository forfeitRepository;
    private ListenerRegistration listenerRegistration;
    private StepByStepPlayerSession playerSession;
    private StepByStepMatchState currentState;
    private String roomId = StepByStepMatchRepository.DEFAULT_MATCH_ID;
    private long lastClockTickAt;
    private String lastRenderedPhase = "";
    private boolean navigatedToMyNumber;

    private ScrollView scrollView;
    private TextView roundLabelText;
    private TextView statusText;
    private TextView timerValue;
    private TextView currentStepValue;
    private TextView currentPointsValue;
    private TextView player1ScoreText;
    private TextView player2ScoreText;
    private ImageView player1Avatar;
    private ImageView player2Avatar;
    private View resultBanner;
    private TextView resultBannerTitle;
    private TextView resultBannerMessage;
    private TextView answerHelpText;
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
        playerSession = resolveCurrentUser();
        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        matchRepository = new StepByStepMatchRepository(gameService, roomId);
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
        if (!navigatedToMyNumber) {
            new GameSessionRepository().abandonRoom(roomId);
        }
        super.onDestroyView();
    }

    private void bindViews(View view) {
        scrollView = view.findViewById(R.id.stepByStepScrollView);
        roundLabelText = view.findViewById(R.id.roundLabelText);
        statusText = view.findViewById(R.id.statusText);
        timerValue = view.findViewById(R.id.timerValue);
        currentStepValue = view.findViewById(R.id.currentStepValue);
        currentPointsValue = view.findViewById(R.id.currentPointsValue);
        currentPointsValue.setVisibility(View.GONE);
        player1ScoreText = view.findViewById(R.id.player1ScoreText);
        player2ScoreText = view.findViewById(R.id.player2ScoreText);
        player1Avatar = view.findViewById(R.id.stepPlayer1Avatar);
        player2Avatar = view.findViewById(R.id.stepPlayer2Avatar);
        resultBanner = view.findViewById(R.id.stepByStepResultBanner);
        resultBannerTitle = view.findViewById(R.id.stepByStepResultTitle);
        resultBannerMessage = view.findViewById(R.id.stepByStepResultMessage);
        answerHelpText = view.findViewById(R.id.answerHelpText);
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
                joinAndListen();
            }

            @Override
            public void onError(Exception error) {
                showError(error);
                statusText.setText("Nema podataka u bazi za Korak po korak.");
            }
        });
    }

    private void joinAndListen() {
        matchRepository.ensureParticipant(playerSession, this::listenToMatch, this::showError);
    }

    private void listenToMatch() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        listenerRegistration = matchRepository.listen(new StepByStepMatchRepository.MatchListener() {
            @Override
            public void onStateChanged(StepByStepMatchState state) {
                currentState = state;
                renderCurrentState();
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    private StepByStepPlayerSession resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = new TestRoomPlayerProvider(requireContext()).getPlayerId();
        String userName;

        if (authUser != null) {
            userName = authUser.getUsername().isEmpty() ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            userName = firebaseUser.getEmail() == null ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            userName = "Gost";
        }
        return new StepByStepPlayerSession(userId, userName);
    }

    private void renderCurrentState() {
        uiHandler.removeCallbacks(ticker);
        if (currentState == null || !isAdded()) {
            return;
        }

        if (currentState.effectivePhase().startsWith("myNumber")) {
            navigateToMyNumber();
            return;
        }

        if (!currentState.isStepByStepGame()) {
            setControlsEnabled(false);
            return;
        }

        StepByStepRound roundData = currentRoundData(currentState.getRound());
        if (roundData == null) {
            statusText.setText("Pitanja se ucitavaju iz baze...");
            setControlsEnabled(false);
            timerValue.setText("--");
            return;
        }

        int myPlayer = currentState.playerNumber(playerSession.getId());
        if (!currentState.isSoloChallenge() && (currentState.isForfeited(currentState.getPlayer1Id())
                || currentState.isForfeited(currentState.getPlayer2Id()))) {
            matchRepository.resolveForfeitTurn(currentState);
        }
        publishSharedClock(myPlayer);
        int openedSteps = gameService.openedSteps(currentState);
        int secondsLeft = gameService.secondsLeft(currentState);
        boolean waitingForServerTime = gameService.waitingForServerTime(currentState);
        boolean isMyTurn = gameService.isMyTurn(currentState, myPlayer);
        String phase = currentState.effectivePhase();
        if (!phase.equals(lastRenderedPhase)) {
            lastRenderedPhase = phase;
            lastClockTickAt = System.currentTimeMillis();
        }

        roundLabelText.setText("Runda " + currentState.getRound() + " / "
                + (currentState.isSoloChallenge() ? 1 : 2));
        timerValue.setText(timerText(currentState, phase, waitingForServerTime, secondsLeft));
        currentStepValue.setText(openedSteps + " / 7");
        player1ScoreText.setText(playerLabel(currentState.getPlayer1Id(),
                        currentState.getPlayer1Name(), "Igrac 1") + ": "
                + currentState.getPlayer1Score());
        player2ScoreText.setText(playerLabel(currentState.getPlayer2Id(),
                        currentState.getPlayer2Name(), "Igrac 2") + ": "
                + currentState.getPlayer2Score());
        updateHeaderVisibility(currentState.isSoloChallenge());
        PlayerHeaderLoader.loadAvatar(currentState.getPlayer1Id(), player1Avatar);
        if (!currentState.isSoloChallenge()) {
            PlayerHeaderLoader.loadAvatar(currentState.getPlayer2Id(), player2Avatar);
        }
        updatePlayerScoreStyle(myPlayer);
        statusText.setText(waitingForServerTime
                ? "Sinhronizuje se pocetak runde..."
                : soloStatusText(currentState, myPlayer));
        updateStepCards(roundData, openedSteps);
        updateControls(phase, currentState.isFinished(), !waitingForServerTime && isMyTurn);
        updateBanner();

        matchRepository.expireIfNeeded(playerSession, currentState);
        if (!currentState.isFinished()
                && (currentState.isSoloChallenge() || currentState.hasSecondPlayer())) {
            uiHandler.postDelayed(ticker, 1000);
        }
    }

    private String timerText(StepByStepMatchState state, String phase,
                             boolean waitingForServerTime, int secondsLeft) {
        if (state.isFinished()) {
            return "Kraj";
        }
        if (StepByStepMatchState.PHASE_WAITING.equals(phase) || waitingForServerTime) {
            return "--";
        }
        return secondsLeft + "s";
    }

    private String pointsText(String phase, boolean waitingForServerTime, int openedSteps) {
        if (StepByStepMatchState.PHASE_WAITING.equals(phase) || waitingForServerTime) {
            return "--";
        }
        return gameService.pointsForStep(openedSteps) + " bodova";
    }

    private void publishSharedClock(int myPlayer) {
        if (currentState.isFinished()
                || gameService.waitingForServerTime(currentState)
                || !gameService.isMyTurn(currentState, myPlayer)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClockTickAt < 900) {
            return;
        }
        lastClockTickAt = now;
        matchRepository.tickClock(playerSession, currentState);
    }

    private void updatePlayerScoreStyle(int myPlayer) {
        player1ScoreText.setTypeface(null, myPlayer == 1 ? Typeface.BOLD : Typeface.NORMAL);
        player2ScoreText.setTypeface(null, myPlayer == 2 ? Typeface.BOLD : Typeface.NORMAL);
        player1ScoreText.setBackgroundColor(myPlayer == 1 ? 0xFFEFEAF8 : 0xFFF5F5F5);
        player2ScoreText.setBackgroundColor(myPlayer == 2 ? 0xFFEFEAF8 : 0xFFF5F5F5);
        player1ScoreText.setTextColor(currentState != null && currentState.isForfeited(currentState.getPlayer1Id())
                ? 0xFFD32F2F : Color.BLACK);
        player2ScoreText.setTextColor(currentState != null && currentState.isForfeited(currentState.getPlayer2Id())
                ? 0xFFD32F2F : Color.BLACK);
    }

    private void updateHeaderVisibility(boolean soloChallenge) {
        int visibility = soloChallenge ? View.GONE : View.VISIBLE;
        player2ScoreText.setVisibility(visibility);
        player2Avatar.setVisibility(visibility);
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
        giveUpButton.setEnabled(!finished && isMyTurn
                && gameService.isRoundPhase(phase));
        updateAnswerHint(phase, finished, isMyTurn);

        newGameButton.setVisibility(finished ? View.VISIBLE : View.GONE);
        newGameButton.setText("Nova igra");
    }

    private void updateAnswerHint(String phase, boolean finished, boolean isMyTurn) {
        if (finished) {
            answerHelpText.setText("Igra je zavrsena.");
        } else if (!isMyTurn) {
            answerHelpText.setText(currentState != null && currentState.isSoloChallenge()
                    ? "Samostalna partija je u toku."
                    : "Cekajte svoj red. Polje za odgovor ce se otkljucati kada budete na potezu.");
        } else if (gameService.isStealPhase(phase)) {
            answerHelpText.setText(currentState != null && currentState.isSoloChallenge()
                    ? "Imas jos jednu sansu za 5 bodova."
                    : "Protivnik nije pogodio. Unesite odgovor za 5 bodova.");
        } else {
            answerHelpText.setText("Tvoj red. Unesite konacni pojam.");
        }
        answerInput.setHint("Tvoj odgovor");
    }

    private void setControlsEnabled(boolean enabled) {
        answerInput.setEnabled(enabled);
        confirmButton.setEnabled(enabled);
        giveUpButton.setEnabled(enabled);
    }

    private void updateBanner() {
        String message = bannerMessage();
        if (message.isEmpty()) {
            resultBanner.setVisibility(View.GONE);
            return;
        }
        resultBanner.setVisibility(View.VISIBLE);
        resultBanner.setBackgroundColor(0xFFF6F2FF);
        resultBannerTitle.setText("Stanje igre");
        resultBannerMessage.setText(message);
    }

    private String bannerMessage() {
        if (currentState.isFinished()) {
            return finalScoreMessage();
        }
        if (!isEmpty(currentState.getStatusMessage())) {
            return sanitizeSoloMessage(currentState.getStatusMessage());
        }
        if (currentState.getRound() == 2 && !isEmpty(currentState.getRound1Result())) {
            return sanitizeSoloMessage(currentState.getRound1Result());
        }
        return "";
    }

    private String soloStatusText(StepByStepMatchState state, int myPlayer) {
        String base = gameService.statusText(state, myPlayer);
        return sanitizeSoloMessage(base);
    }

    private String sanitizeSoloMessage(String message) {
        if (currentState == null || !currentState.isSoloChallenge() || isEmpty(message)) {
            return message;
        }
        String normalized = message.toLowerCase();
        if (normalized.contains("napustio partiju")) {
            return "Samostalna partija je u toku.";
        }
        if (normalized.contains("protivnik nije pogodio")) {
            return "Imas jos jednu sansu za 5 bodova.";
        }
        if (normalized.contains("cekas da se drugi igrac pridruzi")
                || normalized.contains("ceka se drugi igrac")
                || normalized.contains("drugi igrac trenutno odgovara")) {
            return "Samostalna partija je u toku.";
        }
        return message;
    }

    private String finalScoreMessage() {
        long p1 = currentState.getPlayer1Score();
        long p2 = currentState.getPlayer2Score();
        if (currentState.isSoloChallenge()) {
            StringBuilder builder = new StringBuilder();
            if (!isEmpty(currentState.getRound1Result())) {
                builder.append(currentState.getRound1Result()).append("\n");
            }
            if (!isEmpty(currentState.getRound2Result())) {
                builder.append(currentState.getRound2Result()).append("\n");
            }
            builder.append("Konacno: ").append(p1);
            return builder.toString();
        }
        String winner;
        if (p1 > p2) {
            winner = "Pobednik: Igrac 1";
        } else if (p2 > p1) {
            winner = "Pobednik: Igrac 2";
        } else {
            winner = "Nereseno";
        }
        StringBuilder builder = new StringBuilder();
        if (!isEmpty(currentState.getRound1Result())) {
            builder.append(currentState.getRound1Result()).append("\n");
        }
        if (!isEmpty(currentState.getRound2Result())) {
            builder.append(currentState.getRound2Result()).append("\n");
        }
        builder.append("Konacno - Igrac 1: ").append(p1)
                .append(", Igrac 2: ").append(p2)
                .append("\n").append(winner);
        return builder.toString();
    }

    private void submitAnswer() {
        if (currentState == null || answerInput.getText() == null) {
            return;
        }
        String answer = answerInput.getText().toString().trim();
        if (answer.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi odgovor.", Toast.LENGTH_SHORT).show();
            return;
        }
        StepByStepRound roundData = currentRoundData(currentState.getRound());
        if (roundData == null) {
            return;
        }
        matchRepository.submitAnswer(playerSession, roundData, answer, this::showError);
        answerInput.setText("");
    }

    private void giveUpRound() {
        matchRepository.giveUpRound(playerSession, this::showError);
    }

    private void resetMatch() {
        matchRepository.resetMatch(playerSession);
        scrollToTop();
    }

    private StepByStepRound currentRoundData(int round) {
        int index = round - 1;
        if (index < 0 || index >= rounds.size()) {
            return null;
        }
        return rounds.get(index);
    }

    private void showError(Exception error) {
        if (isAdded()) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
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

    private void scrollToTop() {
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }

    private void navigateToMyNumber() {
        if (navigatedToMyNumber || getView() == null) {
            return;
        }
        navigatedToMyNumber = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.myNumberFragment, args);
    }

    @Override
    public boolean handleExitRequest() {
        if (currentState == null || currentState.isFinished()
                || currentState.effectivePhase().startsWith("myNumber")) {
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
}
