/*
 * FEATURE  : Đối soát cuối ngày
 * VAI TRÒ  : Hình dạng JSON của một lần chạy đối soát.
 * LIÊN QUAN: ReconciliationRun · ReconciliationController
 */
package com.vidien.ewallet.reconciliation.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.vidien.ewallet.reconciliation.domain.ReconciliationRun;

/**
 * Ket qua mot lan doi soat, dang gui ra ngoai.
 *
 * <p>
 * ⚠️ KHONG tra ve {@code details} (cot JSONB). No chua so lieu tung vi va tung
 * loai giao dich - huu ich cho nguoi van hanh, nhung day la endpoint ma mot
 * nguoi dung binh thuong goi duoc. Tong so cua CA HE THONG thi khong lo gi ve
 * mot ca nhan cu the; chi tiet tung vi thi co.
 *
 * <p>
 * Ba so tien deu di qua JSON duoi dang CHUOI, cung ly do voi so du vi:
 * JavaScript chi co mot kieu so va no la {@code double}. {@code 12345678901234567.89}
 * ve toi JS thanh {@code 12345678901234568} - mat ca xu lan hang don vi.
 */
public record ReconciliationView(
        Long id,
        LocalDate businessDate,
        String status,

        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal walletTotal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ledgerTotal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal drift,

        Instant ranAt,
        Long durationMs) {

    public static ReconciliationView of(ReconciliationRun r) {
        return new ReconciliationView(r.getId(), r.getBusinessDate(), r.getStatus().name(),
                r.getWalletTotal(), r.getLedgerTotal(), r.getDrift(), r.getRanAt(),
                r.getDurationMs());
    }
}
