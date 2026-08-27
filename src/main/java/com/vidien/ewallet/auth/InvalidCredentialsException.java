package com.vidien.ewallet.auth;

/**
 * Sai email HOAC sai mat khau - co y khong phan biet.
 *
 * <p>
 * Tra "email khong ton tai" rieng va "mat khau sai" rieng la tang cho ke tan cong mot cong
 * cu do xem email nao co that trong he thong (user enumeration). Ca hai truong hop tra dung
 * mot thong bao va dung mot ma loi.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Email hoac mat khau khong dung");
    }
}
