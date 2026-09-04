package com.vidien.ewallet.beneficiary.domain.exception;

/** Luu mot vi da co trong so dia chi. */
public class BeneficiaryAlreadySavedException extends RuntimeException {
    public BeneficiaryAlreadySavedException() {
        super("Ví này đã có trong danh sách người nhận");
    }
}
