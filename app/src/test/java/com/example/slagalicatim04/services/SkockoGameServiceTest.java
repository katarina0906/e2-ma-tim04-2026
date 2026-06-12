package com.example.slagalicatim04.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.slagalicatim04.models.SkockoGameResult;

import org.junit.Test;

import java.util.Random;

public class SkockoGameServiceTest {

    @Test
    public void calculateFeedbackHandlesDuplicateSymbols() {
        SkockoGameService.Feedback feedback = SkockoGameService.calculateFeedback(
                new int[]{0, 0, 1, 2},
                new int[]{0, 1, 0, 0}
        );

        assertEquals(1, feedback.getExact());
        assertEquals(2, feedback.getPartial());
    }

    @Test
    public void pointsMatchSpecification() {
        assertEquals(20, SkockoGameService.pointsForAttempt(1));
        assertEquals(20, SkockoGameService.pointsForAttempt(2));
        assertEquals(15, SkockoGameService.pointsForAttempt(3));
        assertEquals(15, SkockoGameService.pointsForAttempt(4));
        assertEquals(10, SkockoGameService.pointsForAttempt(5));
        assertEquals(10, SkockoGameService.pointsForAttempt(6));
    }

    @Test
    public void gameSupportsTwoRoundsAndStealAttempt() {
        SkockoGameService service = new SkockoGameService(new ConstantRandom(0));
        service.startNewGame();

        SkockoGameService.MoveResult firstRound = service.submitGuess(new int[]{0, 0, 0, 0});
        assertTrue(firstRound.isSolved());
        assertEquals(20, service.getScore(0));
        assertEquals(SkockoGameService.Phase.ROUND_FINISHED, service.getPhase());

        service.startNextRound();
        for (int i = 0; i < SkockoGameService.MAX_ATTEMPTS; i++) {
            service.submitGuess(new int[]{1, 1, 1, 1});
        }
        assertEquals(SkockoGameService.Phase.STEAL, service.getPhase());

        SkockoGameService.MoveResult steal = service.submitGuess(new int[]{0, 0, 0, 0});
        assertTrue(steal.isSolved());
        assertEquals(10, steal.getAwardedPoints());
        assertEquals(SkockoGameService.Phase.GAME_FINISHED, service.getPhase());

        SkockoGameResult result = service.getGameResult();
        assertEquals(30, result.getPlayer1Score());
        assertEquals(0, result.getPlayer2Score());
        assertEquals(2, result.getRounds().size());
        assertEquals(1, result.getRounds().get(0).getSolvedAttempt());
        assertFalse(result.getRounds().get(0).isStealAttempted());
        assertTrue(result.getRounds().get(1).isStealSolved());
    }

    private static class ConstantRandom extends Random {
        private final int value;

        ConstantRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
