package com.vidien.ewallet.beneficiary.domain.exception;

/** Khong tim thay nguoi nhan, HOAC no thuoc ve nguoi khac - xem ghi chu o service. */
public class BeneficiaryNotFoundException extends RuntimeException {
    public BeneficiaryNotFoundException() {
        super("Không tìm thấy người nhận này");
    }
}
