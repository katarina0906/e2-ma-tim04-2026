package com.example.slagalicatim04.repositories;

import com.example.slagalicatim04.models.Question;
import java.util.List;

public interface QuizRepository {
    List<Question> getQuestions();
}