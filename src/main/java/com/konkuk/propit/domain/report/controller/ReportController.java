package com.konkuk.propit.domain.report.controller;

import com.konkuk.propit.domain.report.dto.request.DailyReportRequest;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.report.service.ReportService;
import com.konkuk.propit.global.common.ApiResponse;
import com.konkuk.propit.global.response.SuccessCode;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.konkuk.propit.global.response.SuccessCode.DAILY_REPORT_SUCCESS;
import static com.konkuk.propit.global.response.SuccessCode.OVERVIEW_REPORT_SUCCESS;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/daily")
    public ResponseEntity<?> getDailyReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody DailyReportRequest request
    ) {

        DailyReportResponse response = reportService.generateDailyReport(userDetails, request.date());

        return ResponseEntity.ok().body(ApiResponse.success(DAILY_REPORT_SUCCESS, response));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<OverviewReportResponse>> getOverviewReport(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        OverviewReportResponse response = reportService.getOverviewReport(userDetails);
        return ResponseEntity.ok().body(ApiResponse.success(OVERVIEW_REPORT_SUCCESS, response));
    }
}