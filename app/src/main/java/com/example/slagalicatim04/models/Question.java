package com.example.slagalicatim04.models;

import java.util.List;

public class Question {
    private String id;
    private String text;
    private List<Answer> answers;

    public Question() {
    }

    public Question(String id, String text, List<Answer> answers) {
        this.id = id;
        this.text = text;
        this.answers = answers;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public List<Answer> getAnswers() {
        return answers;
    }
}