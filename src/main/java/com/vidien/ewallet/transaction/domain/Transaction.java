package com.vidien.ewallet.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Mot dong nguyen ven trong bang transactions. Dung o tang repository. */
public record Transaction(Long id, Long fromWalletId, Long toWalletId, BigDecimal amount,
        String type, String status, Instant createdAt) {
}
