package com.example.slagalicatim04.services;

import com.example.slagalicatim04.models.QuizAnswerResult;

public class QuizScoringService {

    public int calculatePointsForPlayer(
            String playerId,
            QuizAnswerResult player1Answer,
            QuizAnswerResult player2Answer
    ) {
        boolean isPlayer1 = player1Answer != null && playerId.equals(player1Answer.getPlayerId());
        QuizAnswerResult myAnswer = isPlayer1 ? player1Answer : player2Answer;
        QuizAnswerResult opponentAnswer = isPlayer1 ? player2Answer : player1Answer;

        if (myAnswer == null) {
            return 0;
        }

        if (!myAnswer.isCorrect()) {
            return -5;
        }

        if (opponentAnswer != null && opponentAnswer.isCorrect()) {
            if (myAnswer.getAnsweredAt() < opponentAnswer.getAnsweredAt()) {
                return 10;
            } else {
                return 0;
            }
        }

        return 10;
    }
}