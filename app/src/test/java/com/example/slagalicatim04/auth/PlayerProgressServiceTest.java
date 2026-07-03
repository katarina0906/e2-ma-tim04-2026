package com.example.slagalicatim04.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerProgressServiceTest {
    @Test
    public void winnerGetsBaseAndScoreStars() {
        PlayerProgressService.RewardComputation reward = PlayerProgressService.computeReward(
                150, true, 0, 0, 0);

        assertEquals(13, reward.starDelta);
        assertEquals(13, reward.remainingStars);
        assertEquals(0, reward.earnedTokens);
        assertEquals(13, reward.nextTotalStars);
        assertEquals(0, reward.nextTokens);
    }

    @Test
    public void loserCannotDropBelowZeroStars() {
        PlayerProgressService.RewardComputation reward = PlayerProgressService.computeReward(
                100, false, 0, 0, 2);

        assertEquals(-8, reward.starDelta);
        assertEquals(0, reward.remainingStars);
        assertEquals(0, reward.earnedTokens);
        assertEquals(0, reward.nextTotalStars);
        assertEquals(2, reward.nextTokens);
    }

    @Test
    public void everyFiftyStarsGeneratesToken() {
        PlayerProgressService.RewardComputation reward = PlayerProgressService.computeReward(
                40, true, 39, 39, 1);

        assertEquals(11, reward.starDelta);
        assertEquals(0, reward.remainingStars);
        assertEquals(1, reward.earnedTokens);
        assertEquals(2, reward.nextTokens);
        assertEquals(50, reward.nextTotalStars);
    }
}
