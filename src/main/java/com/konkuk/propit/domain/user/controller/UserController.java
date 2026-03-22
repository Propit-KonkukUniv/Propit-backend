package com.konkuk.propit.domain.user.controller;

import com.konkuk.propit.domain.user.dto.request.UserSignupRequest;
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

import static com.konkuk.propit.global.response.SuccessCode.USER_SIGNUP_SUCCESS;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserSignupResponse>> signup(
            @Valid @RequestBody UserSignupRequest request
    ) {
        UserSignupResponse response = userService.signup(request);

        return ResponseEntity
            .status(USER_SIGNUP_SUCCESS.getCode())
            .body(ApiResponse.success(USER_SIGNUP_SUCCESS, response));
    }
}
