package com.example.slagalicatim04.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.auth.PlayerHeaderLoader;
import com.example.slagalicatim04.mynumber.MyNumberGameService;
import com.example.slagalicatim04.mynumber.MyNumberMatchState;
import com.example.slagalicatim04.mynumber.MyNumberRepository;
import com.example.slagalicatim04.stepbystep.StepByStepMatchRepository;
import com.example.slagalicatim04.stepbystep.StepByStepPlayerSession;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class MyNumberFragment extends Fragment {
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderCurrentState;
    private final List<ExpressionToken> expressionTokens = new ArrayList<>();
    private final boolean[] usedNumbers = new boolean[6];
    private final TextView[] numberViews = new TextView[6];
    private final MaterialButton[] operatorButtons = new MaterialButton[8];

    private MyNumberRepository repository;
    private ListenerRegistration listenerRegistration;
    private StepByStepPlayerSession playerSession;
    private MyNumberMatchState currentState;
    private String roomId = StepByStepMatchRepository.DEFAULT_MATCH_ID;
    private long lastClockTickAt;
    private int lastRenderedRound = -1;

    private ScrollView scrollView;
    private TextView roundText;
    private TextView timerValue;
    private TextView statusValue;
    private TextView player1ScoreText;
    private TextView player2ScoreText;
    private ImageView player1Avatar;
    private ImageView player2Avatar;
    private View resultBanner;
    private TextView resultBannerTitle;
    private TextView resultBannerMessage;
    private TextView targetCountdown;
    private TextView numbersCountdown;
    private TextView targetValue;
    private TextView resultValue;
    private TextView scoreValue;
    private TextInputEditText expressionInput;
    private MaterialButton stopTargetButton;
    private MaterialButton stopNumbersButton;
    private MaterialButton checkExpressionButton;

    public MyNumberFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_number, container, false);
        bindViews(view);
        playerSession = resolveCurrentUser();
        if (getArguments() != null && !isEmpty(getArguments().getString("roomId"))) {
            roomId = getArguments().getString("roomId");
        }
        repository = new MyNumberRepository(roomId);
        setupActions();
        setExpressionControlsEnabled(false);
        repository.startIfNeeded();
        listen();
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
        scrollView = view.findViewById(R.id.myNumberScrollView);
        roundText = view.findViewById(R.id.myNumberRoundText);
        timerValue = view.findViewById(R.id.myNumberTimerValue);
        statusValue = view.findViewById(R.id.myNumberStatusValue);
        player1ScoreText = view.findViewById(R.id.myNumberPlayer1ScoreText);
        player2ScoreText = view.findViewById(R.id.myNumberPlayer2ScoreText);
        player1Avatar = view.findViewById(R.id.myNumberPlayer1Avatar);
        player2Avatar = view.findViewById(R.id.myNumberPlayer2Avatar);
        resultBanner = view.findViewById(R.id.myNumberResultBanner);
        resultBannerTitle = view.findViewById(R.id.myNumberResultTitle);
        resultBannerMessage = view.findViewById(R.id.myNumberResultMessage);
        targetCountdown = view.findViewById(R.id.targetCountdown);
        numbersCountdown = view.findViewById(R.id.numbersCountdown);
        targetValue = view.findViewById(R.id.targetValue);
        resultValue = view.findViewById(R.id.resultValue);
        scoreValue = view.findViewById(R.id.scoreValue);
        expressionInput = view.findViewById(R.id.expressionInput);
        stopTargetButton = view.findViewById(R.id.stopTargetButton);
        stopNumbersButton = view.findViewById(R.id.stopNumbersButton);
        checkExpressionButton = view.findViewById(R.id.checkExpressionButton);
        numberViews[0] = view.findViewById(R.id.number1);
        numberViews[1] = view.findViewById(R.id.number2);
        numberViews[2] = view.findViewById(R.id.number3);
        numberViews[3] = view.findViewById(R.id.number4);
        numberViews[4] = view.findViewById(R.id.number5);
        numberViews[5] = view.findViewById(R.id.number6);
        operatorButtons[0] = view.findViewById(R.id.openParenthesisButton);
        operatorButtons[1] = view.findViewById(R.id.closeParenthesisButton);
        operatorButtons[2] = view.findViewById(R.id.plusButton);
        operatorButtons[3] = view.findViewById(R.id.minusButton);
        operatorButtons[4] = view.findViewById(R.id.multiplyButton);
        operatorButtons[5] = view.findViewById(R.id.divideButton);
        operatorButtons[6] = view.findViewById(R.id.backspaceButton);
        operatorButtons[7] = view.findViewById(R.id.clearExpressionButton);
        resultBanner.setVisibility(View.GONE);
        numbersCountdown.setVisibility(View.GONE);
    }

    private void setupActions() {
        stopTargetButton.setOnClickListener(v -> repository.revealTarget(playerSession));
        stopNumbersButton.setOnClickListener(v -> repository.revealNumbers(playerSession));
        checkExpressionButton.setOnClickListener(v -> submitExpression());

        for (int i = 0; i < numberViews.length; i++) {
            int index = i;
            numberViews[i].setOnClickListener(v -> appendNumber(index));
        }

        operatorButtons[0].setOnClickListener(v -> appendOperator("("));
        operatorButtons[1].setOnClickListener(v -> appendOperator(")"));
        operatorButtons[2].setOnClickListener(v -> appendOperator("+"));
        operatorButtons[3].setOnClickListener(v -> appendOperator("-"));
        operatorButtons[4].setOnClickListener(v -> appendOperator("*"));
        operatorButtons[5].setOnClickListener(v -> appendOperator("/"));
        operatorButtons[6].setOnClickListener(v -> backspace());
        operatorButtons[7].setOnClickListener(v -> clearExpression());
    }

    private void listen() {
        listenerRegistration = repository.listen(new MyNumberRepository.Listener() {
            @Override
            public void onState(MyNumberMatchState state) {
                currentState = state;
                renderCurrentState();
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    private void renderCurrentState() {
        uiHandler.removeCallbacks(ticker);
        if (currentState == null || !isAdded()) {
            return;
        }

        int myPlayer = myPlayer();
        if (lastRenderedRound != currentState.getRound()) {
            lastRenderedRound = currentState.getRound();
            clearExpression();
            lastClockTickAt = System.currentTimeMillis();
            scrollToTop();
        }

        publishClockIfOwner(myPlayer);
        boolean finished = MyNumberGameService.PHASE_FINISHED.equals(currentState.getPhase());
        boolean isActivePlayer = myPlayer == currentState.getActivePlayer();
        boolean submitted = currentState.isSubmitted(myPlayer);
        boolean canUseExpression = !finished && currentState.isNumbersShown() && !submitted && myPlayer != 0;

        roundText.setText(finished ? "Moj broj - kraj" : "Runda " + currentState.getRound() + " / 2");
        timerValue.setText(timerText(finished));
        player1ScoreText.setText(playerName(currentState.getPlayer1Name(), "Igrac 1") + ": "
                + currentState.getPlayer1Score());
        player2ScoreText.setText(playerName(currentState.getPlayer2Name(), "Igrac 2") + ": "
                + currentState.getPlayer2Score());
        PlayerHeaderLoader.loadAvatar(currentState.getPlayer1Id(), player1Avatar);
        PlayerHeaderLoader.loadAvatar(currentState.getPlayer2Id(), player2Avatar);
        updatePlayerScoreStyle(myPlayer);
        targetValue.setText(currentState.isTargetShown() ? String.valueOf(currentState.getTarget()) : "?");
        updateNumberViews(canUseExpression);
        updateCountdowns();
        updateButtons(finished, isActivePlayer, canUseExpression);
        updateStatus(finished, isActivePlayer, submitted);
        updateResultArea(finished, submitted, myPlayer);

        if (!finished) {
            uiHandler.postDelayed(ticker, 1000);
        }
    }

    private void publishClockIfOwner(int myPlayer) {
        if (currentState == null
                || MyNumberGameService.PHASE_FINISHED.equals(currentState.getPhase())
                || myPlayer != currentState.getActivePlayer()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClockTickAt < 900) {
            return;
        }
        lastClockTickAt = now;
        repository.tick(playerSession);
    }

    private String timerText(boolean finished) {
        if (finished) {
            return "Kraj";
        }
        if (!currentState.isTargetShown()) {
            return currentState.getTargetRevealLeft() + "s";
        }
        if (!currentState.isNumbersShown()) {
            return currentState.getNumbersRevealLeft() + "s";
        }
        return currentState.getSecondsLeft() + "s";
    }

    private void updateNumberViews(boolean canUseExpression) {
        List<Integer> numbers = currentState.getNumbers();
        for (int i = 0; i < numberViews.length; i++) {
            boolean revealed = currentState.isNumbersShown() && i < numbers.size();
            numberViews[i].setText(revealed ? String.valueOf(numbers.get(i)) : "?");
            boolean enabled = canUseExpression && revealed && !usedNumbers[i];
            numberViews[i].setEnabled(enabled);
            numberViews[i].setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private void updateCountdowns() {
        if (!currentState.isTargetShown()) {
            targetCountdown.setVisibility(View.VISIBLE);
            targetCountdown.setText("Trazeni broj se automatski prikazuje za "
                    + currentState.getTargetRevealLeft() + "s");
        } else {
            targetCountdown.setVisibility(View.GONE);
        }
        if (currentState.isTargetShown() && !currentState.isNumbersShown()) {
            numbersCountdown.setVisibility(View.VISIBLE);
            numbersCountdown.setText("Brojevi se automatski prikazuju za "
                    + currentState.getNumbersRevealLeft() + "s");
        } else {
            numbersCountdown.setVisibility(View.GONE);
        }
    }

    private void updateButtons(boolean finished, boolean isActivePlayer, boolean canUseExpression) {
        stopTargetButton.setEnabled(!finished && isActivePlayer && !currentState.isTargetShown());
        stopNumbersButton.setEnabled(!finished && isActivePlayer
                && currentState.isTargetShown() && !currentState.isNumbersShown());
        stopTargetButton.setText(currentState.isTargetShown() ? "Broj prikazan" : "Stop broj");
        stopNumbersButton.setText(currentState.isNumbersShown() ? "Brojevi prikazani" : "Stop brojevi");
        checkExpressionButton.setEnabled(canUseExpression);
        setExpressionControlsEnabled(canUseExpression);
    }

    private void updateStatus(boolean finished, boolean isActivePlayer, boolean submitted) {
        if (finished) {
            statusValue.setText(currentState.getStatusMessage());
        } else if (submitted) {
            statusValue.setText("Tvoje resenje je poslato. Ceka se protivnik ili kraj vremena.");
        } else if (!currentState.isTargetShown()) {
            statusValue.setText(isActivePlayer
                    ? "Tvoja runda. Klikni Stop broj ili sacekaj automatsko prikazivanje."
                    : "Cekajte da igrac " + currentState.getActivePlayer() + " zaustavi trazeni broj.");
        } else if (!currentState.isNumbersShown()) {
            statusValue.setText(isActivePlayer
                    ? "Klikni Stop brojevi ili sacekaj automatsko prikazivanje."
                    : "Cekajte da igrac " + currentState.getActivePlayer() + " zaustavi brojeve.");
        } else {
            statusValue.setText("Sastavite izraz i posaljite resenje pre isteka vremena.");
        }
    }

    private void updateResultArea(boolean finished, boolean submitted, int myPlayer) {
        Integer myResult = myPlayer == 1
                ? currentState.getP1Result()
                : (myPlayer == 2 ? currentState.getP2Result() : null);
        resultValue.setText(myResult == null ? "-" : String.valueOf(myResult));
        scoreValue.setText(finished ? "Ukupni bodovi su upisani gore." : "Ceka se ishod runde.");

        if (!finished && !submitted) {
            resultBanner.setVisibility(View.GONE);
            return;
        }
        resultBanner.setVisibility(View.VISIBLE);
        resultBanner.setBackgroundColor(finished ? 0xFFEDF8F1 : 0xFFEFF3FF);
        resultBannerTitle.setText(finished ? "Moj broj zavrsen" : "Resenje poslato");
        resultBannerMessage.setText(finished
                ? currentState.getStatusMessage()
                : "Tvoj rezultat je sacuvan u bazi. Ishod se racuna kada oba igraca posalju resenje ili istekne vreme.");
    }

    private void appendNumber(int index) {
        if (currentState == null || !currentState.isNumbersShown() || usedNumbers[index]) {
            return;
        }
        List<Integer> numbers = currentState.getNumbers();
        if (index >= numbers.size() || currentState.isSubmitted(myPlayer())) {
            return;
        }
        usedNumbers[index] = true;
        expressionTokens.add(ExpressionToken.number(String.valueOf(numbers.get(index)), index));
        updateExpressionInput();
        updateNumberViews(true);
    }

    private void appendOperator(String operator) {
        if (currentState == null || !currentState.isNumbersShown() || currentState.isSubmitted(myPlayer())) {
            return;
        }
        expressionTokens.add(ExpressionToken.operator(operator));
        updateExpressionInput();
    }

    private void backspace() {
        if (expressionTokens.isEmpty()) {
            return;
        }
        ExpressionToken token = expressionTokens.remove(expressionTokens.size() - 1);
        if (token.isNumber()) {
            usedNumbers[token.numberIndex] = false;
        }
        updateExpressionInput();
        updateNumberViews(currentState != null && currentState.isNumbersShown());
    }

    private void clearExpression() {
        expressionTokens.clear();
        for (int i = 0; i < usedNumbers.length; i++) {
            usedNumbers[i] = false;
        }
        if (expressionInput != null) {
            updateExpressionInput();
        }
    }

    private void submitExpression() {
        if (currentState == null || currentState.isSubmitted(myPlayer())) {
            return;
        }
        repository.submit(playerSession, normalizeExpression(buildExpressionText()));
        setExpressionControlsEnabled(false);
        checkExpressionButton.setEnabled(false);
    }

    private void setExpressionControlsEnabled(boolean enabled) {
        expressionInput.setEnabled(enabled);
        for (MaterialButton button : operatorButtons) {
            button.setEnabled(enabled);
        }
    }

    private void updateExpressionInput() {
        expressionInput.setText(buildExpressionText());
    }

    private String buildExpressionText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < expressionTokens.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(expressionTokens.get(i).value);
        }
        return builder.toString().trim();
    }

    private String normalizeExpression(String expression) {
        return expression == null ? "" : expression.replace(" ", "");
    }

    private int myPlayer() {
        if (currentState == null) {
            return 0;
        }
        String id = playerSession.getId();
        if (id.equals(currentState.getPlayer1Id())) return 1;
        if (id.equals(currentState.getPlayer2Id())) return 2;
        return 0;
    }

    private void updatePlayerScoreStyle(int myPlayer) {
        player1ScoreText.setTypeface(null, myPlayer == 1 ? Typeface.BOLD : Typeface.NORMAL);
        player2ScoreText.setTypeface(null, myPlayer == 2 ? Typeface.BOLD : Typeface.NORMAL);
        player1ScoreText.setBackgroundColor(myPlayer == 1 ? 0xFFEFEAF8 : 0xFFF5F5F5);
        player2ScoreText.setBackgroundColor(myPlayer == 2 ? 0xFFEFEAF8 : 0xFFF5F5F5);
    }

    private StepByStepPlayerSession resolveCurrentUser() {
        AuthUser authUser = AuthService.getInstance(requireContext()).getCurrentUser();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId;
        String userName;

        if (authUser != null) {
            userId = authUser.getId();
            userName = authUser.getUsername().isEmpty() ? authUser.getEmail() : authUser.getUsername();
        } else if (firebaseUser != null) {
            userId = firebaseUser.getUid();
            userName = firebaseUser.getEmail() == null ? firebaseUser.getUid() : firebaseUser.getEmail();
        } else {
            userId = "guest";
            userName = "Gost";
        }
        return new StepByStepPlayerSession(userId, userName);
    }

    private String deviceId() {
        String id = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        return isEmpty(id) ? String.valueOf(System.currentTimeMillis()) : id;
    }

    private void showError(Exception error) {
        if (isAdded()) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String playerName(String name, String fallback) {
        return isEmpty(name) ? fallback : name;
    }

    private void scrollToTop() {
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }

    private static final class ExpressionToken {
        private final String value;
        private final int numberIndex;

        private ExpressionToken(String value, int numberIndex) {
            this.value = value;
            this.numberIndex = numberIndex;
        }

        private static ExpressionToken number(String value, int numberIndex) {
            return new ExpressionToken(value, numberIndex);
        }

        private static ExpressionToken operator(String value) {
            return new ExpressionToken(value, -1);
        }

        private boolean isNumber() {
            return numberIndex >= 0;
        }
    }
}
