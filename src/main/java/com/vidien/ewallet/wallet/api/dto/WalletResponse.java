package com.vidien.ewallet.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Hinh dang cua mot cai vi khi no ra khoi API.
 *
 * <p>
 * Tach khoi entity {@code Wallet} co y: entity la hinh dang cua BANG, cai nay la hop dong voi
 * frontend. Tra entity thang ra ngoai thi doi mot cot la doi luon hop dong API - va nguoc lai,
 * muon them mot truong cho giao dien thi phai them mot cot.
 *
 * <p>
 * 💰 {@code @JsonFormat(shape = STRING)} nam o DAY chu khong o entity, va no la dong quan
 * trong nhat file: JavaScript chi co MOT kieu so va no la double.
 * {@code 12345678901234567.89} ve toi trinh duyet thanh {@code 12345678901234568} - mat ca xu
 * lan hang don vi. Da do that hom 25/08.
 *
 * <p>
 * {@code version} KHONG co trong nay: no la chi tiet noi bo cua optimistic locking, nguoi dung
 * khong can biet va cung khong nen thay.
 */
public record WalletResponse(
        Long id,
        Long userId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        Instant createdAt) {
}
