/*
 * FEATURE  : Hồ sơ người dùng
 * VAI TRÒ  : Thứ client cần biết về CHÍNH mình.
 */
package com.vidien.ewallet.user.api.dto;

/**
 * ⚠️ KHONG co {@code passwordHash}, va do khong phai chuyen hien nhien: entity {@code User}
 * co truong do, nen tra thang entity ra ngoai la <b>gui ma bam mat khau cho trinh duyet</b>.
 *
 * <p>
 * Bam BCrypt khong doc nguoc ra mat khau duoc, nhung no cho ke tan cong mot thu de do OFFLINE
 * - khong bi gioi han so lan thu, khong bi khoa tai khoan. Do la ly do DTO nay ton tai thay
 * vi tra thang {@code User}.
 */
public record MeResponse(Long id, String email, String fullName, String avatarUrl) {
}
