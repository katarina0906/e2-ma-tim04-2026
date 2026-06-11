package com.example.slagalicatim04.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;

import java.util.HashMap;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    private TextView timerText, resultText, playerScoreText;
    private CountDownTimer timer;

    private Button selectedLeftButton = null;
    private int score = 0;

    private final Map<Integer, Integer> correctPairs = new HashMap<>();

    public SpojniceFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);

        timerText = view.findViewById(R.id.timerText);
        resultText = view.findViewById(R.id.resultText);
        playerScoreText = view.findViewById(R.id.spScore0);

        correctPairs.put(R.id.leftDino, R.id.rightNedostajes);
        correctPairs.put(R.id.leftColic, R.id.rightTiSiMiUKrvi);
        correctPairs.put(R.id.leftAdele, R.id.rightEasyOnMe);
        correctPairs.put(R.id.leftCeca, R.id.rightVreteno);


        setupLeftButton(view, R.id.leftDino);
        setupLeftButton(view, R.id.leftColic);
        setupLeftButton(view, R.id.leftAdele);
        setupLeftButton(view, R.id.leftCeca);


        setupRightButton(view, R.id.rightNedostajes);
        setupRightButton(view, R.id.rightTiSiMiUKrvi);
        setupRightButton(view, R.id.rightEasyOnMe);
        setupRightButton(view, R.id.rightVreteno);


        startTimer();

        return view;
    }

    private void setupLeftButton(View view, int buttonId) {
        Button button = view.findViewById(buttonId);

        button.setOnClickListener(v -> {
            if (selectedLeftButton != null && selectedLeftButton.isEnabled()) {
                selectedLeftButton.setBackgroundColor(Color.parseColor("#6F4BB2"));
            }

            selectedLeftButton = button;
            button.setBackgroundColor(Color.parseColor("#F9A825"));
            resultText.setText("Izaberi pesmu za: " + button.getText());
        });
    }

    private void setupRightButton(View view, int buttonId) {
        Button button = view.findViewById(buttonId);

        button.setOnClickListener(v -> {
            if (selectedLeftButton == null) {
                resultText.setText("Prvo izaberi izvođača iz leve kolone.");
                return;
            }

            int selectedLeftId = selectedLeftButton.getId();
            Integer correctRightId = correctPairs.get(selectedLeftId);

            if (correctRightId != null && correctRightId == buttonId) {
                selectedLeftButton.setBackgroundColor(Color.parseColor("#4CAF50"));
                button.setBackgroundColor(Color.parseColor("#4CAF50"));

                selectedLeftButton.setEnabled(false);
                button.setEnabled(false);

                score += 2;
                playerScoreText.setText("Igrač 1: " + score);
                resultText.setText("Tačno! +" + score + " bodova ukupno");

                selectedLeftButton = null;

                if (score == 10) {
                    if (timer != null) timer.cancel();
                    timerText.setText("Završeno");
                    resultText.setText("Sve spojnice su tačne! Osvojeno: 10 bodova");
                }

            } else {
                button.setBackgroundColor(Color.parseColor("#E53935"));
                selectedLeftButton.setBackgroundColor(Color.parseColor("#E53935"));
                resultText.setText("Netačno. Pokušaj drugi par.");

                Button wrongLeft = selectedLeftButton;
                Button wrongRight = button;

                wrongLeft.postDelayed(() -> {
                    if (wrongLeft.isEnabled()) {
                        wrongLeft.setBackgroundColor(Color.parseColor("#6F4BB2"));
                    }
                    if (wrongRight.isEnabled()) {
                        wrongRight.setBackgroundColor(Color.parseColor("#6F4BB2"));
                    }
                }, 700);

                selectedLeftButton = null;
            }
        });
    }

    private void startTimer() {
        timer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText("⏱ " + millisUntilFinished / 1000 + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("⏱ 0s");
                resultText.setText("Vreme je isteklo! Osvojeno: " + score + " bodova");
                disableAllButtons();
            }
        }.start();
    }

    private void disableAllButtons() {
        if (getView() == null) return;

        int[] ids = {
                R.id.leftDino, R.id.leftColic, R.id.leftAdele, R.id.leftCeca,
                R.id.rightNedostajes, R.id.rightTiSiMiUKrvi, R.id.rightEasyOnMe, R.id.rightVreteno
        };

        for (int id : ids) {
            Button button = getView().findViewById(id);
            button.setEnabled(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) {
            timer.cancel();
        }
    }
}
