package com.vidien.ewallet.auth;

/**
 * Tra ve sau khi dang ky / dang nhap.
 *
 * <p>
 * walletId co trong day de frontend khoi phai goi them mot vong nua chi de biet vi cua minh
 * la vi nao. Khi endpoint /api/wallets/me co roi thi bo truong nay di.
 *
 * <p>
 * expiresInSeconds de client biet khi nao phai dang nhap lai, thay vi doi toi luc an 401.
 */
public record AuthResponse(String token, long expiresInSeconds, long walletId, String fullName) {
}
