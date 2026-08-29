/*
 * FEATURE  : Chuyển tiền — khoá chống lặp
 * VAI TRÒ  : Cùng khoá nhưng KHÁC nội dung → 409. Khác hẳn ca bấm hai lần.
 * LIÊN QUAN: TransferService · GlobalExceptionHandler
 */
package com.vidien.ewallet.transfer.domain.exception;

import java.math.BigDecimal;

/**
 * Cung mot {@code Idempotency-Key}, nhung <b>noi dung lenh khac</b> lan truoc.
 *
 * <p>
 * ⚠️ Day KHONG phai mot lan bam hai lan. Mot lan bam hai lan thi noi dung y het nhau, va he
 * thong tra ve ket qua cu - dung nhu thiet ke.
 *
 * <p>
 * Ca nay la: nguoi dung bam gui 1.000, mang cham, ho <b>sua so tien</b> thanh 5.000 roi bam
 * lai - va client dung lai khoa cu. Hai lenh khac nhau doi cung mot khoa.
 *
 * <p>
 * <b>Truoc ban va nay, he thong tra ve "da lam roi" va BO QUA lenh moi.</b> Tuc la nguoi dung
 * tin rang minh vua gui 5.000, con so cai ghi 1.000. Khong loi nao bao, khong ai biet cho toi
 * luc doi soat.
 *
 * <p>
 * Stripe tra 409 cho dung ca nay, va day lam theo. 409 chu khong 400: request nay mot minh no
 * hoan toan hop le - cai sai la <b>quan he cua no voi mot request truoc do</b>.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key, long toWalletId, BigDecimal amount) {
        super("Khoa chong lap '" + key + "' da dung cho mot lenh KHAC "
                + "(lan nay: den vi " + toWalletId + ", so tien " + amount + ")");
    }
}
