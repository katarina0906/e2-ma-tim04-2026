package com.example.slagalicatim04.regions;

import java.util.List;
import java.util.Map;

public class RegionDashboard {
    public final List<RegionRankItem> ranking;
    public final List<RegionPlayerPoint> playerPoints;
    public final Map<String, RegionStats> statsByRegionKey;

    public RegionDashboard(List<RegionRankItem> ranking,
                           List<RegionPlayerPoint> playerPoints,
                           Map<String, RegionStats> statsByRegionKey) {
        this.ranking = ranking;
        this.playerPoints = playerPoints;
        this.statsByRegionKey = statsByRegionKey;
    }
}
