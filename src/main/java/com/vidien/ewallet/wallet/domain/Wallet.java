package com.vidien.ewallet.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Một dòng trong bảng wallets.
 *
 * <p>
 * Tên biến kiểu camelCase, tên cột kiểu snake_case - KHÔNG cần khai báo gì để nối hai bên:
 * JdbcClient tự khớp user_id với userId, created_at với createdAt.
 */
public record Wallet(Long id, Long userId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance, int version,
        Instant createdAt) {

}
