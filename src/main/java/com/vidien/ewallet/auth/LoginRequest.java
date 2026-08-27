package com.vidien.ewallet.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * CO Y khong dat @Email hay @Size o day.
 *
 * <p>
 * Validate chat o man dang nhap chi giup ke do mat khau biet email nao dung dinh dang - vo
 * ich cho nguoi dung that, huu ich cho ke tan cong. Sai email hay sai mat khau deu tra
 * DUNG MOT thong bao nhu nhau.
 */
public record LoginRequest(
        @NotBlank(message = "Email không được để trống") String email,
        @NotBlank(message = "Mật khẩu không được để trống") String password) {
}
