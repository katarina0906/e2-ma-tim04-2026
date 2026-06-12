package com.example.slagalicatim04.auth;

public class AuthResult<T> {

    private final boolean success;
    private final T data;
    private final String message;

    private AuthResult(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> AuthResult<T> success(T data, String message) {
        return new AuthResult<>(true, data, message);
    }

    public static <T> AuthResult<T> error(String message) {
        return new AuthResult<>(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }
}
