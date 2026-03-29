package com.konkuk.propit.domain.report.repository;

import com.konkuk.propit.domain.report.entity.OverviewReportCache;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverviewReportCacheRepository extends JpaRepository<OverviewReportCache, Long> {
    Optional<OverviewReportCache> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}