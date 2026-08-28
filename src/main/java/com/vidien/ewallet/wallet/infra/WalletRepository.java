package com.vidien.ewallet.wallet.infra;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.wallet.domain.Wallet;

/**
 * ⭐ File nay la cho ro nhat cua ca dot doi sang JPA: <b>cai gi de JPA lo, cai gi phai tu viet
 * SQL, va vi sao.</b>
 *
 * <p>
 * Ba method dau la JPA thuan - {@code findById} tu {@code JpaRepository}, khong viet dong nao.
 * Ba method sau la <b>native query</b>, va moi cai deu co mot ly do da DO DUOC chu khong phai
 * so thich:
 *
 * <ul>
 * <li>{@code lockById} - JPA khong phoi ra muc khoa {@code FOR NO KEY UPDATE}
 * <li>{@code addToBalance} / {@code subtractFromBalance} - dieu kien phai nam TRONG cau UPDATE
 * </ul>
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /** Moi nguoi dung dung MOT vi - rang buoc UNIQUE tren user_id o V1 bao dam dieu do. */
    Optional<Wallet> findByUserId(Long userId);

    /**
     * ⭐⭐ VI SAO KHONG DUNG {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} CUA JPA.
     *
     * <p>
     * Annotation do sinh ra {@code FOR UPDATE}. Va {@code FOR UPDATE} <b>xung dot voi
     * {@code FOR KEY SHARE}</b> - muc khoa Postgres tu lay khi kiem khoa ngoai luc INSERT mot
     * dong moi.
     *
     * <p>
     * Ma giua transaction chuyen tien co dung mot lenh INSERT nhu vay: dong so cai
     * {@code status = 'FAILED'} duoc ghi bang {@code REQUIRES_NEW}, va no co khoa ngoai tro
     * vao chinh hai cai vi dang bi khoa. Do that hom 28/08:
     *
     * <pre>
     * FOR UPDATE         -> HTTP 500 sau 5.74 giay, SQLSTATE 55P03 lock timeout
     * FOR NO KEY UPDATE  -> HTTP 409 sau 0.457 giay
     * </pre>
     *
     * Va Postgres <b>khong goi do la deadlock</b>: transaction ngoai khong cho mot khoa nao,
     * no cho mot loi goi Java - vong cho khong khep kin trong database nen
     * {@code deadlock_timeout} mu hoan toan.
     *
     * <p>
     * {@code FOR NO KEY UPDATE} khong xung dot voi {@code FOR KEY SHARE} nen INSERT di qua, ma
     * <b>van xung dot voi chinh no</b> - hai lenh chuyen tien song song van xep hang. Da kiem
     * ca hai ve.
     *
     * <p>
     * 📌 Cau tra loi phong van: <i>"em dung JPA cho gan het, tru dung mot cho can mot muc khoa
     * JPA khong phoi ra."</i>
     *
     * <p>
     * ⚠️ Phai goi theo THU TU ID TANG DAN khi khoa nhieu vi. Hai lenh nguoc chieu (1→2 va 2→1)
     * chay cung luc, moi ben khoa mot vi roi cho ben kia = deadlock that su. Khoa theo thu tu
     * co dinh thi tinh huong do khong ton tai.
     */
    @Query(value = "SELECT id FROM wallets WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("id") long id);

    /**
     * Cong tien BANG SQL, khong doc so du ra Java roi cong roi ghi lai.
     *
     * <p>
     * Doc-roi-ghi la "lost update": hai request song song cung doc duoc 100, cung ghi 150 - nap
     * hai lan ma chi vao mot lan. De database tu cong thi no khoa dong do trong luc UPDATE.
     *
     * <p>
     * ⚠️ {@code clearAutomatically = true}: cau UPDATE nay di THANG xuong database, khong qua
     * persistence context. Neu context dang giu mot ban sao cua dong vi do thi ban sao ay gio
     * CU - doc lai trong cung transaction se ra so du truoc khi cong. Va vi {@code version} bi
     * tang o day ma Hibernate khong biet, lan flush sau se nem OptimisticLockException cho mot
     * dung do khong he ton tai.
     *
     * <p>
     * {@code flushAutomatically = true}: day het thay doi dang cho xuong database TRUOC khi
     * chay cau nay, neu khong thi thu tu ghi that co the khac thu tu trong code.
     *
     * <p>
     * Tra ve so dong bi sua: 0 nghia la khong co vi nao mang id do.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE wallets
            SET balance = balance + :amount,
                version = version + 1
            WHERE id = :id
            """, nativeQuery = true)
    int addToBalance(@Param("id") long walletId, @Param("amount") BigDecimal amount);

    /**
     * Tru tien. Dieu kien {@code balance >= :amount} nam NGAY TRONG cau UPDATE, khong phai kiem
     * o Java.
     *
     * <p>
     * Tra ve 0 khi: khong co vi nao id do, HOAC so du khong du. Tang service da khoa dong tu
     * truoc nen phan biet duoc hai truong hop, nhung dieu kien nay van giu lai lam lop chan
     * thu hai - va no la thu duy nhat con dung neu mai kia co ai goi thang repository.
     *
     * <p>
     * 📌 Day cung la ly do khong dung {@code @Version} cho duong nay: optimistic locking bao
     * "co ai vua sua khong", con dieu kien nay bao "co du tien khong" - hai cau hoi khac nhau,
     * va cau thu hai moi la cau ve tien.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE wallets
            SET balance = balance - :amount,
                version = version + 1
            WHERE id = :id AND balance >= :amount
            """, nativeQuery = true)
    int subtractFromBalance(@Param("id") long walletId, @Param("amount") BigDecimal amount);

    // Tao vi moi thi dung save() cua JpaRepository - xem WalletService.createForUser().
    // Khong viet native INSERT ... RETURNING o day: @Modifying chi tra ve SO DONG bi sua,
    // khong lay duoc id vua sinh, va di duong vong de lay lai thi dai hon save().
}
