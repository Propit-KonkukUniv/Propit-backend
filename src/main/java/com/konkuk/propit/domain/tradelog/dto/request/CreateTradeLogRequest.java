package com.konkuk.propit.domain.tradelog.dto.request;

import java.time.LocalDate;
import java.util.List;

public record CreateTradeLogRequest(
        LocalDate sellDate,
        String stockName,
        Long buyPrice,
        Long sellPrice,
        Integer quantity,
        Integer holdingDays,
        String reason,
        List<String> emotionTags
) {
}