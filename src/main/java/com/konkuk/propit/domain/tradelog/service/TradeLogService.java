package com.konkuk.propit.domain.tradelog.service;

import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.request.UpdateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.entity.TradeEmotion;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class TradeLogService {

    private final TradeLogRepository tradeLogRepository;
    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;

    public void createTradeLog(CreateTradeLogRequest request, MultipartFile image) {

        // 임시 사용자 (id=1 고정)
        User user = userRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // S3 안 쓰므로 이미지 URL 하드코딩
        String imageUrl = "https://dummy-image.com/default.png";

        // TradeLog 생성
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

        // 수익 계산
        tradeLog.calculateProfit();

        // 감정 매핑
        if (request.emotionTags() != null) {
            for (String tag : request.emotionTags()) {

                Emotion emotion = emotionRepository.findByName(tag)
                        .orElseThrow(() -> new RuntimeException("Emotion not found: " + tag));

                TradeEmotion tradeEmotion = TradeEmotion.builder()
                        .tradeLog(tradeLog)
                        .emotion(emotion)
                        .build();

                tradeLog.getTradeEmotions().add(tradeEmotion);
            }
        }

        tradeLogRepository.save(tradeLog);
    }

    public void updateTradeLog(Long tradeLogId, UpdateTradeLogRequest request) {

        TradeLog tradeLog = tradeLogRepository.findById(tradeLogId)
                .orElseThrow(() -> new RuntimeException("TradeLog not found"));

        // 기본 필드 수정
        tradeLog.update(
                request.sellDate(),
                request.stockName(),
                request.buyPrice(),
                request.sellPrice(),
                request.quantity(),
                request.holdingDays(),
                request.reason()
        );

        // 수익 재계산
        tradeLog.calculateProfit();

        // 기존 감정 삭제 (orphanRemoval=true 이므로 clear만 하면 됨)
        tradeLog.getTradeEmotions().clear();

        // 감정 다시 매핑
        if (request.emotionTags() != null) {
            for (String tag : request.emotionTags()) {

                Emotion emotion = emotionRepository.findByName(tag)
                        .orElseThrow(() -> new RuntimeException("Emotion not found: " + tag));

                TradeEmotion tradeEmotion = TradeEmotion.builder()
                        .tradeLog(tradeLog)
                        .emotion(emotion)
                        .build();

                tradeLog.getTradeEmotions().add(tradeEmotion);
            }
        }
    }
}