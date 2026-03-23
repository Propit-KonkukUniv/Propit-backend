package com.konkuk.propit.global.security.dto;

public record TokenResponse (
        String accessToken,
        String refreshToken
) {
}
