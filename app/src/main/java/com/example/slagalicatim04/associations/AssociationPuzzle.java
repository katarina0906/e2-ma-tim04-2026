package com.example.slagalicatim04.associations;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssociationPuzzle {
    public static final int COLUMN_COUNT = 4;
    public static final int CLUES_PER_COLUMN = 4;

    private final String id;
    private final List<String> clues;
    private final List<String> columnAnswers;
    private final String finalAnswer;

    public AssociationPuzzle(String id, List<String> clues, List<String> columnAnswers,
                             String finalAnswer) {
        this.id = id;
        this.clues = Collections.unmodifiableList(new ArrayList<>(clues));
        this.columnAnswers = Collections.unmodifiableList(new ArrayList<>(columnAnswers));
        this.finalAnswer = finalAnswer;
    }

    public AssociationPuzzle(DocumentSnapshot snapshot) {
        this(
                snapshot.getId(),
                stringList(snapshot.get("clues")),
                stringList(snapshot.get("columnAnswers")),
                value(snapshot.getString("finalAnswer"))
        );
    }

    public String getId() {
        return id;
    }

    public String getClue(int column, int row) {
        int index = column * CLUES_PER_COLUMN + row;
        return index >= 0 && index < clues.size() ? clues.get(index) : "";
    }

    public String getColumnAnswer(int column) {
        return column >= 0 && column < columnAnswers.size() ? columnAnswers.get(column) : "";
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public boolean isValid() {
        return clues.size() == COLUMN_COUNT * CLUES_PER_COLUMN
                && columnAnswers.size() == COLUMN_COUNT
                && !finalAnswer.isEmpty();
    }

    public List<String> getClues() {
        return clues;
    }

    public List<String> getColumnAnswers() {
        return columnAnswers;
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
        }
        return result;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
