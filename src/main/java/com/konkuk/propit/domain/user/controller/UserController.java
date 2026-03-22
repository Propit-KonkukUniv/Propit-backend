package com.konkuk.propit.domain.user.controller;

import com.konkuk.propit.domain.user.dto.request.UserLoginRequest;
import com.konkuk.propit.domain.user.dto.request.UserSignupRequest;
import com.konkuk.propit.domain.user.dto.response.UserLoginResponse;
import com.konkuk.propit.domain.user.dto.response.UserSignupResponse;
import com.konkuk.propit.domain.user.service.UserService;
import com.konkuk.propit.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.konkuk.propit.global.response.SuccessCode.USER_LOGIN_SUCCESS;
import static com.konkuk.propit.global.response.SuccessCode.USER_SIGNUP_SUCCESS;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserSignupResponse>> signup(
            @Valid @RequestBody UserSignupRequest request
    ) {
        UserSignupResponse response = userService.signup(request);

        return ResponseEntity
            .status(USER_SIGNUP_SUCCESS.getCode())
            .body(ApiResponse.success(USER_SIGNUP_SUCCESS, response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> login(
            @RequestBody @Valid UserLoginRequest request
    ) {
        UserLoginResponse response = userService.login(request);

        return ResponseEntity.ok().body(ApiResponse.success(USER_LOGIN_SUCCESS, response));
    }
}
