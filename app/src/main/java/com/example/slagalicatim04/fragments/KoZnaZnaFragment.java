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

public class KoZnaZnaFragment extends Fragment {

    private TextView timerText, resultText;
    private Button answerA, answerB, answerC, answerD;
    private CountDownTimer timer;

    private String correctAnswer = "Beograd";

    public KoZnaZnaFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        timerText = view.findViewById(R.id.timerText);
        resultText = view.findViewById(R.id.resultText);

        answerA = view.findViewById(R.id.answerA);
        answerB = view.findViewById(R.id.answerB);
        answerC = view.findViewById(R.id.answerC);
        answerD = view.findViewById(R.id.answerD);

        View.OnClickListener listener = v -> checkAnswer((Button) v);

        answerA.setOnClickListener(listener);
        answerB.setOnClickListener(listener);
        answerC.setOnClickListener(listener);
        answerD.setOnClickListener(listener);

        startTimer();

        return view;
    }

    private void startTimer() {
        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText("⏱ " + millisUntilFinished / 1000 + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("⏱ 0s");
                resultText.setText("Vreme je isteklo!");
                disableButtons();
            }
        }.start();
    }

    private void checkAnswer(Button selectedButton) {
        if (timer != null) timer.cancel();

        String selected = selectedButton.getText().toString();

        if (selected.equals(correctAnswer)) {
            selectedButton.setBackgroundColor(Color.parseColor("#4CAF50"));
            resultText.setText("Tačno! +10 bodova");
        } else {
            selectedButton.setBackgroundColor(Color.parseColor("#E53935"));
            resultText.setText("Netačno! -5 bodova");
            highlightCorrectAnswer();
        }

        disableButtons();
    }

    private void highlightCorrectAnswer() {
        if (answerA.getText().toString().equals(correctAnswer)) {
            answerA.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
        if (answerB.getText().toString().equals(correctAnswer)) {
            answerB.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
        if (answerC.getText().toString().equals(correctAnswer)) {
            answerC.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
        if (answerD.getText().toString().equals(correctAnswer)) {
            answerD.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
    }

    private void disableButtons() {
        answerA.setEnabled(false);
        answerB.setEnabled(false);
        answerC.setEnabled(false);
        answerD.setEnabled(false);
    }
}