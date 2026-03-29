package com.konkuk.propit.domain.report.dto.response;

import java.util.List;

public record OverviewAiResult(
        OverviewReportResponse.AiAnalysis analysis,
        List<OverviewReportResponse.Strategy> strategies
) {
}
