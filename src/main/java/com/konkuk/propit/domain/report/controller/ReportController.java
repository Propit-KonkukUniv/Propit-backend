package com.konkuk.propit.domain.report.controller;

import com.konkuk.propit.domain.report.dto.*;
import com.konkuk.propit.domain.report.dto.request.DailyReportRequest;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.service.ReportService;
import com.konkuk.propit.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/daily")
    public ResponseEntity<?> getDailyReport(
            @RequestBody DailyReportRequest request
    ) {

        DailyReportResponse response =
                reportService.generateDailyReport(request.date());

        return ResponseEntity.ok(
                new ApiResponse<>(true, 20000, "요청에 성공했습니다.", response)
        );
    }
}