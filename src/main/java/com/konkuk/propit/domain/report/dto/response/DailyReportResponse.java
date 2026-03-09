package com.konkuk.propit.domain.report.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DailyReportResponse(
        LocalDate date,
        Summary summary,
        List<EmotionAnalysis> emotionAnalysis,
        AiInsight aiInsight,
        List<String> todayAdvice
) {

    public record Summary(
            int tradeCount,
            double winRate,
            long totalProfit,
            double averageProfitRate
    ) {}

    public record EmotionAnalysis(
            String emotion,
            int count,
            String analysis
    ) {}

    public record AiInsight(
            String strengthPattern,
            String improvementPoint,
            String cautionTime
    ) {}
}
