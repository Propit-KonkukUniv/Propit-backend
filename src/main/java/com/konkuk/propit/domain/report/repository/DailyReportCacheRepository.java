package com.konkuk.propit.domain.report.repository;

import com.konkuk.propit.domain.report.entity.DailyReportCache;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyReportCacheRepository extends JpaRepository<DailyReportCache, Long> {
    Optional<DailyReportCache> findByUserIdAndDate(Long userId, LocalDate date);
    void deleteByUserId(Long userId);
}