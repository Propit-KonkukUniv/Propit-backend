package com.konkuk.propit.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.report.entity.DailyReportCache;
import com.konkuk.propit.domain.report.entity.OverviewReportCache;
import com.konkuk.propit.domain.report.repository.DailyReportCacheRepository;
import com.konkuk.propit.domain.report.repository.OverviewReportCacheRepository;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCacheService {

    private final DailyReportCacheRepository dailyReportCacheRepository;
    private final OverviewReportCacheRepository overviewReportCacheRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<DailyReportResponse> findDaily(Long userId, LocalDate date) {
        Optional<DailyReportCache> cache = dailyReportCacheRepository.findByUserIdAndDate(userId, date);
        if (cache.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cache.get().getReportJson(), DailyReportResponse.class));
        } catch (Exception e) {
            // 읽을 수 없는 캐시는 지우고 새로 만든다
            log.warn("일일 리포트 캐시 파싱 실패: userId={}, date={}", userId, date, e);
            dailyReportCacheRepository.delete(cache.get());
            return Optional.empty();
        }
    }

    @Transactional
    public void saveDaily(Long userId, LocalDate date, DailyReportResponse response) throws JsonProcessingException {
        dailyReportCacheRepository.save(
                DailyReportCache.builder()
                        .userId(userId)
                        .date(date)
                        .reportJson(objectMapper.writeValueAsString(response))
                        .build()
        );
    }

    @Transactional
    public Optional<OverviewReportResponse> findOverview(Long userId) {
        Optional<OverviewReportCache> cache = overviewReportCacheRepository.findByUserId(userId);
        if (cache.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cache.get().getReportJson(), OverviewReportResponse.class));
        } catch (Exception e) {
            // 읽을 수 없는 캐시는 지우고 새로 만든다
            log.warn("전체 리포트 캐시 파싱 실패: userId={}", userId, e);
            overviewReportCacheRepository.delete(cache.get());
            return Optional.empty();
        }
    }

    @Transactional
    public void saveOverview(Long userId, OverviewReportResponse response) throws JsonProcessingException {
        overviewReportCacheRepository.save(
                OverviewReportCache.builder()
                        .userId(userId)
                        .reportJson(objectMapper.writeValueAsString(response))
                        .build()
        );
    }
}
