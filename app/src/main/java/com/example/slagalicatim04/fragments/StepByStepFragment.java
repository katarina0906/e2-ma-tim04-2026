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

public class StepByStepFragment extends Fragment {

    private static final int ROUND_DURATION_SECONDS = 70;
    private static final String FINAL_ANSWER = "Jakov Jozinovic";
    private final String[] hints = {
            "1. Hrvatski",
            "2. Mladi",
            "3. Pjevac",
            "4. Vinkovci",
            "5. Zvjezdice",
            "6. Supertalent",
            "7. Inicijali J. J."
    };

    private final TextView[] stepViews = new TextView[7];
    private int openedSteps = 1;
    private boolean roundFinished = false;
    private CountDownTimer roundTimer;

    public StepByStepFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_by_step, container, false);

        ScrollView scrollView = view.findViewById(R.id.stepByStepScrollView);
        TextView currentStepValue = view.findViewById(R.id.currentStepValue);
        TextView currentPointsValue = view.findViewById(R.id.currentPointsValue);
        TextView timerValue = view.findViewById(R.id.timerValue);
        TextView statusText = view.findViewById(R.id.statusText);
        View resultBanner = view.findViewById(R.id.stepByStepResultBanner);
        TextView resultBannerTitle = view.findViewById(R.id.stepByStepResultTitle);
        TextView resultBannerMessage = view.findViewById(R.id.stepByStepResultMessage);
        TextInputEditText answerInput = view.findViewById(R.id.answerInput);
        MaterialButton nextStepButton = view.findViewById(R.id.nextStepButton);
        MaterialButton confirmButton = view.findViewById(R.id.confirmAnswerButton);
        MaterialButton giveUpButton = view.findViewById(R.id.giveUpButton);

        stepViews[0] = view.findViewById(R.id.step1Text);
        stepViews[1] = view.findViewById(R.id.step2Text);
        stepViews[2] = view.findViewById(R.id.step3Text);
        stepViews[3] = view.findViewById(R.id.step4Text);
        stepViews[4] = view.findViewById(R.id.step5Text);
        stepViews[5] = view.findViewById(R.id.step6Text);
        stepViews[6] = view.findViewById(R.id.step7Text);

        updateStepCards();
        updateRoundState(currentStepValue, currentPointsValue, statusText);
        resultBanner.setVisibility(View.GONE);
        nextStepButton.setVisibility(View.GONE);
        startRoundTimer(timerValue, currentStepValue, currentPointsValue, statusText,
                resultBanner, resultBannerTitle, resultBannerMessage, nextStepButton,
                confirmButton, giveUpButton, answerInput, scrollView);

        confirmButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }

            String answer = "";
            if (answerInput.getText() != null) {
                answer = answerInput.getText().toString().trim();
            }

            if (answer.equalsIgnoreCase(FINAL_ANSWER)) {
                roundFinished = true;
                cancelRoundTimer();
                currentStepValue.setText("Reseno");
                currentPointsValue.setText(currentPointsForStep() + " bodova");
                timerValue.setText("Gotovo");
                statusText.setText("Tacan odgovor. Runda je zavrsena i osvojen je prikazani broj bodova.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFEDF8F1);
                resultBannerTitle.setText("Tacan odgovor");
                resultBannerMessage.setText("Osvojeno je " + currentPointsForStep() + " bodova u ovoj rundi.");
                scrollToTop(scrollView);
                nextStepButton.setEnabled(false);
                confirmButton.setEnabled(false);
                giveUpButton.setEnabled(false);
                answerInput.setEnabled(false);
            } else {
                statusText.setText("Odgovor nije tacan. Otvori sledeci korak ili pokusaj ponovo.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFFFF1F1);
                resultBannerTitle.setText("Odgovor nije tacan");
                resultBannerMessage.setText("Pokreni sledeci korak ili pokusaj ponovo sa novim odgovorom.");
                scrollToTop(scrollView);
            }
        });

        giveUpButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }

            roundFinished = true;
            cancelRoundTimer();
            currentStepValue.setText("Predato");
            currentPointsValue.setText("0 bodova");
            timerValue.setText("Rival 10s");
            statusText.setText("Igrac nije pogodio. Protivnik dobija sansu da osvoji 5 bodova.");
            resultBanner.setVisibility(View.VISIBLE);
            resultBanner.setBackgroundColor(0xFFFFF5E8);
            resultBannerTitle.setText("Runda je predata");
            resultBannerMessage.setText("Protivnik sada dobija sansu da osvoji dodatnih 5 bodova. Tacan odgovor je: " + FINAL_ANSWER + ".");
            scrollToTop(scrollView);
            nextStepButton.setEnabled(false);
            confirmButton.setEnabled(false);
            giveUpButton.setEnabled(false);
            answerInput.setEnabled(false);
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        cancelRoundTimer();
        super.onDestroyView();
    }

    private void updateStepCards() {
        for (int i = 0; i < stepViews.length; i++) {
            TextView stepView = stepViews[i];

            if (i < openedSteps) {
                stepView.setText(hints[i]);
                stepView.setBackgroundColor(0xFFF6F2FF);
                stepView.setTextColor(0xFF1E1E2F);
            } else {
                stepView.setText((i + 1) + ". Zakljucano");
                stepView.setBackgroundColor(0xFFECECEC);
                stepView.setTextColor(0xFF7D7D7D);
            }
        }
    }

    private void updateRoundState(TextView currentStepValue, TextView currentPointsValue,
                                  TextView statusText) {
        currentStepValue.setText(openedSteps + " / 7");
        currentPointsValue.setText(currentPointsForStep() + " bodova");
        statusText.setText("Otvori sledeci korak ili potvrdi odgovor kada zelis.");
    }

    private int currentPointsForStep() {
        return 22 - (openedSteps * 2);
    }

    private void scrollToTop(ScrollView scrollView) {
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }

    private void startRoundTimer(TextView timerValue, TextView currentStepValue,
                                 TextView currentPointsValue, TextView statusText,
                                 View resultBanner, TextView resultBannerTitle,
                                 TextView resultBannerMessage, MaterialButton nextStepButton,
                                 MaterialButton confirmButton, MaterialButton giveUpButton,
                                 TextInputEditText answerInput, ScrollView scrollView) {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(70000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                timerValue.setText(secondsLeft + "s");

                int stepsThatShouldBeOpen = Math.min(
                        hints.length,
                        1 + ((ROUND_DURATION_SECONDS - secondsLeft) / 10)
                );

                if (!roundFinished && stepsThatShouldBeOpen > openedSteps) {
                    openedSteps = stepsThatShouldBeOpen;
                    updateStepCards();
                    updateRoundState(currentStepValue, currentPointsValue, statusText);

                    if (openedSteps == hints.length) {
                        statusText.setText("Otvoren je i poslednji korak. Pokusaj da pogodis konacni pojam.");
                    } else {
                        statusText.setText("Otvoren je novi korak. Pokusaj da pogodis konacni pojam.");
                    }
                }
            }

            @Override
            public void onFinish() {
                if (roundFinished) {
                    return;
                }
                roundFinished = true;
                timerValue.setText("0s");
                currentStepValue.setText("Isteklo");
                currentPointsValue.setText("0 bodova");
                statusText.setText("Vreme je isteklo. Protivnik dobija sansu da osvoji 5 bodova.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFFFF5E8);
                resultBannerTitle.setText("Vreme je isteklo");
                resultBannerMessage.setText("Runda je zavrsena bez tacnog odgovora. Tacan odgovor je: " + FINAL_ANSWER + ".");
                nextStepButton.setEnabled(false);
                confirmButton.setEnabled(false);
                giveUpButton.setEnabled(false);
                answerInput.setEnabled(false);
                scrollToTop(scrollView);
            }
        };
        roundTimer.start();
    }

    private void cancelRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }
}
