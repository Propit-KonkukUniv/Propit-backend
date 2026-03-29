package com.konkuk.propit.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.propit.domain.report.dto.response.DailyAiResult;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewAiResult;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.ai.service.OpenAiService;
import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.konkuk.propit.global.exception.code.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TradeLogRepository tradeLogRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    public DailyReportResponse generateDailyReport(LocalDate date) {

        User user = userRepository.findById(1L)
                .orElseThrow();

        List<TradeLog> logs =
                tradeLogRepository.findByUserAndSellDate(user, date);

        if (logs.isEmpty()) {
            throw new BaseException(TRADELOG_NOT_EXISTS);
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

        // 1. 프롬프트 생성
        String prompt = buildDailyAiPrompt(summary, emotionAnalysisList);

        // 2. OpenAI 호출
        String aiRawJson = openAiService.requestAnalysis(prompt);

        // 3. JSON -> DTO 변환
        DailyAiResult aiResult;

        try {
            aiResult = objectMapper.readValue(aiRawJson, DailyAiResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 응답 파싱 실패", e);
        }

        return new DailyReportResponse(
                date,
                summary,
                emotionAnalysisList, // count는 서버 계산값 유지
                new DailyReportResponse.AiInsight(
                        aiResult.aiInsight().strengthPattern(),
                        aiResult.aiInsight().improvementPoint(),
                        aiResult.aiInsight().cautionTime()
                ),
                aiResult.todayAdvice()
        );
    }

    private String buildDailyAiPrompt(
            DailyReportResponse.Summary summary,
            List<DailyReportResponse.EmotionAnalysis> emotions
    ) {

        String emotionText = emotions.stream()
                .map(e -> e.emotion() + " : " + e.count() + "회")
                .reduce("", (a, b) -> a + "\n" + b);

        return """
            다음은 사용자의 하루 투자 요약 데이터입니다.

            거래 수: %d
            승률: %.2f%%
            총 수익: %d
            평균 수익률: %.2f%%

            감정 통계:
            %s

            반드시 아래 JSON 형식으로만 응답하세요.

            {
              "emotionAnalysis": [
                {
                  "emotion": "",
                  "analysis": ""
                }
              ],
              "aiInsight": {
                "strengthPattern": "",
                "improvementPoint": "",
                "cautionTime": ""
              },
              "todayAdvice": []
            }
            """.formatted(
                summary.tradeCount(),
                summary.winRate(),
                summary.totalProfit(),
                summary.averageProfitRate(),
                emotionText
        );
    }

    public OverviewReportResponse getOverviewReport(CustomUserDetails userDetails) {

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new BaseException(USER_NOT_FOUND));

        List<TradeLog> logs = tradeLogRepository.findAllByUser(user);

        if (logs.isEmpty()) {
            throw new BaseException(TRADELOG_NOT_EXISTS);
        }

        // 1. summary
        OverviewReportResponse.Summary summary = calculateSummary(logs);

        // 2. trend
        List<OverviewReportResponse.ProfitRatePoint> trend = calculateTrend(logs);

        // 3. sector
        List<OverviewReportResponse.SectorPerformance> sector = calculateSector(logs);

        // 4. AI & 전략
        OverviewAiResult aiResult = generateAiResult(logs);

        return new OverviewReportResponse(
                summary,
                trend,
                aiResult.analysis(),
                sector,
                aiResult.strategies()
        );
    }

    private OverviewReportResponse.Summary calculateSummary(List<TradeLog> logs) {

        int totalTrades = logs.size();

        long winCount = logs.stream()
                .filter(log -> log.getProfitAmount() > 0)
                .count();

        double winRate = (double) winCount / totalTrades * 100;

        long totalProfit = logs.stream()
                .mapToLong(TradeLog::getProfitAmount)
                .sum();

        double avgReturn = logs.stream()
                .mapToDouble(TradeLog::getProfitRate)
                .average()
                .orElse(0.0);

        TradeLog best = logs.stream()
                .max(Comparator.comparing(TradeLog::getProfitAmount))
                .orElse(null);

        TradeLog worst = logs.stream()
                .min(Comparator.comparing(TradeLog::getProfitAmount))
                .orElse(null);

        Map.Entry<String, Long> emotionEntry = calculateEmotion(logs);

        double avgHoldingDays = calculateHoldingDays(logs);

        return new OverviewReportResponse.Summary(
                totalTrades,
                winRate,
                totalProfit,
                avgReturn,
                toTradeInfo(best),
                toTradeInfo(worst),
                new OverviewReportResponse.EmotionInfo(
                        emotionEntry.getKey(),
                        emotionEntry.getValue()
                ),
                avgHoldingDays
        );
    }

    private Map.Entry<String, Long> calculateEmotion(List<TradeLog> logs) {
        return logs.stream()
                .flatMap(log -> log.getTradeEmotions().stream())
                .collect(Collectors.groupingBy(
                        te -> te.getEmotion().getName(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(Map.entry("NONE", 0L));
    }

    private double calculateHoldingDays(List<TradeLog> logs) {
        return logs.stream()
                .mapToInt(TradeLog::getHoldingDays)
                .average()
                .orElse(0);
    }

    private List<OverviewReportResponse.ProfitRatePoint> calculateTrend(List<TradeLog> logs) {

        return logs.stream()
                .collect(Collectors.groupingBy(
                        log -> java.time.YearMonth.from(log.getSellDate()),
                        TreeMap::new,
                        Collectors.averagingDouble(TradeLog::getProfitRate)
                ))
                .entrySet()
                .stream()
                .map(e -> new OverviewReportResponse.ProfitRatePoint(
                        e.getKey().getMonthValue(),
                        e.getValue()
                ))
                .toList();
    }

    private List<OverviewReportResponse.SectorPerformance> calculateSector(List<TradeLog> logs) {

        return logs.stream()
                .collect(Collectors.groupingBy(
                        TradeLog::getSector,
                        Collectors.averagingDouble(TradeLog::getProfitRate)
                ))
                .entrySet()
                .stream()
                .map(e -> new OverviewReportResponse.SectorPerformance(
                        e.getKey(),
                        e.getValue()
                ))
                .toList();
    }

    private OverviewAiResult generateAiResult(List<TradeLog> logs) {

        String prompt = buildOverviewAiPrompt(logs);

        String aiRaw = openAiService.requestAnalysis(prompt);

        try {
            return objectMapper.readValue(aiRaw, OverviewAiResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 파싱 실패", e);
        }
    }

    private String buildOverviewAiPrompt(List<TradeLog> logs) {

        int totalTrades = logs.size();

        long winCount = logs.stream()
                .filter(l -> l.getProfitAmount() > 0)
                .count();

        double winRate = (double) winCount / totalTrades * 100;

        Map<String, Double> emotionPerformance = logs.stream()
                .flatMap(log -> log.getTradeEmotions().stream()
                        .map(te -> Map.entry(
                                te.getEmotion().getName(),
                                log.getProfitRate()
                        )))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.averagingDouble(Map.Entry::getValue)
                ));

        String emotionText = emotionPerformance.entrySet().stream()
                .map(e -> e.getKey() + ": " + String.format("%.2f%%", e.getValue()))
                .collect(Collectors.joining("\n"));

        return """
        투자 데이터 기반 분석.

        거래 수: %d
        승률: %.2f%%

        감정별 수익률:
        %s
        
        전략은 반드시 서로 다른 관점으로 3~4개 생성
        
        JSON 형식으로 응답:

        {
          "analysis": {
            "positive": { "emotion": "", "description": "", "insight": "" },
            "negative": { "emotion": "", "description": "", "insight": "" }
          },
          "strategies": [
            { "title": "", "description": "" }
          ]
        }
        """.formatted(totalTrades, winRate, emotionText);
    }

    private OverviewReportResponse.TradeInfo toTradeInfo(TradeLog log) {
        if (log == null) return null;

        return new OverviewReportResponse.TradeInfo(
                log.getStockName(),
                log.getProfitAmount(),
                log.getProfitRate(),
                log.getSellDate().toString()
        );
    }

}