package com.konkuk.propit.domain.tradelog.controller;

import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.request.UpdateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogDetailResponse;
import com.konkuk.propit.domain.tradelog.dto.response.TradeLogSummaryResponse;
import com.konkuk.propit.domain.tradelog.service.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.konkuk.propit.global.common.ApiResponse;

import java.util.List;

import static com.konkuk.propit.global.response.SuccessCode.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tradelogs")
public class TradeLogController {

    private final TradeLogService tradeLogService;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<Void>> createTradeLog(
            @RequestPart("data") CreateTradeLogRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {

        tradeLogService.createTradeLog(request, image);

        return ResponseEntity.ok().body(ApiResponse.success(TRADELOG_CREATE_SUCCESS, null));
    }

    @PutMapping("/{tradeLogId}")
    public ResponseEntity<ApiResponse<Void>> updateTradeLog(
            @PathVariable Long tradeLogId,
            @RequestBody UpdateTradeLogRequest request
    ) {

        tradeLogService.updateTradeLog(tradeLogId, request);

        return ResponseEntity.ok().body(ApiResponse.success(TRADELOG_UPDATE_SUCCESS, null));
    }

    @GetMapping("/{tradeLogId}")
    public ResponseEntity<ApiResponse<TradeLogDetailResponse>> getTradeLogDetail(
            @PathVariable Long tradeLogId
    ) {

        TradeLogDetailResponse response = tradeLogService.getTradeLogDetail(tradeLogId);

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_DETAIL_SUCCESS, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TradeLogSummaryResponse>>> getTradeLogs() {

        List<TradeLogSummaryResponse> response = tradeLogService.getTradeLogs();

        return ResponseEntity.ok()
                .body(ApiResponse.success(TRADELOG_LIST_SUCCESS, response));
    }
}