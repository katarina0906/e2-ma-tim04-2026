package com.example.slagalicatim04.repositories;

import com.example.slagalicatim04.models.Answer;
import com.example.slagalicatim04.models.Question;

import java.util.Arrays;
import java.util.List;

public class LocalQuizRepository implements QuizRepository {

    @Override
    public List<Question> getQuestions() {
        return Arrays.asList(
                new Question("q1", "Koji je glavni grad Srbije?", Arrays.asList(
                        new Answer("a", "Novi Sad", false),
                        new Answer("b", "Beograd", true),
                        new Answer("c", "Niš", false),
                        new Answer("d", "Kragujevac", false)
                )),
                new Question("q2", "Koliko je 5 + 7?", Arrays.asList(
                        new Answer("a", "10", false),
                        new Answer("b", "11", false),
                        new Answer("c", "12", true),
                        new Answer("d", "13", false)
                )),
                new Question("q3", "Koja planeta je najbliža Suncu?", Arrays.asList(
                        new Answer("a", "Venera", false),
                        new Answer("b", "Mars", false),
                        new Answer("c", "Merkur", true),
                        new Answer("d", "Jupiter", false)
                )),
                new Question("q4", "Koji programski jezik se koristi za Android aplikacije?", Arrays.asList(
                        new Answer("a", "Java", true),
                        new Answer("b", "HTML", false),
                        new Answer("c", "CSS", false),
                        new Answer("d", "SQL", false)
                )),
                new Question("q5", "Koliko sekundi traje jedno pitanje u ovoj igri?", Arrays.asList(
                        new Answer("a", "3", false),
                        new Answer("b", "5", true),
                        new Answer("c", "10", false),
                        new Answer("d", "25", false)
                ))
        );
    }
}