package com.konkuk.propit.domain.tradelog.service;

import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.report.repository.DailyReportCacheRepository;
import com.konkuk.propit.domain.report.repository.OverviewReportCacheRepository;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.request.UpdateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogDetailResponse;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogSummaryResponse;
import com.konkuk.propit.domain.tradelog.entity.TradeEmotion;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.exception.code.ErrorCode;
import java.time.LocalDate;

import com.konkuk.propit.global.security.principal.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TradeLogService {

    private final TradeLogRepository tradeLogRepository;
    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;
    private final OverviewReportCacheRepository overviewCacheRepository;
    private final DailyReportCacheRepository dailyReportCacheRepository;

    public void createTradeLog(CustomUserDetails userDetails, CreateTradeLogRequest request, MultipartFile image) {

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // S3 하드코딩
        String imageUrl = "https://dummy-image.com/default.png";

        TradeLog tradeLog = TradeLog.builder()
                .user(user)
                .sellDate(request.sellDate())
                .stockName(request.stockName())
                .buyPrice(request.buyPrice())
                .sellPrice(request.sellPrice())
                .quantity(request.quantity())
                .holdingDays(request.holdingDays())
                .reason(request.reason())
                .imageUrl(imageUrl)
                .build();

        tradeLog.calculateProfit();

        if (request.emotionTags() != null) {
            for (String tag : request.emotionTags()) {

                Emotion emotion = emotionRepository.findByName(tag)
                        .orElseThrow(() -> new BaseException(ErrorCode.EMOTION_NOT_FOUND));

                TradeEmotion tradeEmotion = TradeEmotion.builder()
                        .tradeLog(tradeLog)
                        .emotion(emotion)
                        .build();

                tradeLog.getTradeEmotions().add(tradeEmotion);
            }
        }

        tradeLogRepository.save(tradeLog);

        // user 기준 캐시 삭제
        overviewCacheRepository.deleteByUserId(user.getId());
        dailyReportCacheRepository.deleteByUserId(user.getId());
    }

    public void updateTradeLog(CustomUserDetails userDetails, Long tradeLogId, UpdateTradeLogRequest request) {

        TradeLog tradeLog = tradeLogRepository.findByIdAndUserId(
                tradeLogId, userDetails.getUserId()
        ).orElseThrow(() -> new BaseException(ErrorCode.TRADELOG_NOT_FOUND));

        tradeLog.update(
                request.sellDate(),
                request.stockName(),
                request.buyPrice(),
                request.sellPrice(),
                request.quantity(),
                request.holdingDays(),
                request.reason()
        );

        tradeLog.calculateProfit();

        tradeLog.getTradeEmotions().clear();

        if (request.emotionTags() != null) {
            for (String tag : request.emotionTags()) {

                Emotion emotion = emotionRepository.findByName(tag)
                        .orElseThrow(() -> new BaseException(ErrorCode.EMOTION_NOT_FOUND));

                TradeEmotion tradeEmotion = TradeEmotion.builder()
                        .tradeLog(tradeLog)
                        .emotion(emotion)
                        .build();

                tradeLog.getTradeEmotions().add(tradeEmotion);
            }
        }

        // 유저 기준 캐시 삭제
        overviewCacheRepository.deleteByUserId(userDetails.getUserId());
        dailyReportCacheRepository.deleteByUserId(userDetails.getUserId());
    }

    @Transactional(readOnly = true)
    public TradeLogDetailResponse getTradeLogDetail(CustomUserDetails userDetails, Long tradeLogId) {

        TradeLog tradeLog = tradeLogRepository.findByIdAndUserId(
                tradeLogId, userDetails.getUserId()
        ).orElseThrow(() -> new BaseException(ErrorCode.TRADELOG_NOT_FOUND));

        return TradeLogDetailResponse.from(tradeLog);
    }

    @Transactional(readOnly = true)
    public List<TradeLogSummaryResponse> getTradeLogs(CustomUserDetails userDetails) {

        List<TradeLog> tradeLogs = tradeLogRepository.findByUserId(
                userDetails.getUserId(),
                Sort.by(Sort.Direction.DESC, "sellDate")
        );

        return tradeLogs.stream()
                .map(TradeLogSummaryResponse::from)
                .toList();
    }

    public void deleteTradeLog(CustomUserDetails userDetails, Long tradeLogId) {
        TradeLog tradeLog = tradeLogRepository.findByIdAndUserId(
                tradeLogId, userDetails.getUserId()
        ).orElseThrow(() -> new BaseException(ErrorCode.TRADELOG_NOT_FOUND));

        tradeLogRepository.delete(tradeLog);

        overviewCacheRepository.deleteByUserId(userDetails.getUserId());
        dailyReportCacheRepository.deleteByUserId(userDetails.getUserId());
    }
}