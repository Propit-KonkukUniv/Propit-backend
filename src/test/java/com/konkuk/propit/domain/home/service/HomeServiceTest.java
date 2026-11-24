package com.konkuk.propit.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.home.dto.HomeResponse;
import com.konkuk.propit.domain.report.repository.DailyReportCacheRepository;
import com.konkuk.propit.domain.report.repository.OverviewReportCacheRepository;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.tradelog.service.TradeLogService;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.ai.service.OpenAiService;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        // H2에서 user가 예약어라 테이블이 만들어지지 않는 문제를 피한다
        "spring.datasource.url=jdbc:h2:mem:home-service;NON_KEYWORDS=USER"
})
@ActiveProfiles("test")
class HomeServiceTest {

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

    @Autowired private HomeService homeService;
    @Autowired private TradeLogService tradeLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmotionRepository emotionRepository;
    @Autowired private TradeLogRepository tradeLogRepository;
    @Autowired private DailyReportCacheRepository dailyReportCacheRepository;
    @Autowired private OverviewReportCacheRepository overviewReportCacheRepository;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            return prompt.contains("하루 투자 요약") ? DAILY_AI_JSON : OVERVIEW_AI_JSON;
        }).given(openAiService).requestAnalysis(anyString());

        emotionRepository.save(Emotion.builder().name(EMOTION).build());
        Long userId = userRepository.save(User.builder()
                .email("user@test.com")
                .password("password")
                .nickname("user")
                .build()).getId();
        userDetails = new CustomUserDetails(userId);
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
    void 오늘_매매일지가_있으면_오늘의_감정과_리포트를_보여준다() {
        createTradeLog(LocalDate.now());

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.todayMood().exists()).isTrue();
        assertThat(home.todayMood().mood()).isEqualTo(EMOTION);
        assertThat(home.dailyReport().exists()).isTrue();
        assertThat(home.dailyReport().data().tradeCount()).isEqualTo(1);
        assertThat(home.cumulativeReport().exists()).isTrue();
    }

    @Test
    void 오늘_매매일지가_없으면_기록_안내를_보여준다() {
        createTradeLog(LocalDate.now().minusDays(1));

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.todayMood().exists()).isFalse();
        assertThat(home.todayMood().message()).isEqualTo("오늘의 매매를 기록하지 않았어요.");
        assertThat(home.dailyReport().exists()).isFalse();
        assertThat(home.cumulativeReport().exists()).isTrue();
    }

    @Test
    void 감정_없는_매매일지만_있어도_홈을_보여준다() {
        createTradeLog(LocalDate.now(), null);

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.todayMood().exists()).isTrue();
        assertThat(home.todayMood().mood()).isEqualTo("UNKNOWN");
        assertThat(home.dailyReport().exists()).isTrue();
        assertThat(home.dailyReport().data().tradeCount()).isEqualTo(1);
    }

    @Test
    void 일일_리포트는_감정_없는_매매일지까지_센다() {
        createTradeLog(LocalDate.now());
        createTradeLog(LocalDate.now(), null);

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.dailyReport().data().tradeCount()).isEqualTo(2);
        assertThat(home.cumulativeReport().data().totalTradeCount()).isEqualTo(2);
    }

    private void createTradeLog(LocalDate sellDate) {
        createTradeLog(sellDate, List.of(EMOTION));
    }

    private void createTradeLog(LocalDate sellDate, List<String> emotionTags) {
        tradeLogService.createTradeLog(userDetails, new CreateTradeLogRequest(
                sellDate, "삼성전자", "반도체", 70000L, 77000L, 10, 3, null, emotionTags, null
        ));
    }
}
