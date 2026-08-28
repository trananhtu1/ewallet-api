package com.vidien.ewallet.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient db;

    public RefreshTokenRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * ⚠️ expiresAt phai doi sang OffsetDateTime truoc khi truyen xuong.
     *
     * <p>
     * Do that, va thong bao rat ro rang so voi hau het loi JDBC:
     *
     * <pre>
     * PSQLException: Can't infer the SQL type to use for an instance of java.time.Instant.
     * </pre>
     *
     * <p>
     * Cho phan truc giac: doc TIMESTAMPTZ ra {@code Instant} thi chay tot - moi record trong
     * project nay deu lam vay. Nhung DOC va GHI khong doi xung: luc doc, driver biet kieu cot
     * nen doi duoc; luc ghi, no phai suy ra kieu SQL tu kieu Java, ma bang anh xa cua JDBC 4.2
     * chi co {@code OffsetDateTime} tro toi TIMESTAMP WITH TIME ZONE. {@code Instant} khong
     * nam trong bang do.
     *
     * <p>
     * ZoneOffset.UTC chu khong phai mui gio may chu: {@code Instant} von la mot moc tren truc
     * thoi gian, khong mang mui gio nao. Gan mui gio may chu vao la them mot thong tin khong
     * co that, va la thu se sai khi deploy sang mot may cau hinh khac.
     */
    public void insert(long userId, String tokenHash, UUID familyId, Instant expiresAt) {
        db.sql("""
                INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
                VALUES (:userId, :hash, :family, :expiresAt)
                """)
                .param("userId", userId)
                .param("hash", tokenHash)
                .param("family", familyId)
                .param("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .update();
    }

    /**
     * Tim theo BAM cua token, khong theo token.
     *
     * <p>
     * KHONG loc {@code used_at IS NULL} o day - co y. Phai lay ve CA token da dung roi thi
     * tang tren moi phan biet duoc "token nay khong ton tai" (rac) voi "token nay da dung roi"
     * (dau hieu ro ri). Loc o SQL la vut mat thong tin do.
     */
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return db.sql("""
                SELECT id, user_id, token_hash, family_id, issued_at, expires_at,
                       used_at, revoked_at
                FROM refresh_tokens
                WHERE token_hash = :hash
                """)
                .param("hash", tokenHash)
                .query(RefreshToken.class)
                .optional();
    }

    /** Danh dau da dung. Mot refresh token chi duoc doi lay token moi DUNG MOT LAN. */
    public void markUsed(long id) {
        db.sql("UPDATE refresh_tokens SET used_at = now() WHERE id = :id AND used_at IS NULL")
                .param("id", id)
                .update();
    }

    /**
     * ⭐ Giet CA family. Goi khi phat hien mot token da dung lai duoc trinh ra lan nua.
     *
     * <p>
     * Tra ve so dong bi thu hoi - con so do di thang vao nhat ky kiem toan, vi no cho biet
     * chuoi bi anh huong dai bao nhieu.
     */
    public int revokeFamily(UUID familyId) {
        return db.sql("""
                UPDATE refresh_tokens SET revoked_at = now()
                WHERE family_id = :family AND revoked_at IS NULL
                """)
                .param("family", familyId)
                .update();
    }

    /** Dang xuat: thu hoi moi token con song cua nguoi nay. */
    public int revokeAllForUser(long userId) {
        return db.sql("""
                UPDATE refresh_tokens SET revoked_at = now()
                WHERE user_id = :userId AND revoked_at IS NULL
                """)
                .param("userId", userId)
                .update();
    }
}
