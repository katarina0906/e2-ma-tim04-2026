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

public class MyNumberFragment extends Fragment {

    private boolean targetShown = false;
    private boolean numbersShown = false;
    private boolean roundFinished = false;

    public MyNumberFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_number, container, false);

        TextView targetValue = view.findViewById(R.id.targetValue);
        TextView timerValue = view.findViewById(R.id.myNumberTimerValue);
        TextView statusValue = view.findViewById(R.id.myNumberStatusValue);
        TextView resultValue = view.findViewById(R.id.resultValue);
        TextView scoreValue = view.findViewById(R.id.scoreValue);
        View resultBanner = view.findViewById(R.id.myNumberResultBanner);
        TextView resultBannerTitle = view.findViewById(R.id.myNumberResultTitle);
        TextView resultBannerMessage = view.findViewById(R.id.myNumberResultMessage);
        TextInputEditText expressionInput = view.findViewById(R.id.expressionInput);
        MaterialButton stopTargetButton = view.findViewById(R.id.stopTargetButton);
        MaterialButton stopNumbersButton = view.findViewById(R.id.stopNumbersButton);
        MaterialButton checkExpressionButton = view.findViewById(R.id.checkExpressionButton);
        MaterialButton autoRevealButton = view.findViewById(R.id.autoRevealButton);

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

        stopTargetButton.setOnClickListener(v -> {
            if (roundFinished || targetShown) {
                return;
            }

            targetShown = true;
            targetValue.setText("724");
            timerValue.setText("55s");
            statusValue.setText("Trazeni broj je zaustavljen. Sada zaustavi 6 brojeva.");
            stopNumbersButton.setEnabled(true);
            stopTargetButton.setText("Zaustavljeno");
        });

        stopNumbersButton.setOnClickListener(v -> {
            if (roundFinished || !targetShown || numbersShown) {
                return;
            }

            numbersShown = true;
            updateNumberViews(numberViews, true);
            timerValue.setText("50s");
            statusValue.setText("Brojevi su zaustavljeni. Sastavi izraz koristeci ponudjene operande.");
            stopNumbersButton.setText("Brojevi zaustavljeni");
            expressionInput.setEnabled(true);
            checkExpressionButton.setEnabled(true);
        });

        autoRevealButton.setOnClickListener(v -> {
            if (roundFinished) {
                return;
            }

            targetShown = true;
            numbersShown = true;
            targetValue.setText("724");
            updateNumberViews(numberViews, true);
            timerValue.setText("50s");
            statusValue.setText("Isteklo je 5 sekundi i brojevi su automatski prikazani.");
            stopTargetButton.setText("Zaustavljeno");
            stopNumbersButton.setText("Brojevi prikazani");
            stopNumbersButton.setEnabled(false);
            expressionInput.setEnabled(true);
            checkExpressionButton.setEnabled(true);
        });

        checkExpressionButton.setOnClickListener(v -> {
            if (roundFinished || !numbersShown) {
                return;
            }

            String expression = "";
            if (expressionInput.getText() != null) {
                expression = expressionInput.getText().toString().trim();
            }

            if (expression.equals("100*(7+2)-15-1") || expression.equals("100 * (7 + 2) - 15 - 1")) {
                roundFinished = true;
                resultValue.setText("724");
                scoreValue.setText("10 bodova");
                timerValue.setText("Gotovo");
                statusValue.setText("Tacno resenje. Igrac dobija maksimalnih 10 bodova.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFEDF8F1);
                resultBannerTitle.setText("Tacan rezultat");
                resultBannerMessage.setText("Izraz je pogodio trazeni broj i osvojeno je 10 bodova.");
                checkExpressionButton.setEnabled(false);
                stopTargetButton.setEnabled(false);
                stopNumbersButton.setEnabled(false);
                autoRevealButton.setEnabled(false);
                expressionInput.setEnabled(false);
            } else if (expression.isEmpty()) {
                resultValue.setText("-");
                scoreValue.setText("0 bodova");
                statusValue.setText("Nema unosa. Igrac ne osvaja bodove.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFFFF5E8);
                resultBannerTitle.setText("Nema unosa");
                resultBannerMessage.setText("Izraz nije unet, pa igrac u ovoj rundi ne osvaja bodove.");
            } else {
                resultValue.setText("719");
                scoreValue.setText("5 bodova");
                statusValue.setText("Rezultat je blizu trazenog broja.");
                resultBanner.setVisibility(View.VISIBLE);
                resultBanner.setBackgroundColor(0xFFFFF1F1);
                resultBannerTitle.setText("Blizu trazenog broja");
                resultBannerMessage.setText("Uneti izraz nije tacan, ali rezultat ostaje blizu trazenog broja.");
            }
        });

        return view;
    }

    private void updateNumberViews(TextView[] numberViews, boolean revealed) {
        String[] values = revealed
                ? new String[]{"7", "8", "2", "1", "15", "100"}
                : new String[]{"?", "?", "?", "?", "?", "?"};

        for (int i = 0; i < numberViews.length; i++) {
            numberViews[i].setText(values[i]);
        }
    }
}
