package com.example.slagalicatim04.associations;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class AssociationGameService {
    public static final int ROUND_COUNT = 2;
    public static final int ROUND_SECONDS = 120;

    public boolean matches(String guess, String answer) {
        return normalize(guess).equals(normalize(answer));
    }

    public int columnPoints(List<Boolean> revealed, int column) {
        return 2 + hiddenClueCount(revealed, column);
    }

    public int finalPoints(List<Boolean> revealed, List<Boolean> solvedColumns) {
        int points = 7;
        for (int column = 0; column < AssociationPuzzle.COLUMN_COUNT; column++) {
            if (booleanAt(solvedColumns, column)) {
                continue;
            }
            int opened = openedClueCount(revealed, column);
            points += opened == 0 ? 6 : 2 + hiddenClueCount(revealed, column);
        }
        return points;
    }

    public int hiddenClueCount(List<Boolean> revealed, int column) {
        int hidden = 0;
        int start = column * AssociationPuzzle.CLUES_PER_COLUMN;
        for (int row = 0; row < AssociationPuzzle.CLUES_PER_COLUMN; row++) {
            if (!booleanAt(revealed, start + row)) {
                hidden++;
            }
        }
        return hidden;
    }

    public int openedClueCount(List<Boolean> revealed, int column) {
        return AssociationPuzzle.CLUES_PER_COLUMN - hiddenClueCount(revealed, column);
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean booleanAt(List<Boolean> values, int index) {
        return index >= 0 && index < values.size() && Boolean.TRUE.equals(values.get(index));
    }
}
