package com.example.slagalicatim04.stepbystep;

import java.util.ArrayList;
import java.util.List;

public class StepByStepRound {

    private final int index;
    private final List<String> steps;
    private final String answer;

    public StepByStepRound(int index, List<String> steps, String answer) {
        this.index = index;
        this.steps = new ArrayList<>(steps);
        this.answer = answer;
    }

    public int getIndex() {
        return index;
    }

    public List<String> getSteps() {
        return new ArrayList<>(steps);
    }

    public String getAnswer() {
        return answer;
    }
}
