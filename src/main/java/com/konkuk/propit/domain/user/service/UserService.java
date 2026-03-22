package com.konkuk.propit.domain.user.service;

import com.konkuk.propit.domain.user.dto.request.UserSignupRequest;
import com.konkuk.propit.domain.user.dto.response.UserSignupResponse;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSignupResponse signup(UserSignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new BaseException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BaseException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.builder()
                .email(request.email())
                .password(encodedPassword)
                .nickname(request.nickname())
                .build();

        userRepository.save(user);

        return UserSignupResponse.from(user);
    }

}
