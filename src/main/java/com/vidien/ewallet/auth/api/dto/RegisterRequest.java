package com.vidien.ewallet.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @NotBlank chu khong phai @NotNull: chuoi toan dau cach cung phai bi chan.
 *
 * <p>
 * Do dai mat khau toi thieu 8, toi da 72. Con so 72 khong phai bia: BCrypt chi doc 72 BYTE
 * dau tien va lang le bo phan con lai. Mat khau 100 ky tu va mat khau 200 ky tu cung tien to
 * se dang nhap duoc bang nhau - khong loi nao bao. Chan o day de chuyen do khong xay ra.
 */
public record RegisterRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên quá dài")
        String fullName) {
}
