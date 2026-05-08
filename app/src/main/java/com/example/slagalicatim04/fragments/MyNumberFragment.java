package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class MyNumberFragment extends Fragment {

    private static final String TARGET_NUMBER = "724";
    private static final String[] OFFERED_NUMBERS = {"7", "8", "2", "1", "15", "100"};

    private boolean targetShown = false;
    private boolean numbersShown = false;
    private boolean roundFinished = false;
    private CountDownTimer roundTimer;
    private CountDownTimer targetRevealTimer;
    private CountDownTimer numbersRevealTimer;
    private CountDownTimer resultRevealTimer;
    private final List<ExpressionToken> expressionTokens = new ArrayList<>();
    private final boolean[] usedNumbers = new boolean[OFFERED_NUMBERS.length];

    public MyNumberFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_number, container, false);

        ScrollView scrollView = view.findViewById(R.id.myNumberScrollView);
        TextView targetValue = view.findViewById(R.id.targetValue);
        TextView timerValue = view.findViewById(R.id.myNumberTimerValue);
        TextView statusValue = view.findViewById(R.id.myNumberStatusValue);
        TextView targetCountdown = view.findViewById(R.id.targetCountdown);
        TextView numbersCountdown = view.findViewById(R.id.numbersCountdown);
        TextView resultValue = view.findViewById(R.id.resultValue);
        TextView scoreValue = view.findViewById(R.id.scoreValue);
        View resultBanner = view.findViewById(R.id.myNumberResultBanner);
        TextView resultBannerTitle = view.findViewById(R.id.myNumberResultTitle);
        TextView resultBannerMessage = view.findViewById(R.id.myNumberResultMessage);
        TextInputEditText expressionInput = view.findViewById(R.id.expressionInput);
        MaterialButton stopTargetButton = view.findViewById(R.id.stopTargetButton);
        MaterialButton stopNumbersButton = view.findViewById(R.id.stopNumbersButton);
        MaterialButton checkExpressionButton = view.findViewById(R.id.checkExpressionButton);
        MaterialButton openParenthesisButton = view.findViewById(R.id.openParenthesisButton);
        MaterialButton closeParenthesisButton = view.findViewById(R.id.closeParenthesisButton);
        MaterialButton plusButton = view.findViewById(R.id.plusButton);
        MaterialButton minusButton = view.findViewById(R.id.minusButton);
        MaterialButton multiplyButton = view.findViewById(R.id.multiplyButton);
        MaterialButton divideButton = view.findViewById(R.id.divideButton);
        MaterialButton backspaceButton = view.findViewById(R.id.backspaceButton);
        MaterialButton clearButton = view.findViewById(R.id.clearExpressionButton);

        int[] numberViewIds = {
                R.id.number1, R.id.number2, R.id.number3,
                R.id.number4, R.id.number5, R.id.number6
        };

        TextView[] numberViews = new TextView[numberViewIds.length];
        for (int i = 0; i < numberViewIds.length; i++) {
            numberViews[i] = view.findViewById(numberViewIds[i]);
        }

        updateNumberViews(numberViews, false);
        resultBanner.setVisibility(View.GONE);
        numbersCountdown.setVisibility(View.GONE);
        expressionInput.setEnabled(false);
        expressionInput.setText("");

        startRoundTimer(timerValue, statusValue);
        startTargetCountdown(targetCountdown, targetValue, statusValue, stopTargetButton,
                stopNumbersButton, numbersCountdown, numberViews, expressionInput,
                checkExpressionButton, backspaceButton, clearButton,
                openParenthesisButton, closeParenthesisButton, plusButton, minusButton,
                multiplyButton, divideButton);

        stopTargetButton.setOnClickListener(v -> {
            if (roundFinished || targetShown) {
                return;
            }
            revealTarget(targetCountdown, targetValue, statusValue, stopTargetButton,
                    stopNumbersButton, numbersCountdown, numberViews, expressionInput,
                    checkExpressionButton, backspaceButton, clearButton,
                    openParenthesisButton, closeParenthesisButton, plusButton, minusButton,
                    multiplyButton, divideButton);
        });

        stopNumbersButton.setOnClickListener(v -> {
            if (roundFinished || !targetShown || numbersShown) {
                return;
            }
            revealNumbers(numbersCountdown, numberViews, statusValue, stopNumbersButton,
                    expressionInput, checkExpressionButton, backspaceButton, clearButton,
                    openParenthesisButton, closeParenthesisButton, plusButton, minusButton,
                    multiplyButton, divideButton);
        });

        checkExpressionButton.setOnClickListener(v -> {
            if (roundFinished || !numbersShown) {
                return;
            }

            String expression = normalizeExpression(buildExpressionText());
            roundFinished = true;
            cancelAllTimers();
            expressionInput.setEnabled(false);
            stopTargetButton.setEnabled(false);
            stopNumbersButton.setEnabled(false);
            checkExpressionButton.setEnabled(false);
            backspaceButton.setEnabled(false);
            clearButton.setEnabled(false);
            openParenthesisButton.setEnabled(false);
            closeParenthesisButton.setEnabled(false);
            plusButton.setEnabled(false);
            minusButton.setEnabled(false);
            multiplyButton.setEnabled(false);
            divideButton.setEnabled(false);
            updateNumberUsage(numberViews);

            if (expression.equals("100*(7+2)-15-1")) {
                resultValue.setText(TARGET_NUMBER);
                scoreValue.setText("Ceka se...");
                timerValue.setText("Ceka se");
                statusValue.setText("Tvoje resenje je poslato. Ceka se protivnik.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFEFF3FF);
                resultBannerTitle.setText("Ceka se protivnik");
                resultBannerMessage.setText("Tvoj rezultat je sacuvan i uskoro stize ishod runde.");
                scrollToTop(scrollView);
                revealRoundOutcome(timerValue, statusValue, resultBanner, resultBannerTitle,
                        resultBannerMessage, scoreValue, "Pobeda u rundi",
                        "Protivnik nije imao bolji rezultat. Osvojeno je 10 bodova.",
                        "10 bodova", 0xFFEDF8F1);
            } else if (expression.isEmpty()) {
                resultValue.setText("-");
                scoreValue.setText("Ceka se...");
                timerValue.setText("Ceka se");
                statusValue.setText("Tvoja runda je zavrsena. Ceka se protivnik.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFEFF3FF);
                resultBannerTitle.setText("Ceka se protivnik");
                resultBannerMessage.setText("Tvoja runda je poslata i uskoro stize ishod runde.");
                scrollToTop(scrollView);
                revealRoundOutcome(timerValue, statusValue, resultBanner, resultBannerTitle,
                        resultBannerMessage, scoreValue, "Poraz u rundi",
                        "Protivnik je imao bolji rezultat u ovoj rundi.",
                        "0 bodova", 0xFFFFF1F1);
            } else {
                resultValue.setText("719");
                scoreValue.setText("Ceka se...");
                timerValue.setText("Ceka se");
                statusValue.setText("Tvoj rezultat je poslato. Ceka se protivnik.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFEFF3FF);
                resultBannerTitle.setText("Ceka se protivnik");
                resultBannerMessage.setText("Tvoj rezultat je sacuvan i uskoro stize ishod runde.");
                scrollToTop(scrollView);
                revealRoundOutcome(timerValue, statusValue, resultBanner, resultBannerTitle,
                        resultBannerMessage, scoreValue, "Poraz u rundi",
                        "Protivnik je imao bolji rezultat u ovoj rundi.",
                        "0 bodova", 0xFFFFF1F1);
            }
        });

        for (int i = 0; i < numberViews.length; i++) {
            int index = i;
            numberViews[i].setOnClickListener(v -> {
                if (!numbersShown || roundFinished || usedNumbers[index]) {
                    return;
                }
                usedNumbers[index] = true;
                expressionTokens.add(ExpressionToken.number(OFFERED_NUMBERS[index], index));
                updateExpressionInput(expressionInput);
                updateNumberUsage(numberViews);
            });
        }

        openParenthesisButton.setOnClickListener(v -> appendOperator("(", expressionInput));
        closeParenthesisButton.setOnClickListener(v -> appendOperator(")", expressionInput));
        plusButton.setOnClickListener(v -> appendOperator("+", expressionInput));
        minusButton.setOnClickListener(v -> appendOperator("-", expressionInput));
        multiplyButton.setOnClickListener(v -> appendOperator("*", expressionInput));
        divideButton.setOnClickListener(v -> appendOperator("/", expressionInput));

        backspaceButton.setOnClickListener(v -> {
            if (roundFinished || expressionTokens.isEmpty()) {
                return;
            }
            ExpressionToken removedToken = expressionTokens.remove(expressionTokens.size() - 1);
            if (removedToken.isNumber()) {
                usedNumbers[removedToken.numberIndex] = false;
                updateNumberUsage(numberViews);
            }
            updateExpressionInput(expressionInput);
        });

        clearButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }
            expressionTokens.clear();
            for (int i = 0; i < usedNumbers.length; i++) {
                usedNumbers[i] = false;
            }
            updateExpressionInput(expressionInput);
            updateNumberUsage(numberViews);
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        cancelAllTimers();
        super.onDestroyView();
    }

    private void updateNumberViews(TextView[] numberViews, boolean revealed) {
        String[] values = revealed ? OFFERED_NUMBERS : new String[]{"?", "?", "?", "?", "?", "?"};

        for (int i = 0; i < numberViews.length; i++) {
            numberViews[i].setText(values[i]);
        }
    }

    private void scrollToTop(ScrollView scrollView) {
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }

    private void startRoundTimer(TextView timerValue, TextView statusValue) {
        roundTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerValue.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                roundFinished = true;
                statusValue.setText("Vreme je isteklo.");
                timerValue.setText("0s");
            }
        };
        roundTimer.start();
    }

    private void startTargetCountdown(TextView targetCountdown, TextView targetValue,
                                      TextView statusValue, MaterialButton stopTargetButton,
                                      MaterialButton stopNumbersButton, TextView numbersCountdown,
                                      TextView[] numberViews, TextInputEditText expressionInput,
                                      MaterialButton checkExpressionButton,
                                      MaterialButton backspaceButton, MaterialButton clearButton,
                                      MaterialButton openParenthesisButton,
                                      MaterialButton closeParenthesisButton,
                                      MaterialButton plusButton, MaterialButton minusButton,
                                      MaterialButton multiplyButton, MaterialButton divideButton) {
        cancelTargetCountdown();
        targetCountdown.setVisibility(View.VISIBLE);

        targetRevealTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (millisUntilFinished + 999) / 1000;
                targetCountdown.setText("Automatski ce se prikazati za " + seconds + "s");
            }

            @Override
            public void onFinish() {
                if (!targetShown && !roundFinished) {
                    revealTarget(targetCountdown, targetValue, statusValue, stopTargetButton,
                            stopNumbersButton, numbersCountdown, numberViews, expressionInput,
                            checkExpressionButton, backspaceButton, clearButton,
                            openParenthesisButton, closeParenthesisButton, plusButton,
                            minusButton, multiplyButton, divideButton);
                }
            }
        };

        targetRevealTimer.start();
    }

    private void revealTarget(TextView targetCountdown, TextView targetValue, TextView statusValue,
                              MaterialButton stopTargetButton, MaterialButton stopNumbersButton,
                              TextView numbersCountdown, TextView[] numberViews,
                              TextInputEditText expressionInput,
                              MaterialButton checkExpressionButton,
                              MaterialButton backspaceButton, MaterialButton clearButton,
                              MaterialButton openParenthesisButton,
                              MaterialButton closeParenthesisButton,
                              MaterialButton plusButton, MaterialButton minusButton,
                              MaterialButton multiplyButton, MaterialButton divideButton) {
        cancelTargetCountdown();
        targetShown = true;
        targetValue.setText(TARGET_NUMBER);
        targetCountdown.setVisibility(View.GONE);
        statusValue.setText("Trazeni broj je prikazan. Sada zaustavi 6 brojeva.");
        stopNumbersButton.setEnabled(true);
        stopTargetButton.setEnabled(false);
        stopTargetButton.setText("Broj prikazan");
        startNumbersCountdown(numbersCountdown, numberViews, statusValue, stopNumbersButton,
                expressionInput, checkExpressionButton, backspaceButton, clearButton,
                openParenthesisButton, closeParenthesisButton, plusButton, minusButton,
                multiplyButton, divideButton);
    }

    private void startNumbersCountdown(TextView numbersCountdown, TextView[] numberViews,
                                       TextView statusValue, MaterialButton stopNumbersButton,
                                       TextInputEditText expressionInput,
                                       MaterialButton checkExpressionButton,
                                       MaterialButton backspaceButton,
                                       MaterialButton clearButton,
                                       MaterialButton openParenthesisButton,
                                       MaterialButton closeParenthesisButton,
                                       MaterialButton plusButton, MaterialButton minusButton,
                                       MaterialButton multiplyButton, MaterialButton divideButton) {
        cancelNumbersCountdown();
        numbersCountdown.setVisibility(View.VISIBLE);

        numbersRevealTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (millisUntilFinished + 999) / 1000;
                numbersCountdown.setText("Automatski ce se prikazati za " + seconds + "s");
            }

            @Override
            public void onFinish() {
                if (!numbersShown && !roundFinished) {
                    revealNumbers(numbersCountdown, numberViews, statusValue, stopNumbersButton,
                            expressionInput, checkExpressionButton, backspaceButton,
                            clearButton, openParenthesisButton, closeParenthesisButton,
                            plusButton, minusButton, multiplyButton, divideButton);
                }
            }
        };

        numbersRevealTimer.start();
    }

    private void revealNumbers(TextView numbersCountdown, TextView[] numberViews,
                               TextView statusValue, MaterialButton stopNumbersButton,
                               TextInputEditText expressionInput,
                               MaterialButton checkExpressionButton,
                               MaterialButton backspaceButton, MaterialButton clearButton,
                               MaterialButton openParenthesisButton,
                               MaterialButton closeParenthesisButton,
                               MaterialButton plusButton, MaterialButton minusButton,
                               MaterialButton multiplyButton, MaterialButton divideButton) {
        cancelNumbersCountdown();
        numbersShown = true;
        numbersCountdown.setVisibility(View.GONE);
        updateNumberViews(numberViews, true);
        updateNumberUsage(numberViews);
        statusValue.setText("Brojevi su prikazani. Sastavi izraz koristeci ponudjene operande.");
        stopNumbersButton.setEnabled(false);
        stopNumbersButton.setText("Brojevi prikazani");
        expressionInput.setEnabled(true);
        checkExpressionButton.setEnabled(true);
        backspaceButton.setEnabled(true);
        clearButton.setEnabled(true);
        openParenthesisButton.setEnabled(true);
        closeParenthesisButton.setEnabled(true);
        plusButton.setEnabled(true);
        minusButton.setEnabled(true);
        multiplyButton.setEnabled(true);
        divideButton.setEnabled(true);
    }

    private void appendOperator(String operator, TextInputEditText expressionInput) {
        if (!numbersShown || roundFinished) {
            return;
        }
        expressionTokens.add(ExpressionToken.operator(operator));
        updateExpressionInput(expressionInput);
    }

    private void updateExpressionInput(TextInputEditText expressionInput) {
        expressionInput.setText(buildExpressionText());
    }

    private String buildExpressionText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < expressionTokens.size(); i++) {
            ExpressionToken token = expressionTokens.get(i);
            if (i > 0 && !")".equals(token.value) && !"(".equals(token.value)) {
                builder.append(" ");
            }
            if (i > 0 && "(".equals(token.value)) {
                builder.append(" ");
            }
            builder.append(token.value);
        }
        return builder.toString().trim();
    }

    private String normalizeExpression(String expression) {
        return expression.replace(" ", "");
    }

    private void updateNumberUsage(TextView[] numberViews) {
        for (int i = 0; i < numberViews.length; i++) {
            boolean enabled = numbersShown && !usedNumbers[i] && !roundFinished;
            numberViews[i].setEnabled(enabled);
            numberViews[i].setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private void cancelAllTimers() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        cancelTargetCountdown();
        cancelNumbersCountdown();
        cancelResultReveal();
    }

    private void cancelTargetCountdown() {
        if (targetRevealTimer != null) {
            targetRevealTimer.cancel();
            targetRevealTimer = null;
        }
    }

    private void cancelNumbersCountdown() {
        if (numbersRevealTimer != null) {
            numbersRevealTimer.cancel();
            numbersRevealTimer = null;
        }
    }

    private void revealRoundOutcome(TextView timerValue, TextView statusValue, View resultBanner,
                                    TextView resultBannerTitle, TextView resultBannerMessage,
                                    TextView scoreValue, String finalTitle,
                                    String finalMessage, String finalScore, int bannerColor) {
        cancelResultReveal();
        resultRevealTimer = new CountDownTimer(2200, 2200) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                timerValue.setText("Gotovo");
                statusValue.setText(finalTitle);
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(bannerColor);
                resultBannerTitle.setText(finalTitle);
                resultBannerMessage.setText(finalMessage);
                scoreValue.setText(finalScore);
            }
        };
        resultRevealTimer.start();
    }

    private void cancelResultReveal() {
        if (resultRevealTimer != null) {
            resultRevealTimer.cancel();
            resultRevealTimer = null;
        }
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
