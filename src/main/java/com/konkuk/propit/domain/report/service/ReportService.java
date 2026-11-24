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
import com.konkuk.propit.global.lock.RedisLock;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.konkuk.propit.global.exception.code.ErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    // AI 호출 도중에 락이 풀리지 않도록 최대 호출 시간보다 넉넉하게 잡는다
    private static final Duration LOCK_TTL = OpenAiService.MAX_CALL_DURATION.plusSeconds(30);
    // 앞 요청의 AI 호출이 끝날 때까지 기다렸다가 그 결과(캐시)를 받아 간다
    private static final Duration LOCK_WAIT = OpenAiService.MAX_CALL_DURATION.plusSeconds(10);

    private final TradeLogRepository tradeLogRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final ReportCacheService reportCacheService;
    private final RedisLock redisLock;
    private final TransactionTemplate transactionTemplate;

    public DailyReportResponse generateDailyReport(CustomUserDetails userDetails, LocalDate date) {

        Long userId = userDetails.getUserId();

        Optional<DailyReportResponse> cached = reportCacheService.findDaily(userId, date);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 같은 유저·같은 날짜 요청끼리만 순서대로 처리한다.
        // 락은 트랜잭션 바깥에서 잡는다. 그래야 커밋이 끝난 뒤에 락이 풀리고,
        // 기다리던 요청이 새 트랜잭션에서 앞 요청이 저장한 캐시를 볼 수 있다.
        String lockKey = "report:daily:" + userId + ":" + date;
        return redisLock.executeWithLock(lockKey, LOCK_TTL, LOCK_WAIT, () -> {

            Optional<DailyReportResponse> cachedAfterWait = reportCacheService.findDaily(userId, date);
            if (cachedAfterWait.isPresent()) {
                return cachedAfterWait.get();
            }

            return createDailyReport(userId, date);
        });
    }

    private DailyReportResponse createDailyReport(Long userId, LocalDate date) {

        // DB 조회와 통계 계산만 트랜잭션 안에서 하고, AI 호출 동안에는 DB 커넥션을 잡지 않는다
        DailyStats stats = transactionTemplate.execute(status -> loadDailyStats(userId, date));

        String prompt = buildDailyAiPrompt(stats.summary(), stats.emotionCountMap(), stats.emotionAvgProfitMap());

        Optional<String> aiRawJson = openAiService.requestAnalysis(prompt);

        if (aiRawJson.isEmpty()) {
            // AI 호출에 실패하면 통계만 담아 응답하고 캐시에는 저장하지 않는다. 다음 요청에서 다시 시도한다.
            return delayedDailyReport(date, stats.summary());
        }

        DailyAiResult aiResult = parseDailyAiResult(aiRawJson.get());

        List<DailyReportResponse.EmotionAnalysis> emotionAnalysisList =
                aiResult.emotionAnalysis().stream()
                        .map(ai -> new DailyReportResponse.EmotionAnalysis(
                                ai.emotion(),
                                stats.emotionCountMap().getOrDefault(ai.emotion(), 0L).intValue(),
                                ai.analysis()
                        ))
                        .toList();

        DailyReportResponse response = new DailyReportResponse(
                date,
                stats.summary(),
                emotionAnalysisList,
                new DailyReportResponse.AiInsight(
                        aiResult.aiInsight().strengthPattern(),
                        aiResult.aiInsight().improvementPoint(),
                        aiResult.aiInsight().cautionTime()
                ),
                aiResult.todayAdvice()
        );

        // 캐시 저장은 자기 트랜잭션에서 끝나므로, 실패해도 응답에는 영향이 없다
        try {
            reportCacheService.saveDaily(userId, date, stats.version(), response);
        } catch (Exception e) {
            log.warn("캐시 저장 실패", e);
        }

        return response;
    }

    private DailyAiResult parseDailyAiResult(String aiRawJson) {
        DailyAiResult aiResult;
        try {
            aiResult = objectMapper.readValue(aiRawJson, DailyAiResult.class);
        } catch (Exception e) {
            throw new BaseException(AI_RESPONSE_PARSE_FAILED);
        }
        // 필수 항목이 빠진 응답은 파싱 실패로 본다
        if (aiResult.emotionAnalysis() == null || aiResult.aiInsight() == null) {
            throw new BaseException(AI_RESPONSE_PARSE_FAILED);
        }
        return aiResult;
    }

    private DailyReportResponse delayedDailyReport(LocalDate date, DailyReportResponse.Summary summary) {
        return new DailyReportResponse(
                date,
                summary,
                List.of(),
                new DailyReportResponse.AiInsight(
                        "AI 분석 지연",
                        "잠시 후 다시 시도해주세요",
                        "현재 요청이 많습니다"
                ),
                List.of("잠시 후 다시 요청해주세요")
        );
    }

    private DailyStats loadDailyStats(Long userId, LocalDate date) {

        // 버전을 매매 내역보다 먼저 읽는다. 그사이 매매일지가 바뀌어도
        // 캐시에는 옛 버전이 붙어서 다음 조회 때 새로 만들어진다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(USER_NOT_FOUND));
        long version = user.getTradeLogVersion();

        List<TradeLog> logs =
                tradeLogRepository.findWithEmotions(user, date);

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

        Map<String, Long> emotionCountMap = logs.stream()
                .flatMap(log -> log.getTradeEmotions().stream())
                .collect(Collectors.groupingBy(
                        te -> te.getEmotion().getName(),
                        Collectors.counting()
                ));

        Map<String, Double> emotionAvgProfitMap = logs.stream()
                .flatMap(log -> log.getTradeEmotions().stream()
                        .map(te -> Map.entry(
                                te.getEmotion().getName(),
                                log.getProfitRate()
                        )))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.averagingDouble(Map.Entry::getValue)
                ));

        return new DailyStats(version, summary, emotionCountMap, emotionAvgProfitMap);
    }

    private record DailyStats(
            long version,
            DailyReportResponse.Summary summary,
            Map<String, Long> emotionCountMap,
            Map<String, Double> emotionAvgProfitMap
    ) {}

    private String buildDailyAiPrompt(
            DailyReportResponse.Summary summary,
            Map<String, Long> emotionCountMap,
            Map<String, Double> emotionAvgProfitMap
    ) {

        String emotionText = emotionCountMap.keySet().stream()
                .map(emotion ->
                        emotion + ": "
                                + emotionCountMap.get(emotion) + "회, "
                                + String.format("평균 %.2f%%", emotionAvgProfitMap.getOrDefault(emotion, 0.0))
                )
                .collect(Collectors.joining("\n"));

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

        Long userId = userDetails.getUserId();

        Optional<OverviewReportResponse> cached = reportCacheService.findOverview(userId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 같은 유저 요청끼리만 순서대로 처리한다. 락을 트랜잭션 바깥에서 잡는 이유는 일일 리포트와 같다.
        String lockKey = "report:overview:" + userId;
        return redisLock.executeWithLock(lockKey, LOCK_TTL, LOCK_WAIT, () -> {

            Optional<OverviewReportResponse> cachedAfterWait = reportCacheService.findOverview(userId);
            if (cachedAfterWait.isPresent()) {
                return cachedAfterWait.get();
            }

            return createOverviewReport(userId);
        });
    }

    private OverviewReportResponse createOverviewReport(Long userId) {

        // DB 조회와 통계 계산만 트랜잭션 안에서 하고, AI 호출 동안에는 DB 커넥션을 잡지 않는다
        OverviewStats stats = transactionTemplate.execute(status -> loadOverviewStats(userId));

        // 4. AI & 전략
        Optional<OverviewAiResult> aiResultOrEmpty = generateAiResult(stats.aiPrompt());

        if (aiResultOrEmpty.isEmpty()) {
            // AI 호출에 실패하면 통계만 담아 응답하고 캐시에는 저장하지 않는다. 다음 요청에서 다시 시도한다.
            return new OverviewReportResponse(
                    stats.summary(),
                    stats.trend(),
                    null,
                    stats.sector(),
                    List.of()
            );
        }

        OverviewAiResult aiResult = aiResultOrEmpty.get();

        OverviewReportResponse response = new OverviewReportResponse(
                stats.summary(),
                stats.trend(),
                aiResult.analysis(),
                stats.sector(),
                aiResult.strategies()
        );

        // 캐시 저장은 자기 트랜잭션에서 끝나므로, 실패해도 응답에는 영향이 없다
        try {
            reportCacheService.saveOverview(userId, stats.version(), response);
        } catch (Exception e) {
            log.warn("캐시 저장 실패", e);
        }

        return response;
    }

    private OverviewStats loadOverviewStats(Long userId) {

        // 버전을 매매 내역보다 먼저 읽는다 (loadDailyStats 참고)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(USER_NOT_FOUND));
        long version = user.getTradeLogVersion();

        List<TradeLog> logs = tradeLogRepository.findAllWithEmotionsByUser(user);

        if (logs.isEmpty()) {
            throw new BaseException(TRADELOG_NOT_EXISTS);
        }

        return new OverviewStats(
                version,
                calculateSummary(logs),     // 1. summary
                calculateTrend(logs),       // 2. trend
                calculateSector(logs),      // 3. sector
                buildOverviewAiPrompt(logs)
        );
    }

    private record OverviewStats(
            long version,
            OverviewReportResponse.Summary summary,
            List<OverviewReportResponse.ProfitRatePoint> trend,
            List<OverviewReportResponse.SectorPerformance> sector,
            String aiPrompt
    ) {}

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

    private Optional<OverviewAiResult> generateAiResult(String prompt) {

        Optional<String> aiRaw = openAiService.requestAnalysis(prompt);

        if (aiRaw.isEmpty()) {
            return Optional.empty();
        }

        OverviewAiResult aiResult;
        try {
            aiResult = objectMapper.readValue(aiRaw.get(), OverviewAiResult.class);
        } catch (Exception e) {
            throw new BaseException(AI_RESPONSE_PARSE_FAILED);
        }
        // 필수 항목이 빠진 응답은 파싱 실패로 본다. 그대로 두면 빈 리포트가 캐시에 남는다.
        if (aiResult.analysis() == null || aiResult.strategies() == null) {
            throw new BaseException(AI_RESPONSE_PARSE_FAILED);
        }
        return Optional.of(aiResult);
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