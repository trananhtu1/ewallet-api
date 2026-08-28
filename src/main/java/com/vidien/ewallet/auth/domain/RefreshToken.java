package com.vidien.ewallet.auth.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mot dong trong bang refresh_tokens.
 *
 * <p>
 * {@code tokenHash} chu khong phai {@code token}: he thong KHONG BAO GIO giu ban goc. Sinh
 * xong thi tra cho client mot lan duy nhat, con lai chi giu bam - dung nhu mat khau.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /**
     * ⭐ Cot nay tung la {@code CHAR(64)} o V4, va {@code ddl-auto=validate} bat duoc ngay:
     *
     * <pre>
     * wrong column type in column [token_hash]: found [bpchar], but expecting [varchar(64)]
     * </pre>
     *
     * <p>
     * Cach chua KHONG phai be entity cho vua schema. {@code CHAR} trong Postgres <b>dem khoang
     * trang</b> va <b>so sanh bo qua khoang trang cuoi</b> - voi mot cot dung de TRA CUU BANG
     * BAM thi ca hai deu la min. V5 doi schema sang {@code VARCHAR(64)}, ly do day du o do.
     *
     * <p>
     * 📌 Day la ca do gia tri nhat cua {@code validate}: no khong chi bat loi go nham, no bat
     * ca mot lua chon kieu du lieu sai - va bat truoc khi co request nao chay qua.
     */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    /**
     * Ca chuoi xoay vong dung CHUNG mot family_id. Nho vay khi phat hien ro ri thi thu hoi
     * duoc CA CHUOI chu khong phai tung cai.
     *
     * <p>
     * Hibernate 6 anh xa {@code UUID} sang kieu {@code uuid} cua Postgres san, khong phai khai
     * bao gi them - khac han luc dung JdbcClient, o do phai tu doi kieu.
     */
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false, updatable = false, insertable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
