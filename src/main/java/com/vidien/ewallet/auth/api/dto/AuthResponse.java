package com.vidien.ewallet.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tra ve sau khi dang ky / dang nhap / doi token.
 *
 * <p>
 * walletId co trong day de frontend khoi phai goi them mot vong nua chi de biet vi cua minh
 * la vi nao. Khi endpoint /api/wallets/me co roi thi bo truong nay di.
 *
 * <p>
 * expiresInSeconds de client biet khi nao phai doi token, thay vi doi toi luc an 401.
 *
 * <p>
 * ⭐ HAI TOKEN, hai vai khac han nhau - va do la ca diem cua thiet ke nay:
 *
 * <pre>
 *   token         JWT, song 15 PHUT. Gan vao moi request. Khong cham database.
 *                 KHONG thu hoi duoc - va do la ly do no nhanh.
 *   refreshToken  chuoi ngau nhien, song 7 NGAY. CHI dung o /api/auth/refresh.
 *                 Kiem trong database moi lan -> THU HOI DUOC.
 * </pre>
 *
 * <p>
 * Khe ho con lai dung bang TTL cua access token: 15 phut. Doi lay viec moi request khac
 * khong phai hoi database. Danh doi co y.
 *
 * <p>
 * NON_NULL: {@code fullName} chi co o dang nhap/dang ky, khong co o /refresh - luc doi token
 * thi client da biet ten roi, gui lai chi ton bang thong va them mot cho de lech du lieu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, String refreshToken, long expiresInSeconds,
        long walletId, String fullName) {
}
