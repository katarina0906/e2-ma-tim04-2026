package com.example.slagalicatim04.regions;

public class RegionPlayerPoint {
    public final String playerId;
    public final String regionKey;
    public final double latitude;
    public final double longitude;
    public final float x;
    public final float y;

    public RegionPlayerPoint(String playerId, String regionKey, double latitude, double longitude) {
        this.playerId = playerId;
        this.regionKey = regionKey;
        this.latitude = latitude;
        this.longitude = longitude;
        this.x = (float) latitude;
        this.y = (float) longitude;
    }
}
