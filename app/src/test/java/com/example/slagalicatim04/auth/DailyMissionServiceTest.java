package com.example.slagalicatim04.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class DailyMissionServiceTest {
    @Test
    public void firstCompletionGivesThreeStars() {
        DailyMissionService.Reward reward = DailyMissionService.computeReward(
                "2026-07-03", new HashMap<>(), false,
                DailyMissionService.Mission.WIN_MATCH);

        assertTrue(reward.changed);
        assertEquals(3, reward.starDelta);
        assertEquals(0, reward.tokenDelta);
        assertTrue(reward.missions.get(DailyMissionService.Mission.WIN_MATCH.id));
        assertFalse(reward.allBonusClaimed);
    }

    @Test
    public void alreadyCompletedMissionDoesNotRewardAgain() {
        Map<String, Boolean> missions = new HashMap<>();
        missions.put(DailyMissionService.Mission.SEND_CHAT_MESSAGE.id, true);

        DailyMissionService.Reward reward = DailyMissionService.computeReward(
                "2026-07-03", missions, false,
                DailyMissionService.Mission.SEND_CHAT_MESSAGE);

        assertFalse(reward.changed);
        assertEquals(0, reward.starDelta);
        assertEquals(0, reward.tokenDelta);
    }

    @Test
    public void fourthMissionGivesCompletionBonus() {
        Map<String, Boolean> missions = new HashMap<>();
        missions.put(DailyMissionService.Mission.WIN_MATCH.id, true);
        missions.put(DailyMissionService.Mission.SEND_CHAT_MESSAGE.id, true);
        missions.put(DailyMissionService.Mission.PLAY_FRIENDLY_MATCH.id, true);

        DailyMissionService.Reward reward = DailyMissionService.computeReward(
                "2026-07-03", missions, false,
                DailyMissionService.Mission.WIN_TOURNAMENT_MATCH);

        assertTrue(reward.changed);
        assertEquals(6, reward.starDelta);
        assertEquals(2, reward.tokenDelta);
        assertTrue(reward.allBonusClaimed);
    }
}
