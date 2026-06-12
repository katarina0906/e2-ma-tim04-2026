package com.example.slagalicatim04.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.models.Answer;
import com.example.slagalicatim04.models.Question;
import com.example.slagalicatim04.repositories.LocalQuizRepository;

import java.util.List;

public class KoZnaZnaFragment extends Fragment {

    private TextView timerText, resultText, playerScoreText, opponentScoreText;
    private TextView questionText, questionCounterText;
    private Button answerA, answerB, answerC, answerD;
    private CountDownTimer timer;

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private int playerScore = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    public KoZnaZnaFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        timerText = view.findViewById(R.id.timerText);
        resultText = view.findViewById(R.id.resultText);
        playerScoreText = view.findViewById(R.id.kzzScore0);
        opponentScoreText = view.findViewById(R.id.kzzScore1);
        questionText = view.findViewById(R.id.questionText);
        questionCounterText = view.findViewById(R.id.questionCounterText);

        answerA = view.findViewById(R.id.answerA);
        answerB = view.findViewById(R.id.answerB);
        answerC = view.findViewById(R.id.answerC);
        answerD = view.findViewById(R.id.answerD);

        questions = new LocalQuizRepository().getQuestions();

        answerA.setOnClickListener(v -> checkAnswer(answerA, 0));
        answerB.setOnClickListener(v -> checkAnswer(answerB, 1));
        answerC.setOnClickListener(v -> checkAnswer(answerC, 2));
        answerD.setOnClickListener(v -> checkAnswer(answerD, 3));

        showQuestion();

        return view;
    }

    private void showQuestion() {
        resetButtons();

        if (currentQuestionIndex >= questions.size()) {
            finishGame();
            return;
        }

        Question currentQuestion = questions.get(currentQuestionIndex);

        questionCounterText.setText("Pitanje " + (currentQuestionIndex + 1) + " / " + questions.size());
        questionText.setText(currentQuestion.getText());

        List<Answer> answers = currentQuestion.getAnswers();

        answerA.setText(answers.get(0).getText());
        answerB.setText(answers.get(1).getText());
        answerC.setText(answers.get(2).getText());
        answerD.setText(answers.get(3).getText());

        resultText.setText("");
        startTimer();
    }

    private void startTimer() {
        if (timer != null) timer.cancel();

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText("⏱ " + millisUntilFinished / 1000 + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("⏱ 0s");
                resultText.setText("Vreme je isteklo! 0 bodova");
                disableButtons();

                handler.postDelayed(() -> {
                    currentQuestionIndex++;
                    showQuestion();
                }, 1200);
            }
        }.start();
    }

    private void checkAnswer(Button selectedButton, int answerIndex) {
        if (timer != null) timer.cancel();

        Question currentQuestion = questions.get(currentQuestionIndex);
        Answer selectedAnswer = currentQuestion.getAnswers().get(answerIndex);

        if (selectedAnswer.isCorrect()) {
            playerScore += 10;
            selectedButton.setBackgroundColor(Color.parseColor("#4CAF50"));
            resultText.setText("Tačno! +10 bodova");
        } else {
            playerScore -= 5;
            selectedButton.setBackgroundColor(Color.parseColor("#E53935"));
            resultText.setText("Netačno! -5 bodova");
            highlightCorrectAnswer(currentQuestion);
        }

        playerScoreText.setText("Igrač 1: " + playerScore);
        disableButtons();

        handler.postDelayed(() -> {
            currentQuestionIndex++;
            showQuestion();
        }, 1200);
    }

    private void highlightCorrectAnswer(Question question) {
        List<Answer> answers = question.getAnswers();

        if (answers.get(0).isCorrect()) answerA.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (answers.get(1).isCorrect()) answerB.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (answers.get(2).isCorrect()) answerC.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (answers.get(3).isCorrect()) answerD.setBackgroundColor(Color.parseColor("#4CAF50"));
    }

    private void resetButtons() {
        Button[] buttons = {answerA, answerB, answerC, answerD};

        for (Button button : buttons) {
            button.setEnabled(true);
            button.setBackgroundColor(Color.parseColor("#6750A4"));
            button.setTextColor(Color.WHITE);
        }
    }

    private void disableButtons() {
        answerA.setEnabled(false);
        answerB.setEnabled(false);
        answerC.setEnabled(false);
        answerD.setEnabled(false);
    }

    private void finishGame() {
        if (timer != null) timer.cancel();

        timerText.setText("⏱ 0s");
        questionCounterText.setText("Kraj igre");
        questionText.setText("Ko zna zna je završena!");
        resultText.setText("Ukupno bodova: " + playerScore);

        answerA.setVisibility(View.GONE);
        answerB.setVisibility(View.GONE);
        answerC.setVisibility(View.GONE);
        answerD.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }
}