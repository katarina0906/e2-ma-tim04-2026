package com.example.slagalicatim04.models;

public class Answer {
    private String id;
    private String text;
    private boolean correct;

    public Answer() {
    }

    public Answer(String id, String text, boolean correct) {
        this.id = id;
        this.text = text;
        this.correct = correct;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return correct;
    }
}