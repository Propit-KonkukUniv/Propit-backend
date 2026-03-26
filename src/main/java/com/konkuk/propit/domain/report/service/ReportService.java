package com.konkuk.propit.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.propit.domain.report.dto.response.AiGeneratedResult;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.ai.service.OpenAiService;
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
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

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

        // 1. 프롬프트 생성
        String prompt = buildPrompt(summary, emotionAnalysisList);

        // 2. OpenAI 호출
        String aiRawJson = openAiService.requestDailyAnalysis(prompt);

        // 3. JSON -> DTO 변환
        AiGeneratedResult aiResult;

        try {
            aiResult = objectMapper.readValue(aiRawJson, AiGeneratedResult.class);
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

    private String buildPrompt(
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
}