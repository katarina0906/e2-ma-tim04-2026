package com.example.slagalicatim04.associations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AssociationGameServiceTest {
    private final AssociationGameService service = new AssociationGameService();

    @Test
    public void answersIgnoreCaseAndDiacritics() {
        assertTrue(service.matches("  povrće ", "POVRCE"));
    }

    @Test
    public void columnScoresTwoPlusEveryHiddenClue() {
        List<Boolean> revealed = falseList(16);
        revealed.set(0, true);
        assertEquals(5, service.columnPoints(revealed, 0));
    }

    @Test
    public void finalFromOneOpenedClueScoresThirty() {
        List<Boolean> revealed = falseList(16);
        revealed.set(0, true);
        assertEquals(30, service.finalPoints(
                revealed, Arrays.asList(false, false, false, false)));
    }

    @Test
    public void solvedColumnsAreNotScoredTwiceByFinalAnswer() {
        List<Boolean> revealed = falseList(16);
        for (int index = 0; index < 4; index++) {
            revealed.set(index, true);
        }
        assertEquals(25, service.finalPoints(
                revealed, Arrays.asList(true, false, false, false)));
    }

    private List<Boolean> falseList(int size) {
        List<Boolean> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(false);
        }
        return values;
    }
}
