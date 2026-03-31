package com.konkuk.propit.domain.home.controller;

import com.konkuk.propit.domain.home.dto.HomeResponse;
import com.konkuk.propit.domain.home.service.HomeService;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public HomeResponse getHome(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return homeService.getHome(userDetails);
    }
}
