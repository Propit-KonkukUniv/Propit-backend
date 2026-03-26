package com.konkuk.propit.domain.tradelog.dto.response;

import com.konkuk.propit.domain.tradelog.entity.TradeLog;

import java.time.LocalDate;

public record TradeLogSummaryResponse(
        Long tradeLogId,
        LocalDate sellDate,
        String stockName
) {
    public static TradeLogSummaryResponse from(TradeLog tradeLog) {
        return new TradeLogSummaryResponse(
                tradeLog.getId(),
                tradeLog.getSellDate(),
                tradeLog.getStockName()
        );
    }
}