package com.example.slagalicatim04.friends;

public class FriendItem {
    public final String id;
    public final String username;
    public final String email;
    public final String region;
    public final String avatarData;
    public final int avatarFramePlace;
    public final int monthlyRank;
    public final long totalStars;
    public final String league;
    public final boolean online;
    public final boolean inGame;

    public FriendItem(String id, String username, String email, String region,
                      String avatarData, int avatarFramePlace, int monthlyRank,
                      long totalStars, String league, boolean online, boolean inGame) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.region = region;
        this.avatarData = avatarData;
        this.avatarFramePlace = avatarFramePlace;
        this.monthlyRank = monthlyRank;
        this.totalStars = totalStars;
        this.league = league;
        this.online = online;
        this.inGame = inGame;
    }

    public boolean canStartGame() {
        return online && !inGame;
    }
}
