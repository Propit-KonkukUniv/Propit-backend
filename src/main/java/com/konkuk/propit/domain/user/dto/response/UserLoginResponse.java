package com.konkuk.propit.domain.user.dto.response;

import com.konkuk.propit.global.security.dto.TokenResponse;

public record UserLoginResponse (
        Long userId,
        String accessToken,
        String refreshToken
) {
    public static UserLoginResponse of(Long userId, TokenResponse token) {
        return new UserLoginResponse(
                userId,
                token.accessToken(),
                token.refreshToken()
        );
    }
}
