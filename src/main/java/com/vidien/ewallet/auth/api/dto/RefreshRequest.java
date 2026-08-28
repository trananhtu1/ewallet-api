package com.vidien.ewallet.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh token di trong BODY chu khong phai header Authorization.
 *
 * <p>
 * Header Authorization danh cho ACCESS token, va o buoc nay access token da het han - gui no
 * kem la vo nghia. Tach hai thu ra cung tranh duoc mot loi de mac: proxy, log truy cap va
 * cong cu giam sat thuong ghi lai header Authorization; refresh token song 7 ngay nen no la
 * thu it dang bi ghi vao log nhat.
 */
public record RefreshRequest(
        @NotBlank(message = "Thiếu refresh token") String refreshToken) {
}
