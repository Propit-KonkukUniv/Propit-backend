package com.konkuk.propit.domain.tradelog.controller;

import com.konkuk.propit.domain.tradelog.dto.request.CreateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.dto.request.UpdateTradeLogRequest;
import com.konkuk.propit.domain.tradelog.service.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.konkuk.propit.global.common.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tradelogs")
public class TradeLogController {

    private final TradeLogService tradeLogService;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createTradeLog(
            @RequestPart("data") CreateTradeLogRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {

        tradeLogService.createTradeLog(request, image);

        return ResponseEntity.ok().body(
                new ApiResponse(true, 20000, "요청에 성공했습니다.")
        );
    }

    @PutMapping("/{tradeLogId}")
    public ResponseEntity<?> updateTradeLog(
            @PathVariable Long tradeLogId,
            @RequestBody UpdateTradeLogRequest request
    ) {

        tradeLogService.updateTradeLog(tradeLogId, request);

        return ResponseEntity.ok(
                new ApiResponse(true, 20000, "요청에 성공했습니다.")
        );
    }
}