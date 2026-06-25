package com.example.slagalicatim04.friends;

public class FriendItem {
    public final String id;
    public final String username;
    public final String email;
    public final String region;
    public final String avatarData;
    public final int avatarFramePlace;

    public FriendItem(String id, String username, String email, String region,
                      String avatarData, int avatarFramePlace) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.region = region;
        this.avatarData = avatarData;
        this.avatarFramePlace = avatarFramePlace;
    }
}
