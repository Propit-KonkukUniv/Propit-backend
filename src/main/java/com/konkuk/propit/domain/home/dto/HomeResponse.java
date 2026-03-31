package com.konkuk.propit.domain.home.dto;

public record HomeResponse(

        TodayMood todayMood,
        DailyReport dailyReport,
        CumulativeReport cumulativeReport

) {

    public record TodayMood(
            boolean exists,
            String mood,
            String message
    ) {}

    public record DailyReport(
            boolean exists,
            Data data
    ) {
        public record Data(
                int tradeCount,
                double winRate,
                long profit,
                double avgReturnRate,
                String feedback
        ) {}
    }

    public record CumulativeReport(
            boolean exists,
            Data data
    ) {
        public record Data(
                int totalTradeCount,
                double winRate,
                long totalProfit,
                double avgReturnRate
        ) {}
    }
}
