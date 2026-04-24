package com.fintrack.imports.dto;

import com.fintrack.common.entity.InvestmentTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportPreviewRow(
        int rowNumber,
        LocalDate date,
        String rawType,
        InvestmentTransaction.TxnType mappedType,
        String assetSymbol,
        BigDecimal amountTry,
        String note,
        String warning) {}
