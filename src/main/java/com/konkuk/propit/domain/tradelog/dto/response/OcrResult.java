package com.konkuk.propit.domain.tradelog.dto.response;

import java.time.LocalDate;
import java.util.List;

public record OcrResult(
        LocalDate sellDate,
        String stockName,
        String sectorName,
        Long buyPrice,
        Long sellPrice,
        Integer quantity,
        LocalDate buyDate,
        List<String> s3Keys
) {}