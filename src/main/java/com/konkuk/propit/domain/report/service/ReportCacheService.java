package com.konkuk.propit.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.report.entity.DailyReportCache;
import com.konkuk.propit.domain.report.entity.OverviewReportCache;
import com.konkuk.propit.domain.report.repository.DailyReportCacheRepository;
import com.konkuk.propit.domain.report.repository.OverviewReportCacheRepository;
import com.konkuk.propit.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 캐시 조회·저장.
 * 메서드마다 짧은 트랜잭션으로 실행돼서, 락을 기다린 뒤 다시 조회하면
 * 앞 요청이 커밋한 캐시를 볼 수 있다.
 *
 * 캐시에는 만들 때의 매매일지 버전(User.tradeLogVersion)이 함께 저장되고,
 * 조회할 때 지금 버전과 다르면 없는 것으로 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCacheService {

    private final DailyReportCacheRepository dailyReportCacheRepository;
    private final OverviewReportCacheRepository overviewReportCacheRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<DailyReportResponse> findDaily(Long userId, LocalDate date) {
        Optional<Long> currentVersion = userRepository.findTradeLogVersionById(userId);
        Optional<DailyReportCache> cache = dailyReportCacheRepository.findByUserIdAndDate(userId, date);

        if (currentVersion.isEmpty() || cache.isEmpty()
                || cache.get().getVersion() != currentVersion.get()) {
            return Optional.empty();
        }
        return parse(cache.get().getReportJson(), DailyReportResponse.class);
    }

    @Transactional
    public void saveDaily(Long userId, LocalDate date, long version, DailyReportResponse response)
            throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(response);

        dailyReportCacheRepository.findByUserIdAndDate(userId, date)
                .ifPresentOrElse(
                        cache -> {
                            // 더 최신 버전으로 만든 캐시를 옛 버전으로 덮어쓰지 않는다
                            if (version >= cache.getVersion()) {
                                cache.update(json, version);
                            }
                        },
                        () -> dailyReportCacheRepository.save(
                                DailyReportCache.builder()
                                        .userId(userId)
                                        .date(date)
                                        .reportJson(json)
                                        .version(version)
                                        .build()
                        )
                );
    }

    @Transactional(readOnly = true)
    public Optional<OverviewReportResponse> findOverview(Long userId) {
        Optional<Long> currentVersion = userRepository.findTradeLogVersionById(userId);
        Optional<OverviewReportCache> cache = overviewReportCacheRepository.findByUserId(userId);

        if (currentVersion.isEmpty() || cache.isEmpty()
                || cache.get().getVersion() != currentVersion.get()) {
            return Optional.empty();
        }
        return parse(cache.get().getReportJson(), OverviewReportResponse.class);
    }

    @Transactional
    public void saveOverview(Long userId, long version, OverviewReportResponse response)
            throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(response);

        overviewReportCacheRepository.findByUserId(userId)
                .ifPresentOrElse(
                        cache -> {
                            // 더 최신 버전으로 만든 캐시를 옛 버전으로 덮어쓰지 않는다
                            if (version >= cache.getVersion()) {
                                cache.update(json, version);
                            }
                        },
                        () -> overviewReportCacheRepository.save(
                                OverviewReportCache.builder()
                                        .userId(userId)
                                        .reportJson(json)
                                        .version(version)
                                        .build()
                        )
                );
    }

    private <T> Optional<T> parse(String json, Class<T> type) {
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            // 읽을 수 없는 캐시는 없는 것으로 보고 새로 만든다. 저장할 때 덮어쓴다.
            log.warn("리포트 캐시 파싱 실패: {}", type.getSimpleName(), e);
            return Optional.empty();
        }
    }
}
