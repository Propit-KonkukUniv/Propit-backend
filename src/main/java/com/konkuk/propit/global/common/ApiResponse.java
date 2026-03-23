package com.konkuk.propit.global.common;

import com.konkuk.propit.global.exception.code.ErrorCode;
import com.konkuk.propit.global.response.SuccessCode;

public record ApiResponse<T>(
        boolean success,
        int code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(SuccessCode code, T data) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), data);
    }

    public static ApiResponse<?> error(ErrorCode errorCode) {
        return new ApiResponse<>(
                false,
                errorCode.getStatus().value(),
                errorCode.getMessage(),
                null
        );
    }
}