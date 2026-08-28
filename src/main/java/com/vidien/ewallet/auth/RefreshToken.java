package com.vidien.ewallet.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot dong trong bang refresh_tokens.
 *
 * <p>
 * {@code tokenHash} chu khong phai {@code token}: he thong KHONG BAO GIO giu ban goc. Sinh
 * xong thi tra cho client mot lan duy nhat, con lai chi giu bam. Mat ban goc thi khong ai
 * lay lai duoc - dung nhu mat khau.
 */
public record RefreshToken(
        Long id,
        Long userId,
        String tokenHash,
        UUID familyId,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt) {

    /** Con dung duoc khong: chua dung, chua thu hoi, chua het han. */
    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }
}
