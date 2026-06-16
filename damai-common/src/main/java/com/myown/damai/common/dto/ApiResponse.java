package com.myown.damai.common.dto;

/**
 * Wraps all HTTP API responses with a stable code, message, and data shape.
 */
public record ApiResponse<T>(String code, String message, T data) {

    /**
     * Builds a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "success", data);
    }

    /**
     * Builds a successful response without data.
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>("SUCCESS", "success", null);
    }

    /**
     * Builds a failed response with a business code and message.
     */
    public static ApiResponse<Void> failed(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
