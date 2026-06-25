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

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, "");
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData) {
        this(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, 0);
    }

    public AuthUser(String id, String email, String username, String region, String passwordHash,
                    String passwordSalt, boolean emailVerified, String verificationToken,
                    String avatarData, int tokens) {
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

    public AuthUser verified() {
        return new AuthUser(id, email, username, region, passwordHash, passwordSalt, true, "",
                avatarData, tokens);
    }

    public AuthUser withPassword(String passwordHash, String passwordSalt) {
        return new AuthUser(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, tokens);
    }

    public AuthUser withVerificationToken(String verificationToken) {
        return new AuthUser(id, email, username, region, passwordHash, passwordSalt, emailVerified,
                verificationToken, avatarData, tokens);
    }
}
