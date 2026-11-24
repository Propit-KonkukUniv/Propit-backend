package com.konkuk.propit.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.home.dto.HomeResponse;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.report.service.ReportService;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.tradelog.service.TradeLogService;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
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

/**
 * 홈 화면 조립 로직 테스트. 리포트 생성(AI 호출)은 가짜로 대체한다.
 */
@SpringBootTest(properties = {
        // H2에서 user가 예약어라 테이블이 만들어지지 않는 문제를 피한다
        "spring.datasource.url=jdbc:h2:mem:home-service;NON_KEYWORDS=USER"
})
@ActiveProfiles("test")
class HomeServiceTest {

    private static final String EMOTION = "확신";

    @MockitoBean
    private ReportService reportService;

    @Autowired private HomeService homeService;
    @Autowired private TradeLogService tradeLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmotionRepository emotionRepository;
    @Autowired private TradeLogRepository tradeLogRepository;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        given(reportService.generateDailyReport(any(), any())).willReturn(new DailyReportResponse(
                LocalDate.now(),
                new DailyReportResponse.Summary(1, 100.0, 70000L, 10.0),
                List.of(),
                new DailyReportResponse.AiInsight("패턴", "개선점", "오전"),
                List.of("조언")
        ));
        given(reportService.getOverviewReport(any())).willReturn(new OverviewReportResponse(
                new OverviewReportResponse.Summary(
                        1, 100.0, 70000L, 10.0, null, null,
                        new OverviewReportResponse.EmotionInfo(EMOTION, 1), 3.0
                ),
                List.of(), null, List.of(), List.of()
        ));

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
        userRepository.deleteAll();
        emotionRepository.deleteAll();
    }

    @Test
    void 오늘_매매일지가_있으면_오늘의_감정과_리포트를_보여준다() {
        createTradeLog(LocalDate.now(), List.of(EMOTION));

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.todayMood().exists()).isTrue();
        assertThat(home.todayMood().mood()).isEqualTo(EMOTION);
        assertThat(home.dailyReport().exists()).isTrue();
        assertThat(home.cumulativeReport().exists()).isTrue();
    }

    @Test
    void 오늘_매매일지에_감정이_없으면_오늘의_감정은_UNKNOWN이다() {
        createTradeLog(LocalDate.now(), null);

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.todayMood().exists()).isTrue();
        assertThat(home.todayMood().mood()).isEqualTo("UNKNOWN");
        assertThat(home.dailyReport().exists()).isTrue();
    }

    @Test
    void 오늘_매매일지가_없으면_기록_안내를_보여준다() {
        createTradeLog(LocalDate.now().minusDays(1), List.of(EMOTION));

        HomeResponse home = homeService.getHome(userDetails);

        assertThat(home.todayMood().exists()).isFalse();
        assertThat(home.todayMood().message()).isEqualTo("오늘의 매매를 기록하지 않았어요.");
        assertThat(home.dailyReport().exists()).isFalse();
        assertThat(home.cumulativeReport().exists()).isTrue();
        then(reportService).should(never()).generateDailyReport(any(), any());
    }

    private void createTradeLog(LocalDate sellDate, List<String> emotionTags) {
        tradeLogService.createTradeLog(userDetails, new CreateTradeLogRequest(
                sellDate, "삼성전자", "반도체", 70000L, 77000L, 10, 3, null, emotionTags, null
        ));
    }
}
