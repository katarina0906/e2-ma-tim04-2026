package com.example.slagalicatim04.leagues;

import android.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LeagueInfo {
    private static final List<LeagueInfo> LEAGUES = Collections.unmodifiableList(Arrays.asList(
            new LeagueInfo(0, "Nulta liga", 0L, R.drawable.ic_menu_help),
            new LeagueInfo(1, "Bronzana liga", 100L, R.drawable.star_big_off),
            new LeagueInfo(2, "Srebrna liga", 200L, R.drawable.star_big_on),
            new LeagueInfo(3, "Zlatna liga", 400L, R.drawable.btn_star_big_on),
            new LeagueInfo(4, "Platinasta liga", 800L, R.drawable.ic_menu_compass),
            new LeagueInfo(5, "Legendarna liga", 1600L, R.drawable.ic_menu_upload)
    ));

    public final int level;
    public final String name;
    public final long requiredStars;
    public final int iconRes;

    private LeagueInfo(int level, String name, long requiredStars, int iconRes) {
        this.level = level;
        this.name = name;
        this.requiredStars = requiredStars;
        this.iconRes = iconRes;
    }

    public static List<LeagueInfo> all() {
        return LEAGUES;
    }

    public static LeagueInfo forStars(long stars) {
        LeagueInfo result = LEAGUES.get(0);
        for (LeagueInfo league : LEAGUES) {
            if (stars >= league.requiredStars) {
                result = league;
            }
        }
        return result;
    }

    public LeagueInfo next() {
        int nextLevel = level + 1;
        return nextLevel >= LEAGUES.size() ? null : LEAGUES.get(nextLevel);
    }

    public long starsUntilNext(long stars) {
        LeagueInfo next = next();
        return next == null ? 0L : Math.max(0L, next.requiredStars - stars);
    }
}
