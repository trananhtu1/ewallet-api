/*
 * FEATURE  : Sao kê theo tháng
 * VAI TRÒ  : Một dòng tổng hợp: tháng, tiền vào, tiền ra, số giao dịch.
 * LIÊN QUAN: StatementRepository · StatementController
 */
package com.vidien.ewallet.statement.api.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Tong hop mot thang.
 *
 * <p>
 * {@code month} dang {@code "2026-09"} chu khong phai mot moc thoi gian: day la mot
 * KHOANG, khong phai mot diem. Tra ve {@code Instant} thi frontend phai doan xem no
 * la dau thang hay cuoi thang, va doan sai thi lech mui gio mot ngay.
 *
 * <p>
 * Ba so tien deu di qua JSON duoi dang CHUOI - cung luat voi so du.
 */
public record MonthlyStatement(
        String month,

        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal moneyIn,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal moneyOut,

        /**
         * Chenh lech = vao - ra. Tinh o SQL chu khong o frontend.
         *
         * <p>
         * ⚠️ Khong de frontend tru hai so: JavaScript chi co mot kieu so va no la
         * {@code double}. Tru hai chuoi tien o do la dung cai bay ma ca he thong nay
         * da tranh tu dau.
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal net,

        int count) {
}
