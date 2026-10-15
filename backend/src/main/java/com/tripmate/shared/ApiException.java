package com.tripmate.shared;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiException notFound() {
        return new ApiException(404, "NOT_FOUND", "Resource not found");
    }
    public static ApiException invalid(String message) {
        return new ApiException(400, "INVALID_INPUT", message);
    }
    public static ApiException conflict() {
        return new ApiException(409, "VERSION_CONFLICT", "Resource was updated by another trip member");
    }
}
