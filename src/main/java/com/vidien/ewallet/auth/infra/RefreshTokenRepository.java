package com.vidien.ewallet.auth.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.auth.domain.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // ⭐ @Transactional dat o TUNG METHOD cua repository, khong o service goi chung.
    //
    // Doi sang JPA lam lo ra mot xung dot voi ket luan da rut hom nay: rotate() CO Y khong
    // co @Transactional - boc transaction quanh no thi lenh thu hoi family bi rollback cuon
    // di boi chinh dong throw ngay sau. Nhung @Modifying cua JPA thi BAT BUOC phai co
    // transaction, neu khong no nem:
    //
    //   No EntityManager with actual transaction available - cannot reliably process 'flush'
    //
    // Dat o day thi ca hai deu duoc: moi cau UPDATE tu mo va tu commit mot transaction rieng,
    // dung bang hanh vi auto-commit cua JdbcClient truoc day. Va do cung la dieu DUNG ve mat
    // thiet ke - don vi nguyen tu la MOT CAU UPDATE, khong phai ca method.

    /**
     * Tim theo BAM cua token, khong theo token.
     *
     * <p>
     * KHONG loc {@code usedAt IS NULL} o day - co y. Phai lay ve CA token da dung roi thi tang
     * tren moi phan biet duoc "token nay khong ton tai" (rac) voi "token nay da dung roi"
     * (dau hieu ro ri). Loc o cau truy van la vut mat thong tin do.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * ⭐ GIANH quyen dung token nay. Tra ve 1 neu gianh duoc, 0 neu ai do gianh truoc roi.
     *
     * <p>
     * <b>Van la cau UPDATE viet tay, khong phai doc-sua-luu qua JPA</b> - va day la cho thu ba
     * co y giu SQL. Ly do da do:
     *
     * <p>
     * Doc entity ra, kiem {@code usedAt == null}, roi {@code save()} - do la doc-roi-ghi, va
     * hai request song song <b>ca hai deu doc thay "chua dung"</b> va ca hai deu duoc doi
     * token. Do that: hai lenh /refresh dong thoi -> CA HAI deu 200. Co che phat hien dung lai
     * khong no dung luc can nhat.
     *
     * <p>
     * Dieu kien {@code usedAt IS NULL} nam NGAY TRONG cau UPDATE thi database la nguoi phan
     * xu, va so dong tra ve <b>chinh la</b> cau tra loi "ai la nguoi dau tien". Cung hinh dang
     * voi {@code subtractFromBalance} tra 0 khi khong du tien.
     *
     * <p>
     * ⚠️ {@code @Version} khong thay the duoc cho nay: optimistic locking hoi "co ai vua sua
     * khong", con day hoi "co ai gianh truoc khong" - va no phai tra loi bang mot cau lenh duy
     * nhat, khong phai bang mot lan thu lai.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.usedAt = CURRENT_TIMESTAMP "
            + "WHERE t.id = :id AND t.usedAt IS NULL")
    @Transactional
    int claimForRotation(@Param("id") long id);

    /**
     * Giet CA family. Goi khi phat hien mot token da dung duoc trinh ra lan nua.
     *
     * <p>
     * Tra ve so dong bi thu hoi - con so do di thang vao nhat ky kiem toan, vi no cho biet
     * chuoi bi anh huong dai bao nhieu.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP "
            + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    @Transactional
    int revokeFamily(@Param("familyId") UUID familyId);

    /** Dang xuat: thu hoi moi token con song cua nguoi nay. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    @Transactional
    int revokeAllForUser(@Param("userId") long userId);
}
