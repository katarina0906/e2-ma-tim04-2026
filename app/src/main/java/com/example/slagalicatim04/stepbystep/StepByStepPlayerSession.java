package com.example.slagalicatim04.stepbystep;

public class StepByStepPlayerSession {
    private final String id;
    private final String name;

    public StepByStepPlayerSession(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
