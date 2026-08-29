package com.vidien.ewallet.kyc.api.dto;

import java.time.Instant;
import java.util.Map;
import com.vidien.ewallet.kyc.domain.KycSubmission;

/**
 * Ho so KYC nhin tu phia nguoi nop.
 *
 * <p>
 * ⚠️ {@code metadata} co tra ve, nhung <b>khong bao gio</b> co {@code sha256}. Ma bam la thu
 * noi bo dung de doi chieu va de phat hien hai nguoi nop cung mot anh; dua no ra ngoai la cho
 * nguoi ta mot cach kiem tra <i>"anh cua toi da nam trong he thong chua"</i> ma khong can nop.
 */
public record KycView(Long id, String status, Map<String, Object> metadata, String rejectReason,
        Instant createdAt, Instant reviewedAt) {

    public static KycView of(KycSubmission k) {
        Map<String, Object> loc = k.getMetadata() == null ? Map.of()
                : k.getMetadata().entrySet().stream()
                        .filter(e -> !"sha256".equals(e.getKey()))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                Map.Entry::getValue));

        return new KycView(k.getId(), k.getStatus().name(), loc, k.getRejectReason(),
                k.getCreatedAt(), k.getReviewedAt());
    }
}
