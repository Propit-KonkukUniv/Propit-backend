package com.konkuk.propit.global.common;

public record ApiResponse(
        boolean success,
        int code,
        String message
) {
}