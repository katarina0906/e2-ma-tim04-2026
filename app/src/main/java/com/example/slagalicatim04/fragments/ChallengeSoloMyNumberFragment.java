package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.mynumber.MyNumberGameService;
import com.example.slagalicatim04.regions.RegionChallengeRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSoloMyNumberFragment extends Fragment {
    public static final String ARG_CHALLENGE_ID = "challengeId";

    private final MyNumberGameService gameService = new MyNumberGameService();
    private final RegionChallengeRepository challengeRepository = new RegionChallengeRepository();
    private final List<Token> expressionTokens = new ArrayList<>();
    private final boolean[] usedNumbers = new boolean[6];
    private final TextView[] numberViews = new TextView[6];
    private final MaterialButton[] operatorButtons = new MaterialButton[8];

    private AuthUser currentUser;
    private String challengeId;
    private CountDownTimer timer;
    private List<Integer> numbers = new ArrayList<>();
    private int target;
    private int secondsLeft = MyNumberGameService.ROUND_SECONDS;
    private boolean finished;

    private TextView targetText;
    private TextView timerText;
    private TextView statusText;
    private TextView resultText;
    private TextInputEditText expressionInput;
    private MaterialButton submitButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_solo_my_number, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        challengeId = getArguments() == null ? "" : getArguments().getString(ARG_CHALLENGE_ID, "");
        bindViews(view);
        startGame();
    }

    @Override
    public void onDestroyView() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        super.onDestroyView();
    }

    private void bindViews(View view) {
        targetText = view.findViewById(R.id.challengeSoloTarget);
        timerText = view.findViewById(R.id.challengeSoloTimer);
        statusText = view.findViewById(R.id.challengeSoloStatus);
        resultText = view.findViewById(R.id.challengeSoloResult);
        expressionInput = view.findViewById(R.id.challengeSoloExpressionInput);
        submitButton = view.findViewById(R.id.challengeSoloSubmitButton);
        numberViews[0] = view.findViewById(R.id.challengeSoloNumber1);
        numberViews[1] = view.findViewById(R.id.challengeSoloNumber2);
        numberViews[2] = view.findViewById(R.id.challengeSoloNumber3);
        numberViews[3] = view.findViewById(R.id.challengeSoloNumber4);
        numberViews[4] = view.findViewById(R.id.challengeSoloNumber5);
        numberViews[5] = view.findViewById(R.id.challengeSoloNumber6);
        operatorButtons[0] = view.findViewById(R.id.challengeSoloOpenParenthesisButton);
        operatorButtons[1] = view.findViewById(R.id.challengeSoloCloseParenthesisButton);
        operatorButtons[2] = view.findViewById(R.id.challengeSoloPlusButton);
        operatorButtons[3] = view.findViewById(R.id.challengeSoloMinusButton);
        operatorButtons[4] = view.findViewById(R.id.challengeSoloMultiplyButton);
        operatorButtons[5] = view.findViewById(R.id.challengeSoloDivideButton);
        operatorButtons[6] = view.findViewById(R.id.challengeSoloBackspaceButton);
        operatorButtons[7] = view.findViewById(R.id.challengeSoloClearButton);

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
        submitButton.setOnClickListener(v -> finishGame());
    }

    private void startGame() {
        target = gameService.generateTarget();
        numbers = gameService.generateNumbers();
        targetText.setText(String.valueOf(target));
        statusText.setText("Igras samostalnu partiju. Ne cekas druge igrace.");
        resultText.setText("");
        renderNumbers();
        renderTimer();
        timer = new CountDownTimer(MyNumberGameService.ROUND_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                secondsLeft = (int) Math.max(0L, millisUntilFinished / 1000L);
                renderTimer();
            }

            @Override
            public void onFinish() {
                secondsLeft = 0;
                renderTimer();
                finishGame();
            }
        };
        timer.start();
    }

    private void finishGame() {
        if (finished) {
            return;
        }
        finished = true;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        setControlsEnabled(false);
        Integer result = evaluateExpression();
        long score = scoreFor(result);
        resultText.setText(result == null
                ? "Nemas vazeci rezultat. Osvojio si 0 bodova."
                : "Rezultat: " + result + " • Osvojio si " + score + " bodova.");
        statusText.setText("Rezultat se salje u izazov.");
        challengeRepository.submitScore(currentUser, challengeId, score,
                () -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    Toast.makeText(requireContext(), "Rezultat je upisan u izazov.", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).popBackStack();
                },
                this::showError);
    }

    private Integer evaluateExpression() {
        try {
            String expression = buildExpressionText().replace(" ", "");
            if (expression.isEmpty()) {
                return null;
            }
            return gameService.evaluate(expression, numbers);
        } catch (Exception error) {
            return null;
        }
    }

    private long scoreFor(Integer result) {
        if (result == null) {
            return 0L;
        }
        return result == target ? 10L : 0L;
    }

    private void appendNumber(int index) {
        if (finished || index >= numbers.size() || usedNumbers[index]) {
            return;
        }
        if (!expressionTokens.isEmpty()) {
            Token previous = expressionTokens.get(expressionTokens.size() - 1);
            if (previous.isNumber() || ")".equals(previous.value)) {
                return;
            }
        }
        usedNumbers[index] = true;
        expressionTokens.add(Token.number(String.valueOf(numbers.get(index)), index));
        updateExpression();
        renderNumbers();
    }

    private void appendOperator(String operator) {
        if (finished) {
            return;
        }
        Token previous = expressionTokens.isEmpty() ? null : expressionTokens.get(expressionTokens.size() - 1);
        if ("(".equals(operator)) {
            if (previous != null && (previous.isNumber() || ")".equals(previous.value))) {
                return;
            }
        } else if (")".equals(operator)) {
            if (previous == null || (!previous.isNumber() && !")".equals(previous.value))
                    || unmatchedOpeningParentheses() <= 0) {
                return;
            }
        } else if (previous == null || (!previous.isNumber() && !")".equals(previous.value))) {
            return;
        }
        expressionTokens.add(Token.operator(operator));
        updateExpression();
    }

    private void backspace() {
        if (finished || expressionTokens.isEmpty()) {
            return;
        }
        Token token = expressionTokens.remove(expressionTokens.size() - 1);
        if (token.isNumber()) {
            usedNumbers[token.numberIndex] = false;
        }
        updateExpression();
        renderNumbers();
    }

    private void clearExpression() {
        expressionTokens.clear();
        for (int i = 0; i < usedNumbers.length; i++) {
            usedNumbers[i] = false;
        }
        updateExpression();
        renderNumbers();
    }

    private int unmatchedOpeningParentheses() {
        int balance = 0;
        for (Token token : expressionTokens) {
            if ("(".equals(token.value)) {
                balance++;
            } else if (")".equals(token.value)) {
                balance--;
            }
        }
        return balance;
    }

    private void renderNumbers() {
        for (int i = 0; i < numberViews.length; i++) {
            if (i < numbers.size()) {
                numberViews[i].setText(String.valueOf(numbers.get(i)));
                numberViews[i].setEnabled(!usedNumbers[i] && !finished);
                numberViews[i].setAlpha(usedNumbers[i] || finished ? 0.45f : 1f);
            }
        }
    }

    private void renderTimer() {
        timerText.setText(secondsLeft + "s");
    }

    private void updateExpression() {
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
        return builder.toString();
    }

    private void setControlsEnabled(boolean enabled) {
        for (TextView numberView : numberViews) {
            numberView.setEnabled(enabled);
        }
        for (MaterialButton operatorButton : operatorButtons) {
            operatorButton.setEnabled(enabled);
        }
        expressionInput.setEnabled(enabled);
        submitButton.setEnabled(enabled);
    }

    private void showError(Exception error) {
        if (isAdded()) {
            String message = error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                    ? "Operacija nije uspela."
                    : error.getMessage();
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private static final class Token {
        private final String value;
        private final int numberIndex;

        private Token(String value, int numberIndex) {
            this.value = value;
            this.numberIndex = numberIndex;
        }

        private static Token number(String value, int numberIndex) {
            return new Token(value, numberIndex);
        }

        private static Token operator(String value) {
            return new Token(value, -1);
        }

        private boolean isNumber() {
            return numberIndex >= 0;
        }
    }
}
