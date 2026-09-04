/*
 * FEATURE  : Người nhận đã lưu
 * VAI TRÒ  : Body của POST /api/beneficiaries.
 */
package com.vidien.ewallet.beneficiary.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code walletId} la {@code Long} chu khong phai {@code long}: kieu nguyen thuy
 * mac dinh ve 0 khi client quen gui truong nay, va 0 thi @Positive bat duoc -
 * nhung thong bao se la "phai lon hon 0" thay vi "thieu truong". Kieu boxed cho
 * phep phan biet "khong gui" voi "gui so sai".
 */
public record SaveBeneficiaryRequest(
        @NotNull(message = "Thiếu mã ví") @Positive(message = "Mã ví phải lớn hơn 0") Long walletId,

        @NotBlank(message = "Nhập tên gợi nhớ")
        @Size(max = 60, message = "Tên tối đa 60 ký tự") String label) {
}
