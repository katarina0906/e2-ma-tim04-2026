package com.example.slagalicatim04.auth;

public class AuthUser {

    private final String id;
    private final String email;
    private final String username;
    private final String region;
    private final String passwordHash;
    private final String passwordSalt;
    private final boolean emailVerified;
    private final String verificationToken;
    private final String avatarData;
    private final int tokens;
    private final int stars;
    private final int totalStars;
    private final Double regionMapLatitude;
    private final Double regionMapLongitude;
    private final int avatarFramePlace;

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, "");
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, 0, 0, null, null, 0);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, int tokens) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, tokens, 0, null, null, 0);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, Double regionMapLatitude, Double regionMapLongitude) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, 0, 0, regionMapLatitude, regionMapLongitude, 0);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, int tokens, int stars) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, tokens, stars, null, null, 0);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, Double regionMapLatitude, Double regionMapLongitude,
                    int avatarFramePlace) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, 0, 0, regionMapLatitude, regionMapLongitude,
                avatarFramePlace);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, int tokens, int stars, Double regionMapLatitude,
                    Double regionMapLongitude, int avatarFramePlace) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, tokens, stars, stars, regionMapLatitude,
                regionMapLongitude, avatarFramePlace);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, int tokens, int stars, int totalStars,
                    Double regionMapLatitude, Double regionMapLongitude, int avatarFramePlace) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.region = region;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.emailVerified = emailVerified;
        this.verificationToken = verificationToken;
        this.avatarData = avatarData;
        this.tokens = tokens;
        this.stars = stars;
        this.totalStars = totalStars;
        this.regionMapLatitude = regionMapLatitude;
        this.regionMapLongitude = regionMapLongitude;
        this.avatarFramePlace = avatarFramePlace;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getRegion() {
        return region;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public String getAvatarData() {
        return avatarData;
    }

    public int getTokens() {
        return tokens;
    }

    public int getStars() {
        return stars;
    }

    public int getTotalStars() {
        return totalStars;
    }

    public Double getRegionMapLatitude() {
        return regionMapLatitude;
    }

    public Double getRegionMapLongitude() {
        return regionMapLongitude;
    }

    public int getAvatarFramePlace() {
        return avatarFramePlace;
    }

    public AuthUser verified() {
        return new AuthUser(id, email, username, region, passwordHash, passwordSalt, true, "",
                avatarData, tokens, stars, totalStars, regionMapLatitude, regionMapLongitude,
                avatarFramePlace);
    }

    public AuthUser withPassword(String passwordHash, String passwordSalt) {
        return new AuthUser(id, email, username, region, passwordHash, passwordSalt,
                emailVerified, verificationToken, avatarData, tokens, stars, totalStars,
                regionMapLatitude, regionMapLongitude, avatarFramePlace);
    }

    public AuthUser withVerificationToken(String verificationToken) {
        return new AuthUser(id, email, username, region, passwordHash, passwordSalt,
                emailVerified, verificationToken, avatarData, tokens, stars, totalStars,
                regionMapLatitude, regionMapLongitude, avatarFramePlace);
    }
}
