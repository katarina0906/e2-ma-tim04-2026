package com.example.slagalicatim04.models;

public class Player {
    private String id;
    private String name;
    private int score;

    public Player() {
    }

    public Player(String id, String name, int score) {
        this.id = id;
        this.name = name;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }
}