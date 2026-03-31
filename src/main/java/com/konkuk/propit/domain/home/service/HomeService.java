package com.konkuk.propit.domain.home.service;

import com.konkuk.propit.domain.home.dto.HomeResponse;
import com.konkuk.propit.domain.report.dto.response.DailyReportResponse;
import com.konkuk.propit.domain.report.dto.response.OverviewReportResponse;
import com.konkuk.propit.domain.report.service.ReportService;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.konkuk.propit.global.exception.code.ErrorCode.TRADELOG_NOT_EXISTS;
import static com.konkuk.propit.global.exception.code.ErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final TradeLogRepository tradeLogRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;

    public HomeResponse getHome(CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        LocalDate today = LocalDate.now();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(USER_NOT_FOUND));

        List<TradeLog> todayLogs =
                tradeLogRepository.findByUserAndSellDate(user, today);

        return new HomeResponse(
                buildTodayMood(todayLogs),
                buildDailyReport(userDetails, todayLogs, today),
                buildOverviewReport(userDetails)
        );
    }

    private HomeResponse.TodayMood buildTodayMood(List<TradeLog> logs) {

        if (logs.isEmpty()) {
            return new HomeResponse.TodayMood(
                    false,
                    null,
                    "오늘의 매매를 기록하지 않았어요."
            );
        }

        TradeLog latest = logs.stream()
                .max(Comparator.comparing(TradeLog::getCreatedAt))
                .orElseThrow();

        String mood = latest.getTradeEmotions().stream()
                .findFirst()
                .map(te -> te.getEmotion().getName())
                .orElse("UNKNOWN");

        return new HomeResponse.TodayMood(true, mood, null);
    }

    private HomeResponse.DailyReport buildDailyReport(
            CustomUserDetails userDetails,
            List<TradeLog> logs,
            LocalDate today
    ) {

        if (logs.isEmpty()) {
            return new HomeResponse.DailyReport(false, null);
        }

        DailyReportResponse report =
                reportService.generateDailyReport(userDetails, today);

        return new HomeResponse.DailyReport(
                true,
                new HomeResponse.DailyReport.Data(
                        report.summary().tradeCount(),
                        report.summary().winRate(),
                        report.summary().totalProfit(),
                        report.summary().averageProfitRate(),
                        report.aiInsight().strengthPattern()
                )
        );
    }

    private HomeResponse.CumulativeReport buildOverviewReport(
            CustomUserDetails userDetails
    ) {

        try {
            OverviewReportResponse report =
                    reportService.getOverviewReport(userDetails);

            return new HomeResponse.CumulativeReport(
                    true,
                    new HomeResponse.CumulativeReport.Data(
                            report.summary().totalTradeCount(),
                            report.summary().winRate(),
                            report.summary().totalProfit(),
                            report.summary().avgProfitRate()
                    )
            );

        } catch (BaseException e) {
            if (e.getErrorCode() == TRADELOG_NOT_EXISTS) {
                return new HomeResponse.CumulativeReport(false, null);
            }
            throw e;
        }
    }
}
