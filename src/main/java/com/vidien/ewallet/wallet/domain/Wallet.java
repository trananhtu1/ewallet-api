package com.vidien.ewallet.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vi tien.
 *
 * <p>
 * Truoc day la mot {@code record}. Doi sang class vi JPA doi mot constructor khong tham so va
 * kha nang gan gia tri sau khi tao - record thi bat bien, khong lam duoc.
 *
 * <p>
 * ⚠️ KHONG con {@code @JsonFormat} o day. Entity khong duoc biet gi ve JSON: no la hinh dang
 * cua BANG, khong phai hinh dang cua API. Tra entity thang ra ngoai la buoc chat hai thu do
 * vao nhau - doi mot cot la doi luon hop dong API, va nguoc lai. Luat "tien di qua JSON duoi
 * dang chuoi" chuyen sang {@code WalletResponse}.
 *
 * <p>
 * {@code @NoArgsConstructor(PROTECTED)} chu khong phai public: Hibernate can no de dung lai
 * doi tuong tu database, nhung code cua minh KHONG duoc phep tao mot cai vi rong roi gan tung
 * truong. De public la mo duong cho mot cai vi khong co user_id di lang thang.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ⚠️ De {@code Long userId} chu KHONG phai {@code @ManyToOne User user}.
     *
     * <p>
     * Lua chon co y, va la cho hay bi lam nguoc. Dat quan he doi tuong o day thi moi lan doc
     * mot cai vi, Hibernate doc luon ca User - hoac te hon, tra ve mot proxy lazy roi no chet
     * o tang JSON vi {@code open-in-view=false}.
     *
     * <p>
     * Chuyen tien khong bao gio can biet ten chu vi. Giu id la giu quan he o dung muc can
     * thiet, va la cach re nhat de KHONG BAO GIO gap N+1 o duong nong nhat cua he thong.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /**
     * precision/scale phai KHOP voi {@code NUMERIC(19,2)} o V1.
     *
     * <p>
     * Khop de {@code ddl-auto=validate} lam duoc viec cua no: doi mot ben ma quen ben kia thi
     * app chet luc khoi dong, thay vi lam tron am tham mot so tien o request dau tien.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * ⭐ Cot nay dat ra tu V1 (25/08), ghi chu luc do la "danh cho optimistic locking - dung o
     * buoi sau". Mai den gio moi thuc su dung duoc.
     *
     * <p>
     * {@code @Version} = optimistic locking: Hibernate them {@code AND version = ?} vao moi
     * cau UPDATE no sinh ra, va 0 dong bi sua thi nem {@code OptimisticLockException}. Hai
     * nguoi cung sua mot cai vi qua JPA thi nguoi thu hai bi tu choi thay vi ghi de.
     *
     * <p>
     * ⚠️ Duong CHUYEN TIEN khong dua vao co che nay - no dung pessimistic locking
     * ({@code FOR NO KEY UPDATE}) vi tien la cho tranh chap that va hau qua khong sua duoc.
     * {@code @Version} o day bao ve cac duong con lai, va la lop thu hai neu mai kia co ai
     * them mot duong ghi moi ma quen khoa.
     */
    @Version
    @Column(nullable = false)
    private Integer version;

    /**
     * Database tu dat bang {@code DEFAULT now()}, nen {@code insertable = false}: de Hibernate
     * gui mot gia tri len la co HAI dong ho cung quyet dinh mot moc thoi gian, va dong ho may
     * chu ung dung thi khong phai dong ho database.
     */
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
