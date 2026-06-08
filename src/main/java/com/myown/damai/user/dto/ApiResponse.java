package com.myown.damai.user.dto;

public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "success", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("SUCCESS", "success", null);
    }

    public static ApiResponse<Void> failed(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
