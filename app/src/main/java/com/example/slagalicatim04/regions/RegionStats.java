package com.example.slagalicatim04.regions;

public class RegionStats {
    public final RegionInfo region;
    public final long firstPlaces;
    public final long secondPlaces;
    public final long thirdPlaces;
    public final long activePlayers;
    public final long totalPlayers;

    public RegionStats(RegionInfo region, long firstPlaces, long secondPlaces,
                       long thirdPlaces, long activePlayers, long totalPlayers) {
        this.region = region;
        this.firstPlaces = firstPlaces;
        this.secondPlaces = secondPlaces;
        this.thirdPlaces = thirdPlaces;
        this.activePlayers = activePlayers;
        this.totalPlayers = totalPlayers;
    }
}
