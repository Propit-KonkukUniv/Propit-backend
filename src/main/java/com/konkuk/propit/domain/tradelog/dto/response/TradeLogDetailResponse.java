package com.konkuk.propit.domain.tradelog.dto.response;

import com.konkuk.propit.domain.tradelog.entity.TradeLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TradeLogDetailResponse(
        Long tradeLogId,
        LocalDate sellDate,
        String stockName,
        Long buyPrice,
        Long sellPrice,
        Integer quantity,
        Integer holdingDays,
        Long profitAmount,
        Double profitRate,
        String reason,
        String imageUrl,
        List<String> emotionTags,
        LocalDateTime createdAt
) {

    public static TradeLogDetailResponse from(TradeLog tradeLog) {
        return new TradeLogDetailResponse(
                tradeLog.getId(),
                tradeLog.getSellDate(),
                tradeLog.getStockName(),
                tradeLog.getBuyPrice(),
                tradeLog.getSellPrice(),
                tradeLog.getQuantity(),
                tradeLog.getHoldingDays(),
                tradeLog.getProfitAmount(),
                tradeLog.getProfitRate(),
                tradeLog.getReason(),
                tradeLog.getImageUrl(),
                tradeLog.getTradeEmotions().stream()
                        .map(te -> te.getEmotion().getName())
                        .toList(),
                tradeLog.getCreatedAt()
        );
    }
}