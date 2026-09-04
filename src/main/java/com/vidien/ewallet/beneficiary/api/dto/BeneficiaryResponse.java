/*
 * FEATURE  : Người nhận đã lưu
 * VAI TRÒ  : Hình dạng JSON của một người nhận.
 * LIÊN QUAN: Beneficiary · BeneficiaryController
 */
package com.vidien.ewallet.beneficiary.api.dto;

import java.time.Instant;
import com.vidien.ewallet.beneficiary.domain.Beneficiary;

/**
 * ⚠️ KHONG tra ve so du cua vi nguoi nhan, du entity co san doi tuong Wallet
 * trong tay va lay ra chi ton mot dong.
 *
 * <p>
 * Luu mot cai vi vao so dia chi la mot hanh dong mot chieu - chu vi khong he
 * dong y gi ca. Neu response kem so du thi bat ky ai <b>doan dung mot so vi</b>
 * la xem duoc so du cua nguoi la. Do la lo BOLA da bit o PR #2, quay lai duoi
 * mot cai ten than thien hon.
 */
public record BeneficiaryResponse(Long id, Long walletId, String label, Instant createdAt) {

    public static BeneficiaryResponse of(Beneficiary b) {
        // b.getWallet().getId() - day chinh la dong lam no ra N+1 neu cau truy
        // van khong JOIN FETCH. Doc mot truong cua quan he lazy la du de
        // Hibernate ban them mot cau, cho tung dong.
        return new BeneficiaryResponse(b.getId(), b.getWallet().getId(), b.getLabel(),
                b.getCreatedAt());
    }
}
