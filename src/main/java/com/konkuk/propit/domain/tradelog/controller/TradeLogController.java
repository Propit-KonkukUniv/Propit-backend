package com.konkuk.propit.domain.tradelog.controller;

import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.request.UpdateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.response.OcrResult;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogDetailResponse;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogSummaryResponse;
import com.konkuk.propit.domain.tradelog.service.TradeLogService;
import com.konkuk.propit.global.common.ApiResponse;
import com.konkuk.propit.global.security.principal.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.konkuk.propit.global.response.SuccessCode.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tradelogs")
public class TradeLogController {

    private final TradeLogService tradeLogService;

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OcrResult>> parseTradeImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("files") List<MultipartFile> files
    ) {
        OcrResult result = tradeLogService.parseTradeImages(userDetails, files);
        return ResponseEntity.ok(ApiResponse.success(OCR_PARSE_SUCCESS, result));
    }

    // 생성
    @PostMapping()
    public ResponseEntity<ApiResponse<Void>> createTradeLog(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateTradeLogRequest request
    ) {

        tradeLogService.createTradeLog(userDetails, request);

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_CREATE_SUCCESS, null));
    }

    // 수정
    @PutMapping("/{tradeLogId}")
    public ResponseEntity<ApiResponse<Void>> updateTradeLog(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tradeLogId,
            @RequestBody UpdateTradeLogRequest request
    ) {

        tradeLogService.updateTradeLog(userDetails, tradeLogId, request);

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_UPDATE_SUCCESS, null));
    }

    // 상세 조회
    @GetMapping("/{tradeLogId}")
    public ResponseEntity<ApiResponse<TradeLogDetailResponse>> getTradeLogDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tradeLogId
    ) {

        TradeLogDetailResponse response =
                tradeLogService.getTradeLogDetail(userDetails, tradeLogId);

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_DETAIL_SUCCESS, response));
    }

    // 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<TradeLogSummaryResponse>>> getTradeLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        List<TradeLogSummaryResponse> response =
                tradeLogService.getTradeLogs(userDetails);

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_LIST_SUCCESS, response));
    }

    // 삭제
    @DeleteMapping("/{tradeLogId}")
    public ResponseEntity<ApiResponse<Void>> deleteTradeLog(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tradeLogId
    ) {

        tradeLogService.deleteTradeLog(userDetails, tradeLogId);

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_DELETE_SUCCESS, null));
    }
}