package com.konkuk.propit.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;

import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.report.repository.DailyReportCacheRepository;
import com.konkuk.propit.domain.report.repository.OverviewReportCacheRepository;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.tradelog.service.TradeLogService;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.ai.service.OpenAiService;
import com.konkuk.propit.global.lock.RedisLock;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 리포트 생성 동시성 테스트.
 * OpenAI는 일정 시간 걸리는 가짜 응답으로, Redis 락은 같은 키끼리만 막는 메모리 락으로 바꿔서 돌린다.
 */
@SpringBootTest(properties = {
        // H2에서 user가 예약어라 테이블이 만들어지지 않는 문제를 피한다
        "spring.datasource.url=jdbc:h2:mem:report-concurrency;NON_KEYWORDS=USER"
})
@ActiveProfiles("test")
class ReportServiceConcurrencyTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static final String EMOTION = "확신";

    private static final String DAILY_AI_JSON = """
            {
              "emotionAnalysis": [{ "emotion": "확신", "analysis": "분석" }],
              "aiInsight": { "strengthPattern": "패턴", "improvementPoint": "개선점", "cautionTime": "오전" },
              "todayAdvice": ["조언"]
            }
            """;

    private static final String OVERVIEW_AI_JSON = """
            {
              "analysis": {
                "positive": { "emotion": "확신", "description": "설명", "insight": "인사이트" },
                "negative": { "emotion": "불안", "description": "설명", "insight": "인사이트" }
              },
              "strategies": [{ "title": "전략", "description": "설명" }]
            }
            """;

    @MockitoBean
    private OpenAiService openAiService;

    @MockitoBean
    private RedisLock redisLock;

    @Autowired private ReportService reportService;
    @Autowired private TradeLogService tradeLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmotionRepository emotionRepository;
    @Autowired private TradeLogRepository tradeLogRepository;
    @Autowired private DailyReportCacheRepository dailyReportCacheRepository;
    @Autowired private OverviewReportCacheRepository overviewReportCacheRepository;
    @Autowired private DataSource dataSource;

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicInteger aiCalls = new AtomicInteger();
    private final AtomicInteger maxActiveConnectionsDuringAi = new AtomicInteger();

    private Long userId;

    @BeforeEach
    void setUp() {
        given(redisLock.executeWithLock(anyString(), any(), any(), any())).willAnswer(invocation -> {
            ReentrantLock lock = locks.computeIfAbsent(invocation.getArgument(0), key -> new ReentrantLock());
            lock.lock();
            try {
                Supplier<?> task = invocation.getArgument(3);
                return task.get();
            } finally {
                lock.unlock();
            }
        });
        givenAiResponds(300);

        emotionRepository.save(Emotion.builder().name(EMOTION).build());
        userId = createUser("user1");
        createTradeLog(userId);
    }

    @AfterEach
    void tearDown() {
        tradeLogRepository.deleteAll();
        dailyReportCacheRepository.deleteAll();
        overviewReportCacheRepository.deleteAll();
        userRepository.deleteAll();
        emotionRepository.deleteAll();
    }

    @Test
    void 같은_리포트를_동시에_요청해도_AI는_한_번만_호출된다() throws Exception {
        List<DailyReportResponse> responses = runConcurrently(10, () -> dailyReport(userId));

        assertThat(responses).hasSize(10);
        assertThat(responses).allSatisfy(r -> assertThat(r.summary()).isEqualTo(responses.get(0).summary()));
        assertThat(aiCalls.get()).isEqualTo(1);
        assertThat(dailyReportCacheRepository.count()).isEqualTo(1);
    }

    @Test
    void AI_호출_중에는_DB_커넥션을_잡고_있지_않는다() {
        dailyReport(userId);
        overviewReport(userId);

        assertThat(aiCalls.get()).isEqualTo(2);
        assertThat(maxActiveConnectionsDuringAi.get()).isZero();
    }

    @Test
    void 다른_유저의_리포트는_서로_기다리지_않는다() throws Exception {
        Long otherUserId = createUser("user2");
        createTradeLog(otherUserId);
        givenAiResponds(1500);

        long start = System.nanoTime();
        AtomicInteger index = new AtomicInteger();
        runConcurrently(2, () -> dailyReport(index.getAndIncrement() == 0 ? userId : otherUserId));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(aiCalls.get()).isEqualTo(2);
        // 한 줄로 처리됐다면 3초 이상 걸린다
        assertThat(elapsedMillis).isLessThan(2700);
    }

    @Test
    void 매매일지가_바뀌면_캐시를_새로_만든다() {
        assertThat(dailyReport(userId).summary().tradeCount()).isEqualTo(1);
        assertThat(dailyReport(userId).summary().tradeCount()).isEqualTo(1);
        assertThat(aiCalls.get()).isEqualTo(1);

        createTradeLog(userId);

        assertThat(dailyReport(userId).summary().tradeCount()).isEqualTo(2);
        assertThat(aiCalls.get()).isEqualTo(2);
    }

    @Test
    void 리포트를_만드는_도중_매매일지가_바뀌면_그_리포트는_캐시로_쓰지_않는다() {
        willAnswer(invocation -> {
            if (aiCalls.incrementAndGet() == 1) {
                // 첫 리포트의 AI 호출 도중에 매매일지가 추가된다
                createTradeLog(userId);
            }
            return Optional.of(DAILY_AI_JSON);
        }).given(openAiService).requestAnalysis(anyString());

        assertThat(dailyReport(userId).summary().tradeCount()).isEqualTo(1);

        assertThat(dailyReport(userId).summary().tradeCount()).isEqualTo(2);
        assertThat(aiCalls.get()).isEqualTo(2);
    }

    @Test
    void AI_호출에_실패하면_일일_리포트를_캐시에_저장하지_않는다() {
        willAnswer(invocation -> {
            aiCalls.incrementAndGet();
            return Optional.empty();
        }).given(openAiService).requestAnalysis(anyString());

        DailyReportResponse response = dailyReport(userId);

        assertThat(response.aiInsight().strengthPattern()).isEqualTo("AI 분석 지연");
        assertThat(response.emotionAnalysis()).isEmpty();
        assertThat(dailyReportCacheRepository.count()).isZero();

        givenAiResponds(0);
        assertThat(dailyReport(userId).aiInsight().strengthPattern()).isEqualTo("패턴");
        assertThat(dailyReportCacheRepository.count()).isEqualTo(1);
    }

    @Test
    void AI_호출에_실패하면_전체_리포트를_캐시에_저장하지_않는다() {
        willReturn(Optional.empty()).given(openAiService).requestAnalysis(anyString());

        OverviewReportResponse response = overviewReport(userId);

        assertThat(response.summary().totalTradeCount()).isEqualTo(1);
        assertThat(response.aiAnalysis()).isNull();
        assertThat(response.strategies()).isEmpty();
        assertThat(overviewReportCacheRepository.count()).isZero();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    // given(mock.method()) 형태로 다시 스텁하면 이전 스텁이 한 번 실행되므로 willAnswer().given() 형태를 쓴다
    private void givenAiResponds(long delayMillis) {
        willAnswer(invocation -> {
            aiCalls.incrementAndGet();
            recordActiveConnections();
            Thread.sleep(delayMillis);
            recordActiveConnections();
            String prompt = invocation.getArgument(0);
            return Optional.of(prompt.contains("하루 투자 요약") ? DAILY_AI_JSON : OVERVIEW_AI_JSON);
        }).given(openAiService).requestAnalysis(anyString());
    }

    private void recordActiveConnections() {
        int active = ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections();
        maxActiveConnectionsDuringAi.accumulateAndGet(active, Math::max);
    }

    private DailyReportResponse dailyReport(Long userId) {
        return reportService.generateDailyReport(new CustomUserDetails(userId), DATE);
    }

    private OverviewReportResponse overviewReport(Long userId) {
        return reportService.getOverviewReport(new CustomUserDetails(userId));
    }

    private Long createUser(String name) {
        return userRepository.save(User.builder()
                .email(name + "@test.com")
                .password("password")
                .nickname(name)
                .build()).getId();
    }

    private void createTradeLog(Long userId) {
        tradeLogService.createTradeLog(new CustomUserDetails(userId), new CreateTradeLogRequest(
                DATE, "삼성전자", "반도체", 70000L, 77000L, 10, 3, null, List.of(EMOTION), null
        ));
    }

    private <T> List<T> runConcurrently(int threads, Supplier<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.get();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
