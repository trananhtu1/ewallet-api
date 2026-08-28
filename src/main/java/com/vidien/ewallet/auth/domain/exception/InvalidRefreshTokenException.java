package com.vidien.ewallet.auth.domain.exception;

/**
 * Refresh token khong dung duoc: khong ton tai, het han, da thu hoi, HOAC da dung roi.
 *
 * <p>
 * Bon ly do, MOT thong bao. Cung nguyen tac voi InvalidCredentialsException: noi ro
 * "token nay da bi dung lai" la xac nhan cho ke trom rang no da bi phat hien - va la
 * mot goi y de lan sau lam nhanh tay hon. Chi tiet o lai nhat ky kiem toan.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token khong hop le");
    }
}
