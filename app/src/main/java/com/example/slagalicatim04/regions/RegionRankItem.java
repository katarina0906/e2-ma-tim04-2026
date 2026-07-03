package com.example.slagalicatim04.regions;

public class RegionRankItem {
    public final RegionInfo region;
    public final long monthlyStars;
    public final int rank;
    public final boolean currentPlayerRegion;

    public RegionRankItem(RegionInfo region, long monthlyStars, int rank,
                          boolean currentPlayerRegion) {
        this.region = region;
        this.monthlyStars = monthlyStars;
        this.rank = rank;
        this.currentPlayerRegion = currentPlayerRegion;
    }
}
