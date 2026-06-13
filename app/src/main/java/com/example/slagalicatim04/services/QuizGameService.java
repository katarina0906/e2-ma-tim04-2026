package com.example.slagalicatim04.services;

import com.example.slagalicatim04.models.Question;

import java.util.List;

public class QuizGameService {

    private List<Question> questions;
    private int currentQuestionIndex = 0;

    public QuizGameService(List<Question> questions) {
        this.questions = questions;
    }

    public Question getCurrentQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            return null;
        }
        return questions.get(currentQuestionIndex);
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public boolean hasNextQuestion() {
        return currentQuestionIndex < questions.size() - 1;
    }

    public void moveToNextQuestion() {
        if (hasNextQuestion()) {
            currentQuestionIndex++;
        }
    }

    public boolean isFinished() {
        return currentQuestionIndex >= questions.size() - 1;
    }

    public int getTotalQuestions() {
        return questions.size();
    }
}