package com.konkuk.propit.global.security.service;

import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.exception.code.ErrorCode;
import com.konkuk.propit.global.security.jwt.JwtTokenProvider;
import com.konkuk.propit.global.security.dto.TokenResponse;
import com.konkuk.propit.global.security.redis.RedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRepository redisRepository;

    private static final String REFRESH_PREFIX = "RT:";

    public TokenResponse generateToken(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        redisRepository.save(
                REFRESH_PREFIX + userId,
                refreshToken,
                jwtTokenProvider.getRefreshExpire()
        );

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse refreshToken(String refreshToken) {

        jwtTokenProvider.validateToken(refreshToken);

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        String savedRefreshToken = redisRepository.get(REFRESH_PREFIX + userId);

        if (savedRefreshToken == null) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        if (!savedRefreshToken.equals(refreshToken)) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        redisRepository.save(
                REFRESH_PREFIX + userId,
                newRefreshToken,
                jwtTokenProvider.getRefreshExpire()
        );

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        redisRepository.delete(REFRESH_PREFIX + userId);
    }
}
