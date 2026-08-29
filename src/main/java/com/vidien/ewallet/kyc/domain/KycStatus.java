package com.vidien.ewallet.kyc.domain;

/** Trang thai ho so KYC. Khop CHECK constraint trong V7 - lech mot chu la app khong ghi duoc. */
public enum KycStatus {
    PENDING,
    APPROVED,
    REJECTED
}
