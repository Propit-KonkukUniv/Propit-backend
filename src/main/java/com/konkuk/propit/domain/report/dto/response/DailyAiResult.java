package com.konkuk.propit.domain.report.dto.response;

import java.util.List;

public record DailyAiResult(
        List<EmotionAnalysis> emotionAnalysis,
        AiInsight aiInsight,
        List<String> todayAdvice
) {

    public record EmotionAnalysis(
            String emotion,
            String analysis
    ) {}

    public record AiInsight(
            String strengthPattern,
            String improvementPoint,
            String cautionTime
    ) {}
}
