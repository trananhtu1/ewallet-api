/*
 * FEATURE  : KYC — nộp ảnh CCCD (giả lập)
 * VAI TRÒ  : Trạng thái hồ sơ. Khớp CHECK constraint trong V7.
 * LIÊN QUAN: KycSubmission · V7__create_kyc_submissions.sql
 */
package com.vidien.ewallet.kyc.domain;

/** Trang thai ho so KYC. Khop CHECK constraint trong V7 - lech mot chu la app khong ghi duoc. */
public enum KycStatus {
    PENDING,
    APPROVED,
    REJECTED
}
