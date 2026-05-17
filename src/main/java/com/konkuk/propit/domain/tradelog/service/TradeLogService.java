package com.konkuk.propit.domain.tradelog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konkuk.propit.domain.emotion.entity.Emotion;
import com.konkuk.propit.domain.emotion.repository.EmotionRepository;
import com.konkuk.propit.domain.report.repository.DailyReportCacheRepository;
import com.konkuk.propit.domain.report.repository.OverviewReportCacheRepository;
import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.request.UpdateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.response.OcrResult;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogDetailResponse;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogSummaryResponse;
import com.konkuk.propit.domain.tradelog.entity.TradeEmotion;
import com.konkuk.propit.domain.tradelog.entity.TradeImage;
import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.tradelog.repository.TradeLogRepository;
import com.konkuk.propit.domain.user.entity.User;
import com.konkuk.propit.domain.user.repository.UserRepository;
import com.konkuk.propit.global.ai.service.OpenAiService;
import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.exception.code.ErrorCode;
import com.konkuk.propit.global.s3.S3Uploader;
import com.konkuk.propit.global.security.principal.CustomUserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TradeLogService {

    private final TradeLogRepository tradeLogRepository;
    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;
    private final OverviewReportCacheRepository overviewCacheRepository;
    private final DailyReportCacheRepository dailyReportCacheRepository;
    private final S3Uploader s3Uploader;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    public OcrResult parseTradeImages(CustomUserDetails userDetails, List<MultipartFile> files) {
        Long userId = userDetails.getUserId();

        List<String> s3Keys = files.stream()
                .map(file -> {
                    String ext = extractExt(file.getOriginalFilename());
                    String key = "trade/" + userId + "/" + UUID.randomUUID() + ext;
                    s3Uploader.upload(file, key);
                    return key;
                })
                .toList();

        List<String> imageUrls = s3Keys.stream()
                .map(s3Uploader::getPresignedUrl)
                .toList();

        // 2) GPT-4o Vision 호출
        String json = openAiService.analyzeImages(
                buildOcrSystemPrompt(),
                buildOcrUserPrompt(),
                imageUrls
        );

        // 3) JSON → OcrResult
        return parseOcrJson(json, s3Keys);
    }

    // ── 프롬프트 ──────────────────────────────────────────────────────────

    private String buildOcrSystemPrompt() {
        return "당신은 증권사 앱 스크린샷을 분석하는 AI입니다. 반드시 JSON 형식으로만 응답하세요.";
    }

    private String buildOcrUserPrompt() {
        return """
                첨부된 증권사 앱 스크린샷에서 아래 항목을 추출해주세요.
                여러 장의 이미지가 있다면 정보를 합쳐서 하나의 JSON으로 응답하세요.
                이미지에서 인식할 수 없는 항목은 null로 응답하세요.
                
                응답 형식 :
                {
                  "sellDate": "YYYY-MM-DD",
                  "buyDate": "YYYY-MM-DD",
                  "stockName": "종목명",
                  "sectorName": "업종명",
                  "buyPrice": 숫자 (1주당 매수가, 소수점이면 반올림),
                  "sellPrice": 숫자 (1주당 매도가, 소수점이면 반올림),
                  "quantity": 숫자 (매도 수량)
                }
            
                """;
    }

    // ── JSON 파싱 ─────────────────────────────────────────────────────────

    private OcrResult parseOcrJson(String json, List<String> s3Keys) {
        try {
            String cleaned = json.replaceAll("```json|```", "").trim();
            JsonNode node = objectMapper.readTree(cleaned);

            LocalDate sellDate = parseDate(node.path("sellDate").asText(null));
            LocalDate buyDate  = parseDate(node.path("buyDate").asText(null));

            return new OcrResult(
                    sellDate,
                    textOrNull(node.path("stockName").asText(null)),
                    textOrNull(node.path("sectorName").asText(null)),
                    longOrNull(node.path("buyPrice")),
                    longOrNull(node.path("sellPrice")),
                    intOrNull(node.path("quantity")),
                    buyDate,
                    s3Keys
            );
        } catch (Exception e) {
            log.warn("GPT Vision 응답 파싱 실패: {}", json, e);
            return new OcrResult(null, null, null, null, null, null, null, s3Keys);
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("null")) return null;
        try { return LocalDate.parse(raw.trim()); } catch (Exception e) { return null; }
    }

    private String textOrNull(String raw) {
        return (raw == null || raw.isBlank() || raw.equals("null")) ? null : raw.trim();
    }

    private Long longOrNull(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        try { return node.asLong(); } catch (Exception e) { return null; }
    }

    private Integer intOrNull(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        try { return node.asInt(); } catch (Exception e) { return null; }
    }

    private String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    // ════════════════════════════════════════════════════════════════════════
    // CRUD
    // ════════════════════════════════════════════════════════════════════════

    public void createTradeLog(CustomUserDetails userDetails, CreateTradeLogRequest request) {

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        TradeLog tradeLog = TradeLog.builder()
                .user(user)
                .sellDate(request.sellDate())
                .stockName(request.stockName())
                .sector(request.sectorName())
                .buyPrice(request.buyPrice())
                .sellPrice(request.sellPrice())
                .quantity(request.quantity())
                .holdingDays(request.holdingDays())
                .reason(request.reason())
                .build();

        tradeLog.calculateProfit();

        if (request.emotionTags() != null) {
            for (String tag : request.emotionTags()) {
                Emotion emotion = emotionRepository.findByName(tag)
                        .orElseThrow(() -> new BaseException(ErrorCode.EMOTION_NOT_FOUND));
                tradeLog.getTradeEmotions().add(
                        TradeEmotion.builder().tradeLog(tradeLog).emotion(emotion).build()
                );
            }
        }

        if (request.s3Keys() != null) {
            for (String key : request.s3Keys()) {
                tradeLog.addImage(new TradeImage(key));
            }
        }

        tradeLogRepository.save(tradeLog);
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
                request.sectorName(),
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
                tradeLog.getTradeEmotions().add(
                        TradeEmotion.builder().tradeLog(tradeLog).emotion(emotion).build()
                );
            }
        }

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
        return tradeLogRepository
                .findByUserId(userDetails.getUserId(), Sort.by(Sort.Direction.DESC, "sellDate"))
                .stream()
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