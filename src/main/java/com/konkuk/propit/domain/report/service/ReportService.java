package com.konkuk.propit.domain.report.service;

import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TradeLogRepository tradeLogRepository;
    private final UserRepository userRepository;

    public DailyReportResponse generateDailyReport(LocalDate date) {

        User user = userRepository.findById(1L)
                .orElseThrow();

        List<TradeLog> logs =
                tradeLogRepository.findByUserAndSellDate(user, date);

        if (logs.isEmpty()) {
            throw new RuntimeException("해당 날짜에 작성된 매매기록이 없습니다.");
        }

        int tradeCount = logs.size();

        long totalProfit = logs.stream()
                .mapToLong(TradeLog::getProfitAmount)
                .sum();

        double avgProfitRate = logs.stream()
                .mapToDouble(TradeLog::getProfitRate)
                .average()
                .orElse(0.0);

        long winCount = logs.stream()
                .filter(l -> l.getProfitAmount() > 0)
                .count();

        double winRate = (double) winCount / tradeCount * 100;

        DailyReportResponse.Summary summary =
                new DailyReportResponse.Summary(
                        tradeCount,
                        winRate,
                        totalProfit,
                        avgProfitRate
                );

        // 감정 집계
        Map<String, Long> emotionCountMap = logs.stream()
                .flatMap(log -> log.getTradeEmotions().stream())
                .collect(Collectors.groupingBy(
                        te -> te.getEmotion().getName(),
                        Collectors.counting()
                ));

        List<DailyReportResponse.EmotionAnalysis> emotionAnalysisList =
                emotionCountMap.entrySet().stream()
                        .map(entry ->
                                new DailyReportResponse.EmotionAnalysis(
                                        entry.getKey(),
                                        entry.getValue().intValue(),
                                        entry.getKey() + " 감정이 자주 등장했습니다."
                                )
                        ).toList();

        // Todo AI Insight (지금은 mock)
        DailyReportResponse.AiInsight insight =
                new DailyReportResponse.AiInsight(
                        "장기 보유 전략이 유리합니다.",
                        "불안 감정에서 손절 타이밍이 늦습니다.",
                        "최근 오후 시간대 손실이 많습니다."
                );

        List<String> advice = List.of(
                "오늘은 변동성이 크니 신중하세요.",
                "감정 기반 매매를 줄여보세요."
        );

        return new DailyReportResponse(
                date,
                summary,
                emotionAnalysisList,
                insight,
                advice
        );
    }
}