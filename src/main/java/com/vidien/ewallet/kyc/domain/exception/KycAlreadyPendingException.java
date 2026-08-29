package com.vidien.ewallet.kyc.domain.exception;

/**
 * Nguoi nay da co mot ho so dang cho duyet.
 *
 * <p>
 * 409 chu khong 400: file gui len hoan toan hop le, chi la <b>trang thai hien tai</b> cua he
 * thong khong cho phep. 400 se khien nguoi dung tuong minh chup anh sai va chup lai - vo ich.
 * Cung ly do voi INSUFFICIENT_FUNDS.
 */
public class KycAlreadyPendingException extends RuntimeException {

    public KycAlreadyPendingException(long userId) {
        super("Nguoi dung " + userId + " da co ho so KYC dang cho duyet");
    }
}
