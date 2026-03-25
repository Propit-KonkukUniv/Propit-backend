package com.konkuk.propit.global.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SuccessCode {

    TRADELOG_CREATE_SUCCESS(HttpStatus.OK.value(), "매매 기록 생성에 성공했습니다."),
    TRADELOG_UPDATE_SUCCESS(HttpStatus.OK.value(), "매매 기록 수정에 성공했습니다."),
    TRADELOG_DETAIL_SUCCESS(HttpStatus.OK.value(), "매매 기록 조회에 성공했습니다."),
    TRADELOG_LIST_SUCCESS(HttpStatus.OK.value(), "매매 기록 목록 조회에 성공했습니다."),

    USER_SIGNUP_SUCCESS(HttpStatus.CREATED.value(), "회원가입에 성공했습니다."),
    USER_LOGIN_SUCCESS(HttpStatus.OK.value(), "로그인에 성공했습니다.");


    private final int code;
    private final String message;

    SuccessCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
