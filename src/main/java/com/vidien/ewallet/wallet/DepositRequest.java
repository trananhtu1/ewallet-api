package com.vidien.ewallet.wallet;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Cung bo luat tien voi TransferRequest, va cung mot ly do:
 *
 * <p>
 * KHONG dung @Positive - 0.001 lon hon 0 nen lot qua, nhung NUMERIC(19,2) lam tron xuong 0.00,
 * ghi vao so mot giao dich nap 0 dong ma khong co loi nao bao.
 *
 * <p>
 * @DecimalMin nhan CHUOI "0.01" chu khong phai so 0.01 - so thuc dau phay dong khong bieu dien
 * chinh xac duoc gia tri do.
 */
public record DepositRequest(
        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền tối thiểu là 0.01")
        @Digits(integer = 17, fraction = 2, message = "Số tiền tối đa 2 chữ số thập phân")
        BigDecimal amount) {
}
