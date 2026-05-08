package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class StepByStepFragment extends Fragment {

    private final String[] hints = {
            "1. Nobelovac",
            "2. Fizika",
            "3. Relativitet",
            "4. Naucnik",
            "5. Nemacka",
            "6. Formula",
            "7. Ajnstajn"
    };

    private final TextView[] stepViews = new TextView[7];
    private int openedSteps = 4;
    private boolean roundFinished = false;

    public StepByStepFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_by_step, container, false);

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
        updateRoundState(currentStepValue, currentPointsValue, timerValue, statusText);
        resultBanner.setVisibility(View.GONE);

        nextStepButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }

            if (openedSteps < hints.length) {
                openedSteps++;
                updateStepCards();
                updateRoundState(currentStepValue, currentPointsValue, timerValue, statusText);
            }

            if (openedSteps == hints.length) {
                nextStepButton.setEnabled(false);
                statusText.setText("Otvoren je i poslednji korak. Pokusaj da pogodis konacni pojam.");
            }
        });

        confirmButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }

            String answer = "";
            if (answerInput.getText() != null) {
                answer = answerInput.getText().toString().trim();
            }

            if (answer.equalsIgnoreCase("Albert Ajnstajn") || answer.equalsIgnoreCase("Ajnstajn")) {
                roundFinished = true;
                currentStepValue.setText("Reseno");
                currentPointsValue.setText(currentPointsForStep() + " bodova");
                timerValue.setText("Gotovo");
                statusText.setText("Tacan odgovor. Runda je zavrsena i osvojen je prikazani broj bodova.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFEDF8F1);
                resultBannerTitle.setText("Tacan odgovor");
                resultBannerMessage.setText("Osvojeno je " + currentPointsForStep() + " bodova u ovoj rundi.");
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
            }
        });

        giveUpButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }

            roundFinished = true;
            currentStepValue.setText("Predato");
            currentPointsValue.setText("0 bodova");
            timerValue.setText("Rival 10s");
            statusText.setText("Igrac nije pogodio. Protivnik dobija sansu da osvoji 5 bodova.");
            resultBanner.setVisibility(View.VISIBLE);
            resultBanner.setBackgroundColor(0xFFFFF5E8);
            resultBannerTitle.setText("Runda je predata");
            resultBannerMessage.setText("Protivnik sada dobija sansu da osvoji dodatnih 5 bodova.");
            nextStepButton.setEnabled(false);
            confirmButton.setEnabled(false);
            giveUpButton.setEnabled(false);
            answerInput.setEnabled(false);
        });

        return view;
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
                                  TextView timerValue, TextView statusText) {
        currentStepValue.setText(openedSteps + " / 7");
        currentPointsValue.setText(currentPointsForStep() + " bodova");
        timerValue.setText((80 - openedSteps * 10) + "s");
        statusText.setText("Otvori sledeci korak ili potvrdi odgovor kada zelis.");
    }

    private int currentPointsForStep() {
        return 22 - (openedSteps * 2);
    }
}
