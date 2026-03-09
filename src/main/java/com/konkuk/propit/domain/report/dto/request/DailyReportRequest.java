package com.konkuk.propit.domain.report.dto.request;

import java.time.LocalDate;

public record DailyReportRequest(
        LocalDate date
) {
}