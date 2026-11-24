package com.konkuk.propit.domain.tradelog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
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

/**
 * 날짜별 매매일지 조회 쿼리 테스트.
 * 트랜잭션 없이 호출해서, 감정까지 미리 가져오는지(지연 로딩이 안 일어나는지)도 함께 확인한다.
 */
@SpringBootTest(properties = {
        // H2에서 user가 예약어라 테이블이 만들어지지 않는 문제를 피한다
        "spring.datasource.url=jdbc:h2:mem:trade-log-repository;NON_KEYWORDS=USER"
})
@ActiveProfiles("test")
class TradeLogRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static final String EMOTION = "확신";

    @Autowired private TradeLogRepository tradeLogRepository;
    @Autowired private TradeLogService tradeLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmotionRepository emotionRepository;

    private User user;

    @BeforeEach
    void setUp() {
        emotionRepository.save(Emotion.builder().name(EMOTION).build());
        user = userRepository.save(User.builder()
                .email("user@test.com")
                .password("password")
                .nickname("user")
                .build());

        createTradeLog(DATE, List.of(EMOTION));
        createTradeLog(DATE, null);
        createTradeLog(DATE.minusDays(1), List.of(EMOTION));
    }

    @AfterEach
    void tearDown() {
        tradeLogRepository.deleteAll();
        userRepository.deleteAll();
        emotionRepository.deleteAll();
    }

    @Test
    void 일일_리포트_조회는_감정_없는_매매일지도_포함하고_감정까지_가져온다() {
        List<TradeLog> logs = tradeLogRepository.findWithEmotions(user, DATE);

        assertThat(logs).hasSize(2);
        assertThat(emotionNames(logs)).containsExactly(EMOTION);
    }

    @Test
    void 홈_화면_조회는_감정_없는_매매일지도_포함하고_감정까지_가져온다() {
        List<TradeLog> logs = tradeLogRepository.findWithEmotionsByUserAndSellDate(user, DATE);

        assertThat(logs).hasSize(2);
        assertThat(emotionNames(logs)).containsExactly(EMOTION);
    }

    // 트랜잭션 밖에서 읽으므로, fetch join으로 가져오지 않았다면 여기서 LazyInitializationException이 난다
    private List<String> emotionNames(List<TradeLog> logs) {
        return logs.stream()
                .flatMap(log -> log.getTradeEmotions().stream())
                .map(te -> te.getEmotion().getName())
                .toList();
    }

    private void createTradeLog(LocalDate sellDate, List<String> emotionTags) {
        tradeLogService.createTradeLog(new CustomUserDetails(user.getId()), new CreateTradeLogRequest(
                sellDate, "삼성전자", "반도체", 70000L, 77000L, 10, 3, null, emotionTags, null
        ));
    }
}
