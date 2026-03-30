package com.konkuk.propit.domain.report.dto.response;

import java.util.List;

public record OverviewReportResponse(
        Summary summary,
        List<ProfitRatePoint> profitRateTrendSeries,
        AiAnalysis aiAnalysis,
        List<SectorPerformance> sectorPerformance,
        List<Strategy> strategies
) {
    public record Summary(
            int totalTradeCount,
            double winRate,
            long totalProfit,
            double avgProfitRate,
            TradeInfo bestTrade,
            TradeInfo worstTrade,
            EmotionInfo emotionSummary,
            double averageHoldingDays
    ) {}

    public record TradeInfo(
            String stockName,
            long profit,
            double profitRate,
            String date
    ) {}

    public record EmotionInfo(
            String emotion,
            long count
    ){}

    public record ProfitRatePoint(
            int month,
            double profitRate
    ) {}

    public record AiAnalysis(
            AnalysisBlock positive,
            AnalysisBlock negative
    ) {}

    public record AnalysisBlock(
            String emotion,
            String description,
            String insight
    ) {}

    public record SectorPerformance(
            String sector,
            double rate
    ) {}

    public record Strategy(
            String title,
            String description
    ) {}
}
